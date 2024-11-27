package org.qortal.controller.arbitrary;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.Controller;
import org.qortal.data.arbitrary.ArbitraryFileListResponseInfo;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.network.Peer;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.utils.ArbitraryTransactionUtils;
import org.qortal.utils.Base58;
import org.qortal.utils.NTP;

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import static java.lang.Thread.NORM_PRIORITY;

public class ArbitraryDataFileRequestThread implements Runnable {

    private static final Logger LOGGER = LogManager.getLogger(ArbitraryDataFileRequestThread.class);

    private static final long SLEEP_INTERVAL = 1000L; // 1 second
    private static final ArbitraryDataFileManager FILE_MANAGER = ArbitraryDataFileManager.getInstance();

    public ArbitraryDataFileRequestThread() {
        // Default constructor
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Arbitrary Data File Request Thread");
        Thread.currentThread().setPriority(NORM_PRIORITY);

        try {
            while (!Controller.isStopping()) {
                processPendingFileHashes(NTP.getTime());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            LOGGER.info("Arbitrary Data File Request Thread interrupted, exiting.");
        }
    }

    private void processPendingFileHashes(Long currentTime) throws InterruptedException {
        if (Controller.isStopping()) {
            return;
        }

        ArbitraryFileListResponseInfo responseInfo = fetchNextValidResponseInfo(currentTime);

        if (responseInfo == null) {
            // No files to process, sleep and retry later
            Thread.sleep(SLEEP_INTERVAL);
            return;
        }

        // Extract and decode data
        byte[] hash = decodeBase58(responseInfo.getHash58(), "hash");
        byte[] signature = decodeBase58(responseInfo.getSignature58(), "signature");
        Peer peer = responseInfo.getPeer();

        if (hash == null || signature == null || peer == null) {
            LOGGER.warn("Incomplete response info: skipping hash {}", responseInfo.getHash58());
            return;
        }

        processFileFromPeer(hash, signature, peer);
    }

    private ArbitraryFileListResponseInfo fetchNextValidResponseInfo(Long currentTime) {
        synchronized (FILE_MANAGER.arbitraryDataFileHashResponses) {
            if (FILE_MANAGER.arbitraryDataFileHashResponses.isEmpty()) {
                return null;
            }

            // Sort responses by the number of hops
            FILE_MANAGER.arbitraryDataFileHashResponses.sort(
                    Comparator.comparingInt(ArbitraryFileListResponseInfo::getRequestHops)
            );

            Iterator<ArbitraryFileListResponseInfo> iterator = FILE_MANAGER.arbitraryDataFileHashResponses.iterator();

            while (iterator.hasNext()) {
                ArbitraryFileListResponseInfo responseInfo = iterator.next();

                if (shouldRemoveResponse(responseInfo, currentTime)) {
                    iterator.remove();
                    continue;
                }

                if (!FILE_MANAGER.arbitraryDataFileRequests.containsKey(responseInfo.getHash58())) {
                    // Valid response to process
                    iterator.remove();
                    return responseInfo;
                }
            }
        }

        return null;
    }

    private boolean shouldRemoveResponse(ArbitraryFileListResponseInfo responseInfo, Long currentTime) {
        if (responseInfo == null) {
            return true;
        }

        Long timestamp = responseInfo.getTimestamp();
        boolean isExpired = (currentTime - timestamp) >= ArbitraryDataManager.ARBITRARY_RELAY_TIMEOUT;

        return isExpired || responseInfo.getSignature58() == null || responseInfo.getPeer() == null;
    }

    private void processFileFromPeer(byte[] hash, byte[] signature, Peer peer) {
        try (Repository repository = RepositoryManager.getRepository()) {
            ArbitraryTransactionData transactionData = ArbitraryTransactionUtils.fetchTransactionData(repository, signature);

            if (transactionData == null) {
                LOGGER.warn("Transaction data not found for signature: {}", Base58.encode(signature));
                return;
            }

            LOGGER.trace("Fetching file {} from peer {} via request thread...", Base58.encode(hash), peer);
            FILE_MANAGER.fetchArbitraryDataFiles(repository, peer, signature, transactionData, List.of(hash));
        } catch (DataException e) {
            LOGGER.error("Error processing file from peer: {}", e.getMessage(), e);
        }
    }

    private byte[] decodeBase58(String input, String type) {
        try {
            return Base58.decode(input);
        } catch (Exception e) {
            LOGGER.error("Failed to decode {}: {}", type, input, e);
            return null;
        }
    }
}
