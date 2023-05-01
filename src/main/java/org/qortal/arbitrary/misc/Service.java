package org.qortal.arbitrary.misc;

import org.apache.commons.io.FilenameUtils;
import org.json.JSONObject;
import org.qortal.arbitrary.ArbitraryDataRenderer;
import org.qortal.transaction.Transaction;
import org.qortal.utils.FilesystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;

public enum Service {
    AUTO_UPDATE(1, false, null, false, null),
    ARBITRARY_DATA(100, false, null, false, null),
    QCHAT_ATTACHMENT(120, true, 1024*1024L, true, null) {
        @Override
        public ValidationResult validate(Path path) throws IOException {
            ValidationResult superclassResult = super.validate(path);
            if (superclassResult != ValidationResult.OK) {
                return superclassResult;
            }

            File[] files = path.toFile().listFiles();
            // If already a single file, replace the list with one that contains that file only
            if (files == null && path.toFile().isFile()) {
                files = new File[] { path.toFile() };
            }
            // Now validate the file's extension
            if (files != null && files[0] != null) {
                final String extension = FilenameUtils.getExtension(files[0].getName()).toLowerCase();
                // We must allow blank file extensions because these are used by data published from a plaintext or base64-encoded string
                final List<String> allowedExtensions = Arrays.asList("zip", "pdf", "txt", "odt", "ods", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "");
                if (extension == null || !allowedExtensions.contains(extension)) {
                    return ValidationResult.INVALID_FILE_EXTENSION;
                }
            }
            return ValidationResult.OK;
        }
    },
    ATTACHMENT(130, false, null, true, null),
    FILE(140, false, null, true, null),
    FILES(150, false, null, false, null),
    CHAIN_DATA(160, true, 239L, true, null),
    WEBSITE(200, true, null, false, null) {
        @Override
        public ValidationResult validate(Path path) throws IOException {
            ValidationResult superclassResult = super.validate(path);
            if (superclassResult != ValidationResult.OK) {
                return superclassResult;
            }

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
    GIT_REPOSITORY(300, false, null, false, null),
    IMAGE(400, true, 10*1024*1024L, true, null),
    THUMBNAIL(410, true, 500*1024L, true, null),
    QCHAT_IMAGE(420, true, 500*1024L, true, null),
    VIDEO(500, false, null, true, null),
    AUDIO(600, false, null, true, null),
    QCHAT_AUDIO(610, true, 10*1024*1024L, true, null),
    QCHAT_VOICE(620, true, 10*1024*1024L, true, null),
    VOICE(630, true, 10*1024*1024L, true, null),
    PODCAST(640, false, null, true, null),
    BLOG(700, false, null, false, null),
    BLOG_POST(777, false, null, true, null),
    BLOG_COMMENT(778, true, 500*1024L, true, null),
    DOCUMENT(800, false, null, true, null),
    LIST(900, true, null, true, null),
    PLAYLIST(910, true, null, true, null),
    APP(1000, true, 50*1024*1024L, false, null),
    METADATA(1100, false, null, true, null),
    JSON(1110, true, 25*1024L, true, null) {
        @Override
        public ValidationResult validate(Path path) throws IOException {
            ValidationResult superclassResult = super.validate(path);
            if (superclassResult != ValidationResult.OK) {
                return superclassResult;
            }

            // Require valid JSON
            byte[] data = FilesystemUtils.getSingleFileContents(path);
            String json = new String(data, StandardCharsets.UTF_8);
            try {
                objectMapper.readTree(json);
                return ValidationResult.OK;
            } catch (IOException e) {
                return ValidationResult.INVALID_CONTENT;
            }
        }
    },
    GIF_REPOSITORY(1200, true, 25*1024*1024L, false, null) {
        @Override
        public ValidationResult validate(Path path) throws IOException {
            ValidationResult superclassResult = super.validate(path);
            if (superclassResult != ValidationResult.OK) {
                return superclassResult;
            }

            // Custom validation function to require .gif files only, and at least 1
            int gifCount = 0;
            File[] files = path.toFile().listFiles();
            // If already a single file, replace the list with one that contains that file only
            if (files == null && path.toFile().isFile()) {
                files = new File[] { path.toFile() };
            }
            if (files != null) {
                for (File file : files) {
                    if (file.getName().equals(".qortal")) {
                        continue;
                    }
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
    },
    STORE(1300, false, null, true, null),
    PRODUCT(1310, false, null, true, null),
    OFFER(1330, false, null, true, null),
    COUPON(1340, false, null, true, null),
    CODE(1400, false, null, true, null),
    PLUGIN(1410, false, null, true, null),
    EXTENSION(1420, false, null, true, null),
    GAME(1500, false, null, false, null),
    ITEM(1510, false, null, true, null),
    NFT(1600, false, null, true, null),
    DATABASE(1700, false, null, false, null),
    SNAPSHOT(1710, false, null, false, null),
    COMMENT(1800, true, 500*1024L, true, null),
    CHAIN_COMMENT(1810, true, 239L, true, null),
    MAIL(1900, true, 1024*1024L, true, null),
    MESSAGE(1910, true, 1024*1024L, true, null);

    public final int value;
    private final boolean requiresValidation;
    private final Long maxSize;
    private final boolean single;
    private final List<String> requiredKeys;

    private static final Map<Integer, Service> map = stream(Service.values())
            .collect(toMap(service -> service.value, service -> service));

    // For JSON validation
    private static final ObjectMapper objectMapper = new ObjectMapper();

    Service(int value, boolean requiresValidation, Long maxSize, boolean single, List<String> requiredKeys) {
        this.value = value;
        this.requiresValidation = requiresValidation;
        this.maxSize = maxSize;
        this.single = single;
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

        // Validate file count if needed
        if (this.single && data == null) {
            return ValidationResult.INVALID_FILE_COUNT;
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
        String dataString = new String(data, StandardCharsets.UTF_8);
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
        INVALID_FILE_COUNT(8),
        INVALID_CONTENT(9);

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
