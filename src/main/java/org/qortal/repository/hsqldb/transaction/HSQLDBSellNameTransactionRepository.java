package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.SellNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBSellNameTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBSellNameTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT name, amount FROM SellNameTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet != null && resultSet.next()) {
                String name = resultSet.getString("name");  // Use column name for clarity
                long amount = resultSet.getLong("amount");
                return new SellNameTransactionData(baseTransactionData, name, amount);
            }
            return null;  // Explicit null if no result found
        } catch (SQLException e) {
            throw new DataException("Unable to fetch sell name transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        SellNameTransactionData sellNameTransactionData = (SellNameTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("SellNameTransactions")) {
            saveHelper.bind("signature", sellNameTransactionData.getSignature())
                    .bind("owner", sellNameTransactionData.getOwnerPublicKey())
                    .bind("name", sellNameTransactionData.getName())
                    .bind("amount", sellNameTransactionData.getAmount());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save sell name transaction into repository", e);
        }
    }
}
