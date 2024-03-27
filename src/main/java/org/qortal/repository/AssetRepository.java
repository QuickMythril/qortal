package org.qortal.repository;

import org.qortal.data.asset.AssetData;
import org.qortal.data.asset.OrderData;
import org.qortal.data.asset.RecentTradeData;
import org.qortal.data.asset.TradeData;

import java.util.List;

public interface AssetRepository {

	// Assets

	AssetData fromAssetId(long assetId) throws DataException;

	AssetData fromAssetName(String assetName) throws DataException;

	boolean assetExists(long assetId) throws DataException;

	boolean assetExists(String assetName) throws DataException;

	boolean reducedAssetNameExists(String reducedAssetName) throws DataException;

	List<AssetData> getAllAssets(Integer limit, Integer offset, Boolean reverse) throws DataException;

	default List<AssetData> getAllAssets() throws DataException {
		return getAllAssets(null, null, null);
	}

	List<Long> getRecentAssetIds(long startTimestamp) throws DataException;

	// For a list of asset holders, see AccountRepository.getAssetBalances

	void save(AssetData assetData) throws DataException;

	void delete(long assetId) throws DataException;

	// Orders

	OrderData fromOrderId(byte[] orderId) throws DataException;

	List<OrderData> getOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/** Returns open orders, ordered by ascending unit price (i.e. best price first), for use by order matching logic. */
	default List<OrderData> getOpenOrders(long haveAssetId, long wantAssetId) throws DataException {
		return getOpenOrders(haveAssetId, wantAssetId, null, null, null);
	}

	List<OrderData> getOpenOrdersForTrading(long haveAssetId, long wantAssetId, Long minimumPrice) throws DataException;

	List<OrderData> getAggregatedOpenOrders(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	List<OrderData> getAccountsOrders(byte[] publicKey, Boolean optIsClosed, Boolean optIsFulfilled, Integer limit, Integer offset, Boolean reverse)
			throws DataException;

	List<OrderData> getAccountsOrders(byte[] publicKey, long haveAssetId, long wantAssetId, Boolean optIsClosed, Boolean optIsFulfilled,
                                      Integer limit, Integer offset, Boolean reverse) throws DataException;

	// Internal, non-API use
	default List<OrderData> getAccountsOrders(byte[] publicKey, Boolean optIsClosed, Boolean optIsFulfilled) throws DataException {
		return getAccountsOrders(publicKey, optIsClosed, optIsFulfilled, null, null, null);
	}

	void save(OrderData orderData) throws DataException;

	void delete(byte[] orderId) throws DataException;

	// Trades

	List<TradeData> getTrades(long haveAssetId, long wantAssetId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	// Internal, non-API use
	default List<TradeData> getTrades(long haveAssetId, long wantAssetId) throws DataException {
		return getTrades(haveAssetId, wantAssetId, null, null, null);
	}

	List<RecentTradeData> getRecentTrades(List<Long> assetIds, List<Long> otherAssetIds, Integer limit, Integer offset, Boolean reverse) throws DataException;

	/** Returns TradeData for trades where orderId was involved, i.e. either initiating OR target order */
    List<TradeData> getOrdersTrades(byte[] orderId, Integer limit, Integer offset, Boolean reverse) throws DataException;

	// Internal, non-API use
	default List<TradeData> getOrdersTrades(byte[] orderId) throws DataException {
		return getOrdersTrades(orderId, null, null, null);
	}

	void save(TradeData tradeData) throws DataException;

	void delete(TradeData tradeData) throws DataException;

}
