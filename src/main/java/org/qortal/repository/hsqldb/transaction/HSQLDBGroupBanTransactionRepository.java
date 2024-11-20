package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.GroupBanTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBGroupBanTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBGroupBanTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT group_id, address, reason, time_to_live, member_reference, admin_reference, join_invite_reference, previous_group_id " +
                     "FROM GroupBanTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // Return null if no result is found
            }

            int groupId = resultSet.getInt("group_id");
            String offender = resultSet.getString("address");
            String reason = resultSet.getString("reason");
            int timeToLive = resultSet.getInt("time_to_live");
            byte[] memberReference = resultSet.getBytes("member_reference");
            byte[] adminReference = resultSet.getBytes("admin_reference");
            byte[] joinInviteReference = resultSet.getBytes("join_invite_reference");

            Integer previousGroupId = resultSet.getInt("previous_group_id");
            if (previousGroupId == 0 && resultSet.wasNull()) {
                previousGroupId = null;
            }

            return new GroupBanTransactionData(baseTransactionData, groupId, offender, reason, timeToLive,
                    memberReference, adminReference, joinInviteReference, previousGroupId);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch group ban transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        GroupBanTransactionData groupBanTransactionData = (GroupBanTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("GroupBanTransactions")) {
            saveHelper.bind("signature", groupBanTransactionData.getSignature())
                      .bind("admin", groupBanTransactionData.getAdminPublicKey())
                      .bind("group_id", groupBanTransactionData.getGroupId())
                      .bind("address", groupBanTransactionData.getOffender())
                      .bind("reason", groupBanTransactionData.getReason())
                      .bind("time_to_live", groupBanTransactionData.getTimeToLive())
                      .bind("member_reference", groupBanTransactionData.getMemberReference())
                      .bind("admin_reference", groupBanTransactionData.getAdminReference())
                      .bind("join_invite_reference", groupBanTransactionData.getJoinInviteReference())
                      .bind("previous_group_id", groupBanTransactionData.getPreviousGroupId());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save group ban transaction into repository", e);
        }
    }
}
