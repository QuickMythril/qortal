package org.qortal.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.data.block.BlockData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.network.Network;
import org.qortal.network.Peer;
import org.qortal.network.message.GetTransactionMessage;
import org.qortal.network.message.Message;
import org.qortal.network.message.TransactionMessage;
import org.qortal.network.message.TransactionSignaturesMessage;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.transaction.Transaction;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class TransactionImporter extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(TransactionImporter.class);
    private static final int MAX_INCOMING_TRANSACTIONS = 5000;
    public static final long INVALID_TRANSACTION_STALE_TIMEOUT = 30 * 60 * 1000L;
    public static final long INVALID_TRANSACTION_RECHECK_INTERVAL = 60 * 60 * 1000L;
    public static final long EXPIRED_TRANSACTION_RECHECK_INTERVAL = 10 * 60 * 1000L;

    private static TransactionImporter instance;
    private volatile boolean isStopping = false;
    private final Map<TransactionData, Boolean> incomingTransactions = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Long> invalidUnconfirmedTransactions = Collections.synchronizedMap(new HashMap<>());
    public static List<TransactionData> unconfirmedTransactionsCache = null;

    public static synchronized TransactionImporter getInstance() {
        if (instance == null) {
            instance = new TransactionImporter();
        }
        return instance;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Transaction Importer");
        try {
            while (!Controller.isStopping()) {
                Thread.sleep(500L);
                validateTransactionsInQueue();
                importTransactionsInQueue();
                cleanupInvalidTransactionsList(NTP.getTime());
            }
        } catch (InterruptedException e) {
            // Fall through to exit thread
        }
    }

    public void shutdown() {
        isStopping = true;
        this.interrupt();
    }

    private boolean incomingTransactionQueueContains(byte[] signature) {
        synchronized (incomingTransactions) {
            return incomingTransactions.keySet().stream().anyMatch(t -> Arrays.equals(t.getSignature(), signature));
        }
    }

    private void removeIncomingTransaction(byte[] signature) {
        incomingTransactions.keySet().removeIf(t -> Arrays.equals(t.getSignature(), signature));
    }

    private List<TransactionData> getCachedSigValidTransactions() {
        synchronized (this.incomingTransactions) {
            return this.incomingTransactions.entrySet().stream()
                    .filter(t -> Boolean.TRUE.equals(t.getValue()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }

    private void validateTransactionsInQueue() {
        if (this.incomingTransactions.isEmpty()) return;

        try (final Repository repository = RepositoryManager.getRepository()) {
            Map<TransactionData, Boolean> incomingTransactionsCopy = Map.copyOf(this.incomingTransactions);
            int unvalidatedCount = Collections.frequency(incomingTransactionsCopy.values(), Boolean.FALSE);
            int validatedCount = 0;

            List<Transaction> sigValidTransactions = new ArrayList<>();
            List<byte[]> newlyValidSignatures = new ArrayList<>();

            boolean isLiteNode = Settings.getInstance().isLite();
            BlockData latestBlock = Controller.getInstance().getChainTip();
            Long now = NTP.getTime();

            if (now == null) return;

            for (Map.Entry<TransactionData, Boolean> transactionEntry : incomingTransactionsCopy.entrySet()) {
                if (isStopping) return;

                TransactionData transactionData = transactionEntry.getKey();
                Transaction transaction = Transaction.fromData(repository, transactionData);
                String signature58 = Base58.encode(transactionData.getSignature());

                if (latestBlock != null && transaction.getDeadline() <= latestBlock.getTimestamp()) {
                    LOGGER.debug("Removing expired {} transaction {} from import queue", transactionData.getType().name(), signature58);
                    removeIncomingTransaction(transactionData.getSignature());
                    invalidUnconfirmedTransactions.put(signature58, (now + EXPIRED_TRANSACTION_RECHECK_INTERVAL));
                    continue;
                }

                if (!Boolean.TRUE.equals(transactionEntry.getValue())) {
                    if (isLiteNode) {
                        sigValidTransactions.add(transaction);
                        newlyValidSignatures.add(transactionData.getSignature());
                        incomingTransactions.computeIfPresent(transactionData, (k, v) -> Boolean.TRUE);
                        continue;
                    }

                    if (!transaction.isSignatureValid()) {
                        LOGGER.debug("Ignoring {} transaction {} with invalid signature", transactionData.getType().name(), signature58);
                        removeIncomingTransaction(transactionData.getSignature());
                        invalidUnconfirmedTransactions.put(signature58, now + INVALID_TRANSACTION_RECHECK_INTERVAL);
                        continue;
                    }

                    validatedCount++;
                    incomingTransactions.computeIfPresent(transactionData, (k, v) -> Boolean.TRUE);
                    newlyValidSignatures.add(transactionData.getSignature());
                }

                sigValidTransactions.add(transaction);
            }

            LOGGER.debug("Finished validating signatures in incoming transactions queue (valid this round: {}, total pending import: {})...", validatedCount, sigValidTransactions.size());

        } catch (DataException e) {
            LOGGER.error("Repository issue while processing incoming transactions", e);
        }
    }

    private void importTransactionsInQueue() {
        List<TransactionData> sigValidTransactions = this.getCachedSigValidTransactions();
        if (sigValidTransactions.isEmpty()) return;

        if (Synchronizer.getInstance().isSyncRequested() || Synchronizer.getInstance().isSynchronizing()) {
            return;
        }

        ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
        if (!blockchainLock.tryLock()) {
            LOGGER.debug("Too busy to import incoming transactions queue");
            return;
        }

        LOGGER.debug("Importing incoming transactions queue (size {})...", sigValidTransactions.size());
        int processedCount = 0;

        try (final Repository repository = RepositoryManager.getRepository()) {
            List<TransactionData> unconfirmedTransactions = repository.getTransactionRepository().getUnconfirmedTransactions();
            unconfirmedTransactions.removeIf(t -> t.getType() == Transaction.TransactionType.CHAT);
            unconfirmedTransactionsCache = unconfirmedTransactions;

            List<byte[]> newlyImportedSignatures = new ArrayList<>();

            for (TransactionData transactionData : sigValidTransactions) {
                if (isStopping) return;

                if (Synchronizer.getInstance().isSyncRequestPending()) {
                    LOGGER.debug("Breaking out of transaction importing with {} remaining, because a sync request is pending", sigValidTransactions.size());
                    return;
                }

                Transaction transaction = Transaction.fromData(repository, transactionData);
                Transaction.ValidationResult validationResult = transaction.importAsUnconfirmed();
                processedCount++;

                switch (validationResult) {
                    case TRANSACTION_ALREADY_EXISTS:
                        LOGGER.trace(() -> String.format("Ignoring existing transaction %s", Base58.encode(transactionData.getSignature())));
                        break;
                    case NO_BLOCKCHAIN_LOCK:
                        LOGGER.trace(() -> String.format("Couldn't lock blockchain to import unconfirmed transaction %s", Base58.encode(transactionData.getSignature())));
                        break;
                    case OK:
                        LOGGER.debug(() -> String.format("Imported %s transaction %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature())));
                        if (transactionData.getType() != Transaction.TransactionType.CHAT && unconfirmedTransactionsCache != null) {
                            unconfirmedTransactionsCache.add(transactionData);
                        }
                        newlyImportedSignatures.add(transactionData.getSignature());
                        break;
                    default:
                        handleInvalidTransaction(validationResult, transactionData);
                }

                removeIncomingTransaction(transactionData.getSignature());
            }

            if (!newlyImportedSignatures.isEmpty()) {
                LOGGER.debug("Broadcasting {} newly imported signatures", newlyImportedSignatures.size());
                Message newTransactionSignatureMessage = new TransactionSignaturesMessage(newlyImportedSignatures);
                Network.getInstance().broadcast(broadcastPeer -> newTransactionSignatureMessage);
            }
        } catch (DataException e) {
            LOGGER.error("Repository issue while importing incoming transactions", e);
        } finally {
            blockchainLock.unlock();
            unconfirmedTransactionsCache = null;
        }
    }

    private void handleInvalidTransaction(Transaction.ValidationResult validationResult, TransactionData transactionData) {
        final String signature58 = Base58.encode(transactionData.getSignature());
        LOGGER.debug(() -> String.format("Ignoring invalid (%s) %s transaction %s", validationResult.name(), transactionData.getType().name(), signature58));

        Long now = NTP.getTime();
        if (now != null && now - transactionData.getTimestamp() > INVALID_TRANSACTION_STALE_TIMEOUT) {
            Long expiryLength = (validationResult == Transaction.ValidationResult.TIMESTAMP_TOO_OLD) ? EXPIRED_TRANSACTION_RECHECK_INTERVAL : INVALID_TRANSACTION_RECHECK_INTERVAL;
            Long expiry = now + expiryLength;
            invalidUnconfirmedTransactions.put(signature58, expiry);
        }
    }

    private void cleanupInvalidTransactionsList(Long now) {
        if (now == null) return;

        invalidUnconfirmedTransactions.entrySet().removeIf(entry -> now > entry.getValue());
    }
}
