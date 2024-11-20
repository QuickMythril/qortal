package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.ChatTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBChatTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBChatTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT sender, nonce, recipient, is_text, is_encrypted, data, chat_reference FROM ChatTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet != null && resultSet.next()) {  // Check for valid row
                String sender = resultSet.getString("sender");
                int nonce = resultSet.getInt("nonce");
                String recipient = resultSet.getString("recipient");
                boolean isText = resultSet.getBoolean("is_text");
                boolean isEncrypted = resultSet.getBoolean("is_encrypted");
                byte[] data = resultSet.getBytes("data");
                byte[] chatReference = resultSet.getBytes("chat_reference");

                return new ChatTransactionData(baseTransactionData, sender, nonce, recipient, chatReference, data, isText, isEncrypted);
            }
            return null;  // Explicitly return null if no matching result
        } catch (SQLException e) {
            throw new DataException("Unable to fetch chat transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        ChatTransactionData chatTransactionData = (ChatTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("ChatTransactions")) {
            saveHelper.bind("signature", chatTransactionData.getSignature())
                    .bind("nonce", chatTransactionData.getNonce())
                    .bind("sender", chatTransactionData.getSender())
                    .bind("recipient", chatTransactionData.getRecipient())
                    .bind("is_text", chatTransactionData.getIsText())
                    .bind("is_encrypted", chatTransactionData.getIsEncrypted())
                    .bind("data", chatTransactionData.getData())
                    .bind("chat_reference", chatTransactionData.getChatReference());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save chat transaction into repository", e);
        }
    }
}
