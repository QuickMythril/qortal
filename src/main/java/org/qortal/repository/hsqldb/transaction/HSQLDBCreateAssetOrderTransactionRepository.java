package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.CreateAssetOrderTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;

public class HSQLDBCreateAssetOrderTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBCreateAssetOrderTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        // LEFT OUTER JOIN because asset might not exist (e.g. if ISSUE_ASSET & CREATE_ASSET_ORDER are both unconfirmed)
        String sql = "SELECT have_asset_id, amount, want_asset_id, price, HaveAsset.asset_name, WantAsset.asset_name "
                   + "FROM CreateAssetOrderTransactions "
                   + "LEFT OUTER JOIN Assets AS HaveAsset ON HaveAsset.asset_id = have_asset_id "
                   + "LEFT OUTER JOIN Assets AS WantAsset ON WantAsset.asset_id = want_asset_id "
                   + "WHERE signature = ?";

        try (ResultSet resultSet = this.repository.checkedExecute(sql, baseTransactionData.getSignature())) {
            if (resultSet != null && resultSet.next()) { // Ensure a valid row exists
                long haveAssetId = resultSet.getLong("have_asset_id");
                long amount = resultSet.getLong("amount");
                long wantAssetId = resultSet.getLong("want_asset_id");
                long price = resultSet.getLong("price");
                String haveAssetName = resultSet.getString("HaveAsset.asset_name");
                String wantAssetName = resultSet.getString("WantAsset.asset_name");

                return new CreateAssetOrderTransactionData(baseTransactionData, haveAssetId, wantAssetId, amount, price, haveAssetName, wantAssetName);
            }
            return null; // Explicitly return null if no data found
        } catch (SQLException e) {
            throw new DataException("Unable to fetch create order transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        CreateAssetOrderTransactionData createOrderTransactionData = (CreateAssetOrderTransactionData) transactionData;

        try (HSQLDBSaver saveHelper = new HSQLDBSaver("CreateAssetOrderTransactions")) {
            saveHelper.bind("signature", createOrderTransactionData.getSignature())
                    .bind("creator", createOrderTransactionData.getCreatorPublicKey())
                    .bind("have_asset_id", createOrderTransactionData.getHaveAssetId())
                    .bind("amount", createOrderTransactionData.getAmount())
                    .bind("want_asset_id", createOrderTransactionData.getWantAssetId())
                    .bind("price", createOrderTransactionData.getPrice());

            saveHelper.execute(this.repository);
        } catch (SQLException e) {
            throw new DataException("Unable to save create order transaction into repository", e);
        }
    }
}
