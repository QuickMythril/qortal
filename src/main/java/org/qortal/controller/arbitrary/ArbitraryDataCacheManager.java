package org.qortal.controller.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.api.resource.TransactionsResource;
import org.qortal.controller.Controller;
import org.qortal.data.arbitrary.ArbitraryResourceData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.gui.SplashFrame;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;
import org.qortal.transaction.ArbitraryTransaction;
import org.qortal.transaction.Transaction;
import org.qortal.utils.Base58;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ArbitraryDataCacheManager extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataCacheManager.class);

    private static final long SLEEP_INTERVAL_MS = 500L;
    private static final int BATCH_SIZE = 100;

    private static ArbitraryDataCacheManager instance;

    private volatile boolean isStopping = false;

    /** Queue of arbitrary transactions that require cache updates */
    private final List<ArbitraryTransactionData> updateQueue = new CopyOnWriteArrayList<>();

    /** Singleton instance access */
    public static synchronized ArbitraryDataCacheManager getInstance() {
        if (instance == null) {
            instance = new ArbitraryDataCacheManager();
        }
        return instance;
    }

    private ArbitraryDataCacheManager() {
        // Private constructor for singleton
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Arbitrary Data Cache Manager");
        Thread.currentThread().setPriority(NORM_PRIORITY);

        try {
            while (!isStopping && !Controller.isStopping()) {
                Thread.sleep(SLEEP_INTERVAL_MS);
                processUpdateQueue();
            }
        } catch (InterruptedException e) {
            LOGGER.info("Cache Manager interrupted, preparing to stop.");
            Thread.currentThread().interrupt(); // Restore interrupt flag
        } finally {
            // Ensure the queue is processed before shutting down
            processUpdateQueue();
        }
    }

    public void shutdown() {
        isStopping = true;
        this.interrupt();
    }

    private void processUpdateQueue() {
        if (updateQueue.isEmpty()) {
            return;
        }

        try (Repository repository = RepositoryManager.getRepository()) {
            // Snapshot the queue to avoid locking during processing
            List<ArbitraryTransactionData> snapshot = List.copyOf(updateQueue);

            for (ArbitraryTransactionData transactionData : snapshot) {
                processTransaction(repository, transactionData);
                updateQueue.remove(transactionData); // Remove from queue after processing
            }
        } catch (DataException e) {
            LOGGER.error("Repository issue while processing arbitrary resource cache updates", e);
        }
    }

    private void processTransaction(Repository repository, ArbitraryTransactionData transactionData) {
        try {
            LOGGER.debug("Processing transaction {} in cache manager...", Base58.encode(transactionData.getSignature()));

            // Update arbitrary resource caches
            ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);
            arbitraryTransaction.updateArbitraryResourceCache(repository);
            arbitraryTransaction.updateArbitraryMetadataCache(repository);
            repository.saveChanges();

            // Update resource statuses separately
            arbitraryTransaction.updateArbitraryResourceStatus(repository);
            repository.saveChanges();

            LOGGER.debug("Completed processing transaction {}", Base58.encode(transactionData.getSignature()));
        } catch (DataException e) {
            LOGGER.error("Error processing transaction {}: {}", Base58.encode(transactionData.getSignature()), e.getMessage(), e);
            repository.discardChanges();
        }
    }

    public void addToUpdateQueue(ArbitraryTransactionData transactionData) {
        updateQueue.add(transactionData);
        LOGGER.debug("Transaction {} added to update queue", Base58.encode(transactionData.getSignature()));
    }

    public boolean needsCacheRebuild(Repository repository) throws DataException {
        List<ArbitraryTransactionData> oldestTransactions = repository.getArbitraryRepository()
                .getArbitraryTransactions(true, 1, 0, false);

        if (oldestTransactions == null || oldestTransactions.isEmpty()) {
            LOGGER.debug("No arbitrary transactions available for cache rebuild.");
            return false;
        }

        ArbitraryTransactionData oldestTransaction = oldestTransactions.get(0);
        ArbitraryResourceData cachedResource = repository.getArbitraryRepository()
                .getArbitraryResource(oldestTransaction.getService(), oldestTransaction.getName(), oldestTransaction.getIdentifier());

        if (cachedResource != null) {
            LOGGER.debug("Cache already built for arbitrary resources.");
            return false;
        }

        return true;
    }

    public boolean buildCache(Repository repository, boolean forceRebuild) throws DataException {
        if (Settings.getInstance().isLite()) {
            LOGGER.warn("Lite nodes cannot build caches.");
            return false;
        }

        if (!needsCacheRebuild(repository) && !forceRebuild) {
            LOGGER.debug("Arbitrary resources cache already built.");
            return false;
        }

        LOGGER.info("Building arbitrary resources cache...");
        SplashFrame.getInstance().updateStatus("Building QDN cache - please wait...");

        try {
            processTransactionsInBatches(repository);
            refreshStatuses(repository);

            LOGGER.info("Cache build completed successfully.");
            return true;
        } catch (DataException e) {
            LOGGER.error("Error building cache: {}", e.getMessage(), e);
            repository.discardChanges();
            throw new DataException("Cache build failed.");
        }
    }

    private void processTransactionsInBatches(Repository repository) throws DataException {
        int offset = 0;

        while (!Controller.isStopping()) {
            LOGGER.info("Processing transactions {} - {}", offset, offset + BATCH_SIZE - 1);

            List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(
                    null, null, null,
                    List.of(Transaction.TransactionType.ARBITRARY),
                    null, null, null,
                    TransactionsResource.ConfirmationStatus.BOTH,
                    BATCH_SIZE, offset, false
            );

            if (signatures.isEmpty()) {
                break; // No more transactions to process
            }

            for (byte[] signature : signatures) {
                ArbitraryTransactionData transactionData = (ArbitraryTransactionData)
                        repository.getTransactionRepository().fromSignature(signature);

                if (transactionData.getService() != null) {
                    ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);
                    arbitraryTransaction.updateArbitraryResourceCache(repository);
                    arbitraryTransaction.updateArbitraryMetadataCache(repository);
                    repository.saveChanges();
                }
            }

            offset += BATCH_SIZE;
        }
    }

    private void refreshStatuses(Repository repository) throws DataException {
        LOGGER.info("Refreshing arbitrary resource statuses...");
        SplashFrame.getInstance().updateStatus("Refreshing statuses - please wait...");

        int offset = 0;

        while (!Controller.isStopping()) {
            List<ArbitraryTransactionData> hostedTransactions = ArbitraryDataStorageManager.getInstance()
                    .listAllHostedTransactions(repository, BATCH_SIZE, offset);

            if (hostedTransactions.isEmpty()) {
                break;
            }

            for (ArbitraryTransactionData transactionData : hostedTransactions) {
                ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);
                arbitraryTransaction.updateArbitraryResourceStatus(repository);
                repository.saveChanges();
            }

            offset += BATCH_SIZE;
        }

        LOGGER.info("Arbitrary resource statuses refreshed.");
    }
}
