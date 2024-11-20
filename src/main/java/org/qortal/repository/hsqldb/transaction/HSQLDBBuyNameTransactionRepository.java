package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.BuyNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBBuyNameTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBBuyNameTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT name, amount, seller, name_reference FROM BuyNameTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            String name = resultSet.getString(1);
            long amount = resultSet.getLong(2);  // amount is a primitive, so no need for null handling
            String seller = resultSet.getString(3);
            byte[] nameReference = resultSet.getBytes(4);

            return new BuyNameTransactionData(baseTransactionData, name, amount, seller, nameReference);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch buy name transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        BuyNameTransactionData buyNameTransactionData = (BuyNameTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("BuyNameTransactions")
                .bind("signature", buyNameTransactionData.getSignature())
                .bind("buyer", buyNameTransactionData.getBuyerPublicKey())
                .bind("name", buyNameTransactionData.getName())
                .bind("amount", buyNameTransactionData.getAmount())
                .bind("seller", buyNameTransactionData.getSeller())
                .bind("name_reference", buyNameTransactionData.getNameReference());

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
}
