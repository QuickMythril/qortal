package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.PaymentData;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.MultiPaymentTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class HSQLDBMultiPaymentTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBMultiPaymentTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT TRUE FROM MultiPaymentTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // Return null if no record is found
            }

            // Retrieve payments associated with the multi-payment transaction
            List<PaymentData> payments = getPaymentsFromSignature(baseTransactionData.getSignature());

            return new MultiPaymentTransactionData(baseTransactionData, payments);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch multi-payment transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        MultiPaymentTransactionData multiPaymentTransactionData = (MultiPaymentTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("MultiPaymentTransactions")) {
            saveHelper.bind("signature", multiPaymentTransactionData.getSignature())
                      .bind("sender", multiPaymentTransactionData.getSenderPublicKey());

            // Save the multi-payment transaction record
            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save multi-payment transaction into repository", e);
        }

        // Save associated payments after the multi-payment transaction is saved
        savePayments(transactionData.getSignature(), multiPaymentTransactionData.getPayments());
    }

    private List<PaymentData> getPaymentsFromSignature(String signature) throws DataException {
        // Retrieve the payments associated with the given signature (query and logic omitted for brevity)
        // The implementation here will depend on how payments are stored in the database.
        return List.of();  // Placeholder - replace with actual logic for retrieving payments
    }
}
