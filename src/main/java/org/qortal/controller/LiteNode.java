package org.qortal.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.data.account.AccountBalanceData;
import org.qortal.data.account.AccountData;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.network.Network;
import org.qortal.network.Peer;
import org.qortal.network.message.*;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.qortal.network.message.MessageType.*;

public class LiteNode {

    private static final Logger LOGGER = LogManager.getLogger(LiteNode.class);
    private static final int MAX_TRANSACTIONS_PER_MESSAGE = 100;

    private static LiteNode instance;

    private final Map<Integer, Long> pendingRequests = new ConcurrentHashMap<>();

    private LiteNode() {
    }

    public static synchronized LiteNode getInstance() {
        if (instance == null) {
            instance = new LiteNode();
        }
        return instance;
    }

    public AccountData fetchAccountData(String address) {
        LOGGER.debug("Fetching account data for address: {}", address);
        try {
            GetAccountMessage getAccountMessage = new GetAccountMessage(address);
            AccountMessage accountMessage = (AccountMessage) this.sendMessage(getAccountMessage, ACCOUNT);
            return accountMessage != null ? accountMessage.getAccountData() : null;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch account data for address: {}", address, e);
            return null;
        }
    }

    public AccountBalanceData fetchAccountBalance(String address, long assetId) {
        LOGGER.debug("Fetching account balance for address: {}, assetId: {}", address, assetId);
        try {
            GetAccountBalanceMessage getAccountMessage = new GetAccountBalanceMessage(address, assetId);
            AccountBalanceMessage accountMessage = (AccountBalanceMessage) this.sendMessage(getAccountMessage, ACCOUNT_BALANCE);
            return accountMessage != null ? accountMessage.getAccountBalanceData() : null;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch account balance for address: {}, assetId: {}", address, assetId, e);
            return null;
        }
    }

    public List<TransactionData> fetchAccountTransactions(String address, int limit, int offset) {
        LOGGER.debug("Fetching transactions for address: {}, limit: {}, offset: {}", address, limit, offset);
        List<TransactionData> allTransactions = new ArrayList<>();
        limit = (limit == 0) ? Integer.MAX_VALUE : limit;

        try {
            int batchSize = Math.min(limit, MAX_TRANSACTIONS_PER_MESSAGE);
            while (allTransactions.size() < limit) {
                GetAccountTransactionsMessage getAccountTransactionsMessage = 
                        new GetAccountTransactionsMessage(address, batchSize, offset);
                TransactionsMessage transactionsMessage = 
                        (TransactionsMessage) this.sendMessage(getAccountTransactionsMessage, TRANSACTIONS);

                if (transactionsMessage == null || transactionsMessage.getTransactions() == null) {
                    LOGGER.warn("No transactions received for address: {}", address);
                    return null;
                }

                allTransactions.addAll(transactionsMessage.getTransactions());

                if (transactionsMessage.getTransactions().size() < batchSize) {
                    break; // No more transactions to fetch
                }

                offset += batchSize;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to fetch transactions for address: {}", address, e);
            return null;
        }
        return allTransactions;
    }

    public List<NameData> fetchAccountNames(String address) {
        LOGGER.debug("Fetching account names for address: {}", address);
        try {
            GetAccountNamesMessage getAccountNamesMessage = new GetAccountNamesMessage(address);
            NamesMessage namesMessage = (NamesMessage) this.sendMessage(getAccountNamesMessage, NAMES);
            return namesMessage != null ? namesMessage.getNameDataList() : null;
        } catch (Exception e) {
            LOGGER.error("Failed to fetch account names for address: {}", address, e);
            return null;
        }
    }

    public NameData fetchNameData(String name) {
        LOGGER.debug("Fetching name data for name: {}", name);
        try {
            GetNameMessage getNameMessage = new GetNameMessage(name);
            NamesMessage namesMessage = (NamesMessage) this.sendMessage(getNameMessage, NAMES);

            if (namesMessage == null || namesMessage.getNameDataList() == null || namesMessage.getNameDataList().size() != 1) {
                LOGGER.warn("Unexpected name data response for name: {}", name);
                return null;
            }
            return namesMessage.getNameDataList().get(0);
        } catch (Exception e) {
            LOGGER.error("Failed to fetch name data for name: {}", name, e);
            return null;
        }
    }

    private Message sendMessage(Message message, MessageType expectedResponseMessageType) {
        LOGGER.debug("Preparing to send {} message", message.getType());

        try {
            List<Peer> peers = new ArrayList<>(Network.getInstance().getImmutableHandshakedPeers());
            peers.removeIf(Controller.hasMisbehaved);
            peers.removeIf(Controller.hasOldVersion);

            if (peers.isEmpty()) {
                LOGGER.warn("No suitable peers available to send {} message", message.getType());
                return null;
            }

            Peer peer = peers.get(new SecureRandom().nextInt(peers.size()));
            LOGGER.debug("Sending {} message to peer {}", message.getType(), peer);

            Message responseMessage = peer.getResponse(message);
            if (responseMessage == null) {
                LOGGER.warn("No response received for {} message from peer {}", message.getType(), peer);
                return null;
            }

            if (responseMessage.getType() != expectedResponseMessageType) {
                LOGGER.warn("Unexpected response type {} for {} message from peer {}", responseMessage.getType(), message.getType(), peer);
                return null;
            }

            LOGGER.debug("Successfully received {} message from peer {}", responseMessage.getType(), peer);
            return responseMessage;

        } catch (InterruptedException e) {
            LOGGER.error("Message sending interrupted for {} message", message.getType(), e);
            Thread.currentThread().interrupt(); // Restore interrupt status
            return null;
        } catch (Exception e) {
            LOGGER.error("Error sending {} message", message.getType(), e);
            return null;
        }
    }
}
