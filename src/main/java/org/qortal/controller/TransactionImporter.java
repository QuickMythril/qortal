package org.qortal.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.data.transaction.TransactionData;
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
import org.qortal.transform.TransformationException;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class TransactionImporter extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(TransactionImporter.class);

    private static TransactionImporter instance;
    private volatile boolean isStopping = false;

    private static final int MAX_INCOMING_TRANSACTIONS = 5000;

    /** Minimum time before considering an invalid unconfirmed transaction as "stale" */
    public static final long INVALID_TRANSACTION_STALE_TIMEOUT = 30 * 60 * 1000L; // ms
    /** Minimum frequency to re-request stale unconfirmed transactions from peers, to recheck validity */
    public static final long INVALID_TRANSACTION_RECHECK_INTERVAL = 60 * 60 * 1000L; // ms\
    /** Minimum frequency to re-request expired unconfirmed transactions from peers, to recheck validity
     * This mainly exists to stop expired transactions from bloating the list */
    public static final long EXPIRED_TRANSACTION_RECHECK_INTERVAL = 10 * 60 * 1000L; // ms


    /** Map of incoming transaction that are in the import queue. Key is transaction data, value is whether signature has been validated. */
    private final Map<TransactionData, Boolean> incomingTransactions = Collections.synchronizedMap(new HashMap<>());

    /** Map of recent invalid unconfirmed transactions. Key is base58 transaction signature, value is do-not-request expiry timestamp. */
    private final Map<String, Long> invalidUnconfirmedTransactions = Collections.synchronizedMap(new HashMap<>());


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
                Thread.sleep(1000L);

                // Process incoming transactions queue
                processIncomingTransactionsQueue();
                // Clean up invalid incoming transactions list
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


    // Incoming transactions queue

    private boolean incomingTransactionQueueContains(byte[] signature) {
        synchronized (incomingTransactions) {
            return incomingTransactions.keySet().stream().anyMatch(t -> Arrays.equals(t.getSignature(), signature));
        }
    }

    private void removeIncomingTransaction(byte[] signature) {
        incomingTransactions.keySet().removeIf(t -> Arrays.equals(t.getSignature(), signature));
    }

    private void processIncomingTransactionsQueue() {
        if (this.incomingTransactions.isEmpty()) {
            // Nothing to do?
            return;
        }

        try (final Repository repository = RepositoryManager.getRepository()) {
            // Take a snapshot of incomingTransactions, so we don't need to lock it while processing
            Map<TransactionData, Boolean> incomingTransactionsCopy = Map.copyOf(this.incomingTransactions);

            int unvalidatedCount = Collections.frequency(incomingTransactionsCopy.values(), Boolean.FALSE);
            int validatedCount = 0;

            if (unvalidatedCount > 0) {
                LOGGER.debug("Validating signatures in incoming transactions queue (size {})...", unvalidatedCount);
            }

            List<Transaction> sigValidTransactions = new ArrayList<>();

            boolean isLiteNode = Settings.getInstance().isLite();

            // Signature validation round - does not require blockchain lock
            for (Map.Entry<TransactionData, Boolean> transactionEntry : incomingTransactionsCopy.entrySet()) {
                // Quick exit?
                if (isStopping) {
                    return;
                }

                TransactionData transactionData = transactionEntry.getKey();
                Transaction transaction = Transaction.fromData(repository, transactionData);

                // Only validate signature if we haven't already done so
                Boolean isSigValid = transactionEntry.getValue();
                if (!Boolean.TRUE.equals(isSigValid)) {
                    if (isLiteNode) {
                        // Lite nodes can't easily validate transactions, so for now we will have to assume that everything is valid
                        sigValidTransactions.add(transaction);
                        continue;
                    }

                    if (!transaction.isSignatureValid()) {
                        String signature58 = Base58.encode(transactionData.getSignature());

                        LOGGER.trace("Ignoring {} transaction {} with invalid signature", transactionData.getType().name(), signature58);
                        removeIncomingTransaction(transactionData.getSignature());

                        // Also add to invalidIncomingTransactions map
                        Long now = NTP.getTime();
                        if (now != null) {
                            Long expiry = now + INVALID_TRANSACTION_RECHECK_INTERVAL;
                            LOGGER.trace("Adding stale invalid transaction {} to invalidUnconfirmedTransactions...", signature58);
                            // Add to invalidUnconfirmedTransactions so that we don't keep requesting it
                            invalidUnconfirmedTransactions.put(signature58, expiry);
                        }

                        continue;
                    }
                    else {
                        // Count the number that were validated in this round, for logging purposes
                        validatedCount++;
                    }

                    // Add mark signature as valid if transaction still exists in import queue
                    incomingTransactions.computeIfPresent(transactionData, (k, v) -> Boolean.TRUE);
                } else {
                    LOGGER.trace(() -> String.format("Transaction %s known to have valid signature", Base58.encode(transactionData.getSignature())));
                }

                // Signature valid - add to shortlist
                sigValidTransactions.add(transaction);
            }

            if (unvalidatedCount > 0) {
                LOGGER.debug("Finished validating signatures in incoming transactions queue (valid this round: {}, total pending import: {})...", validatedCount, sigValidTransactions.size());
            }

            if (sigValidTransactions.isEmpty()) {
                // Don't bother locking if there are no new transactions to process
                return;
            }

            if (Synchronizer.getInstance().isSyncRequested() || Synchronizer.getInstance().isSynchronizing()) {
                // Prioritize syncing, and don't attempt to lock
                // Signature validity is retained in the incomingTransactions map, to avoid the above work being wasted
                return;
            }

            try {
                ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
                if (!blockchainLock.tryLock(2, TimeUnit.SECONDS)) {
                    // Signature validity is retained in the incomingTransactions map, to avoid the above work being wasted
                    LOGGER.debug("Too busy to process incoming transactions queue");
                    return;
                }
            } catch (InterruptedException e) {
                LOGGER.debug("Interrupted when trying to acquire blockchain lock");
                return;
            }

            LOGGER.debug("Processing incoming transactions queue (size {})...", sigValidTransactions.size());

            // Import transactions with valid signatures
            try {
                for (int i = 0; i < sigValidTransactions.size(); ++i) {
                    if (isStopping) {
                        return;
                    }

                    if (Synchronizer.getInstance().isSyncRequestPending()) {
                        LOGGER.debug("Breaking out of transaction processing with {} remaining, because a sync request is pending", sigValidTransactions.size() - i);
                        return;
                    }

                    Transaction transaction = sigValidTransactions.get(i);
                    TransactionData transactionData = transaction.getTransactionData();

                    Transaction.ValidationResult validationResult = transaction.importAsUnconfirmed();

                    switch (validationResult) {
                        case TRANSACTION_ALREADY_EXISTS: {
                            LOGGER.trace(() -> String.format("Ignoring existing transaction %s", Base58.encode(transactionData.getSignature())));
                            break;
                        }

                        case NO_BLOCKCHAIN_LOCK: {
                            // Is this even possible considering we acquired blockchain lock above?
                            LOGGER.trace(() -> String.format("Couldn't lock blockchain to import unconfirmed transaction %s", Base58.encode(transactionData.getSignature())));
                            break;
                        }

                        case OK: {
                            LOGGER.debug(() -> String.format("Imported %s transaction %s", transactionData.getType().name(), Base58.encode(transactionData.getSignature())));
                            break;
                        }

                        // All other invalid cases:
                        default: {
                            final String signature58 = Base58.encode(transactionData.getSignature());
                            LOGGER.trace(() -> String.format("Ignoring invalid (%s) %s transaction %s", validationResult.name(), transactionData.getType().name(), signature58));

                            Long now = NTP.getTime();
                            if (now != null && now - transactionData.getTimestamp() > INVALID_TRANSACTION_STALE_TIMEOUT) {
                                Long expiryLength = INVALID_TRANSACTION_RECHECK_INTERVAL;

                                if (validationResult == Transaction.ValidationResult.TIMESTAMP_TOO_OLD) {
                                    // Use shorter recheck interval for expired transactions
                                    expiryLength = EXPIRED_TRANSACTION_RECHECK_INTERVAL;
                                }

                                Long expiry = now + expiryLength;
                                LOGGER.trace("Adding stale invalid transaction {} to invalidUnconfirmedTransactions...", signature58);
                                // Invalid, unconfirmed transaction has become stale - add to invalidUnconfirmedTransactions so that we don't keep requesting it
                                invalidUnconfirmedTransactions.put(signature58, expiry);
                            }
                        }
                    }

                    // Transaction has been processed, even if only to reject it
                    removeIncomingTransaction(transactionData.getSignature());
                }
            } finally {
                LOGGER.debug("Finished processing incoming transactions queue");
                ReentrantLock blockchainLock = Controller.getInstance().getBlockchainLock();
                blockchainLock.unlock();
            }
        } catch (DataException e) {
            LOGGER.error("Repository issue while processing incoming transactions", e);
        }
    }

    private void cleanupInvalidTransactionsList(Long now) {
        if (now == null) {
            return;
        }
        // Periodically remove invalid unconfirmed transactions from the list, so that they can be fetched again
        invalidUnconfirmedTransactions.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue() < now);
    }


    // Network handlers

    public void onNetworkTransactionMessage(Peer peer, Message message) {
        TransactionMessage transactionMessage = (TransactionMessage) message;
        TransactionData transactionData = transactionMessage.getTransactionData();

        if (this.incomingTransactions.size() < MAX_INCOMING_TRANSACTIONS) {
            synchronized (this.incomingTransactions) {
                if (!incomingTransactionQueueContains(transactionData.getSignature())) {
                    this.incomingTransactions.put(transactionData, Boolean.FALSE);
                }
            }
        }
    }

    public void onNetworkGetTransactionMessage(Peer peer, Message message) {
        GetTransactionMessage getTransactionMessage = (GetTransactionMessage) message;
        byte[] signature = getTransactionMessage.getSignature();

        try (final Repository repository = RepositoryManager.getRepository()) {
            TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
            if (transactionData == null) {
                LOGGER.debug(() -> String.format("Ignoring GET_TRANSACTION request from peer %s for unknown transaction %s", peer, Base58.encode(signature)));
                // Send no response at all???
                return;
            }

            Message transactionMessage = new TransactionMessage(transactionData);
            transactionMessage.setId(message.getId());
            if (!peer.sendMessage(transactionMessage))
                peer.disconnect("failed to send transaction");
        } catch (DataException e) {
            LOGGER.error(String.format("Repository issue while sending transaction %s to peer %s", Base58.encode(signature), peer), e);
        } catch (TransformationException e) {
            LOGGER.error(String.format("Serialization issue while sending transaction %s to peer %s", Base58.encode(signature), peer), e);
        }
    }

    public void onNetworkGetUnconfirmedTransactionsMessage(Peer peer, Message message) {
        try (final Repository repository = RepositoryManager.getRepository()) {
            List<byte[]> signatures = Collections.emptyList();

            // If we're NOT up-to-date then don't send out unconfirmed transactions
            // as it's possible they are already included in a later block that we don't have.
            if (Controller.getInstance().isUpToDate())
                signatures = repository.getTransactionRepository().getUnconfirmedTransactionSignatures();

            Message transactionSignaturesMessage = new TransactionSignaturesMessage(signatures);
            if (!peer.sendMessage(transactionSignaturesMessage))
                peer.disconnect("failed to send unconfirmed transaction signatures");
        } catch (DataException e) {
            LOGGER.error(String.format("Repository issue while sending unconfirmed transaction signatures to peer %s", peer), e);
        }
    }

    public void onNetworkTransactionSignaturesMessage(Peer peer, Message message) {
        TransactionSignaturesMessage transactionSignaturesMessage = (TransactionSignaturesMessage) message;
        List<byte[]> signatures = transactionSignaturesMessage.getSignatures();

        try (final Repository repository = RepositoryManager.getRepository()) {
            for (byte[] signature : signatures) {
                String signature58 = Base58.encode(signature);
                if (invalidUnconfirmedTransactions.containsKey(signature58)) {
                    // Previously invalid transaction - don't keep requesting it
                    // It will be periodically removed from invalidUnconfirmedTransactions to allow for rechecks
                    continue;
                }

                // Ignore if this transaction is in the queue
                if (incomingTransactionQueueContains(signature)) {
                    LOGGER.trace(() -> String.format("Ignoring existing queued transaction %s from peer %s", Base58.encode(signature), peer));
                    continue;
                }

                // Do we have it already? (Before requesting transaction data itself)
                if (repository.getTransactionRepository().exists(signature)) {
                    LOGGER.trace(() -> String.format("Ignoring existing transaction %s from peer %s", Base58.encode(signature), peer));
                    continue;
                }

                // Check isInterrupted() here and exit fast
                if (Thread.currentThread().isInterrupted())
                    return;

                // Fetch actual transaction data from peer
                Message getTransactionMessage = new GetTransactionMessage(signature);
                if (!peer.sendMessage(getTransactionMessage)) {
                    peer.disconnect("failed to request transaction");
                    return;
                }
            }
        } catch (DataException e) {
            LOGGER.error(String.format("Repository issue while processing unconfirmed transactions from peer %s", peer), e);
        }
    }

}
