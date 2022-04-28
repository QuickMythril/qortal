package org.qortal.crosschain;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.libdohj.params.BitcoinCashMainNetParams;
import org.qortal.crosschain.ElectrumX.Server;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.settings.Settings;

public class BitcoinCash extends Bitcoiny {

	public static final String CURRENCY_CODE = "BCH";

	private static final Coin DEFAULT_FEE_PER_KB = Coin.valueOf(1250); // 0.00001250 BCH per 1000 bytes

	private static final long MINIMUM_ORDER_AMOUNT = 100000; // 0.001 BCH minimum order, to avoid dust errors

	// Temporary values until a dynamic fee system is written.
	private static final long MAINNET_FEE = 1000L;
	private static final long NON_MAINNET_FEE = 1000L; // enough for TESTNET3 and should be OK for REGTEST

	private static final Map<ConnectionType, Integer> DEFAULT_ELECTRUMX_PORTS = new EnumMap<>(ConnectionType.class);
	static {
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.TCP, 50001);
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.SSL, 50002);
	}

	public enum BitcoinCashNet {
		MAIN {
			@Override
			public NetworkParameters getParams() {
				return BitcoinCashMainNetParams.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(
						// Servers chosen on NO BASIS WHATSOEVER from various sources!
						// Status verified at https://1209k.com/bitcoin-eye/ele.php?chain=bch
						new Server("167.99.91.237", ConnectionType.SSL, 50002),
						new Server("bch.rossbennetts.com", ConnectionType.SSL, 50012),
						new Server("crypto.mldlabs.com", ConnectionType.SSL, 50002),
						new Server("electrumx-bch.cryptonermal.net", ConnectionType.SSL, 50002),
						new Server("electrumx-cash.1209k.com", ConnectionType.SSL, 50002),
						new Server("electrum1.cipig.net", ConnectionType.SSL, 20055),
						new Server("electrum2.cipig.net", ConnectionType.SSL, 20055),
						new Server("electrum3.cipig.net", ConnectionType.SSL, 20055));
			}

			@Override
			public String getGenesisHash() {
				return "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f";
			}

			@Override
			public long getP2shFee(Long timestamp) {
				// TODO: This will need to be replaced with something better in the near future!
				return MAINNET_FEE;
			}
		},
		TEST3 {
			@Override
			public NetworkParameters getParams() {
				return TestNet3Params.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(); // TODO: find testnet servers
			}

			@Override
			public String getGenesisHash() {
				return "000000000933ea01ad0ee984209779baaec3ced90fa3f408719526f8d77f4943";
			}

			@Override
			public long getP2shFee(Long timestamp) {
				return NON_MAINNET_FEE;
			}
		},
		REGTEST {
			@Override
			public NetworkParameters getParams() {
				return RegTestParams.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(
						new Server("localhost", ConnectionType.TCP, 50001),
						new Server("localhost", ConnectionType.SSL, 50002));
			}

			@Override
			public String getGenesisHash() {
				// This is unique to each regtest instance
				return null;
			}

			@Override
			public long getP2shFee(Long timestamp) {
				return NON_MAINNET_FEE;
			}
		};

		public abstract NetworkParameters getParams();
		public abstract Collection<Server> getServers();
		public abstract String getGenesisHash();
		public abstract long getP2shFee(Long timestamp) throws ForeignBlockchainException;
	}

	private static BitcoinCash instance;

	private final BitcoinCashNet bitcoinCashNet;

	// Constructors and instance

	private BitcoinCash(BitcoinCashNet bitcoinCashNet, BitcoinyBlockchainProvider blockchain, Context bitcoinjContext, String currencyCode) {
		super(blockchain, bitcoinjContext, currencyCode);
		this.bitcoinCashNet = bitcoinCashNet;

		LOGGER.info(() -> String.format("Starting BitcoinCash support using %s", this.bitcoinCashNet.name()));
	}

	public static synchronized BitcoinCash getInstance() {
		if (instance == null) {
			BitcoinCashNet bitcoinCashNet = Settings.getInstance().getBitcoinCashNet();

			BitcoinyBlockchainProvider electrumX = new ElectrumX("BitcoinCash-" + bitcoinCashNet.name(), bitcoinCashNet.getGenesisHash(), bitcoinCashNet.getServers(), DEFAULT_ELECTRUMX_PORTS);
			Context bitcoinjContext = new Context(bitcoinCashNet.getParams());

			instance = new BitcoinCash(bitcoinCashNet, electrumX, bitcoinjContext, CURRENCY_CODE);
		}

		return instance;
	}

	// Getters & setters

	public static synchronized void resetForTesting() {
		instance = null;
	}

	// Actual useful methods for use by other classes

	@Override
	public Coin getFeePerKb() {
		return DEFAULT_FEE_PER_KB;
	}

	@Override
	public long getMinimumOrderAmount() {
		return MINIMUM_ORDER_AMOUNT;
	}

	/**
	 * Returns estimated BCH fee, in sats per 1000bytes, optionally for historic timestamp.
	 * 
	 * @param timestamp optional milliseconds since epoch, or null for 'now'
	 * @return sats per 1000bytes, or throws ForeignBlockchainException if something went wrong
	 */
	@Override
	public long getP2shFee(Long timestamp) throws ForeignBlockchainException {
		return this.bitcoinCashNet.getP2shFee(timestamp);
	}

}
