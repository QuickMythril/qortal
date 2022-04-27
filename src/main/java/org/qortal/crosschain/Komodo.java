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
import org.libdohj.params.KomodoMainNetParams;
import org.qortal.crosschain.ElectrumX.Server;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.settings.Settings;

public class Komodo extends Bitcoiny {

	public static final String CURRENCY_CODE = "KMD";

	private static final Coin DEFAULT_FEE_PER_KB = Coin.valueOf(10000); // 0.00010000 KMD per 1000 bytes

	private static final long MINIMUM_ORDER_AMOUNT = 1000000; // 0.01 KMD minimum order, to avoid dust errors

	// Temporary values until a dynamic fee system is written.
	private static final long MAINNET_FEE = 1000L;
	private static final long NON_MAINNET_FEE = 1000L; // enough for TESTNET3 and should be OK for REGTEST

	private static final Map<ConnectionType, Integer> DEFAULT_ELECTRUMX_PORTS = new EnumMap<>(ConnectionType.class);
	static {
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.TCP, 50001);
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.SSL, 50002);
	}

	public enum KomodoNet {
		MAIN {
			@Override
			public NetworkParameters getParams() {
				return KomodoMainNetParams.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(
						// Servers chosen on NO BASIS WHATSOEVER from various sources!
						// Status verified at https://1209k.com/bitcoin-eye/ele.php?chain=kmd
						new Server("electrum1.cipig.net", ConnectionType.SSL, 20001),
						new Server("electrum2.cipig.net", ConnectionType.SSL, 20001),
						new Server("electrum3.cipig.net", ConnectionType.SSL, 20001));
			}

			@Override
			public String getGenesisHash() {
				return "027e3758c3a65b12aa1046462b486d0a63bfa1beae327897f56c5cfb7daaae71";
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
				return "05a60a92d99d85997cce3b87616c089f6124d7342af37106edc76126334a2c38";
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

	private static Komodo instance;

	private final KomodoNet komodoNet;

	// Constructors and instance

	private Komodo(KomodoNet komodoNet, BitcoinyBlockchainProvider blockchain, Context bitcoinjContext, String currencyCode) {
		super(blockchain, bitcoinjContext, currencyCode);
		this.komodoNet = komodoNet;

		LOGGER.info(() -> String.format("Starting Komodo support using %s", this.komodoNet.name()));
	}

	public static synchronized Komodo getInstance() {
		if (instance == null) {
			KomodoNet komodoNet = Settings.getInstance().getKomodoNet();

			BitcoinyBlockchainProvider electrumX = new ElectrumX("Komodo-" + komodoNet.name(), komodoNet.getGenesisHash(), komodoNet.getServers(), DEFAULT_ELECTRUMX_PORTS);
			Context bitcoinjContext = new Context(komodoNet.getParams());

			instance = new Komodo(komodoNet, electrumX, bitcoinjContext, CURRENCY_CODE);
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
	 * Returns estimated KMD fee, in sats per 1000bytes, optionally for historic timestamp.
	 * 
	 * @param timestamp optional milliseconds since epoch, or null for 'now'
	 * @return sats per 1000bytes, or throws ForeignBlockchainException if something went wrong
	 */
	@Override
	public long getP2shFee(Long timestamp) throws ForeignBlockchainException {
		return this.komodoNet.getP2shFee(timestamp);
	}

}
