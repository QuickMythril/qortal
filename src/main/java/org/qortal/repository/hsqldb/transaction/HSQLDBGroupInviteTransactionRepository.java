package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.GroupInviteTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBGroupInviteTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBGroupInviteTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT group_id, invitee, time_to_live, join_reference, previous_group_id " +
                     "FROM GroupInviteTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // Return null if no result is found
            }

            int groupId = resultSet.getInt("group_id");
            String invitee = resultSet.getString("invitee");
            int timeToLive = resultSet.getInt("time_to_live");
            byte[] joinReference = resultSet.getBytes("join_reference");

            Integer previousGroupId = resultSet.getInt("previous_group_id");
            if (previousGroupId == 0 && resultSet.wasNull()) {
                previousGroupId = null;
            }

            return new GroupInviteTransactionData(baseTransactionData, groupId, invitee, timeToLive, joinReference, previousGroupId);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch group invite transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        GroupInviteTransactionData groupInviteTransactionData = (GroupInviteTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("GroupInviteTransactions")) {
            saveHelper.bind("signature", groupInviteTransactionData.getSignature())
                      .bind("admin", groupInviteTransactionData.getAdminPublicKey())
                      .bind("group_id", groupInviteTransactionData.getGroupId())
                      .bind("invitee", groupInviteTransactionData.getInvitee())
                      .bind("time_to_live", groupInviteTransactionData.getTimeToLive())
                      .bind("join_reference", groupInviteTransactionData.getJoinReference())
                      .bind("previous_group_id", groupInviteTransactionData.getPreviousGroupId());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save group invite transaction into repository", e);
        }
    }
}
