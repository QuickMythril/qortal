package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.PublicizeTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBPublicizeTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBPublicizeTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT nonce FROM PublicizeTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            // If no record is found, return null
            if (!resultSet.next()) {
                return null;
            }

            // Retrieve nonce from result set
            int nonce = resultSet.getInt("nonce");

            // Return the PublicizeTransactionData instance with the retrieved values
            return new PublicizeTransactionData(baseTransactionData, nonce);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch publicize transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        PublicizeTransactionData publicizeTransactionData = (PublicizeTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("PublicizeTransactions")) {
            // Bind values to the SQL query
            saveHelper.bind("signature", publicizeTransactionData.getSignature())
                      .bind("nonce", publicizeTransactionData.getNonce());

            // Execute the insert operation
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save publicize transaction into repository", e);
        }
    }
}
