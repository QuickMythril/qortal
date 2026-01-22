package com.monero.wallet2jni;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.controller.MoneroWalletController;

import java.nio.file.Path;

public class MoneroWalletJni {

    protected static final Logger LOGGER = LogManager.getLogger(MoneroWalletJni.class);

    private static boolean loaded = false;

    public static void loadLibrary() {
        if (loaded) {
            return;
        }

        String osName = System.getProperty("os.name");
        String osArchitecture = System.getProperty("os.arch");

        LOGGER.info("OS Name: {}", osName);
        LOGGER.info("OS Architecture: {}", osArchitecture);

        try {
            String libFileName = MoneroWalletController.getMoneroLibFilename();
            if (libFileName == null) {
                LOGGER.info("Monero JNI library not found for OS: {}, arch: {}", osName, osArchitecture);
                return;
            }

            Path libPath = MoneroWalletController.getMoneroLibOuterDirectory().resolve(libFileName);
            System.load(libPath.toAbsolutePath().toString());
            loaded = true;
        } catch (UnsatisfiedLinkError e) {
            LOGGER.info("Unable to load Monero JNI library");
        }
    }

    public static boolean isLoaded() {
        return loaded;
    }

    // Native bindings (implementation provided by monero-wallet2 JNI library)
    public static native String initFromEntropy(String walletId, String walletPathBase, String entropyHex, long restoreHeight, String daemonUrl, String password);

    public static native String startRefresh(String walletId);

    public static native String status(String walletId);

    public static native String balance(String walletId);

    public static native String address(String walletId);

    public static native String transactions(String walletId, int limit, int offset, boolean reverse);

    public static native String send(String walletId, String receivingAddress, long amountAtomic, String memo);

    public static native String stop(String walletId);

    public static native boolean addressValid(String address);

    public static native boolean keyValid(String keyHex, String address, boolean isViewKey);
}
