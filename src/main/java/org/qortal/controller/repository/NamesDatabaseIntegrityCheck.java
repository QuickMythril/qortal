package org.qortal.controller.repository;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.account.PublicKeyAccount;
import org.qortal.api.resource.TransactionsResource.ConfirmationStatus;
import org.qortal.data.naming.NameData;
import org.qortal.data.transaction.*;
import org.qortal.naming.Name;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.repository.RepositoryManager;
import org.qortal.transaction.Transaction.TransactionType;
import org.qortal.utils.Unicode;

import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

public class NamesDatabaseIntegrityCheck {

    private static final Logger LOGGER = LogManager.getLogger(NamesDatabaseIntegrityCheck.class);

    private static final List<TransactionType> ALL_NAME_TX_TYPE = Arrays.asList(
            TransactionType.REGISTER_NAME,
            TransactionType.UPDATE_NAME,
            TransactionType.BUY_NAME,
            TransactionType.SELL_NAME,
            TransactionType.CANCEL_SELL_NAME
    );

    private List<TransactionData> nameTransactions = new ArrayList<>();


    public int rebuildName(String name, Repository repository) {
        int modificationCount = 0;
        try {
            List<TransactionData> transactions = this.fetchAllTransactionsInvolvingName(name, repository);
            if (transactions.isEmpty()) {
                // This name was never registered, so there's nothing to do
                return modificationCount;
            }

            // If this name has been updated at any point, we need to add transactions from the other names to the sequence
            int added = this.addAdditionalTransactionsRelatingToName(transactions, name, repository);
            while (added > 0) {
                // Keep going until all have been added
                LOGGER.trace("{} added for {}. Looking for more transactions...", added, name);
                added = this.addAdditionalTransactionsRelatingToName(transactions, name, repository);
            }

            // Loop through each past transaction and re-apply it to the Names table
            for (TransactionData currentTransaction : transactions) {

                // Process REGISTER_NAME transactions
                if (currentTransaction.getType() == TransactionType.REGISTER_NAME) {
                    RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) currentTransaction;
                    Name nameObj = new Name(repository, registerNameTransactionData);
                    nameObj.register();
                    modificationCount++;
                    LOGGER.trace("Processed REGISTER_NAME transaction for name {}", name);
                }

                // Process UPDATE_NAME transactions
                if (currentTransaction.getType() == TransactionType.UPDATE_NAME) {
                    UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) currentTransaction;
                    Name nameObj = new Name(repository, updateNameTransactionData.getName());
                    if (nameObj != null && nameObj.getNameData() != null) {
                        nameObj.update(updateNameTransactionData);
                        modificationCount++;
                        LOGGER.trace("Processed UPDATE_NAME transaction for name {}", name);
                    } else {
                        // Something went wrong
                        throw new DataException(String.format("Name data not found for name %s", updateNameTransactionData.getName()));
                    }
                }

                // Process SELL_NAME transactions
                if (currentTransaction.getType() == TransactionType.SELL_NAME) {
                    SellNameTransactionData sellNameTransactionData = (SellNameTransactionData) currentTransaction;
                    Name nameObj = new Name(repository, sellNameTransactionData.getName());
                    if (nameObj != null && nameObj.getNameData() != null) {
                        nameObj.sell(sellNameTransactionData);
                        modificationCount++;
                        LOGGER.trace("Processed SELL_NAME transaction for name {}", name);
                    }
                    else {
                        // Something went wrong
                        throw new DataException(String.format("Name data not found for name %s", sellNameTransactionData.getName()));
                    }
                }

                // Process CANCEL_SELL_NAME transactions
                if (currentTransaction.getType() == TransactionType.CANCEL_SELL_NAME) {
                    CancelSellNameTransactionData cancelSellNameTransactionData = (CancelSellNameTransactionData) currentTransaction;
                    Name nameObj = new Name(repository, cancelSellNameTransactionData.getName());
                    if (nameObj != null && nameObj.getNameData() != null) {
                        nameObj.cancelSell(cancelSellNameTransactionData);
                        modificationCount++;
                        LOGGER.trace("Processed CANCEL_SELL_NAME transaction for name {}", name);
                    }
                    else {
                        // Something went wrong
                        throw new DataException(String.format("Name data not found for name %s", cancelSellNameTransactionData.getName()));
                    }
                }

                // Process BUY_NAME transactions
                if (currentTransaction.getType() == TransactionType.BUY_NAME) {
                    BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) currentTransaction;
                    Name nameObj = new Name(repository, buyNameTransactionData.getName());
                    if (nameObj != null && nameObj.getNameData() != null) {
                        nameObj.buy(buyNameTransactionData, false);
                        modificationCount++;
                        LOGGER.trace("Processed BUY_NAME transaction for name {}", name);
                    }
                    else {
                        // Something went wrong
                        throw new DataException(String.format("Name data not found for name %s", buyNameTransactionData.getName()));
                    }
                }
            }

        } catch (DataException e) {
            LOGGER.info("Unable to run integrity check for name {}: {}", name, e.getMessage());
        }

        return modificationCount;
    }

    public int rebuildAllNames() {
        int modificationCount = 0;
        try (final Repository repository = RepositoryManager.getRepository()) {
            List<String> names = this.fetchAllNames(repository); // TODO: de-duplicate, to speed up this process
            for (String name : names) {
                modificationCount += this.rebuildName(name, repository);
            }
            repository.saveChanges();
        }
        catch (DataException e) {
            LOGGER.info("Error when running integrity check for all names: {}", e.getMessage());
        }

        //LOGGER.info("modificationCount: {}", modificationCount);
        return modificationCount;
    }

    public void runIntegrityCheck() {
        boolean integrityCheckFailed = false;
        try (final Repository repository = RepositoryManager.getRepository()) {

            // Fetch all the (confirmed) REGISTER_NAME transactions
            List<RegisterNameTransactionData> registerNameTransactions = this.fetchRegisterNameTransactions();

            // Loop through each REGISTER_NAME txn signature and request the full transaction data
            for (RegisterNameTransactionData registerNameTransactionData : registerNameTransactions) {
                String registeredName = registerNameTransactionData.getName();
                NameData nameData = repository.getNameRepository().fromName(registeredName);

                // Check to see if this name has been updated or bought at any point
                TransactionData latestUpdate = this.fetchLatestModificationTransactionInvolvingName(registeredName, repository);
                if (latestUpdate == null) {
                    // Name was never updated once registered
                    // We expect this name to still be registered to this transaction's creator

                    if (nameData == null) {
                        LOGGER.info("Error: registered name {} doesn't exist in Names table. Adding...", registeredName);
                        integrityCheckFailed = true;
                    }
                    else {
                        LOGGER.trace("Registered name {} is correctly registered", registeredName);
                    }

                    // Check the owner is correct
                    PublicKeyAccount creator = new PublicKeyAccount(repository, registerNameTransactionData.getCreatorPublicKey());
                    if (!Objects.equals(creator.getAddress(), nameData.getOwner())) {
                        LOGGER.info("Error: registered name {} is owned by {}, but it should be {}",
                                registeredName, nameData.getOwner(), creator.getAddress());
                        integrityCheckFailed = true;
                    }
                    else {
                        LOGGER.trace("Registered name {} has the correct owner", registeredName);
                    }
                }
                else {
                    // Check if owner is correct after update

                    // Check for name updates
                    if (latestUpdate.getType() == TransactionType.UPDATE_NAME) {
                        UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) latestUpdate;
                        PublicKeyAccount creator = new PublicKeyAccount(repository, updateNameTransactionData.getCreatorPublicKey());

                        // When this name is the "new name", we expect the current owner to match the txn creator
                        if (Objects.equals(updateNameTransactionData.getNewName(), registeredName)) {
                            if (!Objects.equals(creator.getAddress(), nameData.getOwner())) {
                                LOGGER.info("Error: registered name {} is owned by {}, but it should be {}",
                                        registeredName, nameData.getOwner(), creator.getAddress());
                                integrityCheckFailed = true;
                            }
                            else {
                                LOGGER.trace("Registered name {} has the correct owner after being updated", registeredName);
                            }
                        }

                        // When this name is the old name, we expect the "new name"'s owner to match the txn creator
                        // The old name will then be unregistered, or re-registered.
                        // FUTURE: check database integrity for names that have been updated and then the original name re-registered
                        else if (Objects.equals(updateNameTransactionData.getName(), registeredName)) {
                            String newName = updateNameTransactionData.getNewName();
                            if (newName == null || newName.isEmpty()) {
                                // If new name is blank (or maybe null, just to be safe), it means that it stayed the same
                                newName = registeredName;
                            }
                            NameData newNameData = repository.getNameRepository().fromName(newName);
                            if (newNameData == null) {
                                LOGGER.info("Error: registered name {} has no new name data. This is likely due to account {} " +
                                                "being renamed another time, which is a scenario that is not yet checked automatically.",
                                        updateNameTransactionData.getNewName(), creator.getAddress());
                            }
                            else if (!Objects.equals(creator.getAddress(), newNameData.getOwner())) {
                                LOGGER.info("Error: registered name {} is owned by {}, but it should be {}",
                                        updateNameTransactionData.getNewName(), newNameData.getOwner(), creator.getAddress());
                                integrityCheckFailed = true;
                            }
                            else {
                                LOGGER.trace("Registered name {} has the correct owner after being updated", updateNameTransactionData.getNewName());
                            }
                        }

                        else {
                            LOGGER.info("Unhandled update case for name {}", registeredName);
                        }
                    }

                    // Check for name buys
                    else if (latestUpdate.getType() == TransactionType.BUY_NAME) {
                        BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) latestUpdate;
                        PublicKeyAccount creator = new PublicKeyAccount(repository, buyNameTransactionData.getCreatorPublicKey());
                        if (!Objects.equals(creator.getAddress(), nameData.getOwner())) {
                            LOGGER.info("Error: registered name {} is owned by {}, but it should be {}",
                                    registeredName, nameData.getOwner(), creator.getAddress());
                            integrityCheckFailed = true;
                        }
                        else {
                            LOGGER.trace("Registered name {} has the correct owner after being bought", registeredName);
                        }
                    }

                    // Check for name sells
                    else if (latestUpdate.getType() == TransactionType.SELL_NAME) {
                        SellNameTransactionData sellNameTransactionData = (SellNameTransactionData) latestUpdate;
                        PublicKeyAccount creator = new PublicKeyAccount(repository, sellNameTransactionData.getCreatorPublicKey());
                        if (!Objects.equals(creator.getAddress(), nameData.getOwner())) {
                            LOGGER.info("Error: registered name {} is owned by {}, but it should be {}",
                                    registeredName, nameData.getOwner(), creator.getAddress());
                            integrityCheckFailed = true;
                        }
                        else {
                            LOGGER.trace("Registered name {} has the correct owner after being listed for sale", registeredName);
                        }
                    }

                    else {
                        LOGGER.info("Unhandled case for name {}", registeredName);
                    }

                }

            }

        } catch (DataException e) {
            LOGGER.warn(String.format("Repository issue trying to trim online accounts signatures: %s", e.getMessage()));
            integrityCheckFailed = true;
        }

        if (integrityCheckFailed) {
            LOGGER.info("Registered names database integrity check failed. Bootstrapping is recommended.");
        } else {
            LOGGER.info("Registered names database integrity check passed.");
        }
    }

    private List<RegisterNameTransactionData> fetchRegisterNameTransactions() {
        List<RegisterNameTransactionData> registerNameTransactions = new ArrayList<>();

        for (TransactionData transactionData : this.nameTransactions) {
            if (transactionData.getType() == TransactionType.REGISTER_NAME) {
                RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;
                registerNameTransactions.add(registerNameTransactionData);
            }
        }
        return registerNameTransactions;
    }

    private void fetchAllNameTransactions(Repository repository) throws DataException {
        List<TransactionData> nameTransactions = new ArrayList<>();

        // Fetch all the confirmed REGISTER_NAME transaction signatures
        List<byte[]> signatures = repository.getTransactionRepository().getSignaturesMatchingCriteria(
                null, null, null, ALL_NAME_TX_TYPE, null, null,
                null, ConfirmationStatus.CONFIRMED, null, null, false);

        for (byte[] signature : signatures) {
            TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
            nameTransactions.add(transactionData);
        }
        this.nameTransactions = nameTransactions;
    }

    public List<TransactionData> fetchAllTransactionsInvolvingName(String name, Repository repository) throws DataException {
        List<byte[]> signatures = new ArrayList<>();
        String reducedName = Unicode.sanitize(name);

        List<byte[]> registerNameTransactions = repository.getTransactionRepository().getSignaturesMatchingCustomCriteria(
                TransactionType.REGISTER_NAME, Arrays.asList("(name = ? OR reduced_name = ?)"), Arrays.asList(name, reducedName));
        signatures.addAll(registerNameTransactions);

        List<byte[]> updateNameTransactions = repository.getTransactionRepository().getSignaturesMatchingCustomCriteria(
                TransactionType.UPDATE_NAME,
                Arrays.asList("(name = ? OR new_name = ? OR (reduced_new_name != '' AND reduced_new_name = ?))"),
                Arrays.asList(name, name, reducedName));
        signatures.addAll(updateNameTransactions);

        List<byte[]> sellNameTransactions = repository.getTransactionRepository().getSignaturesMatchingCustomCriteria(
                TransactionType.SELL_NAME, Arrays.asList("name = ?"), Arrays.asList(name));
        signatures.addAll(sellNameTransactions);

        List<byte[]> buyNameTransactions = repository.getTransactionRepository().getSignaturesMatchingCustomCriteria(
                TransactionType.BUY_NAME, Arrays.asList("name = ?"), Arrays.asList(name));
        signatures.addAll(buyNameTransactions);

        List<byte[]> cancelSellNameTransactions = repository.getTransactionRepository().getSignaturesMatchingCustomCriteria(
                TransactionType.CANCEL_SELL_NAME, Arrays.asList("name = ?"), Arrays.asList(name));
        signatures.addAll(cancelSellNameTransactions);

        List<TransactionData> transactions = new ArrayList<>();
        for (byte[] signature : signatures) {
            TransactionData transactionData = repository.getTransactionRepository().fromSignature(signature);
            // Filter out any unconfirmed transactions
            if (transactionData.getBlockHeight() != null && transactionData.getBlockHeight() > 0) {
                transactions.add(transactionData);
            }
        }

        // Sort by lowest block height first
        sortTransactions(transactions);

        return transactions;
    }

    private TransactionData fetchLatestModificationTransactionInvolvingName(String registeredName, Repository repository) throws DataException {
        List<TransactionData> transactionsInvolvingName = this.fetchAllTransactionsInvolvingName(registeredName, repository);

        // Get the latest update for this name (excluding REGISTER_NAME transactions)
        TransactionData latestUpdateToName = transactionsInvolvingName.stream()
                .filter(txn -> txn.getType() != TransactionType.REGISTER_NAME)
                .max(Comparator.comparing(TransactionData::getTimestamp))
                .orElse(null);

        return latestUpdateToName;
    }

    private List<String> fetchAllNames(Repository repository) throws DataException {
        List<String> names = new ArrayList<>();

        // Fetch all the confirmed name transactions
        if (this.nameTransactions.isEmpty()) {
            this.fetchAllNameTransactions(repository);
        }

        for (TransactionData transactionData : this.nameTransactions) {

            if ((transactionData instanceof RegisterNameTransactionData)) {
                RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;
                if (!names.contains(registerNameTransactionData.getName())) {
                    names.add(registerNameTransactionData.getName());
                }
            }
            if ((transactionData instanceof UpdateNameTransactionData)) {
                UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;
                if (!names.contains(updateNameTransactionData.getName())) {
                    names.add(updateNameTransactionData.getName());
                }
                if (!names.contains(updateNameTransactionData.getNewName())) {
                    names.add(updateNameTransactionData.getNewName());
                }
            }
            if ((transactionData instanceof BuyNameTransactionData)) {
                BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) transactionData;
                if (!names.contains(buyNameTransactionData.getName())) {
                    names.add(buyNameTransactionData.getName());
                }
            }
            if ((transactionData instanceof SellNameTransactionData)) {
                SellNameTransactionData sellNameTransactionData = (SellNameTransactionData) transactionData;
                if (!names.contains(sellNameTransactionData.getName())) {
                    names.add(sellNameTransactionData.getName());
                }
            }
            if ((transactionData instanceof CancelSellNameTransactionData)) {
                CancelSellNameTransactionData cancelSellNameTransactionData = (CancelSellNameTransactionData) transactionData;
                if (!names.contains(cancelSellNameTransactionData.getName())) {
                    names.add(cancelSellNameTransactionData.getName());
                }
            }
        }
        return names;
    }

    private int addAdditionalTransactionsRelatingToName(List<TransactionData> transactions, String name, Repository repository) throws DataException {
        int added = 0;

        // If this name has been updated at any point, we need to add transactions from the other names to the sequence
        List<String> otherNames = new ArrayList<>();
        List<TransactionData> updateNameTransactions = transactions.stream().filter(t -> t.getType() == TransactionType.UPDATE_NAME).collect(Collectors.toList());
        for (TransactionData transactionData : updateNameTransactions) {
            UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;
            // If the newName field isn't empty, and either the "name" or "newName" is different from our reference name,
            // we should remember this additional name, in case it has relevant transactions associated with it.
            if (updateNameTransactionData.getNewName() != null && !updateNameTransactionData.getNewName().isEmpty()) {
                if (!Objects.equals(updateNameTransactionData.getName(), name)) {
                    otherNames.add(updateNameTransactionData.getName());
                }
                if (!Objects.equals(updateNameTransactionData.getNewName(), name)) {
                    otherNames.add(updateNameTransactionData.getNewName());
                }
            }
        }


        for (String otherName : otherNames) {
            List<TransactionData> otherNameTransactions = this.fetchAllTransactionsInvolvingName(otherName, repository);
            for (TransactionData otherNameTransactionData : otherNameTransactions) {
                if (!transactions.contains(otherNameTransactionData)) {
                    // Add new transaction relating to other name
                    transactions.add(otherNameTransactionData);
                    added++;
                }
            }
        }

        if (added > 0) {
            // New transaction(s) added, so re-sort
            sortTransactions(transactions);
        }

        return added;
    }

    private void sortTransactions(List<TransactionData> transactions) {
        Collections.sort(transactions, new Comparator() {
            public int compare(Object o1, Object o2) {
                TransactionData td1 = (TransactionData) o1;
                TransactionData td2 = (TransactionData) o2;

                // Sort by block height first
                int heightComparison = td1.getBlockHeight().compareTo(td2.getBlockHeight());
                if (heightComparison != 0) {
                    return heightComparison;
                }

                // Same height so compare timestamps
                int timestampComparison = Long.compare(td1.getTimestamp(), td2.getTimestamp());
                if (timestampComparison != 0) {
                    return timestampComparison;
                }

                // Same timestamp so compare signatures
                return new BigInteger(td1.getSignature()).compareTo(new BigInteger(td2.getSignature()));
            }});
    }

}
