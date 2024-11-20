package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.RegisterNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBRegisterNameTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBRegisterNameTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT name, reduced_name, data FROM RegisterNameTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            // If no record is found, return null
            if (!resultSet.next()) {
                return null;
            }

            // Retrieve values from the result set
            String name = resultSet.getString("name");
            String reducedName = resultSet.getString("reduced_name");
            String data = resultSet.getString("data");

            // Return the RegisterNameTransactionData instance with the retrieved values
            return new RegisterNameTransactionData(baseTransactionData, name, data, reducedName);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch register name transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        RegisterNameTransactionData registerNameTransactionData = (RegisterNameTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("RegisterNameTransactions")) {
            // Bind values to the SQL query
            saveHelper.bind("signature", registerNameTransactionData.getSignature())
                      .bind("registrant", registerNameTransactionData.getRegistrantPublicKey())
                      .bind("name", registerNameTransactionData.getName())
                      .bind("data", registerNameTransactionData.getData())
                      .bind("reduced_name", registerNameTransactionData.getReducedName());

            // Execute the insert operation
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save register name transaction into repository", e);
        }
    }
}
