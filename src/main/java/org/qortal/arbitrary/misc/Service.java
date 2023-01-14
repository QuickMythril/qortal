package org.qortal.arbitrary.misc;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;
import org.qortal.arbitrary.ArbitraryDataRenderer;
import org.qortal.transaction.Transaction;
import org.qortal.utils.FilesystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public enum Service {
    AUTO_UPDATE(1, false, null, null),
    ARBITRARY_DATA(100, false, null, null),
    QCHAT_ATTACHMENT(120, true, 1024*1024L, null) {
        @Override
        public ValidationResult validate(Path path) {
            // Custom validation function to require a single file, with a whitelisted extension
            int fileCount = 0;
            File[] files = path.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        return ValidationResult.DIRECTORIES_NOT_ALLOWED;
                    }
                    final String extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
                    final List<String> allowedExtensions = Arrays.asList("zip", "pdf", "txt", "odt", "ods", "doc", "docx", "xls", "xlsx", "ppt", "pptx");
                    if (extension == null || !allowedExtensions.contains(extension)) {
                        return ValidationResult.INVALID_FILE_EXTENSION;
                    }
                    fileCount++;
                }
            }
            if (fileCount != 1) {
                return ValidationResult.INVALID_FILE_COUNT;
            }
            return ValidationResult.OK;
        }
    },
    WEBSITE(200, true, null, null) {
        @Override
        public ValidationResult validate(Path path) {
            // Custom validation function to require an index HTML file in the root directory
            List<String> fileNames = ArbitraryDataRenderer.indexFiles();
            String[] files = path.toFile().list();
            if (files != null) {
                for (String file : files) {
                    Path fileName = Paths.get(file).getFileName();
                    if (fileName != null && fileNames.contains(fileName.toString())) {
                        return ValidationResult.OK;
                    }
                }
            }
            return ValidationResult.MISSING_INDEX_FILE;
        }
    },
    GIT_REPOSITORY(300, false, null, null),
    IMAGE(400, true, 10*1024*1024L, null),
    THUMBNAIL(410, true, 500*1024L, null),
    QCHAT_IMAGE(420, true, 500*1024L, null),
    VIDEO(500, false, null, null),
    AUDIO(600, false, null, null),
    BLOG(700, false, null, null),
    BLOG_POST(777, false, null, null),
    BLOG_COMMENT(778, false, null, null),
    DOCUMENT(800, false, null, null),
    LIST(900, true, null, null),
    PLAYLIST(910, true, null, null),
    APP(1000, false, null, null),
    METADATA(1100, false, null, null),
    GIF_REPOSITORY(1200, true, 25*1024*1024L, null) {
        @Override
        public ValidationResult validate(Path path) {
            // Custom validation function to require .gif files only, and at least 1
            int gifCount = 0;
            File[] files = path.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        return ValidationResult.DIRECTORIES_NOT_ALLOWED;
                    }
                    String extension = FilenameUtils.getExtension(file.getName()).toLowerCase();
                    if (!Objects.equals(extension, "gif")) {
                        return ValidationResult.INVALID_FILE_EXTENSION;
                    }
                    gifCount++;
                }
            }
            if (gifCount == 0) {
                return ValidationResult.MISSING_DATA;
            }
            return ValidationResult.OK;
        }
    };

    public final int value;
    private final boolean requiresValidation;
    private final Long maxSize;
    private final List<String> requiredKeys;

    private static final Map<Integer, Service> map = stream(Service.values())
            .collect(toMap(service -> service.value, service -> service));

    Service(int value, boolean requiresValidation, Long maxSize, List<String> requiredKeys) {
        this.value = value;
        this.requiresValidation = requiresValidation;
        this.maxSize = maxSize;
        this.requiredKeys = requiredKeys;
    }

    public ValidationResult validate(Path path) throws IOException {
        if (!this.isValidationRequired()) {
            return ValidationResult.OK;
        }

        byte[] data = FilesystemUtils.getSingleFileContents(path);
        long size = FilesystemUtils.getDirectorySize(path);

        // Validate max size if needed
        if (this.maxSize != null) {
            if (size > this.maxSize) {
                return ValidationResult.EXCEEDS_SIZE_LIMIT;
            }
        }

        // Validate required keys if needed
        if (this.requiredKeys != null) {
            if (data == null) {
                return ValidationResult.MISSING_KEYS;
            }
            JSONObject json = Service.toJsonObject(data);
            for (String key : this.requiredKeys) {
                if (!json.has(key)) {
                    return ValidationResult.MISSING_KEYS;
                }
            }
        }

        // Validation passed
        return ValidationResult.OK;
    }

    public boolean isValidationRequired() {
        return this.requiresValidation;
    }

    public static Service valueOf(int value) {
        return map.get(value);
    }

    public static JSONObject toJsonObject(byte[] data) {
        String dataString = new String(data);
        return new JSONObject(dataString);
    }

    public enum ValidationResult {
        OK(1),
        MISSING_KEYS(2),
        EXCEEDS_SIZE_LIMIT(3),
        MISSING_INDEX_FILE(4),
        DIRECTORIES_NOT_ALLOWED(5),
        INVALID_FILE_EXTENSION(6),
        MISSING_DATA(7),
        INVALID_FILE_COUNT(8);

        public final int value;

        private static final Map<Integer, Transaction.ValidationResult> map = stream(Transaction.ValidationResult.values()).collect(toMap(result -> result.value, result -> result));

        ValidationResult(int value) {
            this.value = value;
        }

        public static Transaction.ValidationResult valueOf(int value) {
            return map.get(value);
        }
    }
}
