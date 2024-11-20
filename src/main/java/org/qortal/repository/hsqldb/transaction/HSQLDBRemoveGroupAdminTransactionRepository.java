package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.RemoveGroupAdminTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBRemoveGroupAdminTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBRemoveGroupAdminTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT group_id, admin, admin_reference FROM RemoveGroupAdminTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            // If no record is found, return null
            if (!resultSet.next()) {
                return null;
            }

            // Retrieve values from the result set
            int groupId = resultSet.getInt("group_id");
            String admin = resultSet.getString("admin");
            byte[] adminReference = resultSet.getBytes("admin_reference");

            // Return the RemoveGroupAdminTransactionData instance with the retrieved values
            return new RemoveGroupAdminTransactionData(baseTransactionData, groupId, admin, adminReference);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch remove group admin transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        RemoveGroupAdminTransactionData removeGroupAdminTransactionData = (RemoveGroupAdminTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("RemoveGroupAdminTransactions")) {
            // Bind values to the SQL query
            saveHelper.bind("signature", removeGroupAdminTransactionData.getSignature())
                      .bind("owner", removeGroupAdminTransactionData.getOwnerPublicKey())
                      .bind("group_id", removeGroupAdminTransactionData.getGroupId())
                      .bind("admin", removeGroupAdminTransactionData.getAdmin())
                      .bind("admin_reference", removeGroupAdminTransactionData.getAdminReference());

            // Execute the insert operation
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save remove group admin transaction into repository", e);
        }
    }
}
