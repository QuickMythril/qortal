package org.qortal.repository.hsqldb;

import org.qortal.api.model.BlockSignerSummary;
import org.qortal.data.block.BlockData;
import org.qortal.data.block.BlockSummaryData;
import org.qortal.data.block.BlockTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.BlockRepository;
import org.qortal.repository.DataException;
import org.qortal.repository.TransactionRepository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HSQLDBBlockRepository implements BlockRepository {

    private static final String BLOCK_DB_COLUMNS = "version, reference, transaction_count, total_fees, "
            + "transactions_signature, height, minted_when, minter, minter_signature, "
            + "AT_count, AT_fees, online_accounts, online_accounts_count, online_accounts_timestamp, online_accounts_signatures";
    protected HSQLDBRepository repository;

    public HSQLDBBlockRepository(HSQLDBRepository repository) {
        this.repository = repository;
    }

    private BlockData getBlockFromResultSet(ResultSet resultSet) throws DataException {
        if (resultSet == null) return null;
        try {
            int version = resultSet.getInt(1);
            byte[] reference = resultSet.getBytes(2);
            int transactionCount = resultSet.getInt(3);
            long totalFees = resultSet.getLong(4);
            byte[] transactionsSignature = resultSet.getBytes(5);
            int height = resultSet.getInt(6);
            long timestamp = resultSet.getLong(7);
            byte[] minterPublicKey = resultSet.getBytes(8);
            byte[] minterSignature = resultSet.getBytes(9);
            int atCount = resultSet.getInt(10);
            long atFees = resultSet.getLong(11);
            byte[] encodedOnlineAccounts = resultSet.getBytes(12);
            int onlineAccountsCount = resultSet.getInt(13);
            Long onlineAccountsTimestamp = resultSet.getLong(14) == 0 && resultSet.wasNull() ? null : resultSet.getLong(14);
            byte[] onlineAccountsSignatures = resultSet.getBytes(15);
            return new BlockData(version, reference, transactionCount, totalFees, transactionsSignature, height, timestamp,
                    minterPublicKey, minterSignature, atCount, atFees, encodedOnlineAccounts, onlineAccountsCount, onlineAccountsTimestamp, onlineAccountsSignatures);
        } catch (SQLException e) {
            throw new DataException("Error extracting data from result set", e);
        }
    }

    private List<BlockData> getBlocksFromQuery(String sql, Object... params) throws DataException {
        List<BlockData> blockData = new ArrayList<>();
        try (ResultSet resultSet = this.repository.checkedExecute(sql, params)) {
            if (resultSet != null) {
                while (resultSet.next()) {
                    blockData.add(getBlockFromResultSet(resultSet));
                }
            }
        } catch (SQLException e) {
            throw new DataException("Error fetching blocks from repository", e);
        }
        return blockData;
    }

    @Override
    public BlockData fromSignature(byte[] signature) throws DataException {
        return fetchBlockByField("signature", signature);
    }

    @Override
    public BlockData fromReference(byte[] reference) throws DataException {
        return fetchBlockByField("reference", reference);
    }

    @Override
    public BlockData fromHeight(int height) throws DataException {
        return fetchBlockByField("height", height);
    }

    private BlockData fetchBlockByField(String field, Object value) throws DataException {
        String sql = "SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks WHERE " + field + " = ? LIMIT 1";
        try (ResultSet resultSet = this.repository.checkedExecute(sql, value)) {
            return getBlockFromResultSet(resultSet);
        } catch (SQLException e) {
            throw new DataException("Error fetching block by " + field + " from repository", e);
        }
    }

    @Override
    public boolean exists(byte[] signature) throws DataException {
        return existsInTable("Blocks", "signature = ?", signature);
    }

    private boolean existsInTable(String table, String condition, Object value) throws DataException {
        try {
            return this.repository.exists(table, condition, value);
        } catch (SQLException e) {
            throw new DataException("Unable to check for block in repository", e);
        }
    }

    @Override
    public int getHeightFromSignature(byte[] signature) throws DataException {
        return fetchIntFromQuery("SELECT height FROM Blocks WHERE signature = ? LIMIT 1", signature);
    }

    @Override
    public int getHeightFromTimestamp(long timestamp) throws DataException {
        String sql = "SELECT height FROM Blocks WHERE minted_when <= ? ORDER BY minted_when DESC, height DESC LIMIT 1";
        return fetchIntFromQuery(sql, timestamp);
    }

    private int fetchIntFromQuery(String sql, Object param) throws DataException {
        try (ResultSet resultSet = this.repository.checkedExecute(sql, param)) {
            return resultSet != null ? resultSet.getInt(1) : 0;
        } catch (SQLException e) {
            throw new DataException("Error fetching data from repository", e);
        }
    }

    @Override
    public long getTimestampFromHeight(int height) throws DataException {
        return fetchLongFromQuery("SELECT minted_when FROM Blocks WHERE height = ?", height);
    }

    private long fetchLongFromQuery(String sql, Object param) throws DataException {
        try (ResultSet resultSet = this.repository.checkedExecute(sql, param)) {
            return resultSet != null ? resultSet.getLong(1) : 0;
        } catch (SQLException e) {
            throw new DataException("Error fetching data from repository", e);
        }
    }

    @Override
    public int getBlockchainHeight() throws DataException {
        return fetchIntFromQuery("SELECT height FROM Blocks ORDER BY height DESC LIMIT 1", null);
    }

    @Override
    public BlockData getLastBlock() throws DataException {
        return fetchBlockByField("height", getBlockchainHeight());
    }

    @Override
    public List<TransactionData> getTransactionsFromSignature(byte[] signature, Integer limit, Integer offset, Boolean reverse) throws DataException {
        StringBuilder sql = new StringBuilder("SELECT transaction_signature FROM BlockTransactions WHERE block_signature = ? ORDER BY block_signature");
        if (reverse != null && reverse) sql.append(" DESC");
        sql.append(", sequence");
        if (reverse != null && reverse) sql.append(" DESC");
        HSQLDBRepository.limitOffsetSql(sql, limit, offset);
        List<TransactionData> transactions = new ArrayList<>();
        try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), signature)) {
            if (resultSet != null) {
                TransactionRepository transactionRepo = this.repository.getTransactionRepository();
                while (resultSet.next()) {
                    byte[] transactionSignature = resultSet.getBytes(1);
                    transactions.add(transactionRepo.fromSignature(transactionSignature));
                }
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch block's transactions from repository", e);
        }
        return transactions;
    }

    @Override
    public List<BlockSignerSummary> getBlockSigners(List<String> addresses, Integer limit, Integer offset, Boolean reverse) throws DataException {
        String subquerySql = "SELECT minter, COUNT(signature) FROM Blocks GROUP BY minter";
        StringBuilder sql = new StringBuilder("SELECT DISTINCT block_minter, n_blocks, minter_public_key, minter, recipient FROM (");
        sql.append(subquerySql);
        sql.append(") AS Minters (block_minter, n_blocks) LEFT OUTER JOIN RewardShares ON reward_share_public_key = block_minter ");
        buildAddressFilteringSQL(addresses, sql);
        sql.append("ORDER BY n_blocks ");
        if (reverse != null && reverse) sql.append("DESC ");
        HSQLDBRepository.limitOffsetSql(sql, limit, offset);
        return fetchBlockSignerSummaries(sql.toString(), addresses);
    }

    private void buildAddressFilteringSQL(List<String> addresses, StringBuilder sql) {
        if (addresses != null && !addresses.isEmpty()) {
            sql.append(" LEFT OUTER JOIN Accounts AS BlockMinterAccounts ON BlockMinterAccounts.public_key = block_minter ");
            sql.append(" LEFT OUTER JOIN Accounts AS RewardShareMinterAccounts ON RewardShareMinterAccounts.public_key = minter_public_key ");
            sql.append(" JOIN (VALUES ");
            for (int i = 0; i < addresses.size(); i++) {
                if (i != 0) sql.append(", ");
                sql.append("(?)");
            }
            sql.append(") AS FilterAccounts (account) ");
            sql.append(" ON FilterAccounts.account IN (recipient, BlockMinterAccounts.account, RewardShareMinterAccounts.account) ");
        }
    }

    private List<BlockSignerSummary> fetchBlockSignerSummaries(String sql, List<String> addresses) throws DataException {
        List<BlockSignerSummary> summaries = new ArrayList<>();
        try (ResultSet resultSet = this.repository.checkedExecute(sql, addresses.toArray())) {
            while (resultSet.next()) {
                summaries.add(new BlockSignerSummary(resultSet.getString("minter"), resultSet.getInt("n_blocks")));
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch block signer summaries from repository", e);
        }
        return summaries;
    }

    @Override
    public List<BlockSummaryData> getBlockSummaries(Integer limit, Integer offset, Boolean reverse) throws DataException {
        String sql = "SELECT " + BLOCK_DB_COLUMNS + " FROM Blocks";
        if (reverse != null && reverse) sql += " ORDER BY height DESC";
        HSQLDBRepository.limitOffsetSql(new StringBuilder(sql), limit, offset);
        return fetchBlockSummaries(sql);
    }

    private List<BlockSummaryData> fetchBlockSummaries(String sql) throws DataException {
        List<BlockSummaryData> blockSummaries = new ArrayList<>();
        try (ResultSet resultSet = this.repository.checkedExecute(sql)) {
            while (resultSet.next()) {
                blockSummaries.add(new BlockSummaryData(getBlockFromResultSet(resultSet)));
            }
        } catch (SQLException e) {
            throw new DataException("Unable to fetch block summaries from repository", e);
        }
        return blockSummaries;
    }
}
