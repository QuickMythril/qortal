package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.UpdateNameTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBUpdateNameTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBUpdateNameTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT name, new_name, new_data, reduced_new_name, name_reference " +
                     "FROM UpdateNameTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            String name = resultSet.getString(1);
            String newName = resultSet.getString(2);
            String newData = resultSet.getString(3);
            String reducedNewName = resultSet.getString(4);
            byte[] nameReference = resultSet.getBytes(5);

            return new UpdateNameTransactionData(baseTransactionData, name, newName, newData, reducedNewName, nameReference);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch update name transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        UpdateNameTransactionData updateNameTransactionData = (UpdateNameTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("UpdateNameTransactions")
                .bind("signature", updateNameTransactionData.getSignature())
                .bind("owner", updateNameTransactionData.getOwnerPublicKey())
                .bind("name", updateNameTransactionData.getName())
                .bind("new_name", updateNameTransactionData.getNewName())
                .bind("new_data", updateNameTransactionData.getNewData())
                .bind("reduced_new_name", updateNameTransactionData.getReducedNewName())
                .bind("name_reference", updateNameTransactionData.getNameReference());

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
