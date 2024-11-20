package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.LeaveGroupTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBLeaveGroupTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBLeaveGroupTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT group_id, member_reference, admin_reference, previous_group_id FROM LeaveGroupTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            int groupId = resultSet.getInt(1);
            byte[] memberReference = resultSet.getBytes(2);
            byte[] adminReference = resultSet.getBytes(3);

            Integer previousGroupId = resultSet.getInt(4);
            if (previousGroupId == 0 && resultSet.wasNull()) previousGroupId = null;

            return new LeaveGroupTransactionData(baseTransactionData, groupId, memberReference, adminReference, previousGroupId);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch leave group transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        LeaveGroupTransactionData leaveGroupTransactionData = (LeaveGroupTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("LeaveGroupTransactions")
                .bind("signature", leaveGroupTransactionData.getSignature())
                .bind("leaver", leaveGroupTransactionData.getLeaverPublicKey())
                .bind("group_id", leaveGroupTransactionData.getGroupId())
                .bind("member_reference", leaveGroupTransactionData.getMemberReference())
                .bind("admin_reference", leaveGroupTransactionData.getAdminReference())
                .bind("previous_group_id", leaveGroupTransactionData.getPreviousGroupId());

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
