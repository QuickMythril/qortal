package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CancelGroupBanTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBCancelGroupBanTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBCancelGroupBanTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT group_id, address, ban_reference FROM CancelGroupBanTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            int groupId = resultSet.getInt(1);
            String member = resultSet.getString(2);
            byte[] banReference = resultSet.getBytes(3);

            return new CancelGroupBanTransactionData(baseTransactionData, groupId, member, banReference);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch group unban transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        CancelGroupBanTransactionData groupUnbanTransactionData = (CancelGroupBanTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("CancelGroupBanTransactions")
                .bind("signature", groupUnbanTransactionData.getSignature())
                .bind("admin", groupUnbanTransactionData.getAdminPublicKey())
                .bind("group_id", groupUnbanTransactionData.getGroupId())
                .bind("address", groupUnbanTransactionData.getMember())
                .bind("ban_reference", groupUnbanTransactionData.getBanReference());

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
