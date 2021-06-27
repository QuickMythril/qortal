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

public class Bitcoin extends Bitcoiny {

	public static final String CURRENCY_CODE = "BTC";

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

	public enum BitcoinNet {
		MAIN {
			@Override
			public NetworkParameters getParams() {
				return MainNetParams.get();
			}

			@Override
			public Collection<ElectrumX.Server> getServers() {
				return Arrays.asList(
						// Servers chosen on NO BASIS WHATSOEVER from various sources!
						new Server("104.248.139.211", Server.ConnectionType.SSL, 50002),
						new Server("128.0.190.26", Server.ConnectionType.SSL, 50002),
						new Server("185.64.116.15", Server.ConnectionType.SSL, 50002),
						new Server("192.166.219.200", Server.ConnectionType.SSL, 50002),
						new Server("2electrumx.hopto.me", Server.ConnectionType.SSL, 56022),
						new Server("2ex.digitaleveryware.com", Server.ConnectionType.SSL, 50002),
						new Server("52.1.56.181", Server.ConnectionType.SSL, 50002),
						new Server("52.1.56.181", Server.ConnectionType.TCP, 50001),
						new Server("alviss.coinjoined.com", Server.ConnectionType.SSL, 50002),
						new Server("bitcoin.aranguren.org", Server.ConnectionType.SSL, 50002),
						new Server("bitcoin.aranguren.org", Server.ConnectionType.TCP, 50001),
						new Server("bitcoin.ec-c.net", Server.ConnectionType.SSL, 50002),
						new Server("bitcoin.grey.pw", Server.ConnectionType.SSL, 50002),
						new Server("btc.electrum.bitbitnet.net", Server.ConnectionType.SSL, 50002),
						new Server("btc.electrum.bitbitnet.net", Server.ConnectionType.TCP, 50001),
						new Server("btc.lastingcoin.net", Server.ConnectionType.SSL, 50002),
						new Server("btc.litepay.ch", Server.ConnectionType.SSL, 50002),
						new Server("btc.rossbennetts.com", Server.ConnectionType.SSL, 50082),
						new Server("caleb.vegas", Server.ConnectionType.SSL, 50002),
						new Server("dxm.no-ip.biz", Server.ConnectionType.SSL, 50002),
						new Server("ecdsa.net", Server.ConnectionType.SSL, 110),
						new Server("electrum.acinq.co", Server.ConnectionType.SSL, 50002),
						new Server("electrum.bitaroo.net", Server.ConnectionType.SSL, 50002),
						new Server("electrum.coineuskal.com", Server.ConnectionType.SSL, 50002),
						new Server("electrum.coineuskal.com", Server.ConnectionType.TCP, 50001),
						new Server("electrum.emzy.de", Server.ConnectionType.SSL, 50002),
						new Server("electrum.emzy.de", Server.ConnectionType.TCP, 50001),
						new Server("electrum.kemperman.nl", Server.ConnectionType.SSL, 50002),
						new Server("electrum.stippy.com", Server.ConnectionType.SSL, 50002),
						new Server("electrumx-btc.cryptonermal.net", Server.ConnectionType.SSL, 50002),
						new Server("electrumx-btc.cryptonermal.net", Server.ConnectionType.TCP, 50001),
						new Server("electrumx-core.1209k.com", Server.ConnectionType.SSL, 50002),
						new Server("electrumx-core.1209k.com", Server.ConnectionType.TCP, 50001),
						new Server("electrumx.alexridevski.net", Server.ConnectionType.SSL, 50002),
						new Server("electrumx.erbium.eu", Server.ConnectionType.SSL, 50002),
						new Server("electrumx.erbium.eu", Server.ConnectionType.TCP, 50001),
						new Server("electrumx.hodlwallet.com", Server.ConnectionType.SSL, 50002),
						new Server("ex.btcmp.com", Server.ConnectionType.SSL, 50002),
						new Server("fortress.qtornado.com", Server.ConnectionType.SSL, 50002),
						new Server("gods-of-rock.screaminglemur.net", Server.ConnectionType.SSL, 50002),
						new Server("gods-of-rock.screaminglemur.net", Server.ConnectionType.TCP, 50001),
						new Server("hodlers.beer", Server.ConnectionType.SSL, 50002),
						new Server("horsey.cryptocowboys.net", Server.ConnectionType.SSL, 50002),
						new Server("horsey.cryptocowboys.net", Server.ConnectionType.TCP, 50001),
						new Server("kirsche.emzy.de", Server.ConnectionType.SSL, 50002),
						new Server("ttbit.mine.bz", Server.ConnectionType.SSL, 50002),
						new Server("vmd63185.contaboserver.net", Server.ConnectionType.SSL, 50002),
						new Server("vmd71287.contaboserver.net", Server.ConnectionType.SSL, 50002),
						new Server("xtrum.com", Server.ConnectionType.SSL, 50002));
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
						new Server("tn.not.fyi", Server.ConnectionType.SSL, 55002),
						new Server("electrumx-test.1209k.com", Server.ConnectionType.SSL, 50002),
						new Server("testnet.qtornado.com", Server.ConnectionType.SSL, 51002),
						new Server("testnet.aranguren.org", Server.ConnectionType.TCP, 51001),
						new Server("testnet.aranguren.org", Server.ConnectionType.SSL, 51002),
						new Server("testnet.hsmiths.com", Server.ConnectionType.SSL, 53012));
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

	private static Bitcoin instance;

	private final BitcoinNet bitcoinNet;

	// Constructors and instance

	private Bitcoin(BitcoinNet bitcoinNet, BitcoinyBlockchainProvider blockchain, Context bitcoinjContext, String currencyCode) {
		super(blockchain, bitcoinjContext, currencyCode);
		this.bitcoinNet = bitcoinNet;

		LOGGER.info(() -> String.format("Starting Bitcoin support using %s", this.bitcoinNet.name()));
	}

	public static synchronized Bitcoin getInstance() {
		if (instance == null) {
			BitcoinNet bitcoinNet = Settings.getInstance().getBitcoinNet();

			BitcoinyBlockchainProvider electrumX = new ElectrumX("Bitcoin-" + bitcoinNet.name(), bitcoinNet.getGenesisHash(), bitcoinNet.getServers(), DEFAULT_ELECTRUMX_PORTS);
			Context bitcoinjContext = new Context(bitcoinNet.getParams());

			instance = new Bitcoin(bitcoinNet, electrumX, bitcoinjContext, CURRENCY_CODE);
		}

		return instance;
	}

	// Getters & setters

	public static synchronized void resetForTesting() {
		instance = null;
	}

	// Actual useful methods for use by other classes

	/**
	 * Returns estimated BTC fee, in sats per 1000bytes, optionally for historic timestamp.
	 * 
	 * @param timestamp optional milliseconds since epoch, or null for 'now'
	 * @return sats per 1000bytes, or throws ForeignBlockchainException if something went wrong
	 */
	@Override
	public long getP2shFee(Long timestamp) throws ForeignBlockchainException {
		return this.bitcoinNet.getP2shFee(timestamp);
	}

}
