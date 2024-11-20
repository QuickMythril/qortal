package org.qortal.repository;

import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.utils.ByteArray;

import java.util.List;
import java.util.Set;

public interface ATRepository {

    // CIYAM AutomatedTransactions
	
    ATData fromATAddress(String atAddress) throws DataException;
    boolean exists(String atAddress) throws DataException;
    byte[] getCreatorPublicKey(String atAddress) throws DataException;
    List<ATData> getAllExecutableATs() throws DataException;
    
    List<ATData> getATsByFunctionality(byte[] codeHash, Boolean isExecutable, Integer limit, Integer offset, Boolean reverse) throws DataException;
    List<ATData> getAllATsByFunctionality(Set<ByteArray> codeHashes, Boolean isExecutable) throws DataException;

    Integer getATCreationBlockHeight(String atAddress) throws DataException;
    void save(ATData atData) throws DataException;
    void delete(String atAddress) throws DataException;

    // AT States
	
    ATStateData getATStateAtHeight(String atAddress, int height) throws DataException;
    ATStateData getLatestATState(String atAddress) throws DataException;
    
    List<ATStateData> getMatchingFinalATStates(byte[] codeHash, Boolean isFinished, Integer dataByteOffset, Long expectedValue, Integer minimumFinalHeight,
            Integer limit, Integer offset, Boolean reverse) throws DataException;

    List<ATStateData> getMatchingFinalATStatesQuorum(byte[] codeHash, Boolean isFinished, Integer dataByteOffset, Long expectedValue,
            int minimumCount, int maximumCount, long minimumPeriod) throws DataException;

    List<ATStateData> getBlockATStatesAtHeight(int height) throws DataException;

    // Rebuilding and trimming AT states
	
    void rebuildLatestAtStates(int maxHeight) throws DataException;
    int getAtTrimHeight() throws DataException;
    void setAtTrimHeight(int trimHeight) throws DataException;
    int trimAtStates(int minHeight, int maxHeight, int limit) throws DataException;

    int getAtPruneHeight() throws DataException;
    void setAtPruneHeight(int pruneHeight) throws DataException;
    int pruneAtStates(int minHeight, int maxHeight) throws DataException;

    boolean hasAtStatesHeightIndex() throws DataException;

    void save(ATStateData atStateData) throws DataException;
    void delete(String atAddress, int height) throws DataException;
    void deleteATStates(int height) throws DataException;

    // Finding transactions for ATs to process
	
    class NextTransactionInfo {
        public final int height;
        public final int sequence;
        public final byte[] signature;

        public NextTransactionInfo(int height, int sequence, byte[] signature) {
            this.height = height;
            this.sequence = sequence;
            this.signature = signature;
        }
    }

    NextTransactionInfo findNextTransaction(String recipient, int height, int sequence) throws DataException;

    // Others
	
    void checkConsistency() throws DataException;
}
