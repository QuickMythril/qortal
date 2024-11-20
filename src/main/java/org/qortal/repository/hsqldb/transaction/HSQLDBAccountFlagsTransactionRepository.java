package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.AccountFlagsTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBAccountFlagsTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBAccountFlagsTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT target, and_mask, or_mask, xor_mask, previous_flags FROM AccountFlagsTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            String target = resultSet.getString(1);
            int andMask = resultSet.getInt(2);
            int orMask = resultSet.getInt(3);
            int xorMask = resultSet.getInt(4);
            Integer previousFlags = getNullableInteger(resultSet, 5);

            return new AccountFlagsTransactionData(baseTransactionData, target, andMask, orMask, xorMask, previousFlags);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch account flags transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        AccountFlagsTransactionData accountFlagsTransactionData = (AccountFlagsTransactionData) transactionData;
        String tableName = "AccountFlagsTransactions";

        HSQLDBSaver saveHelper = new HSQLDBSaver(tableName)
                .bind("signature", accountFlagsTransactionData.getSignature())
                .bind("creator", accountFlagsTransactionData.getCreatorPublicKey())
                .bind("target", accountFlagsTransactionData.getTarget())
                .bind("and_mask", accountFlagsTransactionData.getAndMask())
                .bind("or_mask", accountFlagsTransactionData.getOrMask())
                .bind("xor_mask", accountFlagsTransactionData.getXorMask())
                .bind("previous_flags", accountFlagsTransactionData.getPreviousFlags());

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

    private Integer getNullableInteger(ResultSet resultSet, int columnIndex) throws SQLException {
        int value = resultSet.getInt(columnIndex);
        return (value == 0 && resultSet.wasNull()) ? null : value;
    }
}
