package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CancelGroupInviteTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBCancelGroupInviteTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBCancelGroupInviteTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT group_id, invitee, invite_reference FROM CancelGroupInviteTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            int groupId = resultSet.getInt(1);
            String invitee = resultSet.getString(2);
            byte[] inviteReference = resultSet.getBytes(3);

            return new CancelGroupInviteTransactionData(baseTransactionData, groupId, invitee, inviteReference);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch cancel group invite transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        CancelGroupInviteTransactionData cancelGroupInviteTransactionData = (CancelGroupInviteTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("CancelGroupInviteTransactions")
                .bind("signature", cancelGroupInviteTransactionData.getSignature())
                .bind("admin", cancelGroupInviteTransactionData.getAdminPublicKey())
                .bind("group_id", cancelGroupInviteTransactionData.getGroupId())
                .bind("invitee", cancelGroupInviteTransactionData.getInvitee())
                .bind("invite_reference", cancelGroupInviteTransactionData.getInviteReference());

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
