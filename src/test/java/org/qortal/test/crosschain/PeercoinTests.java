package org.qortal.test.crosschain;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.store.BlockStoreException;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.Peercoin;
import org.qortal.crosschain.BitcoinyHTLC;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

public class PeercoinTests extends Common {

	private Peercoin peercoin;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings(); // TestNet3
		peercoin = Peercoin.getInstance();
	}

	@After
	public void afterTest() {
		Peercoin.resetForTesting();
		peercoin = null;
	}

	@Test
	public void testGetMedianBlockTime() throws BlockStoreException, ForeignBlockchainException {
		long before = System.currentTimeMillis();
		System.out.println(String.format("Peercoin median blocktime: %d", peercoin.getMedianBlockTime()));
		long afterFirst = System.currentTimeMillis();

		System.out.println(String.format("Peercoin median blocktime: %d", peercoin.getMedianBlockTime()));
		long afterSecond = System.currentTimeMillis();

		long firstPeriod = afterFirst - before;
		long secondPeriod = afterSecond - afterFirst;

		System.out.println(String.format("1st call: %d ms, 2nd call: %d ms", firstPeriod, secondPeriod));

		assertTrue("2nd call should be quicker than 1st", secondPeriod < firstPeriod);
		assertTrue("2nd call should take less than 5 seconds", secondPeriod < 5000L);
	}

	@Test
	@Ignore(value = "Doesn't work, to be fixed later")
	public void testFindHtlcSecret() throws ForeignBlockchainException {
		// This actually exists on TEST3 but can take a while to fetch
		String p2shAddress = "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";

		byte[] expectedSecret = "This string is exactly 32 bytes!".getBytes();
		byte[] secret = BitcoinyHTLC.findHtlcSecret(peercoin, p2shAddress);

		assertNotNull("secret not found", secret);
		assertTrue("secret incorrect", Arrays.equals(expectedSecret, secret));
	}

	@Test
	@Ignore(value = "No testnet nodes available, so we can't regularly test buildSpend yet")
	public void testBuildSpend() {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		String recipient = "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";
		long amount = 1000L;

		Transaction transaction = peercoin.buildSpend(xprv58, recipient, amount);
		assertNotNull("insufficient funds", transaction);

		// Check spent key caching doesn't affect outcome

		transaction = peercoin.buildSpend(xprv58, recipient, amount);
		assertNotNull("insufficient funds", transaction);
	}

	@Test
	public void testGetWalletBalance() {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		Long balance = peercoin.getWalletBalance(xprv58);

		assertNotNull(balance);

		System.out.println(peercoin.format(balance));

		// Check spent key caching doesn't affect outcome

		Long repeatBalance = peercoin.getWalletBalance(xprv58);

		assertNotNull(repeatBalance);

		System.out.println(peercoin.format(repeatBalance));

		assertEquals(balance, repeatBalance);
	}

	@Test
	public void testGetUnusedReceiveAddress() throws ForeignBlockchainException {
		String xprv58 = "tprv8ZgxMBicQKsPdahhFSrCdvC1bsWyzHHZfTneTVqUXN6s1wEtZLwAkZXzFP6TYLg2aQMecZLXLre5bTVGajEB55L1HYJcawpdFG66STVAWPJ";

		String address = peercoin.getUnusedReceiveAddress(xprv58);

		assertNotNull(address);

		System.out.println(address);
	}

}
