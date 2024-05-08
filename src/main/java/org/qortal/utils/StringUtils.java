package org.qortal.utils;

public class StringUtils {

    public static String sanitizeString(String input) {

        return input
                .replaceAll("[<>:\"/\\\\|?*]", "") // Remove invalid characters
                .replaceAll("^\\s+|\\s+$", "") // Trim leading and trailing whitespace
                .replaceAll("\\s+", "_");
    }
}
