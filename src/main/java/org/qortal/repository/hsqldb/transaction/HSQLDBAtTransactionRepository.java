package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.ATTransactionData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBAtTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBAtTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT AT_address, recipient, amount, asset_id, message FROM ATTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            String atAddress = resultSet.getString(1);
            String recipient = resultSet.getString(2);
            Long amount = getNullableLong(resultSet, 3);
            Long assetId = getNullableLong(resultSet, 4);
            byte[] message = resultSet.getBytes(5);

            return new ATTransactionData(baseTransactionData, atAddress, recipient, amount, assetId, message);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch AT transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        ATTransactionData atTransactionData = (ATTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("ATTransactions")
                .bind("signature", atTransactionData.getSignature())
                .bind("AT_address", atTransactionData.getATAddress())
                .bind("recipient", atTransactionData.getRecipient())
                .bind("amount", atTransactionData.getAmount())
                .bind("asset_id", atTransactionData.getAssetId())
                .bind("message", atTransactionData.getMessage());

        executeSave(saveHelper);
    }

    // Helper method to execute queries
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

    // Helper method to handle nullable Long values from the ResultSet
    private Long getNullableLong(ResultSet resultSet, int columnIndex) throws SQLException {
        Long value = resultSet.getLong(columnIndex);
        return (value == 0 && resultSet.wasNull()) ? null : value;
    }
}
