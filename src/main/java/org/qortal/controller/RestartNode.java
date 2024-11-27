package org.qortal.controller;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.ApplyRestart;
import org.qortal.globalization.Translator;
import org.qortal.gui.SysTray;
import org.qortal.repository.RepositoryManager;
import org.qortal.settings.Settings;

import java.awt.TrayIcon.MessageType;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class RestartNode {

    private static final Logger LOGGER = LogManager.getLogger(RestartNode.class);

    public static final String JAR_FILENAME = "qortal.jar";
    public static final String AGENTLIB_JVM_HOLDER_ARG = "-DQORTAL_agentlib=";

    public static boolean attemptToRestart() {
        LOGGER.info("Restarting node...");

        // Attempt repository backup if enabled
        if (Settings.getInstance().getRepositoryBackupInterval() > 0) {
            try {
                long timeoutMillis = 60 * 1000L; // 60 seconds
                RepositoryManager.backup(true, "backup", timeoutMillis);
                LOGGER.info("Repository backup completed successfully.");
            } catch (TimeoutException e) {
                LOGGER.warn("Repository backup timed out: {}", e.getMessage());
                // Proceed with the restart even if backup fails
            } catch (Exception e) {
                LOGGER.error("Unexpected error during repository backup: {}", e.getMessage(), e);
                // Proceed with the restart despite errors
            }
        }

        try {
            // Locate the Java binary
            String javaHome = System.getProperty("java.home");
            LOGGER.debug("Java home directory: {}", javaHome);

            Path javaBinary = Paths.get(javaHome, "bin", "java");
            LOGGER.debug("Java binary path: {}", javaBinary);

            // Prepare command to restart the node
            List<String> javaCmd = new ArrayList<>();
            javaCmd.add(javaBinary.toString());

            // JVM arguments
            List<String> jvmArgs = new ArrayList<>(ManagementFactory.getRuntimeMXBean().getInputArguments());

            // Handle -agentlib arguments to avoid port conflicts
            jvmArgs = jvmArgs.stream()
                    .map(arg -> arg.startsWith("-agentlib") ? arg.replace("-agentlib", AGENTLIB_JVM_HOLDER_ARG) : arg)
                    .collect(Collectors.toList());

            // Remove unsupported JNI options
            List<String> unsupportedOptions = Arrays.asList("abort", "exit", "vfprintf");
            jvmArgs.removeAll(unsupportedOptions);

            javaCmd.addAll(jvmArgs);

            // Add the classpath and main class
            javaCmd.addAll(Arrays.asList("-cp", JAR_FILENAME, ApplyRestart.class.getCanonicalName()));

            // Include saved startup arguments
            String[] savedArgs = Controller.getInstance().getSavedArgs();
            if (savedArgs != null) {
                javaCmd.addAll(Arrays.asList(savedArgs));
            }

            LOGGER.info("Restarting node with command: {}", String.join(" ", javaCmd));

            // Notify the user
            SysTray.getInstance().showMessage(
                    Translator.INSTANCE.translate("SysTray", "RESTARTING_NODE"),
                    Translator.INSTANCE.translate("SysTray", "APPLYING_RESTARTING_NODE"),
                    MessageType.INFO
            );

            // Start the new process
            ProcessBuilder processBuilder = new ProcessBuilder(javaCmd);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);

            Process process = processBuilder.start();

            // Close the process's input stream to avoid resource leaks
            try {
                process.getOutputStream().close();
            } catch (Exception e) {
                LOGGER.warn("Failed to close process output stream: {}", e.getMessage());
            }

            return true; // Node restart initiated successfully
        } catch (Exception e) {
            LOGGER.error("Failed to restart node: {}", e.getMessage(), e);
            return true; // Return true to indicate repo was okay even if restart failed
        }
    }
}
