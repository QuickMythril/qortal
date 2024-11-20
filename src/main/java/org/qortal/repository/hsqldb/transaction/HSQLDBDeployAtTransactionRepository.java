package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.DeployAtTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBDeployAtTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBDeployAtTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT AT_name, description, AT_type, AT_tags, creation_bytes, amount, asset_id, AT_address "
                   + "FROM DeployATTransactions WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet == null || !resultSet.next()) {
                return null;  // No result, return null
            }

            String name = resultSet.getString("AT_name");
            String description = resultSet.getString("description");
            String atType = resultSet.getString("AT_type");
            String tags = resultSet.getString("AT_tags");
            byte[] creationBytes = resultSet.getBytes("creation_bytes");
            long amount = resultSet.getLong("amount");
            long assetId = resultSet.getLong("asset_id");

            // Handling potential null AT_address
            String atAddress = resultSet.getString("AT_address");

            return new DeployAtTransactionData(baseTransactionData, atAddress, name, description, atType, tags, creationBytes, amount, assetId);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch deploy AT transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        DeployAtTransactionData deployATTransactionData = (DeployAtTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("DeployATTransactions")) {
            saveHelper.bind("signature", deployATTransactionData.getSignature())
                      .bind("creator", deployATTransactionData.getCreatorPublicKey())
                      .bind("AT_name", deployATTransactionData.getName())
                      .bind("description", deployATTransactionData.getDescription())
                      .bind("AT_type", deployATTransactionData.getAtType())
                      .bind("AT_tags", deployATTransactionData.getTags())
                      .bind("creation_bytes", deployATTransactionData.getCreationBytes())
                      .bind("amount", deployATTransactionData.getAmount())
                      .bind("asset_id", deployATTransactionData.getAssetId())
                      .bind("AT_address", deployATTransactionData.getAtAddress());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save deploy AT transaction into repository", e);
        }
    }
}
