package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.RewardShareTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBRewardShareTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBRewardShareTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT recipient, reward_share_public_key, share_percent, previous_share_percent " +
                     "FROM RewardShareTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            String recipient = resultSet.getString(1);
            byte[] rewardSharePublicKey = resultSet.getBytes(2);
            int sharePercent = resultSet.getInt(3);

            Integer previousSharePercent = resultSet.getInt(4);
            if (previousSharePercent == 0 && resultSet.wasNull())
                previousSharePercent = null;

            return new RewardShareTransactionData(baseTransactionData, recipient, rewardSharePublicKey, sharePercent, previousSharePercent);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch reward-share transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        RewardShareTransactionData rewardShareTransactionData = (RewardShareTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("RewardShareTransactions");

        saveHelper.bind("signature", rewardShareTransactionData.getSignature())
                 .bind("minter_public_key", rewardShareTransactionData.getMinterPublicKey())
                 .bind("recipient", rewardShareTransactionData.getRecipient())
                 .bind("reward_share_public_key", rewardShareTransactionData.getRewardSharePublicKey())
                 .bind("share_percent", rewardShareTransactionData.getSharePercent())
                 .bind("previous_share_percent", rewardShareTransactionData.getPreviousSharePercent());

        executeSave(saveHelper);
    }

    // Helper method to execute SQL queries
    private ResultSet executeQuery(String sql, Object... params) throws DataException {
        try {
            return repository.checkedExecute(sql, params);
        } catch (SQLException e) {
            throw new DataException("Error executing query", e);
        }
    }

    // Helper method to execute save operations
    private void executeSave(HSQLDBSaver saveHelper) throws DataException {
        try {
            saveHelper.execute(repository);
        } catch (SQLException e) {
            throw new DataException("Error saving transaction into repository", e);
        }
    }
}
