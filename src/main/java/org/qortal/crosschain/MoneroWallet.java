package org.qortal.crosschain;

import com.monero.wallet2jni.MoneroWalletJni;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.encoders.Hex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.qortal.api.model.crosschain.MoneroWalletBalance;
import org.qortal.api.model.crosschain.MoneroWalletStatus;
import org.qortal.api.model.crosschain.MoneroWalletTransaction;
import org.qortal.crypto.Crypto;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class MoneroWallet {

    private static final Logger LOGGER = LogManager.getLogger(MoneroWallet.class);

    private final String entropyHex;
    private final String walletId;
    private final Path walletBasePath;
    private final String walletPassword;

    private String daemonUrl;
    private String address;
    private String lastError;
    private boolean initialized;

    public MoneroWallet(String entropyHex, String walletId, Path walletBasePath, String walletPassword) {
        this.entropyHex = entropyHex;
        this.walletId = walletId;
        this.walletBasePath = walletBasePath;
        this.walletPassword = walletPassword;
    }

    public String getWalletId() {
        return walletId;
    }

    public String getDaemonUrl() {
        return daemonUrl;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public String getLastError() {
        return lastError;
    }

    public synchronized boolean initFromEntropy(List<String> daemonUrls, int restoreHeight) throws ForeignBlockchainException {
        if (this.initialized) {
            return true;
        }

        if (!MoneroWalletJni.isLoaded()) {
            throw new ForeignBlockchainException("Monero wallet library not loaded");
        }

        if (daemonUrls == null || daemonUrls.isEmpty()) {
            throw new ForeignBlockchainException("No Monero daemons configured");
        }

        try {
            Files.createDirectories(walletBasePath.getParent());
        } catch (Exception e) {
            throw new ForeignBlockchainException("Unable to create Monero wallet directory");
        }

        String initError = null;
        for (String daemon : daemonUrls) {
            if (daemon == null || daemon.isBlank()) {
                continue;
            }
            String response = MoneroWalletJni.initFromEntropy(walletId, walletBasePath.toString(), entropyHex, restoreHeight, daemon, walletPassword);
            try {
                JSONObject json = parseResponse(response);
                this.daemonUrl = daemon;
                if (json.has("address")) {
                    this.address = json.optString("address", null);
                }
                this.initialized = true;
                this.lastError = null;
                tryStartRefresh();
                return true;
            } catch (ForeignBlockchainException e) {
                initError = e.getMessage();
                this.lastError = initError;
            }
        }

        throw new ForeignBlockchainException(initError != null ? initError : "Unable to initialize Monero wallet");
    }

    public synchronized String getAddress() throws ForeignBlockchainException {
        if (this.address != null) {
            return this.address;
        }

        JSONObject json = parseResponse(MoneroWalletJni.address(walletId));
        this.address = json.optString("address", null);
        if (this.address == null || this.address.isBlank()) {
            throw new ForeignBlockchainException("Missing Monero address");
        }
        return this.address;
    }

    public synchronized MoneroWalletStatus getStatus() throws ForeignBlockchainException {
        JSONObject json = parseResponse(MoneroWalletJni.status(walletId));
        Long walletHeight = json.has("walletHeight") ? json.getLong("walletHeight") : null;
        Long daemonHeight = json.has("daemonHeight") ? json.getLong("daemonHeight") : null;

        MoneroWalletStatus status = new MoneroWalletStatus();
        status.walletHeight = walletHeight != null ? walletHeight : 0L;
        status.daemonHeight = daemonHeight;
        status.isSynchronized = isSynchronized(walletHeight, daemonHeight);
        status.lastError = null;
        return status;
    }

    public synchronized MoneroWalletBalance getBalance() throws ForeignBlockchainException {
        JSONObject json = parseResponse(MoneroWalletJni.balance(walletId));
        Long balance = json.has("balance") ? json.getLong("balance") : null;
        Long unlockedBalance = json.has("unlockedBalance") ? json.getLong("unlockedBalance") : null;
        Long walletHeight = json.has("walletHeight") ? json.getLong("walletHeight") : null;
        Long daemonHeight = json.has("daemonHeight") ? json.getLong("daemonHeight") : null;

        if (balance == null) {
            throw new ForeignBlockchainException("Missing Monero balance");
        }

        MoneroWalletBalance result = new MoneroWalletBalance();
        result.balance = balance;
        result.unlockedBalance = unlockedBalance;
        result.walletHeight = walletHeight;
        result.daemonHeight = daemonHeight;
        return result;
    }

    public synchronized List<MoneroWalletTransaction> getTransactions(int limit, int offset, boolean reverse) throws ForeignBlockchainException {
        JSONObject json = parseResponse(MoneroWalletJni.transactions(walletId, limit, offset, reverse));
        JSONArray array = json.optJSONArray("transactions");
        if (array == null) {
            return Collections.emptyList();
        }

        List<MoneroWalletTransaction> results = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            Object item = array.get(i);
            if (!(item instanceof JSONObject)) {
                continue;
            }
            JSONObject tx = (JSONObject) item;
            MoneroWalletTransaction model = new MoneroWalletTransaction();
            model.txid = tx.optString("txid", tx.optString("hash", null));
            model.timestamp = tx.has("timestamp") ? tx.getLong("timestamp") : null;
            model.blockHeight = tx.has("blockHeight") ? tx.getLong("blockHeight") : null;
            model.confirmations = tx.has("confirmations") ? tx.getLong("confirmations") : null;
            model.isPending = tx.has("isPending") ? tx.getBoolean("isPending") : null;
            model.direction = tx.optString("direction", null);
            model.totalAmount = tx.has("totalAmount") ? tx.getLong("totalAmount") : 0L;
            model.feeAmount = tx.has("feeAmount") ? tx.getLong("feeAmount") : null;
            results.add(model);
        }
        return results;
    }

    public synchronized String send(String receivingAddress, long amountAtomic, String memo) throws ForeignBlockchainException {
        if (receivingAddress == null || receivingAddress.isBlank()) {
            throw new ForeignBlockchainException("Missing Monero receiving address");
        }
        JSONObject json = parseResponse(MoneroWalletJni.send(walletId, receivingAddress, amountAtomic, memo));
        String txid = json.optString("txid", null);
        if (txid == null || txid.isBlank()) {
            JSONArray txids = json.optJSONArray("txids");
            if (txids != null && txids.length() > 0) {
                txid = txids.getString(0);
            }
        }
        if (txid == null || txid.isBlank()) {
            throw new ForeignBlockchainException("Missing transaction id");
        }
        return txid;
    }

    public synchronized void stop() {
        if (!MoneroWalletJni.isLoaded()) {
            return;
        }
        try {
            MoneroWalletJni.stop(walletId);
        } catch (Exception e) {
            LOGGER.debug("Unable to stop Monero wallet: {}", e.getMessage());
        }
        this.initialized = false;
    }

    private void tryStartRefresh() {
        if (!MoneroWalletJni.isLoaded()) {
            return;
        }
        try {
            MoneroWalletJni.startRefresh(walletId);
        } catch (Exception e) {
            LOGGER.debug("Unable to start Monero refresh: {}", e.getMessage());
        }
    }

    private JSONObject parseResponse(String response) throws ForeignBlockchainException {
        if (response == null || response.isBlank()) {
            throw new ForeignBlockchainException("No response from Monero wallet");
        }
        JSONObject json = new JSONObject(response);
        String result = json.optString("result", "error");
        if (!Objects.equals(result, "success")) {
            String error = json.optString("error", "Unknown Monero wallet error");
            throw new ForeignBlockchainException(error);
        }
        return json;
    }

    private boolean isSynchronized(Long walletHeight, Long daemonHeight) {
        if (walletHeight == null || daemonHeight == null || daemonHeight <= 0) {
            return false;
        }
        return walletHeight >= (daemonHeight - 2);
    }

    public static String walletIdFromEntropy(String entropyHex) {
        byte[] entropyBytes = Hex.decode(entropyHex);
        return Hex.toHexString(Crypto.digest(entropyBytes));
    }

    public static String passwordFromEntropy(String entropyHex) {
        String input = "QORTAL_XMR_WALLET_PASSWORD_v1" + entropyHex;
        byte[] digest = Crypto.digest(input.getBytes(StandardCharsets.UTF_8));
        return Hex.toHexString(digest);
    }
}
