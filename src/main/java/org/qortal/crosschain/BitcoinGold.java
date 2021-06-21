package org.qortal.crosschain;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.qortal.crosschain.ElectrumX.Server;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.settings.Settings;

public class BitcoinGold extends Bitcoiny {

	public static final String CURRENCY_CODE = "BTG";

	// Temporary values until a dynamic fee system is written.
	private static final long OLD_FEE_AMOUNT = 4_000L; // Not 5000 so that existing P2SH-B can output 1000, avoiding dust issue, leaving 4000 for fees.
	private static final long NEW_FEE_TIMESTAMP = 1598280000000L; // milliseconds since epoch
	private static final long NEW_FEE_AMOUNT = 10_000L;

	private static final long NON_MAINNET_FEE = 1000L; // enough for TESTNET3 and should be OK for REGTEST

	private static final Map<ElectrumX.Server.ConnectionType, Integer> DEFAULT_ELECTRUMX_PORTS = new EnumMap<>(ElectrumX.Server.ConnectionType.class);
	static {
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.TCP, 50001);
		DEFAULT_ELECTRUMX_PORTS.put(ConnectionType.SSL, 50002);
	}

	public enum BitcoinGoldNet {
		MAIN {
			@Override
			public NetworkParameters getParams() {
				return MainNetParams.get();
			}

			@Override
			public Collection<ElectrumX.Server> getServers() {
				return Arrays.asList(
						// Servers chosen on NO BASIS WHATSOEVER from various sources!
						new Server("bch.rossbennetts.com", Server.ConnectionType.SSL, 50012));
			}

			@Override
			public String getGenesisHash() {
				return "000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f";
			}

			@Override
			public long getP2shFee(Long timestamp) {
				// TODO: This will need to be replaced with something better in the near future!
				if (timestamp != null && timestamp < NEW_FEE_TIMESTAMP)
					return OLD_FEE_AMOUNT;

				return NEW_FEE_AMOUNT;
			}
		},
		TEST3 {
			@Override
			public NetworkParameters getParams() {
				return TestNet3Params.get();
			}

			@Override
			public Collection<ElectrumX.Server> getServers() {
				return Arrays.asList(
						new Server("localhost", Server.ConnectionType.TCP, 50001),
						new Server("localhost", Server.ConnectionType.SSL, 50002));
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
			public Collection<ElectrumX.Server> getServers() {
				return Arrays.asList(
						new Server("localhost", Server.ConnectionType.TCP, 50001),
						new Server("localhost", Server.ConnectionType.SSL, 50002));
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
		public abstract Collection<ElectrumX.Server> getServers();
		public abstract String getGenesisHash();
		public abstract long getP2shFee(Long timestamp) throws ForeignBlockchainException;
	}

	private static BitcoinGold instance;

	private final BitcoinGoldNet bitcoinGoldNet;

	// Constructors and instance

	private BitcoinGold(BitcoinGoldNet bitcoinGoldNet, BitcoinyBlockchainProvider blockchain, Context bitcoinjContext, String currencyCode) {
		super(blockchain, bitcoinjContext, currencyCode);
		this.bitcoinGoldNet = bitcoinGoldNet;

		LOGGER.info(() -> String.format("Starting BitcoinGold support using %s", this.bitcoinGoldNet.name()));
	}

	public static synchronized BitcoinGold getInstance() {
		if (instance == null) {
			BitcoinGoldNet bitcoinGoldNet = Settings.getInstance().getBitcoinGoldNet();

			BitcoinyBlockchainProvider electrumX = new ElectrumX("BitcoinGold-" + bitcoinGoldNet.name(), bitcoinGoldNet.getGenesisHash(), bitcoinGoldNet.getServers(), DEFAULT_ELECTRUMX_PORTS);
			Context bitcoinjContext = new Context(bitcoinGoldNet.getParams());

			instance = new BitcoinGold(bitcoinGoldNet, electrumX, bitcoinjContext, CURRENCY_CODE);
		}

		return instance;
	}

	// Getters & setters

	public static synchronized void resetForTesting() {
		instance = null;
	}

	// Actual useful methods for use by other classes

	/**
	 * Returns estimated BTG fee, in sats per 1000bytes, optionally for historic timestamp.
	 * 
	 * @param timestamp optional milliseconds since epoch, or null for 'now'
	 * @return sats per 1000bytes, or throws ForeignBlockchainException if something went wrong
	 */
	@Override
	public long getP2shFee(Long timestamp) throws ForeignBlockchainException {
		return this.bitcoinGoldNet.getP2shFee(timestamp);
	}

}
