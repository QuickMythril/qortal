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
import org.qortal.crosschain.Namecoin;
import org.qortal.crosschain.BitcoinyHTLC;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

public class NamecoinTests extends Common {

	private Namecoin namecoin;

	@Before
	public void beforeTest() throws DataException {
		Common.useDefaultSettings(); // TestNet3
		namecoin = Namecoin.getInstance();
	}

	@After
	public void afterTest() {
		Namecoin.resetForTesting();
		namecoin = null;
	}

	@Test
	public void testGetMedianBlockTime() throws BlockStoreException, ForeignBlockchainException {
		long before = System.currentTimeMillis();
		System.out.println(String.format("Namecoin median blocktime: %d", namecoin.getMedianBlockTime()));
		long afterFirst = System.currentTimeMillis();

		System.out.println(String.format("Namecoin median blocktime: %d", namecoin.getMedianBlockTime()));
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
		byte[] secret = BitcoinyHTLC.findHtlcSecret(namecoin, p2shAddress);

		assertNotNull("secret not found", secret);
		assertTrue("secret incorrect", Arrays.equals(expectedSecret, secret));
	}

	@Test
	@Ignore(value = "No testnet nodes available, so we can't regularly test buildSpend yet")
	public void testBuildSpend() {
		String xprv58 = "xpub661MyMwAqRbcEt3Ge1wNmkagyb1J7yTQu4Kquvy77Ycg2iPoh7Urg8s9Jdwp7YmrqGkDKJpUVjsZXSSsQgmAVUC17ZVQQeoWMzm7vDTt1y7";

		String recipient = "2N8WCg52ULCtDSMjkgVTm5mtPdCsUptkHWE";
		long amount = 1000L;

		Transaction transaction = namecoin.buildSpend(xprv58, recipient, amount);
		assertNotNull("insufficient funds", transaction);

		// Check spent key caching doesn't affect outcome

		transaction = namecoin.buildSpend(xprv58, recipient, amount);
		assertNotNull("insufficient funds", transaction);
	}

	@Test
	public void testGetWalletBalance() {
		String xprv58 = "xpub661MyMwAqRbcEt3Ge1wNmkagyb1J7yTQu4Kquvy77Ycg2iPoh7Urg8s9Jdwp7YmrqGkDKJpUVjsZXSSsQgmAVUC17ZVQQeoWMzm7vDTt1y7";

		Long balance = namecoin.getWalletBalance(xprv58);

		assertNotNull(balance);

		System.out.println(namecoin.format(balance));

		// Check spent key caching doesn't affect outcome

		Long repeatBalance = namecoin.getWalletBalance(xprv58);

		assertNotNull(repeatBalance);

		System.out.println(namecoin.format(repeatBalance));

		assertEquals(balance, repeatBalance);
	}

	@Test
	public void testGetUnusedReceiveAddress() throws ForeignBlockchainException {
		String xprv58 = "xpub661MyMwAqRbcEt3Ge1wNmkagyb1J7yTQu4Kquvy77Ycg2iPoh7Urg8s9Jdwp7YmrqGkDKJpUVjsZXSSsQgmAVUC17ZVQQeoWMzm7vDTt1y7";

		String address = namecoin.getUnusedReceiveAddress(xprv58);

		assertNotNull(address);

		System.out.println(address);
	}

}
