package org.qortal.crosschain;

import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;

import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.qortal.crosschain.ElectrumX.Server;
import org.qortal.crosschain.ElectrumX.Server.ConnectionType;
import org.qortal.settings.Settings;

public class Bitcoin extends Bitcoiny {

	public static final String CURRENCY_CODE = "BTC";

	private static final long MINIMUM_ORDER_AMOUNT = 100000; // 0.001 BTC minimum order, due to high fees

	// Temporary values until a dynamic fee system is written.
	private static final long OLD_FEE_AMOUNT = 4_000L; // Not 5000 so that existing P2SH-B can output 1000, avoiding dust issue, leaving 4000 for fees.
	private static final long NEW_FEE_TIMESTAMP = 1598280000000L; // milliseconds since epoch
	private static final long NEW_FEE_AMOUNT = 6_000L;

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
					// Status verified at https://1209k.com/bitcoin-eye/ele.php?chain=btc
					new Server("104.248.139.211", Server.ConnectionType.SSL, 50002),
					new Server("128.0.190.26", Server.ConnectionType.SSL, 50002),
					new Server("142.93.6.38", Server.ConnectionType.SSL, 50002),
					new Server("157.245.172.236", Server.ConnectionType.SSL, 50002),
					new Server("167.172.226.175", Server.ConnectionType.SSL, 50002),
					new Server("167.172.42.31", Server.ConnectionType.SSL, 50002),
					new Server("178.62.80.20", Server.ConnectionType.SSL, 50002),
					new Server("185.64.116.15", Server.ConnectionType.SSL, 50002),
					new Server("188.165.206.215", Server.ConnectionType.SSL, 50002),
					new Server("188.165.211.112", Server.ConnectionType.SSL, 50002),
					new Server("2azzarita.hopto.org", Server.ConnectionType.SSL, 50002),
					new Server("2electrumx.hopto.me", Server.ConnectionType.SSL, 56022),
					new Server("2ex.digitaleveryware.com", Server.ConnectionType.SSL, 50002),
					new Server("65.39.140.37", Server.ConnectionType.SSL, 50002),
					new Server("68.183.188.105", Server.ConnectionType.SSL, 50002),
					new Server("71.73.14.254", Server.ConnectionType.SSL, 50002),
					new Server("94.23.247.135", Server.ConnectionType.SSL, 50002),
					new Server("assuredly.not.fyi", Server.ConnectionType.SSL, 50002),
					new Server("ax101.blockeng.ch", Server.ConnectionType.SSL, 50002),
					new Server("ax102.blockeng.ch", Server.ConnectionType.SSL, 50002),
					new Server("b.1209k.com", Server.ConnectionType.SSL, 50002),
					new Server("b6.1209k.com", Server.ConnectionType.SSL, 50002),
					new Server("bitcoin.dermichi.com", Server.ConnectionType.SSL, 50002),
					new Server("bitcoin.lu.ke", Server.ConnectionType.SSL, 50002),
					new Server("bitcoin.lukechilds.co", Server.ConnectionType.SSL, 50002),
					new Server("blkhub.net", Server.ConnectionType.SSL, 50002),
					new Server("btc.electroncash.dk", Server.ConnectionType.SSL, 60002),
					new Server("btc.ocf.sh", Server.ConnectionType.SSL, 50002),
					new Server("btce.iiiiiii.biz", Server.ConnectionType.SSL, 50002),
					new Server("caleb.vegas", Server.ConnectionType.SSL, 50002),
					new Server("eai.coincited.net", Server.ConnectionType.SSL, 50002),
					new Server("electrum.bhoovd.com", Server.ConnectionType.SSL, 50002),
					new Server("electrum.bitaroo.net", Server.ConnectionType.SSL, 50002),
					new Server("electrum.bitcoinlizard.net", Server.ConnectionType.SSL, 50002),
					new Server("electrum.blockstream.info", Server.ConnectionType.SSL, 50002),
					new Server("electrum.emzy.de", Server.ConnectionType.SSL, 50002),
					new Server("electrum.exan.tech", Server.ConnectionType.SSL, 50002),
					new Server("electrum.kendigisland.xyz", Server.ConnectionType.SSL, 50002),
					new Server("electrum.mmitech.info", Server.ConnectionType.SSL, 50002),
					new Server("electrum.petrkr.net", Server.ConnectionType.SSL, 50002),
					new Server("electrum.stippy.com", Server.ConnectionType.SSL, 50002),
					new Server("electrum.thomasfischbach.de", Server.ConnectionType.SSL, 50002),
					new Server("electrum0.snel.it", Server.ConnectionType.SSL, 50002),
					new Server("electrum1.cipig.net", Server.ConnectionType.SSL, 50002),
					new Server("electrum2.cipig.net", Server.ConnectionType.SSL, 50002),
					new Server("electrum3.cipig.net", Server.ConnectionType.SSL, 50002),
					new Server("electrumx.alexridevski.net", Server.ConnectionType.SSL, 50002),
					new Server("electrumx-core.1209k.com", Server.ConnectionType.SSL, 50002),
					new Server("elx.bitske.com", Server.ConnectionType.SSL, 50002),
					new Server("ex03.axalgo.com", Server.ConnectionType.SSL, 50002),
					new Server("ex05.axalgo.com", Server.ConnectionType.SSL, 50002),
					new Server("ex07.axalgo.com", Server.ConnectionType.SSL, 50002),
					new Server("fortress.qtornado.com", Server.ConnectionType.SSL, 50002),
					new Server("fulcrum.grey.pw", Server.ConnectionType.SSL, 50002),
					new Server("fulcrum.sethforprivacy.com", Server.ConnectionType.SSL, 51002),
					new Server("guichet.centure.cc", Server.ConnectionType.SSL, 50002),
					new Server("hodlers.beer", Server.ConnectionType.SSL, 50002),
					new Server("kareoke.qoppa.org", Server.ConnectionType.SSL, 50002),
					new Server("kirsche.emzy.de", Server.ConnectionType.SSL, 50002),
					new Server("node1.btccuracao.com", Server.ConnectionType.SSL, 50002),
					new Server("osr1ex1.compumundohipermegared.one", Server.ConnectionType.SSL, 50002),
					new Server("smmalis37.ddns.net", Server.ConnectionType.SSL, 50002),
					new Server("ulrichard.ch", Server.ConnectionType.SSL, 50002),
					new Server("vmd104012.contaboserver.net", Server.ConnectionType.SSL, 50002),
					new Server("vmd104014.contaboserver.net", Server.ConnectionType.SSL, 50002),
					new Server("vmd63185.contaboserver.net", Server.ConnectionType.SSL, 50002),
					new Server("vmd71287.contaboserver.net", Server.ConnectionType.SSL, 50002),
					new Server("vmd84592.contaboserver.net", Server.ConnectionType.SSL, 50002),
					new Server("xtrum.com", Server.ConnectionType.SSL, 50002)
				);
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
					new Server("testnet.hsmiths.com", Server.ConnectionType.SSL, 53012)
				);
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
					new Server("localhost", Server.ConnectionType.SSL, 50002)
				);
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

			electrumX.setBlockchain(instance);
		}

		return instance;
	}

	// Getters & setters

	public static synchronized void resetForTesting() {
		instance = null;
	}

	@Override
	public long getMinimumOrderAmount() {
		return MINIMUM_ORDER_AMOUNT;
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

	/**
 	* Returns bitcoinj transaction sending <tt>amount</tt> to <tt>recipient</tt> using 20 sat/byte fee.
 	*
 	* @param xprv58 BIP32 private key
 	* @param recipient P2PKH address
 	* @param amount unscaled amount
 	* @return transaction, or null if insufficient funds
 	*/
	@Override
	public Transaction buildSpend(String xprv58, String recipient, long amount) {
		return buildSpend(xprv58, recipient, amount, 20L);
	}

}
