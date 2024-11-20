package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.GenesisTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBGenesisTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBGenesisTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT recipient, amount, asset_id FROM GenesisTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // Return null if no result is found
            }

            String recipient = resultSet.getString("recipient");
            long amount = resultSet.getLong("amount");
            long assetId = resultSet.getLong("asset_id");

            return new GenesisTransactionData(baseTransactionData, recipient, amount, assetId);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch genesis transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        GenesisTransactionData genesisTransactionData = (GenesisTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("GenesisTransactions")) {
            saveHelper.bind("signature", genesisTransactionData.getSignature())
                      .bind("recipient", genesisTransactionData.getRecipient())
                      .bind("amount", genesisTransactionData.getAmount())
                      .bind("asset_id", genesisTransactionData.getAssetId());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save genesis transaction into repository", e);
        }
    }
}
