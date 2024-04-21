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

public interface TransactionRepository {

	// Fetching transactions / transaction height

	TransactionData fromSignature(byte[] signature) throws DataException;

	TransactionData fromReference(byte[] reference) throws DataException;

	TransactionData fromHeightAndSequence(int height, int sequence) throws DataException;

	/** Returns block height containing transaction or 0 if not in a block or transaction doesn't exist */
    int getHeightFromSignature(byte[] signature) throws DataException;

	boolean exists(byte[] signature) throws DataException;

	// Transaction participants

	List<byte[]> getSignaturesInvolvingAddress(String address) throws DataException;

	void saveParticipants(TransactionData transactionData, List<String> participants) throws DataException;

	void deleteParticipants(TransactionData transactionData) throws DataException;

	// Searching transactions

	/**
	 * Returns number of each transaction type in blocks from startHeight to endHeight inclusive.
	 * <p>
	 * Note: endHeight >= startHeight
	 * 
	 * @param startHeight height of first block to check
	 * @param endHeight height of last block to check
	 * @return transaction counts, indexed by transaction type value
	 * @throws DataException
	 */
    Map<TransactionType, Integer> getTransactionSummary(int startHeight, int endHeight) throws DataException;

	/**
	 * Returns signatures for transactions that match search criteria.
	 * <p>
	 * If <tt>blockLimit</tt> is specified, and <tt>startBlock</tt> is <tt>null</tt>,
	 * then <tt>startBlock</tt> is assumed to be 1 or max-block-height,
	 * depending on <tt>reverse</tt> being <tt>false</tt> or <tt>true</tt>
	 * respectively.
	 * 
	 * @param startBlock height of first block to check
	 * @param blockLimit number of blocks (from <tt>startBlock</tt>) to check
	 * @param txGroupId
	 * @param txTypes
	 * @param service arbitrary transaction service ID
	 * @param address
	 * @param confirmationStatus
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return
	 * @throws DataException
	 */
    List<byte[]> getSignaturesMatchingCriteria(Integer startBlock, Integer blockLimit, Integer txGroupId,
                                               List<TransactionType> txTypes, Service service, String name, String address,
                                               ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns signatures for transactions that match search criteria.
	 * <p>
	 * Simpler version that only checks accepts one (optional) transaction type,
	 * and one (optional) public key.
	 * 
	 * @param txType
	 * @param publicKey
	 * @param confirmationStatus
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return
	 * @throws DataException
	 */
    List<byte[]> getSignaturesMatchingCriteria(TransactionType txType, byte[] publicKey,
                                               ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns signatures for transactions that match search criteria.
	 * <p>
	 * Simpler version that only checks accepts one (optional) transaction type,
	 * and one (optional) public key, within an block height range.
	 * 
	 * @param txType
	 * @param publicKey
	 * @param minBlockHeight
	 * @param maxBlockHeight
	 * @return
	 * @throws DataException
	 */
    List<byte[]> getSignaturesMatchingCriteria(TransactionType txType, byte[] publicKey,
                                               Integer minBlockHeight, Integer maxBlockHeight) throws DataException;

	/**
	 * Returns signatures for transactions that match search criteria.
	 * <p>
	 * Alternate version that allows for custom where clauses and bind params.
	 * Only use for very specific use cases, such as the names integrity check.
	 * Not advised to be used otherwise, given that it could be possible for
	 * unsanitized inputs to be passed in if not careful.
	 *
	 * @param txType
	 * @param whereClauses
	 * @param bindParams
	 * @return
	 * @throws DataException
	 */
    List<byte[]> getSignaturesMatchingCustomCriteria(TransactionType txType, List<String> whereClauses,
                                                     List<Object> bindParams) throws DataException;

	/**
	 * Returns signatures for transactions that match search criteria, with optional limit.
	 * <p>
	 * Alternate version that allows for custom where clauses and bind params.
	 * Only use for very specific use cases, such as the names integrity check.
	 * Not advised to be used otherwise, given that it could be possible for
	 * unsanitized inputs to be passed in if not careful.
	 *
	 * @param txType
	 * @param whereClauses
	 * @param bindParams
	 * @return
	 * @throws DataException
	 */
    List<byte[]> getSignaturesMatchingCustomCriteria(TransactionType txType, List<String> whereClauses,
                                                     List<Object> bindParams, Integer limit) throws DataException;

	/**
	 * Returns signature for latest auto-update transaction.
	 * <p>
	 * Transaction must be <tt>CONFIRMED</tt> and <tt>APPROVED</tt>
	 * and also <b>not</b> created by group admin/owner.
	 * <p>
	 * We can check the latter by testing for transaction's <tt>approvalHeight</tt>
	 * being greater than <tt>blockHeight</tt>.
	 * 
	 * @param txType
	 * @param txGroupId
	 * @param service
	 * @return
	 * @throws DataException
	 */
    byte[] getLatestAutoUpdateTransaction(TransactionType txType, int txGroupId, Integer service) throws DataException;

	/**
	 * Returns signatures for all name-registration related transactions relating to supplied name.
	 * Note: this does not currently include ARBITRARY data relating to the name.
	 *
	 * @param name
	 * @param confirmationStatus
	 * @return
	 * @throws DataException
	 */
    List<TransactionData> getTransactionsInvolvingName(String name, ConfirmationStatus confirmationStatus) throws DataException;

	/**
	 * Returns list of transactions relating to specific asset ID.
	 * 
	 * @param assetId
	 * @param confirmationStatus
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return list of transactions, or empty if none
	 */
    List<TransactionData> getAssetTransactions(long assetId, ConfirmationStatus confirmationStatus, Integer limit, Integer offset, Boolean reverse)
			throws DataException;

	/**
	 * Returns list of TRANSFER_ASSET transactions relating to specific asset ID, with optional address filter.
	 * 
	 * @param assetId
	 * @param address
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return list of transactions, or empty if none
	 */
    List<TransferAssetTransactionData> getAssetTransfers(long assetId, String address, Integer limit, Integer offset, Boolean reverse)
			throws DataException;

	/**
	 * Returns list of reward share transaction creators, excluding self shares.
	 * This uses confirmed transactions only.
	 *
	 * @return
	 * @throws DataException
	 */
    List<String> getConfirmedRewardShareCreatorsExcludingSelfShares() throws DataException;

	/**
	 * Returns list of transfer asset transaction creators.
	 * This uses confirmed transactions only.
	 *
	 * @return
	 * @throws DataException
	 */
    List<String> getConfirmedTransferAssetCreators() throws DataException;

	/**
	 * Returns list of transactions pending approval, with optional txGgroupId filtering.
	 * <p>
	 * This is typically called by the API.
	 * 
	 * @param txGroupId
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
    List<TransactionData> getApprovalPendingTransactions(Integer txGroupId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns list of transactions pending approval that can be resolved as of passed blockHeight.
	 * <p>
	 * Typically called by Block.process().
	 * 
	 * @param blockHeight
	 * @return
	 * @throws DataException
	 */
    List<TransactionData> getApprovalPendingTransactions(int blockHeight) throws DataException;

	/**
	 * Returns list of transactions that have now expired as of passed blockHeight.
	 * <p>
	 * Typically called by Block.process().
	 * 
	 * @param blockHeight
	 * @return
	 * @throws DataException
	 */
    List<TransactionData> getApprovalExpiringTransactions(int blockHeight) throws DataException;

	/** Returns list of transactions that had group-approval decided at passed block height. */
    List<TransactionData> getApprovalTransactionDecidedAtHeight(int approvalHeight) throws DataException;

	/** Returns latest approval decision by given admin for given pending transaction signature. */
    GroupApprovalTransactionData getLatestApproval(byte[] pendingSignature, byte[] adminPublicKey) throws DataException;

	/**
	 * Returns list of latest approval decisions for given pending transaction signature.
	 * 
	 * @param pendingSignature
	 * @return
	 * @throws DataException
	 */
    GroupApprovalData getApprovalData(byte[] pendingSignature) throws DataException;

	/**
	 * Returns whether transaction is confirmed or not.
	 * 
	 * @param signature
	 * @return true if confirmed, false if not.
	 */
    boolean isConfirmed(byte[] signature) throws DataException;

	/**
	 * Returns list of unconfirmed transaction signatures in timestamp-else-signature order.
	 * 
	 * @return list of transaction signatures, or empty if none.
	 * @throws DataException
	 */
    List<byte[]> getUnconfirmedTransactionSignatures() throws DataException;

	/**
	 * Returns list of unconfirmed transactions in timestamp-else-signature order.
	 * <p>
	 * This is typically called by the API.
	 * 
	 * @param limit
	 * @param offset
	 * @param reverse
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
    List<TransactionData> getUnconfirmedTransactions(List<TransactionType> txTypes, byte[] creatorPublicKey,
                                                     Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns list of unconfirmed transactions in timestamp-else-signature order.
	 * 
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
	default List<TransactionData> getUnconfirmedTransactions() throws DataException {
		return getUnconfirmedTransactions(null, null, null, null, null);
	}

	/**
	 * Returns list of unconfirmed transactions with specified type and/or creator.
	 * <p>
	 * At least one of <tt>txType</tt> or <tt>creatorPublicKey</tt> must be non-null.
	 * 
	 * @param txType optional
	 * @param creatorPublicKey optional
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
    List<TransactionData> getUnconfirmedTransactions(TransactionType txType, byte[] creatorPublicKey) throws DataException;

	/**
	 * Returns list of unconfirmed transactions excluding specified type(s).
	 * 
	 * @return list of transactions, or empty if none.
	 * @throws DataException
	 */
    List<TransactionData> getUnconfirmedTransactions(EnumSet<TransactionType> excludedTxTypes, Integer limit) throws DataException;

	/**
	 * Remove transaction from unconfirmed transactions pile.
	 * 
	 * @param signature
	 * @throws DataException
	 */
    void confirmTransaction(byte[] signature) throws DataException;

	void updateBlockHeight(byte[] signature, Integer height) throws DataException;

	void updateBlockSequence(byte[] signature, Integer sequence) throws DataException;

	void updateApprovalHeight(byte[] signature, Integer approvalHeight) throws DataException;

	/**
	 * Add transaction to unconfirmed transactions pile.
	 * 
	 * @param transactionData
	 * @throws DataException
	 */
    void unconfirmTransaction(TransactionData transactionData) throws DataException;

	void save(TransactionData transactionData) throws DataException;

	void delete(TransactionData transactionData) throws DataException;

}
