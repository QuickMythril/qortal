package org.qortal.controller;

import com.monero.wallet2jni.MoneroWalletJni;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.api.model.crosschain.MoneroWalletBalance;
import org.qortal.api.model.crosschain.MoneroWalletStatus;
import org.qortal.api.model.crosschain.MoneroWalletTransaction;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.MoneroWallet;
import org.qortal.settings.Settings;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class MoneroWalletController extends Thread {

    protected static final Logger LOGGER = LogManager.getLogger(MoneroWalletController.class);

    private static MoneroWalletController instance;

    private final Map<String, MoneroWallet> wallets = new ConcurrentHashMap<>();
    private final Object walletLock = new Object();

    private volatile boolean running = true;

    private static final Pattern ENTROPY_HEX_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    private MoneroWalletController() {
    }

    public static MoneroWalletController getInstance() {
        if (instance == null) {
            instance = new MoneroWalletController();
        }
        return instance;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Monero Wallet Controller");
        Thread.currentThread().setPriority(MIN_PRIORITY);

        try {
            while (running && !Controller.isStopping()) {
                if (!MoneroWalletJni.isLoaded()) {
                    MoneroWalletJni.loadLibrary();
                    if (!MoneroWalletJni.isLoaded()) {
                        Thread.sleep(5000);
                        continue;
                    }
                }

                Thread.sleep(30000);
            }
        } catch (InterruptedException e) {
            // Exit on interrupt
        }
    }

    public void shutdown() {
        this.running = false;
        this.interrupt();
        for (MoneroWallet wallet : wallets.values()) {
            wallet.stop();
        }
        wallets.clear();
    }

    public String getWalletAddress(String entropyHex) throws ForeignBlockchainException {
        MoneroWallet wallet = ensureInitialized(entropyHex);
        return wallet.getAddress();
    }

    public MoneroWalletBalance getWalletBalance(String entropyHex) throws ForeignBlockchainException {
        MoneroWallet wallet = ensureInitialized(entropyHex);
        return wallet.getBalance();
    }

    public List<MoneroWalletTransaction> getWalletTransactions(String entropyHex, int limit, int offset, boolean reverse) throws ForeignBlockchainException {
        MoneroWallet wallet = ensureInitialized(entropyHex);
        return wallet.getTransactions(limit, offset, reverse);
    }

    public String send(String entropyHex, String receivingAddress, long amountAtomic, String memo) throws ForeignBlockchainException {
        MoneroWallet wallet = ensureInitialized(entropyHex);
        return wallet.send(receivingAddress, amountAtomic, memo);
    }

    public String getSyncStatusString(String entropyHex) {
        String normalized = normalizeEntropyHex(entropyHex);
        if (normalized == null) {
            return "Invalid entropy";
        }

        if (!MoneroWalletJni.isLoaded()) {
            MoneroWalletJni.loadLibrary();
        }
        if (!MoneroWalletJni.isLoaded()) {
            return "Wallet library not loaded";
        }

        MoneroWallet wallet;
        try {
            wallet = ensureInitialized(normalized);
        } catch (ForeignBlockchainException e) {
            return "Initializing wallet...";
        }

        if (wallet == null || !wallet.isInitialized()) {
            return "Not initialized yet";
        }

        try {
            MoneroWalletStatus status = wallet.getStatus();
            return status.isSynchronized ? "Synchronized" : "Syncing";
        } catch (ForeignBlockchainException e) {
            return e.getMessage() != null ? e.getMessage() : "Syncing";
        }
    }

    public MoneroWalletStatus getSyncStatus(String entropyHex) throws ForeignBlockchainException {
        MoneroWallet wallet = ensureInitialized(entropyHex);
        return wallet.getStatus();
    }

    private MoneroWallet ensureInitialized(String entropyHex) throws ForeignBlockchainException {
        String normalized = normalizeEntropyHex(entropyHex);
        if (normalized == null) {
            throw new ForeignBlockchainException("Invalid entropy");
        }

        if (!MoneroWalletJni.isLoaded()) {
            MoneroWalletJni.loadLibrary();
        }
        if (!MoneroWalletJni.isLoaded()) {
            throw new ForeignBlockchainException("Monero wallet library not loaded");
        }

        String walletId = MoneroWallet.walletIdFromEntropy(normalized);
        MoneroWallet wallet = wallets.get(walletId);
        if (wallet == null) {
            synchronized (walletLock) {
                wallet = wallets.get(walletId);
                if (wallet == null) {
                    Path walletBasePath = getMoneroWalletsDirectory().resolve(walletId);
                    String password = MoneroWallet.passwordFromEntropy(normalized);
                    wallet = new MoneroWallet(normalized, walletId, walletBasePath, password);
                    wallets.put(walletId, wallet);
                }
            }
        }

        if (!wallet.isInitialized()) {
            List<String> daemons = Settings.getInstance().getMoneroDaemonList();
            wallet.initFromEntropy(daemons, Settings.getInstance().getMoneroDefaultRestoreHeight());
        }

        return wallet;
    }

    public static String normalizeEntropyHex(String entropyHex) {
        if (entropyHex == null) {
            return null;
        }
        String normalized = entropyHex.trim();
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        normalized = normalized.trim().toLowerCase();
        if (!ENTROPY_HEX_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    public static String getMoneroLibFilename() {
        String osName = System.getProperty("os.name");
        String osArchitecture = System.getProperty("os.arch");

        if (osName.contains("Windows") && Objects.equals(osArchitecture, "amd64")) {
            return "monero_wallet2_jni.dll";
        }
        if (osName.equals("Mac OS X")) {
            return "libmonero_wallet2_jni.dylib";
        }
        if (osName.equals("Linux") || osName.equals("FreeBSD")) {
            return "libmonero_wallet2_jni.so";
        }

        return null;
    }

    public static Path getMoneroWalletsDirectory() {
        return Paths.get(Settings.getInstance().getMoneroWalletsPath(), "Monero");
    }

    public static Path getMoneroLibOuterDirectory() {
        return Paths.get(Settings.getInstance().getMoneroWalletsPath(), "Monero", "lib");
    }
}
