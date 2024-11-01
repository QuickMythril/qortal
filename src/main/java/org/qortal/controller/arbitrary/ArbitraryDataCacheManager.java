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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ArbitraryDataCacheManager extends Thread {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataCacheManager.class);

    private static ArbitraryDataCacheManager instance;
    private volatile boolean isStopping = false;

    /** Queue of arbitrary transactions that require cache updates */
    private final List<ArbitraryTransactionData> updateQueue = Collections.synchronizedList(new ArrayList<>());


    public static synchronized ArbitraryDataCacheManager getInstance() {
        if (instance == null) {
            instance = new ArbitraryDataCacheManager();
        }

        return instance;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Arbitrary Data Cache Manager");
        Thread.currentThread().setPriority(NORM_PRIORITY);

        try {
            while (!Controller.isStopping()) {
                Thread.sleep(500L);

                // Process queue
                processResourceQueue();
            }
        } catch (InterruptedException e) {
            // Fall through to exit thread
        }

        // Clear queue before terminating thread
        processResourceQueue();
    }

    public void shutdown() {
        isStopping = true;
        this.interrupt();
    }


    private void processResourceQueue() {
        if (this.updateQueue.isEmpty()) {
            // Nothing to do
            return;
        }

        try (final Repository repository = RepositoryManager.getRepository()) {
            // Take a snapshot of resourceQueue, so we don't need to lock it while processing
            List<ArbitraryTransactionData> resourceQueueCopy = List.copyOf(this.updateQueue);

            for (ArbitraryTransactionData transactionData : resourceQueueCopy) {
                // Best not to return when controller is stopping, as ideally we need to finish processing

                LOGGER.debug(() -> String.format("Processing transaction %.8s in arbitrary resource queue...", Base58.encode(transactionData.getSignature())));

                // Remove from the queue regardless of outcome
                this.updateQueue.remove(transactionData);

                // Update arbitrary resource caches
                try {
                    ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);
                    arbitraryTransaction.updateArbitraryResourceCache(repository);
                    arbitraryTransaction.updateArbitraryMetadataCache(repository);
                    repository.saveChanges();

                    // Update status as separate commit, as this is more prone to failure
                    arbitraryTransaction.updateArbitraryResourceStatus(repository);
                    repository.saveChanges();

                    LOGGER.debug(() -> String.format("Finished processing transaction %.8s in arbitrary resource queue...", Base58.encode(transactionData.getSignature())));

                } catch (DataException e) {
                    repository.discardChanges();
                    LOGGER.error("Repository issue while updating arbitrary resource caches", e);
                }
            }
        } catch (DataException e) {
            LOGGER.error("Repository issue while processing arbitrary resource cache updates", e);
        }
    }

    public void addToUpdateQueue(ArbitraryTransactionData transactionData) {
        this.updateQueue.add(transactionData);
        LOGGER.debug(() -> String.format("Transaction %.8s added to queue", Base58.encode(transactionData.getSignature())));
    }

    public boolean needsArbitraryResourcesCacheRebuild(Repository repository) throws DataException {
        // Check if we have an entry in the cache for the oldest ARBITRARY transaction with a name
        List<ArbitraryTransactionData> oldestCacheableTransactions = repository.getArbitraryRepository().getArbitraryTransactions(true, 1, 0, false);
        if (oldestCacheableTransactions == null || oldestCacheableTransactions.isEmpty()) {
            // No relevant arbitrary transactions yet on this chain
            LOGGER.debug("No relevant arbitrary transactions exist to build cache from");
            return false;
        }
        // We have an arbitrary transaction, so check if it's in the cache
        ArbitraryTransactionData txn = oldestCacheableTransactions.get(0);
        ArbitraryResourceData cachedResource = repository.getArbitraryRepository().getArbitraryResource(txn.getService(), txn.getName(), txn.getIdentifier());
        if (cachedResource != null) {
            // Earliest resource exists in the cache, so assume it has been built.
            // We avoid checkpointing and prevent the node from starting up in the case of a rebuild failure, so
            // we shouldn't ever be left in a partially rebuilt state.
            LOGGER.debug("Arbitrary resources cache already built");
            return false;
        }

        return true;
    }

    public boolean buildArbitraryResourcesCache(Repository repository, boolean forceRebuild) throws DataException {
        if (Settings.getInstance().isLite()) {
            // Lite nodes have no blockchain
            return false;
        }

        try {
            // Skip if already built
            if (!needsArbitraryResourcesCacheRebuild(repository) && !forceRebuild) {
                LOGGER.debug("Arbitrary resources cache already built");
                return false;
            }

            LOGGER.info("Building arbitrary resources cache...");
            SplashFrame.getInstance().updateStatus("Building QDN cache - please wait...");

            final int batchSize = 100;
            int offset = 0;

            // Loop through all ARBITRARY transactions, and determine latest state
            while (!Controller.isStopping()) {
                LOGGER.info("Fetching arbitrary transactions {} - {}", offset, offset+batchSize-1);

                List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(null, null, null, List.of(Transaction.TransactionType.ARBITRARY), null, null, null, TransactionsResource.ConfirmationStatus.BOTH, batchSize, offset, false);
                if (signatures.isEmpty()) {
                    // Complete
                    break;
                }

                // Expand signatures to transactions
                for (byte[] signature : signatures) {
                    ArbitraryTransactionData transactionData = (ArbitraryTransactionData) repository
                            .getTransactionRepository().fromSignature(signature);

                    if (transactionData.getService() == null) {
                        // Unsupported service - ignore this resource
                        continue;
                    }

                    // Update arbitrary resource caches
                    ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);
                    arbitraryTransaction.updateArbitraryResourceCache(repository);
                    arbitraryTransaction.updateArbitraryMetadataCache(repository);
                    repository.saveChanges();
                }
                offset += batchSize;
            }

            // Now refresh all statuses
            refreshArbitraryStatuses(repository);

            LOGGER.info("Completed build of arbitrary resources cache.");
            return true;
        }
        catch (DataException e) {
            LOGGER.info("Unable to build arbitrary resources cache: {}. The database may have been left in an inconsistent state.", e.getMessage());

            // Throw an exception so that the node startup is halted, allowing for a retry next time.
            repository.discardChanges();
            throw new DataException("Build of arbitrary resources cache failed.");
        }
    }

    private boolean refreshArbitraryStatuses(Repository repository) throws DataException {
        try {
            LOGGER.info("Refreshing arbitrary resource statuses for locally hosted transactions...");
            SplashFrame.getInstance().updateStatus("Refreshing statuses - please wait...");

            final int batchSize = 100;
            int offset = 0;

            // Loop through all ARBITRARY transactions, and determine latest state
            while (!Controller.isStopping()) {
                LOGGER.info("Fetching hosted transactions {} - {}", offset, offset+batchSize-1);

                List<ArbitraryTransactionData> hostedTransactions = ArbitraryDataStorageManager.getInstance().listAllHostedTransactions(repository, batchSize, offset);
                if (hostedTransactions.isEmpty()) {
                    // Complete
                    break;
                }

                // Loop through hosted transactions
                for (ArbitraryTransactionData transactionData : hostedTransactions) {

                    // Determine status and update cache
                    ArbitraryTransaction arbitraryTransaction = new ArbitraryTransaction(repository, transactionData);
                    arbitraryTransaction.updateArbitraryResourceStatus(repository);
                    repository.saveChanges();
                }
                offset += batchSize;
            }

            LOGGER.info("Completed refresh of arbitrary resource statuses.");
            return true;
        }
        catch (DataException e) {
            LOGGER.info("Unable to refresh arbitrary resource statuses: {}. The database may have been left in an inconsistent state.", e.getMessage());

            // Throw an exception so that the node startup is halted, allowing for a retry next time.
            repository.discardChanges();
            throw new DataException("Refresh of arbitrary resource statuses failed.");
        }
    }

}
