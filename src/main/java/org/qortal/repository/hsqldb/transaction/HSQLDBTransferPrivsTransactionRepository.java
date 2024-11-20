package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.data.transaction.TransferPrivsTransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBTransferPrivsTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBTransferPrivsTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT recipient, previous_sender_flags, previous_recipient_flags, previous_sender_blocks_minted_adjustment, previous_sender_blocks_minted " +
                     "FROM TransferPrivsTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            String recipient = resultSet.getString(1);
            Integer previousSenderFlags = getNullableInt(resultSet, 2);
            Integer previousRecipientFlags = getNullableInt(resultSet, 3);
            Integer previousSenderBlocksMintedAdjustment = getNullableInt(resultSet, 4);
            Integer previousSenderBlocksMinted = getNullableInt(resultSet, 5);

            return new TransferPrivsTransactionData(baseTransactionData, recipient, previousSenderFlags, previousRecipientFlags, 
                                                     previousSenderBlocksMintedAdjustment, previousSenderBlocksMinted);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch transfer privs transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        TransferPrivsTransactionData transferPrivsTransactionData = (TransferPrivsTransactionData) transactionData;

        HSQLDBSaver saveHelper = new HSQLDBSaver("TransferPrivsTransactions")
                .bind("signature", transferPrivsTransactionData.getSignature())
                .bind("sender", transferPrivsTransactionData.getSenderPublicKey())
                .bind("recipient", transferPrivsTransactionData.getRecipient())
                .bind("previous_sender_flags", transferPrivsTransactionData.getPreviousSenderFlags())
                .bind("previous_recipient_flags", transferPrivsTransactionData.getPreviousRecipientFlags())
                .bind("previous_sender_blocks_minted_adjustment", transferPrivsTransactionData.getPreviousSenderBlocksMintedAdjustment())
                .bind("previous_sender_blocks_minted", transferPrivsTransactionData.getPreviousSenderBlocksMinted());

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

    // Helper method for handling nullable integers from ResultSet
    private Integer getNullableInt(ResultSet resultSet, int columnIndex) throws SQLException {
        int value = resultSet.getInt(columnIndex);
        return (value == 0 && resultSet.wasNull()) ? null : value;
    }
}
