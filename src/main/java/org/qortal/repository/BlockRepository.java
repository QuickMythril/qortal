package org.qortal.repository;

import org.qortal.api.model.BlockSignerSummary;
import org.qortal.data.block.BlockData;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.data.block.BlockTransactionData;
import org.qortal.data.transaction.TransactionData;

import java.util.List;

public interface BlockRepository {

	/**
	 * Returns BlockData from repository using block signature.
	 * 
	 * @param signature
	 * @return block data, or null if not found in blockchain.
	 * @throws DataException
	 */
    BlockData fromSignature(byte[] signature) throws DataException;

	/**
	 * Returns BlockData from repository using block reference.
	 * 
	 * @param reference
	 * @return block data, or null if not found in blockchain.
	 * @throws DataException
	 */
    BlockData fromReference(byte[] reference) throws DataException;

	/**
	 * Returns BlockData from repository using block height.
	 * 
	 * @param height
	 * @return block data, or null if not found in blockchain.
	 * @throws DataException
	 */
    BlockData fromHeight(int height) throws DataException;

	/**
	 * Returns whether block exists based on passed block signature.
	 */
    boolean exists(byte[] signature) throws DataException;

	/**
	 * Return height of block in blockchain using block's signature.
	 * 
	 * @param signature
	 * @return height, or 0 if not found in blockchain.
	 * @throws DataException
	 */
    int getHeightFromSignature(byte[] signature) throws DataException;

	/**
	 * Return height of block with timestamp just before passed timestamp.
	 * 
	 * @param timestamp
	 * @return height, or 0 if not found in blockchain.
	 * @throws DataException
	 */
    int getHeightFromTimestamp(long timestamp) throws DataException;

	/**
	 * Returns block timestamp for a given height.
	 *
	 * @param height
	 * @return timestamp, or 0 if height is out of bounds.
	 * @throws DataException
	 */
    long getTimestampFromHeight(int height) throws DataException;

	/**
	 * Return highest block height from repository.
	 * 
	 * @return height, or 0 if there are no blocks in DB (not very likely).
	 */
    int getBlockchainHeight() throws DataException;

	/**
	 * Return highest block in blockchain.
	 * 
	 * @return highest block's data
	 * @throws DataException
	 */
    BlockData getLastBlock() throws DataException;

	/**
	 * Returns block's transactions given block's signature.
	 * <p>
	 * This is typically used by API to fetch a block's transactions.
	 * 
	 * @param signature
	 * @return list of transactions, or null if block not found in blockchain.
	 * @throws DataException
	 */
    List<TransactionData> getTransactionsFromSignature(byte[] signature, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns block's transactions given block's signature.
	 * <p>
	 * This is typically used by Block.getTransactions() which uses lazy-loading of transactions.
	 * 
	 * @param signature
	 * @return list of transactions, or null if block not found in blockchain.
	 * @throws DataException
	 */
	default List<TransactionData> getTransactionsFromSignature(byte[] signature) throws DataException {
		return getTransactionsFromSignature(signature, null, null, null);
	}

	/**
	 * Returns number of blocks signed by account/reward-share with given public key.
	 * 
	 * @param publicKey
	 * @return number of blocks
	 * @throws DataException
	 */
    int countSignedBlocks(byte[] publicKey) throws DataException;

	/**
	 * Returns summaries of block signers, optionally limited to passed addresses.
	 */
    List<BlockSignerSummary> getBlockSigners(List<String> addresses, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns block summaries for blocks signed by passed public key, or reward-share with minter with passed public key.
	 */
    List<BlockSummaryData> getBlockSummariesBySigner(byte[] signerPublicKey, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/**
	 * Returns blocks within height range.
	 */
    List<BlockData> getBlocks(int firstBlockHeight, int lastBlockHeight) throws DataException;

	/**
	 * Returns blocks within height range.
	 */
    Long getTotalFeesInBlockRange(int firstBlockHeight, int lastBlockHeight) throws DataException;

	/**
	 * Returns block with highest online accounts count in specified range. If more than one block
	 * has the same high count, the oldest one is returned.
	 */
    BlockData getBlockInRangeWithHighestOnlineAccountsCount(int firstBlockHeight, int lastBlockHeight) throws DataException;

	/**
	 * Returns block summaries for the passed height range.
	 */
    List<BlockSummaryData> getBlockSummaries(int firstBlockHeight, int lastBlockHeight) throws DataException;

	/** Returns height of first trimmable online accounts signatures. */
    int getOnlineAccountsSignaturesTrimHeight() throws DataException;

	/** Sets new base height for trimming online accounts signatures.
	 * <p>
	 * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
	 */
    void setOnlineAccountsSignaturesTrimHeight(int trimHeight) throws DataException;

	/**
	 * Trim online accounts signatures from blocks between passed heights.
	 * 
	 * @return number of blocks trimmed
	 */
    int trimOldOnlineAccountsSignatures(int minHeight, int maxHeight) throws DataException;

	/**
	 * Returns first (lowest height) block that doesn't link back to specified block.
	 * 
	 * @param startHeight height of specified block
	 * @throws DataException
	 */
    BlockData getDetachedBlockSignature(int startHeight) throws DataException;


	/** Returns height of first prunable block. */
    int getBlockPruneHeight() throws DataException;

	/** Sets new base height for block pruning.
	 * <p>
	 * NOTE: performs implicit <tt>repository.saveChanges()</tt>.
	 */
    void setBlockPruneHeight(int pruneHeight) throws DataException;

	/** Prunes full block data between passed heights. Returns number of pruned rows. */
    int pruneBlocks(int minHeight, int maxHeight) throws DataException;


	/**
	 * Saves block into repository.
	 * 
	 * @param blockData
	 * @throws DataException
	 */
    void save(BlockData blockData) throws DataException;

	/**
	 * Deletes block from repository.
	 * 
	 * @param blockData
	 * @throws DataException
	 */
    void delete(BlockData blockData) throws DataException;

	/**
	 * Saves a block-transaction mapping into the repository.
	 * <p>
	 * This essentially links a transaction to a specific block.<br>
	 * Transactions cannot be mapped to more than one block, so attempts will result in a DataException.
	 * <p>
	 * Note: it is the responsibility of the caller to maintain contiguous "sequence" values
	 * for all transactions mapped to a block.
	 * 
	 * @param blockTransactionData
	 * @throws DataException
	 */
    void save(BlockTransactionData blockTransactionData) throws DataException;

	/**
	 * Deletes a block-transaction mapping from the repository.
	 * <p>
	 * This essentially unlinks a transaction from a specific block.
	 * <p>
	 * Note: it is the responsibility of the caller to maintain contiguous "sequence" values
	 * for all transactions mapped to a block.
	 * 
	 * @param blockTransactionData
	 * @throws DataException
	 */
    void delete(BlockTransactionData blockTransactionData) throws DataException;

}
