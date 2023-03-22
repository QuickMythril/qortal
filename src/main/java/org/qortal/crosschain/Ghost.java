package org.qortal.crosschain;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.libdohj.params.GhostMainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.qortal.crosschain.ElectrumX.Server;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.settings.Settings;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

public class Ghost extends Bitcoiny {

	public static final String CURRENCY_CODE = "GHOST";

	private static final Coin DEFAULT_FEE_PER_KB = Coin.valueOf(250000); // 0.0025 GHOST per 1000 bytes

	private static final long MINIMUM_ORDER_AMOUNT = 1000000; // 0.01 GHOST minimum order.

	// Temporary values until a dynamic fee system is written.
	private static final long MAINNET_FEE = 25000L;
	private static final long NON_MAINNET_FEE = 2500L; // TODO: calibrate this

	private static final Map<ConnectionType, Integer> DEFAULT_ELECTRUMX_PORTS = new EnumMap<>(ConnectionType.class);
	static {
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.TCP, 50001);
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.SSL, 50002);
	}

	public enum GhostNet {
		MAIN {
			@Override
			public NetworkParameters getParams() {
				return GhostMainNetParams.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(
						// Servers chosen on NO BASIS WHATSOEVER from various sources!
						// Status verified at https://1209k.com/bitcoin-eye/ele.php?chain=ghost
						// new Server("electrum.qortal.link", Server.ConnectionType.SSL, 54002), // placeholder
						// new Server("electrum-ghost.qortal.online", ConnectionType.SSL, 50002), // placeholder
						// new Server("electrum1-ghost.qortal.online", ConnectionType.SSL, 50002), // placeholder
						new Server("5.230.69.49", ConnectionType.SSL, 50002),
						new Server("209.160.96.120", ConnectionType.SSL, 50002),
						new Server("explorer.myghost.org", ConnectionType.SSL, 50002));
			}

			@Override
			public String getGenesisHash() {
				return "00001e92daa9a7c945afdf3ce2736862b128f95c8966d3cda112caea98dd95f0";
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
				return "0000f7a29616311da755c7ebbcaf69eac2cac94d39f7361d773dafd610174f8f";
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

	private static Ghost instance;

	private final GhostNet ghostNet;

	// Constructors and instance

	private Ghost(GhostNet ghostNet, BitcoinyBlockchainProvider blockchain, Context bitcoinjContext, String currencyCode) {
		super(blockchain, bitcoinjContext, currencyCode);
		this.ghostNet = ghostNet;

		LOGGER.info(() -> String.format("Starting Ghost support using %s", this.ghostNet.name()));
	}

	public static synchronized Ghost getInstance() {
		if (instance == null) {
			GhostNet ghostNet = Settings.getInstance().getGhostNet();

			BitcoinyBlockchainProvider electrumX = new ElectrumX("Ghost-" + ghostNet.name(), ghostNet.getGenesisHash(), ghostNet.getServers(), DEFAULT_ELECTRUMX_PORTS);
			Context bitcoinjContext = new Context(ghostNet.getParams());

			instance = new Ghost(ghostNet, electrumX, bitcoinjContext, CURRENCY_CODE);

			electrumX.setBlockchain(instance);
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
	 * Returns estimated GHOST fee, in sats per 1000bytes, optionally for historic timestamp.
	 * 
	 * @param timestamp optional milliseconds since epoch, or null for 'now'
	 * @return sats per 1000bytes, or throws ForeignBlockchainException if something went wrong
	 */
	@Override
	public long getP2shFee(Long timestamp) throws ForeignBlockchainException {
		return this.ghostNet.getP2shFee(timestamp);
	}

}
