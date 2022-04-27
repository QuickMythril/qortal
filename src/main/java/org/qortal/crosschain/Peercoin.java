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
import org.libdohj.params.PeercoinMainNetParams;
import org.qortal.crosschain.ElectrumX.Server;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.settings.Settings;

public class Peercoin extends Bitcoiny {

	public static final String CURRENCY_CODE = "PPC";

	private static final Coin DEFAULT_FEE_PER_KB = Coin.valueOf(1000000); // 0.01 PPC per 1000 bytes

	private static final long MINIMUM_ORDER_AMOUNT = 1000000; // 0.01 PPC minimum order, to avoid dust errors

	// Temporary values until a dynamic fee system is written.
	private static final long MAINNET_FEE = 1000000L;
	private static final long NON_MAINNET_FEE = 1000000L; // enough for TESTNET3 and should be OK for REGTEST

	private static final Map<ConnectionType, Integer> DEFAULT_ELECTRUMX_PORTS = new EnumMap<>(ConnectionType.class);
	static {
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.TCP, 50001);
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.SSL, 50002);
	}

	public enum PeercoinNet {
		MAIN {
			@Override
			public NetworkParameters getParams() {
				return PeercoinMainNetParams.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(
						// Servers chosen on NO BASIS WHATSOEVER from various sources!
						// Status verified at https://1209k.com/bitcoin-eye/ele.php?chain=ppc
						new Server("allingas.peercoinexplorer.net", ConnectionType.SSL, 50002),
						new Server("electrum.peercoinexplorer.net", ConnectionType.SSL, 50002));
			}

			@Override
			public String getGenesisHash() {
				return "0000000032fe677166d54963b62a4677d8957e87c508eaa4fd7eb1c880cd27e3";
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
				return "00000001f757bb737f6596503e17cd17b0658ce630cc727c0cca81aec47c9f06";
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

	private static Peercoin instance;

	private final PeercoinNet peercoinNet;

	// Constructors and instance

	private Peercoin(PeercoinNet peercoinNet, BitcoinyBlockchainProvider blockchain, Context bitcoinjContext, String currencyCode) {
		super(blockchain, bitcoinjContext, currencyCode);
		this.peercoinNet = peercoinNet;

		LOGGER.info(() -> String.format("Starting Peercoin support using %s", this.peercoinNet.name()));
	}

	public static synchronized Peercoin getInstance() {
		if (instance == null) {
			PeercoinNet peercoinNet = Settings.getInstance().getPeercoinNet();

			BitcoinyBlockchainProvider electrumX = new ElectrumX("Peercoin-" + peercoinNet.name(), peercoinNet.getGenesisHash(), peercoinNet.getServers(), DEFAULT_ELECTRUMX_PORTS);
			Context bitcoinjContext = new Context(peercoinNet.getParams());

			instance = new Peercoin(peercoinNet, electrumX, bitcoinjContext, CURRENCY_CODE);
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
	 * Returns estimated PPC fee, in sats per 1000bytes, optionally for historic timestamp.
	 * 
	 * @param timestamp optional milliseconds since epoch, or null for 'now'
	 * @return sats per 1000bytes, or throws ForeignBlockchainException if something went wrong
	 */
	@Override
	public long getP2shFee(Long timestamp) throws ForeignBlockchainException {
		return this.peercoinNet.getP2shFee(timestamp);
	}

}
