package org.qortal.arbitrary.metadata;

import org.json.JSONArray;
import org.json.JSONObject;
import org.qortal.arbitrary.misc.Category;
import org.qortal.repository.DataException;
import org.qortal.utils.Base58;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ArbitraryDataTransactionMetadata extends ArbitraryDataMetadata {

    private List<byte[]> chunks;
    private String title;
    private String description;
    private List<String> tags;
    private Category category;
    private List<String> files;

    private static int MAX_TITLE_LENGTH = 80;
    private static int MAX_DESCRIPTION_LENGTH = 500;
    private static int MAX_TAG_LENGTH = 20;
    private static int MAX_TAGS_COUNT = 5;

    public ArbitraryDataTransactionMetadata(Path filePath) {
        super(filePath);

    }

    @Override
    protected void readJson() throws DataException {
        if (this.jsonString == null) {
            throw new DataException("Transaction metadata JSON string is null");
        }

        JSONObject metadata = new JSONObject(this.jsonString);

        if (metadata.has("title")) {
            this.title = metadata.getString("title");
        }

        if (metadata.has("description")) {
            this.description = metadata.getString("description");
        }

        List<String> tagsList = new ArrayList<>();
        if (metadata.has("tags")) {
            JSONArray tags = metadata.getJSONArray("tags");
            if (tags != null) {
                for (int i=0; i<tags.length(); i++) {
                    String tag = tags.getString(i);
                    if (tag != null) {
                        tagsList.add(tag);
                    }
                }
            }
            this.tags = tagsList;
        }

        if (metadata.has("category")) {
            this.category = Category.uncategorizedValueOf(metadata.getString("category"));
        }

        List<byte[]> chunksList = new ArrayList<>();
        if (metadata.has("chunks")) {
            JSONArray chunks = metadata.getJSONArray("chunks");
            if (chunks != null) {
                for (int i=0; i<chunks.length(); i++) {
                    String chunk = chunks.getString(i);
                    if (chunk != null) {
                        chunksList.add(Base58.decode(chunk));
                    }
                }
            }
            this.chunks = chunksList;
        }

        List<String> filesList = new ArrayList<>();
        if (metadata.has("files")) {
            JSONArray files = metadata.getJSONArray("files");
            if (files != null) {
                for (int i=0; i<files.length(); i++) {
                    String tag = files.getString(i);
                    if (tag != null) {
                        filesList.add(tag);
                    }
                }
            }
            this.files = filesList;
        }
    }

    @Override
    protected void buildJson() {
        JSONObject outer = new JSONObject();

        if (this.title != null && !this.title.isEmpty()) {
            outer.put("title", this.title);
        }

        if (this.description != null && !this.description.isEmpty()) {
            outer.put("description", this.description);
        }

        JSONArray tags = new JSONArray();
        if (this.tags != null) {
            for (String tag : this.tags) {
                tags.put(tag);
            }
            outer.put("tags", tags);
        }

        if (this.category != null) {
            outer.put("category", this.category.toString());
        }

        JSONArray chunks = new JSONArray();
        if (this.chunks != null) {
            for (byte[] chunk : this.chunks) {
                chunks.put(Base58.encode(chunk));
            }
        }
        outer.put("chunks", chunks);

        JSONArray files = new JSONArray();
        if (this.files != null) {
            for (String file : this.files) {
                files.put(file);
            }
        }
        outer.put("files", files);

        this.jsonString = outer.toString(2);
        LOGGER.trace("Transaction metadata: {}", this.jsonString);
    }


    public void setChunks(List<byte[]> chunks) {
        this.chunks = chunks;
    }

    public List<byte[]> getChunks() {
        return this.chunks;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return this.title;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getTags() {
        return this.tags;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Category getCategory() {
        return this.category;
    }

    public void setFiles(List<String> files) {
        this.files = files;
    }

    public List<String> getFiles() {
        return this.files;
    }

    public boolean containsChunk(byte[] chunk) {
        for (byte[] c : this.chunks) {
            if (Arrays.equals(c, chunk)) {
                return true;
            }
        }
        return false;
    }


    // Static helper methods

    public static String limitTitle(String title) {
        if (title == null) {
            return null;
        }
        if (title.isEmpty()) {
            return null;
        }

        return title.substring(0, Math.min(title.length(), MAX_TITLE_LENGTH));
    }

    public static String limitDescription(String description) {
        if (description == null) {
            return null;
        }
        if (description.isEmpty()) {
            return null;
        }

        return description.substring(0, Math.min(description.length(), MAX_DESCRIPTION_LENGTH));
    }

    public static List<String> limitTags(List<String> tags) {
        if (tags == null) {
            return null;
        }

        // Ensure tags list is mutable
        List<String> mutableTags = new ArrayList<>(tags);

        int tagCount = mutableTags.size();
        if (tagCount == 0) {
            return null;
        }

        // Remove tags over the limit
        // This is cleaner than truncating, which results in malformed tags
        // Also remove tags that are empty
        Iterator iterator = mutableTags.iterator();
        while (iterator.hasNext()) {
            String tag = (String) iterator.next();
            if (tag == null || tag.length() > MAX_TAG_LENGTH || tag.isEmpty()) {
                iterator.remove();
            }
        }

        // Limit the total number of tags
        if (tagCount > MAX_TAGS_COUNT) {
            mutableTags = mutableTags.subList(0, MAX_TAGS_COUNT);
        }

        return mutableTags;
    }

}
