package org.qortal.repository;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public interface Repository extends AutoCloseable {

	ATRepository getATRepository();

	AccountRepository getAccountRepository();

	ArbitraryRepository getArbitraryRepository();

	AssetRepository getAssetRepository();

	BlockRepository getBlockRepository();

	BlockArchiveRepository getBlockArchiveRepository();

	ChatRepository getChatRepository();

	CrossChainRepository getCrossChainRepository();

	GroupRepository getGroupRepository();

	MessageRepository getMessageRepository();

	NameRepository getNameRepository();

	NetworkRepository getNetworkRepository();

	TransactionRepository getTransactionRepository();

	VotingRepository getVotingRepository();

	void saveChanges() throws DataException;

	void discardChanges() throws DataException;

	void setSavepoint() throws DataException;

	void rollbackToSavepoint() throws DataException;

	@Override
    void close() throws DataException;

	void rebuild() throws DataException;

	boolean getDebug();

	void setDebug(boolean debugState);

	void backup(boolean quick, String name, Long timeout) throws DataException, TimeoutException;

	void performPeriodicMaintenance(Long timeout) throws DataException, TimeoutException;

	void exportNodeLocalData() throws DataException;

	void importDataFromFile(String filename) throws DataException, IOException;

	void checkConsistency() throws DataException;

	static void attemptRecovery(String connectionUrl, String name) throws DataException {}

}
