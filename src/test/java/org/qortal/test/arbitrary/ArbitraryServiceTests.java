package org.qortal.test.arbitrary;

import org.junit.Before;
import org.junit.Test;
import org.qortal.arbitrary.misc.Service;
import org.qortal.arbitrary.misc.Service.ValidationResult;
import org.qortal.repository.DataException;
import org.qortal.test.common.Common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Random;

import static org.junit.Assert.*;

public class ArbitraryServiceTests extends Common {

    @Before
    public void beforeTest() throws DataException {
        Common.useDefaultSettings();
    }

    @Test
    public void testDefaultValidation() throws IOException {
        // We don't validate the ARBITRARY_DATA service specifically, so we can use it to test the default validation method
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write to temp path
        Path path = Files.createTempFile("testDefaultValidation", null);
        path.toFile().deleteOnExit();
        Files.write(path, data, StandardOpenOption.CREATE);

        Service service = Service.ARBITRARY_DATA;
        assertFalse(service.isValidationRequired());
        // Test validation anyway to ensure that no exception is thrown
        assertEquals(ValidationResult.OK, service.validate(path));
    }

    @Test
    public void testValidateWebsite() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateWebsite");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "index.html"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data2"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data3"), data, StandardOpenOption.CREATE);

        Service service = Service.WEBSITE;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.OK, service.validate(path));
    }

    @Test
    public void testValidateWebsiteWithoutIndexFile() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateWebsiteWithoutIndexFile");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "data1.html"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data2"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data3"), data, StandardOpenOption.CREATE);

        Service service = Service.WEBSITE;
        assertTrue(service.isValidationRequired());

        // There is no index file in the root
        assertEquals(ValidationResult.MISSING_INDEX_FILE, service.validate(path));
    }

    @Test
    public void testValidateWebsiteWithoutIndexFileInRoot() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateWebsiteWithoutIndexFileInRoot");
        path.toFile().deleteOnExit();
        Files.createDirectories(Paths.get(path.toString(), "directory"));
        Files.write(Paths.get(path.toString(), "directory", "index.html"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data2"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "data3"), data, StandardOpenOption.CREATE);

        Service service = Service.WEBSITE;
        assertTrue(service.isValidationRequired());

        // There is no index file in the root
        assertEquals(ValidationResult.MISSING_INDEX_FILE, service.validate(path));
    }

    @Test
    public void testValidateGifRepository() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateGifRepository");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "image1.gif"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "image2.gif"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "image3.gif"), data, StandardOpenOption.CREATE);

        Service service = Service.GIF_REPOSITORY;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.OK, service.validate(path));
    }

    @Test
    public void testValidateMultiLayerGifRepository() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateMultiLayerGifRepository");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "image1.gif"), data, StandardOpenOption.CREATE);

        Path subdirectory = Paths.get(path.toString(), "subdirectory");
        Files.createDirectories(subdirectory);
        Files.write(Paths.get(subdirectory.toString(), "image2.gif"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(subdirectory.toString(), "image3.gif"), data, StandardOpenOption.CREATE);

        Service service = Service.GIF_REPOSITORY;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.DIRECTORIES_NOT_ALLOWED, service.validate(path));
    }

    @Test
    public void testValidateEmptyGifRepository() throws IOException {
        Path path = Files.createTempDirectory("testValidateEmptyGifRepository");

        Service service = Service.GIF_REPOSITORY;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.MISSING_DATA, service.validate(path));
    }

    @Test
    public void testValidateInvalidGifRepository() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateInvalidGifRepository");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "image1.gif"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "image2.gif"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "image3.jpg"), data, StandardOpenOption.CREATE); // Invalid extension

        Service service = Service.GIF_REPOSITORY;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.INVALID_FILE_EXTENSION, service.validate(path));
    }

    @Test
    public void testValidateQChatAttachment() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateQChatAttachment");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "document.pdf"), data, StandardOpenOption.CREATE);

        Service service = Service.QCHAT_ATTACHMENT;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.OK, service.validate(path));
    }

    @Test
    public void testValidateInvalidQChatAttachmentFileExtension() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateInvalidQChatAttachmentFileExtension");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "application.exe"), data, StandardOpenOption.CREATE);

        Service service = Service.QCHAT_ATTACHMENT;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.INVALID_FILE_EXTENSION, service.validate(path));
    }

    @Test
    public void testValidateEmptyQChatAttachment() throws IOException {
        Path path = Files.createTempDirectory("testValidateEmptyQChatAttachment");

        Service service = Service.QCHAT_ATTACHMENT;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.INVALID_FILE_COUNT, service.validate(path));
    }

    @Test
    public void testValidateMultiLayerQChatAttachment() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateMultiLayerQChatAttachment");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "file1.txt"), data, StandardOpenOption.CREATE);

        Path subdirectory = Paths.get(path.toString(), "subdirectory");
        Files.createDirectories(subdirectory);
        Files.write(Paths.get(subdirectory.toString(), "file2.txt"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(subdirectory.toString(), "file3.txt"), data, StandardOpenOption.CREATE);

        Service service = Service.QCHAT_ATTACHMENT;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.DIRECTORIES_NOT_ALLOWED, service.validate(path));
    }

    @Test
    public void testValidateMultiFileQChatAttachment() throws IOException {
        // Generate some random data
        byte[] data = new byte[1024];
        new Random().nextBytes(data);

        // Write the data to several files in a temp path
        Path path = Files.createTempDirectory("testValidateMultiFileQChatAttachment");
        path.toFile().deleteOnExit();
        Files.write(Paths.get(path.toString(), "file1.txt"), data, StandardOpenOption.CREATE);
        Files.write(Paths.get(path.toString(), "file2.txt"), data, StandardOpenOption.CREATE);

        Service service = Service.QCHAT_ATTACHMENT;
        assertTrue(service.isValidationRequired());

        // There is an index file in the root
        assertEquals(ValidationResult.INVALID_FILE_COUNT, service.validate(path));
    }

}