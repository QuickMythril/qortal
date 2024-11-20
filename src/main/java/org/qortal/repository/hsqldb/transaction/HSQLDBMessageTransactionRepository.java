package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.MessageTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBMessageTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBMessageTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT version, nonce, recipient, is_text, is_encrypted, amount, asset_id, data FROM MessageTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // Early return if no result found
            }

            int version = resultSet.getInt("version");
            int nonce = resultSet.getInt("nonce");
            String recipient = resultSet.getString("recipient");
            boolean isText = resultSet.getBoolean("is_text");
            boolean isEncrypted = resultSet.getBoolean("is_encrypted");
            long amount = resultSet.getLong("amount");

            // Special null-check for asset ID
            Long assetId = resultSet.getLong("asset_id");
            if (assetId == 0 && resultSet.wasNull()) {
                assetId = null;
            }

            byte[] data = resultSet.getBytes("data");

            return new MessageTransactionData(baseTransactionData, version, nonce, recipient, amount, assetId, data, isText, isEncrypted);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch message transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        MessageTransactionData messageTransactionData = (MessageTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("MessageTransactions")) {
            saveHelper.bind("signature", messageTransactionData.getSignature())
                      .bind("version", messageTransactionData.getVersion())
                      .bind("sender", messageTransactionData.getSenderPublicKey())
                      .bind("recipient", messageTransactionData.getRecipient())
                      .bind("is_text", messageTransactionData.isText())
                      .bind("is_encrypted", messageTransactionData.isEncrypted())
                      .bind("amount", messageTransactionData.getAmount())
                      .bind("asset_id", messageTransactionData.getAssetId())
                      .bind("nonce", messageTransactionData.getNonce())
                      .bind("data", messageTransactionData.getData());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save message transaction into repository", e);
        }
    }
}
