package org.qortal.test.crosschain;

import org.junit.Ignore;
import org.junit.Test;
import org.qortal.crosschain.Bitcoin;
import org.qortal.crosschain.Bitcoiny;

public class BitcoinTests extends BitcoinyTests {

	@Override
	protected String getCoinName() {
		return "Bitcoin";
	}

	@Override
	protected String getCoinSymbol() {
		return "BTC";
	}

	@Override
	protected Bitcoiny getCoin() {
		return Bitcoin.getInstance();
	}

	@Override
	protected void resetCoinForTesting() {
		Bitcoin.resetForTesting();
	}

	@Override
	protected String getDeterministicKey58() {
		return "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";
	}

	@Override
	protected String getRecipient() {
		return "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";
	}

	@Test
	@Ignore("Often fails due to unreliable BTC testnet ElectrumX servers")
	public void testGetMedianBlockTime() {}

	@Test
	@Ignore("Often fails due to unreliable BTC testnet ElectrumX servers")
	public void testFindHtlcSecret() {}

	@Test
	@Ignore("Often fails due to unreliable BTC testnet ElectrumX servers")
	public void testBuildSpend() {}

	@Test
	@Ignore("Often fails due to unreliable BTC testnet ElectrumX servers")
	public void testGetWalletBalance() {}

	@Test
	@Ignore("Often fails due to unreliable BTC testnet ElectrumX servers")
	public void testGetUnusedReceiveAddress() {}
}
