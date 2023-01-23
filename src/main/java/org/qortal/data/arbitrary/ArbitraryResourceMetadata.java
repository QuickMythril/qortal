package org.qortal.data.arbitrary;

import org.qortal.arbitrary.metadata.ArbitraryDataTransactionMetadata;
import org.qortal.arbitrary.misc.Category;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import java.util.List;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArbitraryResourceMetadata {

    private String title;
    private String description;
    private List<String> tags;
    private Category category;
    private String categoryName;
    private List<String> files;

    public ArbitraryResourceMetadata() {
    }

    public ArbitraryResourceMetadata(String title, String description, List<String> tags, Category category, List<String> files) {
        this.title = title;
        this.description = description;
        this.tags = tags;
        this.category = category;
        this.files = files;

        if (category != null) {
            this.categoryName = category.getName();
        }
    }

    public static ArbitraryResourceMetadata fromTransactionMetadata(ArbitraryDataTransactionMetadata transactionMetadata, boolean includeFileList) {
        if (transactionMetadata == null) {
            return null;
        }
        String title = transactionMetadata.getTitle();
        String description = transactionMetadata.getDescription();
        List<String> tags = transactionMetadata.getTags();
        Category category = transactionMetadata.getCategory();

        // We don't always want to include the file list as it can be too verbose
        List<String> files = null;
        if (includeFileList) {
            files = transactionMetadata.getFiles();
        }

        if (title == null && description == null && tags == null && category == null && files == null) {
            return null;
        }

        return new ArbitraryResourceMetadata(title, description, tags, category, files);
    }

    public List<String> getFiles() {
        return this.files;
    }
}
