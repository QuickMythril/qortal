package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.PresenceTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;
import org.qortal.transaction.PresenceTransaction.PresenceType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBPresenceTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBPresenceTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT nonce, presence_type, timestamp_signature FROM PresenceTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            // If no record is found, return null
            if (!resultSet.next()) {
                return null;
            }

            // Retrieve values from the result set
            int nonce = resultSet.getInt("nonce");
            int presenceTypeValue = resultSet.getInt("presence_type");
            PresenceType presenceType = PresenceType.valueOf(presenceTypeValue);
            byte[] timestampSignature = resultSet.getBytes("timestamp_signature");

            // Return the PresenceTransactionData instance with the retrieved values
            return new PresenceTransactionData(baseTransactionData, nonce, presenceType, timestampSignature);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch presence transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        PresenceTransactionData presenceTransactionData = (PresenceTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("PresenceTransactions")) {
            // Bind values to the SQL query
            saveHelper.bind("signature", presenceTransactionData.getSignature())
                      .bind("nonce", presenceTransactionData.getNonce())
                      .bind("presence_type", presenceTransactionData.getPresenceType().value)
                      .bind("timestamp_signature", presenceTransactionData.getTimestampSignature());

            // Execute the insert operation
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save presence transaction into repository", e);
        }
    }
}
