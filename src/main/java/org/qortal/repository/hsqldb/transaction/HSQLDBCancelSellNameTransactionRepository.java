package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CancelSellNameTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBCancelSellNameTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBCancelSellNameTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT name, sale_price FROM CancelSellNameTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet != null && resultSet.next()) {
                String name = resultSet.getString("name");  // Use column name for clarity
                Long salePrice = resultSet.getLong("sale_price");
                return new CancelSellNameTransactionData(baseTransactionData, name, salePrice);
            }
            return null;  // Explicit null if no result found
        } catch (SQLException e) {
            throw new DataException("Unable to fetch cancel sell name transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        CancelSellNameTransactionData cancelSellNameTransactionData = (CancelSellNameTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("CancelSellNameTransactions")) {
            saveHelper.bind("signature", cancelSellNameTransactionData.getSignature())
                    .bind("owner", cancelSellNameTransactionData.getOwnerPublicKey())
                    .bind("name", cancelSellNameTransactionData.getName())
                    .bind("sale_price", cancelSellNameTransactionData.getSalePrice());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save cancel sell name transaction into repository", e);
        }
    }
}
