package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.GroupKickTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBGroupKickTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBGroupKickTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT group_id, address, reason, member_reference, admin_reference, join_reference, previous_group_id " +
                     "FROM GroupKickTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // Return null if no result is found
            }

            int groupId = resultSet.getInt("group_id");
            String member = resultSet.getString("address");
            String reason = resultSet.getString("reason");
            byte[] memberReference = resultSet.getBytes("member_reference");
            byte[] adminReference = resultSet.getBytes("admin_reference");
            byte[] joinReference = resultSet.getBytes("join_reference");

            Integer previousGroupId = resultSet.getInt("previous_group_id");
            if (previousGroupId == 0 && resultSet.wasNull()) {
                previousGroupId = null;
            }

            return new GroupKickTransactionData(baseTransactionData, groupId, member, reason, memberReference, adminReference,
                    joinReference, previousGroupId);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch group kick transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        GroupKickTransactionData groupKickTransactionData = (GroupKickTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("GroupKickTransactions")) {
            saveHelper.bind("signature", groupKickTransactionData.getSignature())
                      .bind("admin", groupKickTransactionData.getAdminPublicKey())
                      .bind("group_id", groupKickTransactionData.getGroupId())
                      .bind("address", groupKickTransactionData.getMember())
                      .bind("reason", groupKickTransactionData.getReason())
                      .bind("member_reference", groupKickTransactionData.getMemberReference())
                      .bind("admin_reference", groupKickTransactionData.getAdminReference())
                      .bind("join_reference", groupKickTransactionData.getJoinReference())
                      .bind("previous_group_id", groupKickTransactionData.getPreviousGroupId());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save group kick transaction into repository", e);
        }
    }
}
