package org.qortal.repository.hsqldb;

import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.MessageRepository;
import org.qortal.transaction.Transaction.TransactionType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HSQLDBMessageRepository implements MessageRepository {

    protected HSQLDBRepository repository;

    public HSQLDBMessageRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<MessageTransactionData> getMessagesByParticipants(byte[] senderPublicKey, String recipient, Integer limit, Integer offset, Boolean reverse) throws DataException {
        if (senderPublicKey == null && recipient == null) {
            throw new DataException("At least one of senderPublicKey or recipient is required to fetch messages");
        }

        // Build SQL query and parameters
        StringBuilder sql = new StringBuilder("SELECT signature FROM MessageTransactions JOIN Transactions USING (signature) WHERE ");
        List<Object> bindParams = new ArrayList<>();

        // Add conditions
        if (senderPublicKey != null) {
            sql.append("sender = ? ");
            bindParams.add(senderPublicKey);
        }

        if (recipient != null) {
            if (!bindParams.isEmpty()) sql.append("AND ");
            sql.append("recipient = ? ");
            bindParams.add(recipient);
        }

        // Add sorting and pagination
        sql.append("ORDER BY Transactions.created_when ");
        sql.append((reverse != null && reverse) ? "DESC " : "ASC ");
        HSQLDBRepository.limitOffsetSql(sql, limit, offset);

        // Fetch results
        List<MessageTransactionData> messageTransactionsData = new ArrayList<>();
        try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
            if (resultSet == null) return messageTransactionsData;

            while (resultSet.next()) {
                byte[] signature = resultSet.getBytes(1);
                TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(signature);

                // Validate transaction type
                if (transactionData == null || transactionData.getType() != TransactionType.MESSAGE) {
                    throw new DataException("Inconsistent data: Expected MESSAGE transaction but found another type");
                }

                messageTransactionsData.add((MessageTransactionData) transactionData);
            }
        } catch (SQLException e) {
            throw new DataException("Failed to fetch messages from repository", e);
        }

        return messageTransactionsData;
    }

    @Override
    public boolean exists(byte[] senderPublicKey, String recipient, byte[] messageData) throws DataException {
        try {
            return this.repository.exists(
                "MessageTransactions",
                "sender = ? AND recipient = ? AND data = ?",
                senderPublicKey, recipient, messageData
            );
        } catch (SQLException e) {
            throw new DataException("Failed to check message existence in repository", e);
        }
    }
}
