package org.qortal.repository.hsqldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.qortal.data.account.MintingAccountData;
import org.qortal.data.crosschain.TradeBotData;
import org.qortal.repository.DataException;
import org.qortal.repository.Repository;
import org.qortal.settings.Settings;
import org.qortal.utils.Base58;
import org.qortal.utils.Triple;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class HSQLDBImportExport {

    private static final Logger LOGGER = LogManager.getLogger(HSQLDBImportExport.class);

    // Backup methods
    public static void backupTradeBotStates(Repository repository, List<TradeBotData> additional) throws DataException {
        backupTradeBotStatesHelper(repository, additional, "TradeBotStates.json", "current");
        backupTradeBotStatesHelper(repository, additional, "TradeBotStatesArchive.json", "archive");
        LOGGER.info("Exported sensitive/node-local data: trade bot states");
    }

    public static void backupMintingAccounts(Repository repository) throws DataException {
        backupCurrentData(repository, "MintingAccounts.json", "mintingAccounts", "current", repository.getAccountRepository()::getMintingAccounts);
        LOGGER.info("Exported sensitive/node-local data: minting accounts");
    }

    // Helper method for backup
    private static <T> void backupCurrentData(Repository repository, String fileName, String type, String dataset, DataFetcher<T> fetcher) throws DataException {
        try {
            Path backupDirectory = getExportDirectory(true);
            List<T> dataList = fetcher.fetch(repository);
            JSONArray dataJsonArray = new JSONArray();
            for (T data : dataList) {
                dataJsonArray.put(((DataConvertible) data).toJson());
            }

            JSONObject jsonWrapper = new JSONObject();
            jsonWrapper.put("type", type);
            jsonWrapper.put("dataset", dataset);
            jsonWrapper.put("data", dataJsonArray);

            try (FileWriter writer = new FileWriter(Paths.get(backupDirectory.toString(), fileName).toString())) {
                writer.write(jsonWrapper.toString(2));
            }

        } catch (IOException e) {
            throw new DataException("Unable to export data", e);
        }
    }

    // Helper method for trade bot backup
    private static void backupTradeBotStatesHelper(Repository repository, List<TradeBotData> additional, String fileName, String dataset) throws DataException {
        try {
            Path backupDirectory = getExportDirectory(true);

            List<TradeBotData> allTradeBotData = repository.getCrossChainRepository().getAllTradeBotData();
            if (additional != null && !additional.isEmpty()) {
                allTradeBotData.addAll(additional);
            }

            JSONArray tradeBotDataJson = new JSONArray();
            for (TradeBotData tradeBotData : allTradeBotData) {
                tradeBotDataJson.put(tradeBotData.toJson());
            }

            // Combine with archived data if needed
            if (dataset.equals("archive")) {
                tradeBotDataJson = mergeWithArchivedData(tradeBotDataJson, backupDirectory);
            }

            JSONObject jsonWrapper = new JSONObject();
            jsonWrapper.put("type", "tradeBotStates");
            jsonWrapper.put("dataset", dataset);
            jsonWrapper.put("data", tradeBotDataJson);

            try (FileWriter writer = new FileWriter(Paths.get(backupDirectory.toString(), fileName).toString())) {
                writer.write(jsonWrapper.toString(2));
            }

        } catch (IOException e) {
            throw new DataException("Unable to export trade bot states", e);
        }
    }

    // Merges new trade bot data with archived data
    private static JSONArray mergeWithArchivedData(JSONArray newTradeBotDataJson, Path backupDirectory) throws DataException {
        try {
            String fileName = Paths.get(backupDirectory.toString(), "TradeBotStatesArchive.json").toString();
            File tradeBotStatesBackupFile = new File(fileName);
            if (!tradeBotStatesBackupFile.exists()) {
                return newTradeBotDataJson;
            }

            String jsonString = new String(Files.readAllBytes(Paths.get(fileName)));
            Triple<String, String, JSONArray> parsedJSON = parseJSONString(jsonString);

            if (!"tradeBotStates".equals(parsedJSON.getA()) || !"archive".equals(parsedJSON.getB())) {
                throw new DataException("Format mismatch when exporting archived trade bot states");
            }

            JSONArray existingData = parsedJSON.getC();
            for (Object obj : existingData) {
                JSONObject existingTradeBotDataItem = (JSONObject) obj;
                String existingTradePrivateKey = existingTradeBotDataItem.getString("tradePrivateKey");
                boolean found = newTradeBotDataJson.toList().stream().anyMatch(tradeBotData -> Base58.encode(((TradeBotData) tradeBotData).getTradePrivateKey()).equals(existingTradePrivateKey));
                if (!found) {
                    newTradeBotDataJson.put(existingTradeBotDataItem);
                }
            }
        } catch (IOException e) {
            throw new DataException("Unable to merge with archived trade bot data", e);
        }
        return newTradeBotDataJson;
    }

    // Import method
    public static void importDataFromFile(String filename, Repository repository) throws DataException, IOException {
        Path path = Paths.get(filename);
        if (!path.toFile().exists()) {
            throw new IOException(String.format("File doesn't exist: %s", filename));
        }

        String jsonString = new String(Files.readAllBytes(path));
        Triple<String, String, JSONArray> parsedJSON = parseJSONString(jsonString);

        if (parsedJSON.getA() == null || parsedJSON.getC() == null) {
            throw new DataException(String.format("Missing data when importing %s into repository", filename));
        }

        String type = parsedJSON.getA();
        JSONArray data = parsedJSON.getC();

        for (Object obj : data) {
            JSONObject dataJsonObject = (JSONObject) obj;
            switch (type) {
                case "tradeBotStates":
                    importTradeBotDataJSON(dataJsonObject, repository);
                    break;
                case "mintingAccounts":
                    importMintingAccountDataJSON(dataJsonObject, repository);
                    break;
                default:
                    throw new DataException(String.format("Unrecognized data type when importing %s into repository", filename));
            }
        }
        LOGGER.info(String.format("Imported %s into repository from %s", type, filename));
    }

    private static void importTradeBotDataJSON(JSONObject tradeBotDataJson, Repository repository) throws DataException {
        TradeBotData tradeBotData = TradeBotData.fromJson(tradeBotDataJson);
        repository.getCrossChainRepository().save(tradeBotData);
    }

    private static void importMintingAccountDataJSON(JSONObject mintingAccountDataJson, Repository repository) throws DataException {
        MintingAccountData mintingAccountData = MintingAccountData.fromJson(mintingAccountDataJson);
        repository.getAccountRepository().save(mintingAccountData);
    }

    // Utilities
    public static Path getExportDirectory(boolean createIfNotExists) throws DataException {
        Path backupPath = Paths.get(Settings.getInstance().getExportPath());
        if (createIfNotExists) {
            try {
                Files.createDirectories(backupPath);
            } catch (IOException e) {
                LOGGER.error("Unable to create export directory", e);
                throw new DataException("Unable to create export directory", e);
            }
        }
        return backupPath;
    }

    public static Triple<String, String, JSONArray> parseJSONString(String jsonString) throws DataException {
        try {
            JSONObject jsonData = new JSONObject(jsonString);
            String type = jsonData.optString("type", "tradeBotStates");
            String dataset = jsonData.optString("dataset", "archive");
            JSONArray data = jsonData.optJSONArray("data");

            if (data == null) {
                data = new JSONArray(jsonString);  // Legacy format fallback
            }

            return new Triple<>(type, dataset, data);

        } catch (JSONException e) {
            throw new DataException("Failed to parse JSON string", e);
        }
    }

    // Functional interface for fetching data
    @FunctionalInterface
    public interface DataFetcher<T> {
        List<T> fetch(Repository repository) throws DataException;
    }

    // Interface for convertible objects (toJson method)
    public interface DataConvertible {
        JSONObject toJson();
    }
}
