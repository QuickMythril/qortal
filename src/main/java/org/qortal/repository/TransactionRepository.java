package org.qortal.repository;

import org.qortal.api.resource.TransactionsResource.ConfirmationStatus;
import org.qortal.arbitrary.misc.Service;
import org.qortal.data.group.GroupApprovalData;
import org.qortal.data.transaction.GroupApprovalTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.TransferAssetTransactionData;
import org.qortal.transaction.Transaction.TransactionType;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface TransactionRepository {

    // Fetching transactions / transaction height
	
    public Optional<TransactionData> fromSignature(byte[] signature) throws DataException;

    public Optional<TransactionData> fromReference(byte[] reference) throws DataException;

    public Optional<TransactionData> fromHeightAndSequence(int height, int sequence) throws DataException;

    /** Returns block height containing transaction or 0 if not in a block or transaction doesn't exist */
	
    public int getHeightFromSignature(byte[] signature) throws DataException;

    public boolean exists(byte[] signature) throws DataException;

    // Transaction participants
	
    public List<byte[]> getSignaturesInvolvingAddress(String address) throws DataException;

    public void saveParticipants(TransactionData transactionData, List<String> participants) throws DataException;

    public void deleteParticipants(TransactionData transactionData) throws DataException;

    // Searching transactions
	
    public Map<TransactionType, Integer> getTransactionSummary(int startHeight, int endHeight) throws DataException;

    public List<byte[]> getSignaturesMatchingCriteria(
        Integer startBlock, Integer blockLimit, Integer txGroupId, List<TransactionType> txTypes, 
        Service service, String name, String address, ConfirmationStatus confirmationStatus, 
        Integer limit, Integer offset, Boolean reverse
    ) throws DataException;

    public List<byte[]> getSignaturesMatchingCriteria(
        TransactionType txType, byte[] publicKey, ConfirmationStatus confirmationStatus, 
        Integer limit, Integer offset, Boolean reverse
    ) throws DataException;

    public List<byte[]> getSignaturesMatchingCriteria(
        TransactionType txType, byte[] publicKey, Integer minBlockHeight, Integer maxBlockHeight
    ) throws DataException;

    public List<byte[]> getSignaturesMatchingCustomCriteria(
        TransactionType txType, List<String> whereClauses, List<Object> bindParams
    ) throws DataException;

    public List<byte[]> getSignaturesMatchingCustomCriteria(
        TransactionType txType, List<String> whereClauses, List<Object> bindParams, Integer limit
    ) throws DataException;

    public byte[] getLatestAutoUpdateTransaction(TransactionType txType, int txGroupId, Integer service) throws DataException;

    public List<TransactionData> getTransactionsInvolvingName(String name, ConfirmationStatus confirmationStatus) throws DataException;

    public List<TransactionData> getAssetTransactions(
        long assetId, ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse
    ) throws DataException;

    public List<TransferAssetTransactionData> getAssetTransfers(
        long assetId, String address, Integer limit, Integer offset, Boolean reverse
    ) throws DataException;

    public List<String> getConfirmedRewardShareCreatorsExcludingSelfShares() throws DataException;

    public List<String> getConfirmedTransferAssetCreators() throws DataException;

    public List<TransactionData> getApprovalPendingTransactions(
        Integer txGroupId, Integer limit, Integer offset, Boolean reverse
    ) throws DataException;

    public List<TransactionData> getApprovalPendingTransactions(int blockHeight) throws DataException;

    public List<TransactionData> getApprovalExpiringTransactions(int blockHeight) throws DataException;

    public List<TransactionData> getApprovalTransactionDecidedAtHeight(int approvalHeight) throws DataException;

    public Optional<GroupApprovalTransactionData> getLatestApproval(byte[] pendingSignature, byte[] adminPublicKey) throws DataException;

    public Optional<GroupApprovalData> getApprovalData(byte[] pendingSignature) throws DataException;

    public boolean isConfirmed(byte[] signature) throws DataException;

    public List<byte[]> getUnconfirmedTransactionSignatures() throws DataException;

    public List<TransactionData> getUnconfirmedTransactions(
        List<TransactionType> txTypes, byte[] creatorPublicKey, Integer limit, Integer offset, Boolean reverse
    ) throws DataException;

    public default List<TransactionData> getUnconfirmedTransactions() throws DataException {
        return getUnconfirmedTransactions(null, null, null, null, null);
    }

    public List<TransactionData> getUnconfirmedTransactions(
        TransactionType txType, byte[] creatorPublicKey
    ) throws DataException;

    public List<TransactionData> getUnconfirmedTransactions(
        EnumSet<TransactionType> excludedTxTypes, Integer limit
    ) throws DataException;

    public void confirmTransaction(byte[] signature) throws DataException;

    public void updateBlockHeight(byte[] signature, Integer height) throws DataException;

    public void updateBlockSequence(byte[] signature, Integer sequence) throws DataException;

    public void updateApprovalHeight(byte[] signature, Integer approvalHeight) throws DataException;

    public void unconfirmTransaction(TransactionData transactionData) throws DataException;

    public void save(TransactionData transactionData) throws DataException;

    public void delete(TransactionData transactionData) throws DataException;
}
