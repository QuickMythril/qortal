package org.qortal.crosschain;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.qortal.utils.ByteArray;
import org.qortal.utils.Triple;

public enum SupportedBlockchain {

	BITCOIN(Arrays.asList(
				Triple.valueOf(BitcoinACCTv1.NAME, BitcoinACCTv1.CODE_BYTES_HASH, BitcoinACCTv1::getInstance)
				// Could add improved BitcoinACCTv2 here in the future
			)) {
		@Override
		public ForeignBlockchain getInstance() {
			return Bitcoin.getInstance();
		}

		@Override
		public ACCT getLatestAcct() {
			return BitcoinACCTv1.getInstance();
		}
	},

	BITCOINCASH(Arrays.asList(
				Triple.valueOf(BitcoinCashACCTv1.NAME, BitcoinCashACCTv1.CODE_BYTES_HASH, BitcoinCashACCTv1::getInstance)
				// Could add improved BitcoinACCTv2 here in the future
			)) {
		@Override
		public ForeignBlockchain getInstance() {
			return BitcoinCash.getInstance();
		}

		@Override
		public ACCT getLatestAcct() {
			return BitcoinCashACCTv1.getInstance();
		}
	},

	BITCOINGOLD(Arrays.asList(
				Triple.valueOf(BitcoinGoldACCTv1.NAME, BitcoinGoldACCTv1.CODE_BYTES_HASH, BitcoinGoldACCTv1::getInstance)
				// Could add improved BitcoinACCTv2 here in the future
			)) {
		@Override
		public ForeignBlockchain getInstance() {
			return BitcoinGold.getInstance();
		}

		@Override
		public ACCT getLatestAcct() {
			return BitcoinGoldACCTv1.getInstance();
		}
	},

	BITCOINSV(Arrays.asList(
				Triple.valueOf(BitcoinSVACCTv1.NAME, BitcoinSVACCTv1.CODE_BYTES_HASH, BitcoinSVACCTv1::getInstance)
				// Could add improved BitcoinACCTv2 here in the future
			)) {
		@Override
		public ForeignBlockchain getInstance() {
			return BitcoinSV.getInstance();
		}

		@Override
		public ACCT getLatestAcct() {
			return BitcoinSVACCTv1.getInstance();
		}
	},

	LITECOIN(Arrays.asList(
			Triple.valueOf(LitecoinACCTv1.NAME, LitecoinACCTv1.CODE_BYTES_HASH, LitecoinACCTv1::getInstance)
		)) {
		@Override
		public ForeignBlockchain getInstance() {
			return Litecoin.getInstance();
		}

		@Override
		public ACCT getLatestAcct() {
			return LitecoinACCTv1.getInstance();
		}
	};

	private static final Map<ByteArray, Supplier<ACCT>> supportedAcctsByCodeHash = Arrays.stream(SupportedBlockchain.values())
			.map(supportedBlockchain -> supportedBlockchain.supportedAccts)
			.flatMap(List::stream)
			.collect(Collectors.toUnmodifiableMap(triple -> new ByteArray(triple.getB()), Triple::getC));

	private static final Map<String, Supplier<ACCT>> supportedAcctsByName = Arrays.stream(SupportedBlockchain.values())
			.map(supportedBlockchain -> supportedBlockchain.supportedAccts)
			.flatMap(List::stream)
			.collect(Collectors.toUnmodifiableMap(Triple::getA, Triple::getC));

	private static final Map<String, SupportedBlockchain> blockchainsByName = Arrays.stream(SupportedBlockchain.values())
			.collect(Collectors.toUnmodifiableMap(Enum::name, blockchain -> blockchain));

	private final List<Triple<String, byte[], Supplier<ACCT>>> supportedAccts;

	SupportedBlockchain(List<Triple<String, byte[], Supplier<ACCT>>> supportedAccts) {
		this.supportedAccts = supportedAccts;
	}

	public abstract ForeignBlockchain getInstance();
	public abstract ACCT getLatestAcct();

	public static Map<ByteArray, Supplier<ACCT>> getAcctMap() {
		return supportedAcctsByCodeHash;
	}

	public static SupportedBlockchain fromString(String name) {
		return blockchainsByName.get(name);
	}

	public static Map<ByteArray, Supplier<ACCT>> getFilteredAcctMap(SupportedBlockchain blockchain) {
		if (blockchain == null)
			return getAcctMap();

		return blockchain.supportedAccts.stream()
				.collect(Collectors.toUnmodifiableMap(triple -> new ByteArray(triple.getB()), Triple::getC));
	}

	public static Map<ByteArray, Supplier<ACCT>> getFilteredAcctMap(String specificBlockchain) {
		if (specificBlockchain == null)
			return getAcctMap();

		SupportedBlockchain blockchain = blockchainsByName.get(specificBlockchain);
		if (blockchain == null)
			return Collections.emptyMap();

		return getFilteredAcctMap(blockchain);
	}

	public static ACCT getAcctByCodeHash(byte[] codeHash) {
		ByteArray wrappedCodeHash = new ByteArray(codeHash);

		Supplier<ACCT> acctInstanceSupplier = supportedAcctsByCodeHash.get(wrappedCodeHash);

		if (acctInstanceSupplier == null)
			return null;

		return acctInstanceSupplier.get();
	}

	public static ACCT getAcctByName(String acctName) {
		Supplier<ACCT> acctInstanceSupplier = supportedAcctsByName.get(acctName);

		if (acctInstanceSupplier == null)
			return null;

		return acctInstanceSupplier.get();
	}

}