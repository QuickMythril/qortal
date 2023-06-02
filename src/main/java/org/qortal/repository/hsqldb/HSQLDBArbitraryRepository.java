package org.qortal.repository.hsqldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Longs;
import org.qortal.arbitrary.misc.Service;
import org.qortal.data.arbitrary.ArbitraryResourceInfo;
import org.qortal.data.arbitrary.ArbitraryResourceNameInfo;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.ArbitraryTransactionData.*;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.data.transaction.TransactionData;
import org.qortal.repository.ArbitraryRepository;
import org.qortal.repository.DataException;
import org.qortal.arbitrary.ArbitraryDataFile;
import org.qortal.transaction.ArbitraryTransaction;
import org.qortal.transaction.Transaction.ApprovalStatus;
import org.qortal.utils.Base58;
import org.qortal.utils.ListUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class HSQLDBArbitraryRepository implements ArbitraryRepository {

	private static final Logger LOGGER = LogManager.getLogger(HSQLDBArbitraryRepository.class);

	protected HSQLDBRepository repository;
	
	public HSQLDBArbitraryRepository(HSQLDBRepository repository) {
		this.repository = repository;
	}

	private ArbitraryTransactionData getTransactionData(byte[] signature) throws DataException {
		TransactionData transactionData = this.repository.getTransactionRepository().fromSignature(signature);
		if (transactionData == null)
			return null;

		return (ArbitraryTransactionData) transactionData;
	}

	@Override
	public boolean isDataLocal(byte[] signature) throws DataException {
		ArbitraryTransactionData transactionData = getTransactionData(signature);
		if (transactionData == null) {
			return false;
		}

		// Raw data is always available
		if (transactionData.getDataType() == DataType.RAW_DATA) {
			return true;
		}

		// Load data file(s)
		ArbitraryDataFile arbitraryDataFile = ArbitraryDataFile.fromTransactionData(transactionData);

		// Check if we already have the complete data file or all chunks
		if (arbitraryDataFile.allFilesExist()) {
			return true;
		}

		return false;
	}

	@Override
	public byte[] fetchData(byte[] signature) {
		try {
			ArbitraryTransactionData transactionData = getTransactionData(signature);
			if (transactionData == null) {
				return null;
			}

			// Raw data is always available
			if (transactionData.getDataType() == DataType.RAW_DATA) {
				return transactionData.getData();
			}

			// Load data file(s)
			ArbitraryDataFile arbitraryDataFile = ArbitraryDataFile.fromTransactionData(transactionData);

			// If we have the complete data file, return it
			if (arbitraryDataFile.exists()) {
				// Ensure the file's size matches the size reported by the transaction (throws a DataException if not)
				arbitraryDataFile.validateFileSize(transactionData.getSize());

				return arbitraryDataFile.getBytes();
			}

			// Alternatively, if we have all the chunks, combine them into a single file
			if (arbitraryDataFile.allChunksExist()) {
				arbitraryDataFile.join();

				// Verify that the combined hash matches the expected hash
				byte[] digest = transactionData.getData();
				if (!digest.equals(arbitraryDataFile.digest())) {
					LOGGER.info(String.format("Hash mismatch for transaction: %s", Base58.encode(signature)));
					return null;
				}

				// Ensure the file's size matches the size reported by the transaction
				arbitraryDataFile.validateFileSize(transactionData.getSize());

				return arbitraryDataFile.getBytes();
			}

		} catch (DataException e) {
			LOGGER.info("Unable to fetch data for transaction {}: {}", Base58.encode(signature), e.getMessage());
			return null;
		}

		return null;
	}

	@Override
	public void save(ArbitraryTransactionData arbitraryTransactionData) throws DataException {
		// Already hashed? Nothing to do
		if (arbitraryTransactionData.getDataType() == DataType.DATA_HASH) {
			return;
		}

		// Trivial-sized payloads can remain in raw form
		if (arbitraryTransactionData.getDataType() == DataType.RAW_DATA && arbitraryTransactionData.getData().length <= ArbitraryTransaction.MAX_DATA_SIZE) {
			return;
		}

		throw new IllegalStateException(String.format("Supplied data is larger than maximum size (%d bytes). Please use ArbitraryDataWriter.", ArbitraryTransaction.MAX_DATA_SIZE));
	}

	@Override
	public void delete(ArbitraryTransactionData arbitraryTransactionData) throws DataException {
		// No need to do anything if we still only have raw data, and hence nothing saved in filesystem
		if (arbitraryTransactionData.getDataType() == DataType.RAW_DATA) {
			return;
		}

		// Load data file(s)
		ArbitraryDataFile arbitraryDataFile = ArbitraryDataFile.fromTransactionData(arbitraryTransactionData);

		// Delete file, chunks, and metadata
		arbitraryDataFile.deleteAll(true);
	}

	@Override
	public List<ArbitraryTransactionData> getArbitraryTransactions(String name, Service service, String identifier, long since) throws DataException {
		String sql = "SELECT type, reference, signature, creator, created_when, fee, " +
				"tx_group_id, block_height, approval_status, approval_height, " +
				"version, nonce, service, size, is_data_raw, data, metadata_hash, " +
				"name, identifier, update_method, secret, compression FROM ArbitraryTransactions " +
				"JOIN Transactions USING (signature) " +
				"WHERE lower(name) = ? AND service = ?" +
				"AND (identifier = ? OR (identifier IS NULL AND ? IS NULL))" +
				"AND created_when >= ? ORDER BY created_when ASC";
		List<ArbitraryTransactionData> arbitraryTransactionData = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql, name.toLowerCase(), service.value, identifier, identifier, since)) {
			if (resultSet == null)
				return null;

			do {
				//TransactionType type = TransactionType.valueOf(resultSet.getInt(1));

				byte[] reference = resultSet.getBytes(2);
				byte[] signature = resultSet.getBytes(3);
				byte[] creatorPublicKey = resultSet.getBytes(4);
				long timestamp = resultSet.getLong(5);

				Long fee = resultSet.getLong(6);
				if (fee == 0 && resultSet.wasNull())
					fee = null;

				int txGroupId = resultSet.getInt(7);

				Integer blockHeight = resultSet.getInt(8);
				if (blockHeight == 0 && resultSet.wasNull())
					blockHeight = null;

				ApprovalStatus approvalStatus = ApprovalStatus.valueOf(resultSet.getInt(9));
				Integer approvalHeight = resultSet.getInt(10);
				if (approvalHeight == 0 && resultSet.wasNull())
					approvalHeight = null;

				BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, approvalStatus, blockHeight, approvalHeight, signature);

				int version = resultSet.getInt(11);
				int nonce = resultSet.getInt(12);
				int serviceInt = resultSet.getInt(13);
				int size = resultSet.getInt(14);
				boolean isDataRaw = resultSet.getBoolean(15); // NOT NULL, so no null to false
				DataType dataType = isDataRaw ? DataType.RAW_DATA : DataType.DATA_HASH;
				byte[] data = resultSet.getBytes(16);
				byte[] metadataHash = resultSet.getBytes(17);
				String nameResult = resultSet.getString(18);
				String identifierResult = resultSet.getString(19);
				Method method = Method.valueOf(resultSet.getInt(20));
				byte[] secret = resultSet.getBytes(21);
				Compression compression = Compression.valueOf(resultSet.getInt(22));
				// FUTURE: get payments from signature if needed. Avoiding for now to reduce database calls.

				ArbitraryTransactionData transactionData = new ArbitraryTransactionData(baseTransactionData,
						version, serviceInt, nonce, size, nameResult, identifierResult, method, secret,
						compression, data, dataType, metadataHash, null);

				arbitraryTransactionData.add(transactionData);
			} while (resultSet.next());

			return arbitraryTransactionData;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch arbitrary transactions from repository", e);
		}
	}

	@Override
	public ArbitraryTransactionData getLatestTransaction(String name, Service service, Method method, String identifier) throws DataException {
		StringBuilder sql = new StringBuilder(1024);

		sql.append("SELECT type, reference, signature, creator, created_when, fee, " +
				"tx_group_id, block_height, approval_status, approval_height, " +
				"version, nonce, service, size, is_data_raw, data, metadata_hash, " +
				"name, identifier, update_method, secret, compression FROM ArbitraryTransactions " +
				"JOIN Transactions USING (signature) " +
				"WHERE lower(name) = ? AND service = ? " +
				"AND (identifier = ? OR (identifier IS NULL AND ? IS NULL))");

		if (method != null) {
			sql.append(" AND update_method = ");
			sql.append(method.value);
		}

		sql.append("ORDER BY created_when DESC LIMIT 1");

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), name.toLowerCase(), service.value, identifier, identifier)) {
			if (resultSet == null)
				return null;

			//TransactionType type = TransactionType.valueOf(resultSet.getInt(1));

			byte[] reference = resultSet.getBytes(2);
			byte[] signature = resultSet.getBytes(3);
			byte[] creatorPublicKey = resultSet.getBytes(4);
			long timestamp = resultSet.getLong(5);

			Long fee = resultSet.getLong(6);
			if (fee == 0 && resultSet.wasNull())
				fee = null;

			int txGroupId = resultSet.getInt(7);

			Integer blockHeight = resultSet.getInt(8);
			if (blockHeight == 0 && resultSet.wasNull())
				blockHeight = null;

			ApprovalStatus approvalStatus = ApprovalStatus.valueOf(resultSet.getInt(9));
			Integer approvalHeight = resultSet.getInt(10);
			if (approvalHeight == 0 && resultSet.wasNull())
				approvalHeight = null;

			BaseTransactionData baseTransactionData = new BaseTransactionData(timestamp, txGroupId, reference, creatorPublicKey, fee, approvalStatus, blockHeight, approvalHeight, signature);

			int version = resultSet.getInt(11);
			int nonce = resultSet.getInt(12);
			int serviceInt = resultSet.getInt(13);
			int size = resultSet.getInt(14);
			boolean isDataRaw = resultSet.getBoolean(15); // NOT NULL, so no null to false
			DataType dataType = isDataRaw ? DataType.RAW_DATA : DataType.DATA_HASH;
			byte[] data = resultSet.getBytes(16);
			byte[] metadataHash = resultSet.getBytes(17);
			String nameResult = resultSet.getString(18);
			String identifierResult = resultSet.getString(19);
			Method methodResult = Method.valueOf(resultSet.getInt(20));
			byte[] secret = resultSet.getBytes(21);
			Compression compression = Compression.valueOf(resultSet.getInt(22));
			// FUTURE: get payments from signature if needed. Avoiding for now to reduce database calls.

			ArbitraryTransactionData transactionData = new ArbitraryTransactionData(baseTransactionData,
					version, serviceInt, nonce, size, nameResult, identifierResult, methodResult, secret,
					compression, data, dataType, metadataHash, null);

			return transactionData;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch arbitrary transactions from repository", e);
		}
	}

	@Override
	public List<ArbitraryResourceInfo> getArbitraryResources(Service service, String identifier, List<String> names,
															 boolean defaultResource,  Boolean followedOnly, Boolean excludeBlocked,
															 Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(512);
		List<Object> bindParams = new ArrayList<>();

		sql.append("SELECT name, service, identifier, MAX(size) AS max_size, MIN(created_when) AS date_created, MAX(created_when) AS date_updated " +
				"FROM ArbitraryTransactions " +
				"JOIN Transactions USING (signature) " +
				"WHERE 1=1");

		if (service != null) {
			sql.append(" AND service = ");
			sql.append(service.value);
		}

		if (defaultResource) {
			// Default resource requested - use NULL identifier
			sql.append(" AND identifier IS NULL");
		}
		else {
			// Non-default resource requested
			// Use an exact match identifier, or list all if supplied identifier is null
			sql.append(" AND (identifier = ? OR (? IS NULL))");
			bindParams.add(identifier);
			bindParams.add(identifier);
		}

		if (names != null && !names.isEmpty()) {
			sql.append(" AND name IN (?");
			bindParams.add(names.get(0));

			for (int i = 1; i < names.size(); ++i) {
				sql.append(", ?");
				bindParams.add(names.get(i));
			}

			sql.append(")");
		}

		// Handle "followed only"
		if (followedOnly != null && followedOnly) {
			List<String> followedNames = ListUtils.followedNames();
			if (followedNames != null && !followedNames.isEmpty()) {
				sql.append(" AND name IN (?");
				bindParams.add(followedNames.get(0));

				for (int i = 1; i < followedNames.size(); ++i) {
					sql.append(", ?");
					bindParams.add(followedNames.get(i));
				}
				sql.append(")");
			}
		}

		// Handle "exclude blocked"
		if (excludeBlocked != null && excludeBlocked) {
			List<String> blockedNames = ListUtils.blockedNames();
			if (blockedNames != null && !blockedNames.isEmpty()) {
				sql.append(" AND name NOT IN (?");
				bindParams.add(blockedNames.get(0));

				for (int i = 1; i < blockedNames.size(); ++i) {
					sql.append(", ?");
					bindParams.add(blockedNames.get(i));
				}
				sql.append(")");
			}
		}

		sql.append(" GROUP BY name, service, identifier ORDER BY name COLLATE SQL_TEXT_UCC_NO_PAD");

		if (reverse != null && reverse) {
			sql.append(" DESC");
		}

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<ArbitraryResourceInfo> arbitraryResources = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
			if (resultSet == null)
				return null;

			do {
				String nameResult = resultSet.getString(1);
				Service serviceResult = Service.valueOf(resultSet.getInt(2));
				String identifierResult = resultSet.getString(3);
				Integer sizeResult = resultSet.getInt(4);
				long dateCreated = resultSet.getLong(5);
				long dateUpdated = resultSet.getLong(6);

				// We should filter out resources without names
				if (nameResult == null) {
					continue;
				}

				ArbitraryResourceInfo arbitraryResourceInfo = new ArbitraryResourceInfo();
				arbitraryResourceInfo.name = nameResult;
				arbitraryResourceInfo.service = serviceResult;
				arbitraryResourceInfo.identifier = identifierResult;
				arbitraryResourceInfo.size = Longs.valueOf(sizeResult);
				arbitraryResourceInfo.created = dateCreated;
				arbitraryResourceInfo.updated = dateUpdated;

				arbitraryResources.add(arbitraryResourceInfo);
			} while (resultSet.next());

			return arbitraryResources;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch arbitrary transactions from repository", e);
		}
	}

	@Override
	public List<ArbitraryResourceInfo> searchArbitraryResources(Service service, String query, String identifier, List<String> names, boolean prefixOnly,
																List<String> exactMatchNames, boolean defaultResource, Boolean followedOnly, Boolean excludeBlocked,
																Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(512);
		List<Object> bindParams = new ArrayList<>();

		sql.append("SELECT name, service, identifier, MAX(size) AS max_size, MIN(created_when) AS date_created, MAX(created_when) AS date_updated " +
				"FROM ArbitraryTransactions " +
				"JOIN Transactions USING (signature) " +
				"WHERE 1=1");

		if (service != null) {
			sql.append(" AND service = ");
			sql.append(service.value);
		}

		// Handle general query matches
		if (query != null) {
			// Search anywhere in the fields, unless "prefixOnly" has been requested
			// Note that without prefixOnly it will bypass any indexes so may not scale well
			// Longer term we probably want to copy resources to their own table anyway
			String queryWildcard = prefixOnly ? String.format("%s%%", query.toLowerCase()) : String.format("%%%s%%", query.toLowerCase());

			if (defaultResource) {
				// Default resource requested - use NULL identifier and search name only
				sql.append(" AND LCASE(name) LIKE ? AND identifier IS NULL");
				bindParams.add(queryWildcard);
			} else {
				// Non-default resource requested
				// In this case we search the identifier as well as the name
				sql.append(" AND (LCASE(name) LIKE ? OR LCASE(identifier) LIKE ?)");
				bindParams.add(queryWildcard);
				bindParams.add(queryWildcard);
			}
		}

		// Handle identifier matches
		if (identifier != null) {
			// Search anywhere in the identifier, unless "prefixOnly" has been requested
			String queryWildcard = prefixOnly ? String.format("%s%%", identifier.toLowerCase()) : String.format("%%%s%%", identifier.toLowerCase());
			sql.append(" AND LCASE(identifier) LIKE ?");
			bindParams.add(queryWildcard);
		}

		// Handle name searches
		if (names != null && !names.isEmpty()) {
			sql.append(" AND (");

			for (int i = 0; i < names.size(); ++i) {
				// Search anywhere in the name, unless "prefixOnly" has been requested
				String queryWildcard = prefixOnly ? String.format("%s%%", names.get(i).toLowerCase()) : String.format("%%%s%%", names.get(i).toLowerCase());
				if (i > 0) sql.append(" OR ");
				sql.append("LCASE(name) LIKE ?");
				bindParams.add(queryWildcard);
			}
			sql.append(")");
		}

		// Handle name exact matches
		if (exactMatchNames != null && !exactMatchNames.isEmpty()) {
			sql.append(" AND LCASE(name) IN (?");
			bindParams.add(exactMatchNames.get(0).toLowerCase());

			for (int i = 1; i < exactMatchNames.size(); ++i) {
				sql.append(", ?");
				bindParams.add(exactMatchNames.get(i).toLowerCase());
			}
			sql.append(")");
		}

		// Handle "followed only"
		if (followedOnly != null && followedOnly) {
			List<String> followedNames = ListUtils.followedNames();
			if (followedNames != null && !followedNames.isEmpty()) {
				sql.append(" AND LCASE(name) IN (?");
				bindParams.add(followedNames.get(0).toLowerCase());

				for (int i = 1; i < followedNames.size(); ++i) {
					sql.append(", ?");
					bindParams.add(followedNames.get(i).toLowerCase());
				}
				sql.append(")");
			}
		}

		// Handle "exclude blocked"
		if (excludeBlocked != null && excludeBlocked) {
			List<String> blockedNames = ListUtils.blockedNames();
			if (blockedNames != null && !blockedNames.isEmpty()) {
				sql.append(" AND LCASE(name) NOT IN (?");
				bindParams.add(blockedNames.get(0).toLowerCase());

				for (int i = 1; i < blockedNames.size(); ++i) {
					sql.append(", ?");
					bindParams.add(blockedNames.get(i).toLowerCase());
				}
				sql.append(")");
			}
		}

		sql.append(" GROUP BY name, service, identifier ORDER BY date_created");

		if (reverse != null && reverse) {
			sql.append(" DESC");
		}

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<ArbitraryResourceInfo> arbitraryResources = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), bindParams.toArray())) {
			if (resultSet == null)
				return null;

			do {
				String nameResult = resultSet.getString(1);
				Service serviceResult = Service.valueOf(resultSet.getInt(2));
				String identifierResult = resultSet.getString(3);
				Integer sizeResult = resultSet.getInt(4);
				long dateCreated = resultSet.getLong(5);
				long dateUpdated = resultSet.getLong(6);

				// We should filter out resources without names
				if (nameResult == null) {
					continue;
				}

				ArbitraryResourceInfo arbitraryResourceInfo = new ArbitraryResourceInfo();
				arbitraryResourceInfo.name = nameResult;
				arbitraryResourceInfo.service = serviceResult;
				arbitraryResourceInfo.identifier = identifierResult;
				arbitraryResourceInfo.size = Longs.valueOf(sizeResult);
				arbitraryResourceInfo.created = dateCreated;
				arbitraryResourceInfo.updated = dateUpdated;

				arbitraryResources.add(arbitraryResourceInfo);
			} while (resultSet.next());

			return arbitraryResources;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch arbitrary transactions from repository", e);
		}
	}

	@Override
	public List<ArbitraryResourceNameInfo> getArbitraryResourceCreatorNames(Service service, String identifier,
																			boolean defaultResource, Integer limit, Integer offset, Boolean reverse) throws DataException {
		StringBuilder sql = new StringBuilder(512);

		sql.append("SELECT name FROM ArbitraryTransactions WHERE 1=1");

		if (service != null) {
			sql.append(" AND service = ");
			sql.append(service.value);
		}

		if (defaultResource) {
			// Default resource requested - use NULL identifier
			// The AND ? IS NULL AND ? IS NULL is a hack to make use of the identifier params in checkedExecute()
			identifier = null;
			sql.append(" AND (identifier IS NULL AND ? IS NULL AND ? IS NULL)");
		}
		else {
			// Non-default resource requested
			// Use an exact match identifier, or list all if supplied identifier is null
			sql.append(" AND (identifier = ? OR (? IS NULL))");
		}

		sql.append(" GROUP BY name ORDER BY name COLLATE SQL_TEXT_UCC_NO_PAD");

		if (reverse != null && reverse) {
			sql.append(" DESC");
		}

		HSQLDBRepository.limitOffsetSql(sql, limit, offset);

		List<ArbitraryResourceNameInfo> arbitraryResources = new ArrayList<>();

		try (ResultSet resultSet = this.repository.checkedExecute(sql.toString(), identifier, identifier)) {
			if (resultSet == null)
				return null;

			do {
				String name = resultSet.getString(1);

				// We should filter out resources without names
				if (name == null) {
					continue;
				}

				ArbitraryResourceNameInfo arbitraryResourceNameInfo = new ArbitraryResourceNameInfo();
				arbitraryResourceNameInfo.name = name;

				arbitraryResources.add(arbitraryResourceNameInfo);
			} while (resultSet.next());

			return arbitraryResources;
		} catch (SQLException e) {
			throw new DataException("Unable to fetch arbitrary transactions from repository", e);
		}
	}

}
