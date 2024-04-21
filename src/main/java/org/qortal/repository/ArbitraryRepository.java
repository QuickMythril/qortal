package org.qortal.repository;

import org.qortal.api.SearchMode;
import org.qortal.arbitrary.misc.Service;
import org.qortal.data.arbitrary.ArbitraryResourceData;
import org.qortal.data.arbitrary.ArbitraryResourceMetadata;
import org.qortal.data.arbitrary.ArbitraryResourceStatus;
import org.qortal.data.transaction.ArbitraryTransactionData;
import org.qortal.data.transaction.ArbitraryTransactionData.Method;

import java.util.List;

public interface ArbitraryRepository {

	// Utils

	boolean isDataLocal(byte[] signature) throws DataException;

	byte[] fetchData(byte[] signature) throws DataException;


	// Transaction related

	void save(ArbitraryTransactionData arbitraryTransactionData) throws DataException;

	void delete(ArbitraryTransactionData arbitraryTransactionData) throws DataException;

	List<ArbitraryTransactionData> getArbitraryTransactions(String name, Service service, String identifier, long since) throws DataException;

	ArbitraryTransactionData getInitialTransaction(String name, Service service, Method method, String identifier) throws DataException;

	ArbitraryTransactionData getLatestTransaction(String name, Service service, Method method, String identifier) throws DataException;

	List<ArbitraryTransactionData> getArbitraryTransactions(boolean requireName, Integer limit, Integer offset, Boolean reverse) throws DataException;


	// Resource related

	ArbitraryResourceData getArbitraryResource(Service service, String name, String identifier) throws DataException;

	List<ArbitraryResourceData> getArbitraryResources(Integer limit, Integer offset, Boolean reverse) throws DataException;

	List<ArbitraryResourceData> getArbitraryResources(Service service, String identifier, List<String> names, boolean defaultResource, Boolean followedOnly, Boolean excludeBlocked, Boolean includeMetadata, Boolean includeStatus, Integer limit, Integer offset, Boolean reverse) throws DataException;

	List<ArbitraryResourceData> searchArbitraryResources(Service service, String query, String identifier, List<String> names, String title, String description, boolean prefixOnly, List<String> namesFilter, boolean defaultResource, SearchMode mode, Integer minLevel, Boolean followedOnly, Boolean excludeBlocked, Boolean includeMetadata, Boolean includeStatus, Long before, Long after, Integer limit, Integer offset, Boolean reverse) throws DataException;


	// Arbitrary resources cache save/load

	void save(ArbitraryResourceData arbitraryResourceData) throws DataException;
	void setStatus(ArbitraryResourceData arbitraryResourceData, ArbitraryResourceStatus.Status status) throws DataException;
	void delete(ArbitraryResourceData arbitraryResourceData) throws DataException;

	void save(ArbitraryResourceMetadata metadata) throws DataException;
	void delete(ArbitraryResourceMetadata metadata) throws DataException;
}
