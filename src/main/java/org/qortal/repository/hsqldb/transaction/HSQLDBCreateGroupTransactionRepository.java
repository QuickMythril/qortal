package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CreateGroupTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.group.Group.ApprovalThreshold;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBCreateGroupTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBCreateGroupTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT group_name, description, is_open, approval_threshold, min_block_delay, max_block_delay, group_id, reduced_group_name "
                   + "FROM CreateGroupTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet != null && resultSet.next()) {  // Ensure a row exists
                String groupName = resultSet.getString("group_name");
                String description = resultSet.getString("description");
                boolean isOpen = resultSet.getBoolean("is_open");
                ApprovalThreshold approvalThreshold = ApprovalThreshold.valueOf(resultSet.getInt("approval_threshold"));
                int minBlockDelay = resultSet.getInt("min_block_delay");
                int maxBlockDelay = resultSet.getInt("max_block_delay");

                Integer groupId = resultSet.getInt("group_id");
                if (groupId == 0 && resultSet.wasNull()) {
                    groupId = null;  // Handle possible null value
                }

                String reducedGroupName = resultSet.getString("reduced_group_name");

                return new CreateGroupTransactionData(baseTransactionData, groupName, description, isOpen, approvalThreshold,
                        minBlockDelay, maxBlockDelay, groupId, reducedGroupName);
            }
            return null;  // Explicitly return null if no result found
        } catch (SQLException e) {
            throw new DataException("Unable to fetch create group transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        CreateGroupTransactionData createGroupTransactionData = (CreateGroupTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("CreateGroupTransactions")) {
            saveHelper.bind("signature", createGroupTransactionData.getSignature())
                    .bind("creator", createGroupTransactionData.getCreatorPublicKey())
                    .bind("group_name", createGroupTransactionData.getGroupName())
                    .bind("reduced_group_name", createGroupTransactionData.getReducedGroupName())
                    .bind("description", createGroupTransactionData.getDescription())
                    .bind("is_open", createGroupTransactionData.isOpen())
                    .bind("approval_threshold", createGroupTransactionData.getApprovalThreshold().value)
                    .bind("min_block_delay", createGroupTransactionData.getMinimumBlockDelay())
                    .bind("max_block_delay", createGroupTransactionData.getMaximumBlockDelay())
                    .bind("group_id", createGroupTransactionData.getGroupId());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save create group transaction into repository", e);
        }
    }
}
