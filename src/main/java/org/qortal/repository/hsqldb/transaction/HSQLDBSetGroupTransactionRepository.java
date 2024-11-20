package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.SetGroupTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBSetGroupTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBSetGroupTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT default_group_id, previous_default_group_id FROM SetGroupTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // Return null if no result is found
            }

            int defaultGroupId = resultSet.getInt("default_group_id");
            Integer previousDefaultGroupId = resultSet.getInt("previous_default_group_id");
            if (previousDefaultGroupId == 0 && resultSet.wasNull()) {
                previousDefaultGroupId = null;
            }

            return new SetGroupTransactionData(baseTransactionData, defaultGroupId, previousDefaultGroupId);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch set group transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        SetGroupTransactionData setGroupTransactionData = (SetGroupTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("SetGroupTransactions")) {
            saveHelper.bind("signature", setGroupTransactionData.getSignature())
                      .bind("default_group_id", setGroupTransactionData.getDefaultGroupId())
                      .bind("previous_default_group_id", setGroupTransactionData.getPreviousDefaultGroupId());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save set group transaction into repository", e);
        }
    }
}
