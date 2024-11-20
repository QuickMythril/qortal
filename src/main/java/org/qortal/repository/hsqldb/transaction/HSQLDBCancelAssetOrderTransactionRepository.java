package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CancelAssetOrderTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBCancelAssetOrderTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBCancelAssetOrderTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT asset_order_id FROM CancelAssetOrderTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            byte[] assetOrderId = resultSet.getBytes(1);

            return new CancelAssetOrderTransactionData(baseTransactionData, assetOrderId);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch cancel asset order transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        CancelAssetOrderTransactionData cancelOrderTransactionData = (CancelAssetOrderTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("CancelAssetOrderTransactions")
                .bind("signature", cancelOrderTransactionData.getSignature())
                .bind("creator", cancelOrderTransactionData.getCreatorPublicKey())
                .bind("asset_order_id", cancelOrderTransactionData.getOrderId());

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
