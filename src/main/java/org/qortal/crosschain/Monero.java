package org.qortal.crosschain;

import com.monero.wallet2jni.MoneroWalletJni;
import org.qortal.controller.MoneroWalletController;

public class Monero implements ForeignBlockchain {

    public static final String CURRENCY_CODE = "XMR";

    private static Monero instance;

    private Monero() {
    }

    public static synchronized Monero getInstance() {
        if (instance == null) {
            instance = new Monero();
        }
        return instance;
    }

    @Override
    public String getCurrencyCode() {
        return CURRENCY_CODE;
    }

    @Override
    public boolean isValidAddress(String address) {
        if (address == null || address.isBlank()) {
            return false;
        }
        if (!MoneroWalletJni.isLoaded()) {
            MoneroWalletJni.loadLibrary();
        }
        if (!MoneroWalletJni.isLoaded()) {
            return false;
        }
        return MoneroWalletJni.addressValid(address);
    }

    @Override
    public boolean isValidWalletKey(String walletKey) {
        return MoneroWalletController.normalizeEntropyHex(walletKey) != null;
    }

    @Override
    public long getMinimumOrderAmount() {
        return 0L;
    }
}
