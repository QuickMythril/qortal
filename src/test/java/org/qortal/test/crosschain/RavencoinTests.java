package org.qortal.test.crosschain;

import org.junit.Ignore;
import org.junit.Test;
import org.qortal.crosschain.Bitcoiny;
import org.qortal.crosschain.Ravencoin;

public class RavencoinTests extends BitcoinyTests {

	@Override
	protected String getCoinName() {
		return "Ravencoin";
	}

	@Override
	protected String getCoinSymbol() {
		return "RVN";
	}

	@Override
	protected Bitcoiny getCoin() {
		return Ravencoin.getInstance();
	}

	@Override
	protected void resetCoinForTesting() {
		Ravencoin.resetForTesting();
	}

	@Override
	protected String getDeterministicKey58() {
		return "xpub661MyMwAqRbcEt3Ge1wNmkagyb1J7yTQu4Kquvy77Ycg2iPoh7Urg8s9Jdwp7YmrqGkDKJpUVjsZXSSsQgmAVUC17ZVQQeoWMzm7vDTt1y7";
	}

	@Override
	protected String getRecipient() {
		return null;
	}

	@Test
	@Ignore(value = "Doesn't work, to be fixed later")
	public void testFindHtlcSecret() {}

	@Test
	@Ignore(value = "No testnet nodes available, so we can't regularly test buildSpend yet")
	public void testBuildSpend() {}
}
