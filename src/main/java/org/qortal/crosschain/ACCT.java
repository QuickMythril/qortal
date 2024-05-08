package org.qortal.crosschain;

import org.qortal.data.at.ATData;
import org.qortal.data.at.ATStateData;
import org.qortal.data.crosschain.CrossChainTradeData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;

public interface ACCT {

	byte[] getCodeBytesHash();

	int getModeByteOffset();

	ForeignBlockchain getBlockchain();

	CrossChainTradeData populateTradeData(Repository repository, ATData atData) throws DataException;

	CrossChainTradeData populateTradeData(Repository repository, ATStateData atStateData) throws DataException;

	byte[] buildCancelMessage(String creatorQortalAddress);

	byte[] findSecretA(Repository repository, CrossChainTradeData crossChainTradeData) throws DataException;

}
