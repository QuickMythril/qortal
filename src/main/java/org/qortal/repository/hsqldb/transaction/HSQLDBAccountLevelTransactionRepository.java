package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.AccountLevelTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBAccountLevelTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBAccountLevelTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT target, level FROM AccountLevelTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            String target = resultSet.getString(1);
            int level = resultSet.getInt(2);

            return new AccountLevelTransactionData(baseTransactionData, target, level);
        } catch (SQLException e) {
            throw new DataException("Error fetching account level transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        AccountLevelTransactionData accountLevelTransactionData = (AccountLevelTransactionData) transactionData;
        
        String tableName = "AccountLevelTransactions";

        HSQLDBSaver saveHelper = new HSQLDBSaver(tableName)
                .bind("signature", accountLevelTransactionData.getSignature())
                .bind("creator", accountLevelTransactionData.getCreatorPublicKey())
                .bind("target", accountLevelTransactionData.getTarget())
                .bind("level", accountLevelTransactionData.getLevel());

        executeSave(saveHelper);
    }

    private ResultSet executeQuery(String sql, Object... params) throws DataException {
        try {
            return this.repository.checkedExecute(sql, params);
        } catch (SQLException e) {
            throw new DataException("Error executing query", e);
        }
    }

    private void executeSave(HSQLDBSaver saveHelper) throws DataException {
        try {
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Error saving transaction into repository", e);
        }
    }
}
