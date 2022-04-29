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
import org.libdohj.params.LbryMainNetParams;
import org.qortal.crosschain.ElectrumX.Server;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.settings.Settings;

public class Lbry extends Bitcoiny {

	public static final String CURRENCY_CODE = "LBC";

	private static final Coin DEFAULT_FEE_PER_KB = Coin.valueOf(1125000); // 0.01125 LBC per 1000 bytes

	private static final long MINIMUM_ORDER_AMOUNT = 1000000; // 0.01 LBC minimum order, to avoid dust errors

	// Temporary values until a dynamic fee system is written.
	private static final long MAINNET_FEE = 1000000L;
	private static final long NON_MAINNET_FEE = 1000000L; // enough for TESTNET3 and should be OK for REGTEST

	private static final Map<ConnectionType, Integer> DEFAULT_ELECTRUMX_PORTS = new EnumMap<>(ConnectionType.class);
	static {
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.TCP, 50001);
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.SSL, 50002);
	}

	public enum LbryNet {
		MAIN {
			@Override
			public NetworkParameters getParams() {
				return LbryMainNetParams.get();
			}

			@Override
			public Collection<Server> getServers() {
				return Arrays.asList(
						// Servers chosen on NO BASIS WHATSOEVER from various sources!
						// Status verified at https://1209k.com/bitcoin-eye/ele.php?chain=lbc
						new Server("spv17.lbry.com", ConnectionType.TCP, 50001),
						new Server("spv19.lbry.com", ConnectionType.TCP, 50001));
			}

			@Override
			public String getGenesisHash() {
				return "9c89283ba0f3227f6c03b70216b9f665f0118d5e0fa729cedf4fb34d6a34f463";
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
				return "9c89283ba0f3227f6c03b70216b9f665f0118d5e0fa729cedf4fb34d6a34f463";
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

	private static Lbry instance;

	private final LbryNet lbryNet;

	// Constructors and instance

	private Lbry(LbryNet lbryNet, BitcoinyBlockchainProvider blockchain, Context bitcoinjContext, String currencyCode) {
		super(blockchain, bitcoinjContext, currencyCode);
		this.lbryNet = lbryNet;

		LOGGER.info(() -> String.format("Starting LBRY support using %s", this.lbryNet.name()));
	}

	public static synchronized Lbry getInstance() {
		if (instance == null) {
			LbryNet lbryNet = Settings.getInstance().getLbryNet();

			BitcoinyBlockchainProvider electrumX = new ElectrumX("Lbry-" + lbryNet.name(), lbryNet.getGenesisHash(), lbryNet.getServers(), DEFAULT_ELECTRUMX_PORTS);
			Context bitcoinjContext = new Context(lbryNet.getParams());

			instance = new Lbry(lbryNet, electrumX, bitcoinjContext, CURRENCY_CODE);
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
	 * Returns estimated LBC fee, in sats per 1000bytes, optionally for historic timestamp.
	 * 
	 * @param timestamp optional milliseconds since epoch, or null for 'now'
	 * @return sats per 1000bytes, or throws ForeignBlockchainException if something went wrong
	 */
	@Override
	public long getP2shFee(Long timestamp) throws ForeignBlockchainException {
		return this.lbryNet.getP2shFee(timestamp);
	}

}
