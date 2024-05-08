package org.qortal.repository;

import org.qortal.data.crosschain.TradeBotData;

import java.util.List;

public interface CrossChainRepository {

	TradeBotData getTradeBotData(byte[] tradePrivateKey) throws DataException;

	/** Returns true if there is an existing trade-bot entry relating to given AT address, excluding trade-bot entries with given states. */
    boolean existsTradeWithAtExcludingStates(String atAddress, List<String> excludeStates) throws DataException;

	List<TradeBotData> getAllTradeBotData() throws DataException;

	void save(TradeBotData tradeBotData) throws DataException;

	/** Delete trade-bot states using passed private key. */
    int delete(byte[] tradePrivateKey) throws DataException;

}
