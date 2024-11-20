package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.PaymentTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBPaymentTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBPaymentTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT recipient, amount FROM PaymentTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            // If no record found, return null
            if (!resultSet.next()) {
                return null;
            }

            // Retrieve values directly from the result set
            String recipient = resultSet.getString("recipient");
            long amount = resultSet.getLong("amount");

            // Return new PaymentTransactionData instance with the retrieved values
            return new PaymentTransactionData(baseTransactionData, recipient, amount);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch payment transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        PaymentTransactionData paymentTransactionData = (PaymentTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("PaymentTransactions")) {
            // Bind all necessary values before executing the query
            saveHelper.bind("signature", paymentTransactionData.getSignature())
                      .bind("sender", paymentTransactionData.getSenderPublicKey())
                      .bind("recipient", paymentTransactionData.getRecipient())
                      .bind("amount", paymentTransactionData.getAmount());

            // Execute the insert operation
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save payment transaction into repository", e);
        }
    }
}
