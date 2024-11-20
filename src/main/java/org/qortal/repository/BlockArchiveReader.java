package org.qortal.repository;

import com.google.common.primitives.Ints;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.data.block.BlockArchiveData;
import org.qortal.settings.Settings;
import org.qortal.transform.TransformationException;
import org.qortal.transform.block.BlockTransformation;
import org.qortal.transform.block.BlockTransformer;
import org.qortal.utils.Triple;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;

import static org.qortal.transform.Transformer.INT_LENGTH;

public class BlockArchiveReader {

    private static BlockArchiveReader instance;
    private Map<String, Triple<Integer, Integer, Integer>> fileListCache;
    private static final Logger LOGGER = LogManager.getLogger(BlockArchiveReader.class);

    private BlockArchiveReader() {
    }

    public static synchronized BlockArchiveReader getInstance() {
        if (instance == null) {
            instance = new BlockArchiveReader();
        }
        return instance;
    }

    private void fetchFileList() {
        Path archivePath = Paths.get(Settings.getInstance().getRepositoryPath(), "archive").toAbsolutePath();
        File archiveDirFile = archivePath.toFile();
        String[] files = archiveDirFile.list();

        if (files != null) {
            Map<String, Triple<Integer, Integer, Integer>> map = new HashMap<>();
            for (String file : files) {
                Path filePath = Paths.get(file);
                String filename = filePath.getFileName().toString();

                if (filename == null || !filename.contains("-") || !filename.contains(".")) {
                    continue; // Skip non-useful files
                }

                String[] parts = filename.substring(0, filename.lastIndexOf('.')).split("-");
                try {
                    Integer startHeight = Integer.parseInt(parts[0]);
                    Integer endHeight = Integer.parseInt(parts[1]);
                    map.put(filename, new Triple<>(startHeight, endHeight, endHeight - startHeight));
                } catch (NumberFormatException e) {
                    LOGGER.warn("Failed to parse file name: " + filename, e);
                }
            }
            this.fileListCache = Map.copyOf(map);
        }
    }

    private String getFilenameForHeight(int height) {
        if (fileListCache == null) {
            fetchFileList();
        }

        return fileListCache.entrySet().stream()
                .filter(entry -> height >= entry.getValue().getA() && height <= entry.getValue().getB())
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    public Integer fetchSerializationVersionForHeight(int height) {
        if (fileListCache == null) {
            fetchFileList();
        }

        Triple<byte[], Integer, Integer> serializedBlock = fetchSerializedBlockBytesForHeight(height);
        return (serializedBlock != null) ? serializedBlock.getB() : null;
    }

    public BlockTransformation fetchBlockAtHeight(int height) {
        if (fileListCache == null) {
            fetchFileList();
        }

        Triple<byte[], Integer, Integer> serializedBlock = fetchSerializedBlockBytesForHeight(height);
        if (serializedBlock == null) return null;

        byte[] serializedBytes = serializedBlock.getA();
        Integer serializationVersion = serializedBlock.getB();

        if (serializedBytes == null || serializationVersion == null) return null;

        ByteBuffer byteBuffer = ByteBuffer.wrap(serializedBytes);
        try {
            BlockTransformation blockInfo = switch (serializationVersion) {
                case 1 -> BlockTransformer.fromByteBuffer(byteBuffer);
                case 2 -> BlockTransformer.fromByteBufferV2(byteBuffer);
                default -> null;
            };

            if (blockInfo != null && blockInfo.getBlockData() != null) {
                blockInfo.getBlockData().setHeight(height); // Set the block height
            }

            return blockInfo;

        } catch (TransformationException e) {
            LOGGER.warn("Error transforming block at height " + height, e);
            return null;
        }
    }

    public BlockTransformation fetchBlockWithSignature(byte[] signature, Repository repository) {
        Integer height = fetchHeightForSignature(signature, repository);
        return (height != null) ? fetchBlockAtHeight(height) : null;
    }

    public List<BlockTransformation> fetchBlocksFromRange(int startHeight, int endHeight) {
        List<BlockTransformation> blockInfoList = new ArrayList<>();
        for (int height = startHeight; height <= endHeight; height++) {
            BlockTransformation blockInfo = fetchBlockAtHeight(height);
            if (blockInfo == null) return blockInfoList; // Stop if any block is missing
            blockInfoList.add(blockInfo);
        }
        return blockInfoList;
    }

    public Integer fetchHeightForSignature(byte[] signature, Repository repository) {
        try {
            BlockArchiveData archivedBlock = repository.getBlockArchiveRepository().getBlockArchiveDataForSignature(signature);
            return (archivedBlock != null) ? archivedBlock.getHeight() : null;
        } catch (DataException e) {
            return null;
        }
    }

    public int fetchHeightForTimestamp(long timestamp, Repository repository) {
        try {
            return repository.getBlockArchiveRepository().getHeightFromTimestamp(timestamp);
        } catch (DataException e) {
            return 0;
        }
    }

    public Triple<byte[], Integer, Integer> fetchSerializedBlockBytesForHeight(int height) {
        String filename = getFilenameForHeight(height);
        if (filename == null) {
            invalidateFileListCache(); // Clear the cache if the file is not found
            return null;
        }

        Path filePath = Paths.get(Settings.getInstance().getRepositoryPath(), "archive", filename).toAbsolutePath();
        try (RandomAccessFile file = new RandomAccessFile(filePath.toString(), "r")) {
            int version = file.readInt(); // version
            int startHeight = file.readInt(); // start height
            int endHeight = file.readInt(); // end height
            file.readInt(); // unused block count
            int variableHeaderLength = file.readInt(); // variable length header
            int fixedHeaderLength = (int) file.getFilePointer();

            if (version != 1 && version != 2) {
                LOGGER.info("Error: unknown version in file {}: {}", filename, version);
                return null;
            }

            if (height < startHeight || height > endHeight) {
                LOGGER.info("Error: requested height {} but the range of file {} is {}-{}", height, filename, startHeight, endHeight);
                return null;
            }

            int locationOfBlockIndex = (height - startHeight) * INT_LENGTH;
            file.seek(fixedHeaderLength + locationOfBlockIndex);
            int locationOfBlockInDataSegment = file.readInt();

            int dataSegmentStartIndex = fixedHeaderLength + variableHeaderLength + INT_LENGTH;
            file.seek(dataSegmentStartIndex + locationOfBlockInDataSegment);

            int blockHeight = file.readInt(); // block height
            int blockLength = file.readInt(); // block length

            if (blockHeight != height) {
                LOGGER.info("Error: block height {} does not match requested height {}", blockHeight, height);
                return null;
            }

            byte[] blockBytes = new byte[blockLength];
            file.read(blockBytes);

            return new Triple<>(blockBytes, version, height);

        } catch (IOException e) {
            LOGGER.info("Unable to read block {} from archive: {}", height, e.getMessage());
            return null;
        }
    }

    public int getHeightOfLastArchivedBlock() {
        if (fileListCache == null) {
            fetchFileList();
        }

        return fileListCache.values().stream()
                .mapToInt(Triple::getB)
                .max()
                .orElse(0); // Return the highest endHeight
    }

    public void invalidateFileListCache() {
        this.fileListCache = null;
    }
}
