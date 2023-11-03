package org.qortal.test.common.transaction;

import java.util.Random;

import org.qortal.account.PrivateKeyAccount;
import org.qortal.block.BlockChain;
import org.qortal.data.transaction.BaseTransactionData;
import org.qortal.group.Group;
import org.qortal.repository.DataException;
import org.qortal.utils.NTP;

public abstract class TestTransaction {

	protected static final Random random = new Random();

	public static BaseTransactionData generateBase(PrivateKeyAccount account, int txGroupId) throws DataException {
		long timestamp = System.currentTimeMillis();
		return new BaseTransactionData(timestamp, txGroupId, account.getLastReference(), account.getPublicKey(), BlockChain.getInstance().getUnitFeeAtTimestamp(timestamp), null);
	}

	public static BaseTransactionData generateBase(PrivateKeyAccount account) throws DataException {
		return generateBase(account, Group.NO_GROUP);
	}

}
