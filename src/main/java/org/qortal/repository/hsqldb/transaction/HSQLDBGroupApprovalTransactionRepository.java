package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.GroupApprovalTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBGroupApprovalTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBGroupApprovalTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT pending_signature, approval, prior_reference FROM GroupApprovalTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            byte[] pendingSignature = resultSet.getBytes(1);
            boolean approval = resultSet.getBoolean(2);
            byte[] priorReference = resultSet.getBytes(3);

            return new GroupApprovalTransactionData(baseTransactionData, pendingSignature, approval, priorReference);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch group approval transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        GroupApprovalTransactionData groupApprovalTransactionData = (GroupApprovalTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("GroupApprovalTransactions")
                .bind("signature", groupApprovalTransactionData.getSignature())
                .bind("admin", groupApprovalTransactionData.getAdminPublicKey())
                .bind("pending_signature", groupApprovalTransactionData.getPendingSignature())
                .bind("approval", groupApprovalTransactionData.getApproval())
                .bind("prior_reference", groupApprovalTransactionData.getPriorReference());

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
