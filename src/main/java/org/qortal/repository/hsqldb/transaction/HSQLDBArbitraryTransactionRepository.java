package org.qortal.repository.hsqldb.transaction;

import org.qortal.data.PaymentData;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.ArbitraryTransactionData.DataType;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.DataException;
import org.qortal.repository.hsqldb.HSQLDBRepository;
import org.qortal.repository.hsqldb.HSQLDBSaver;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public class HSQLDBArbitraryTransactionRepository extends HSQLDBTransactionRepository {

    public HSQLDBArbitraryTransactionRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    @Override
    TransactionData fromBase(BaseTransactionData baseTransactionData) throws DataException {
        String sql = "SELECT version, nonce, service, size, is_data_raw, data, metadata_hash, " +
                     "name, identifier, update_method, secret, compression FROM ArbitraryTransactions WHERE signature = ?";

        try (ResultSet resultSet = executeQuery(sql, baseTransactionData.getSignature())) {
            if (resultSet == null) return null;

            int version = resultSet.getInt(1);
            int nonce = resultSet.getInt(2);
            int serviceInt = resultSet.getInt(3);
            int size = resultSet.getInt(4);
            boolean isDataRaw = resultSet.getBoolean(5);
            DataType dataType = isDataRaw ? DataType.RAW_DATA : DataType.DATA_HASH;
            byte[] data = resultSet.getBytes(6);
            byte[] metadataHash = resultSet.getBytes(7);
            String name = resultSet.getString(8);
            String identifier = resultSet.getString(9);
            ArbitraryTransactionData.Method method = ArbitraryTransactionData.Method.valueOf(resultSet.getInt(10));
            byte[] secret = resultSet.getBytes(11);
            ArbitraryTransactionData.Compression compression = ArbitraryTransactionData.Compression.valueOf(resultSet.getInt(12));

            List<PaymentData> payments = getPaymentsFromSignature(baseTransactionData.getSignature());
            return new ArbitraryTransactionData(baseTransactionData, version, serviceInt, nonce, size, name, identifier,
                    method, secret, compression, data, dataType, metadataHash, payments);
        } catch (SQLException e) {
            throw new DataException("Unable to fetch arbitrary transaction from repository", e);
        }
    }

    @Override
    public void save(TransactionData transactionData) throws DataException {
        ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;

        // Save raw data separately for V4+ if needed
        if (arbitraryTransactionData.getVersion() >= 4) {
            repository.getArbitraryRepository().save(arbitraryTransactionData);
        }

        // Default to 0 for method/compression if null
        int method = arbitraryTransactionData.getMethod() != null ? arbitraryTransactionData.getMethod().value : 0;
        int compression = arbitraryTransactionData.getCompression() != null ? arbitraryTransactionData.getCompression().value : 0;

        HSQLDBSaver saveHelper = new HSQLDBSaver("ArbitraryTransactions")
                .bind("signature", arbitraryTransactionData.getSignature())
                .bind("sender", arbitraryTransactionData.getSenderPublicKey())
                .bind("version", arbitraryTransactionData.getVersion())
                .bind("service", arbitraryTransactionData.getServiceInt())
                .bind("nonce", arbitraryTransactionData.getNonce())
                .bind("size", arbitraryTransactionData.getSize())
                .bind("is_data_raw", arbitraryTransactionData.getDataType() == DataType.RAW_DATA)
                .bind("data", arbitraryTransactionData.getData())
                .bind("metadata_hash", arbitraryTransactionData.getMetadataHash())
                .bind("name", arbitraryTransactionData.getName())
                .bind("identifier", arbitraryTransactionData.getIdentifier())
                .bind("update_method", method)
                .bind("secret", arbitraryTransactionData.getSecret())
                .bind("compression", compression);

        executeSave(saveHelper);

        // Only save payments for non-version 1 transactions
        if (arbitraryTransactionData.getVersion() != 1) {
            savePayments(transactionData.getSignature(), arbitraryTransactionData.getPayments());
        }
    }

    @Override
    public void delete(TransactionData transactionData) throws DataException {
        ArbitraryTransactionData arbitraryTransactionData = (ArbitraryTransactionData) transactionData;
        repository.getArbitraryRepository().delete(arbitraryTransactionData);
    }

    // Helper method for query execution
    private ResultSet executeQuery(String sql, Object... params) throws DataException {
        try {
            return repository.checkedExecute(sql, params);
        } catch (SQLException e) {
            throw new DataException("Error executing query", e);
        }
    }

    // Helper method for saving data
    private void executeSave(HSQLDBSaver saveHelper) throws DataException {
        try {
            saveHelper.execute(repository);
        } catch (SQLException e) {
            throw new DataException("Error saving transaction into repository", e);
        }
    }
}
