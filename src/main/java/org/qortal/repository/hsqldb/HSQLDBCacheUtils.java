package org.qortal.repository.hsqldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.qortal.api.SearchMode;
import org.qortal.arbitrary.misc.Category;
import org.qortal.arbitrary.misc.Service;
import org.qortal.data.arbitrary.ArbitraryResourceCache;
import org.qortal.data.arbitrary.ArbitraryResourceData;
import org.qortal.data.arbitrary.ArbitraryResourceMetadata;
import org.qortal.data.arbitrary.ArbitraryResourceStatus;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.qortal.api.SearchMode.LATEST;

public class HSQLDBCacheUtils {

    private static final Logger LOGGER = LogManager.getLogger(HSQLDBCacheUtils.class);
    private static final String DEFAULT_IDENTIFIER = "default";
    private static final Comparator<ArbitraryResourceData> CREATED_WHEN_COMPARATOR = Comparator.comparingLong(data -> Optional.ofNullable(data.created).orElse(Long.MIN_VALUE));

    public static List<ArbitraryResourceData> callCache(
            ArbitraryResourceCache cache, Service service, String query, String identifier, 
            List<String> names, String title, String description, boolean prefixOnly, 
            List<String> exactMatchNames, boolean defaultResource, SearchMode mode, 
            Integer minLevel, Boolean followedOnly, Boolean excludeBlocked, Boolean includeMetadata, 
            Boolean includeStatus, Long before, Long after, Integer limit, Integer offset, Boolean reverse) {

        List<ArbitraryResourceData> candidates = new ArrayList<>();
        if (service != null) {
            candidates.addAll(cache.getDataByService().getOrDefault(service.value, Collections.emptyList()));
        }
        return candidates;
    }

    public static List<ArbitraryResourceData> filterList(
            List<ArbitraryResourceData> candidates, Map<String, Integer> levelByName, 
            Optional<SearchMode> mode, Optional<Service> service, Optional<String> query, 
            Optional<String> identifier, Optional<List<String>> names, Optional<String> title, 
            Optional<String> description, boolean prefixOnly, Optional<List<String>> exactMatchNames, 
            boolean defaultResource, Optional<Integer> minLevel, Optional<Supplier<List<String>>> includeOnly, 
            Optional<Supplier<List<String>>> exclude, Optional<Boolean> includeMetadata, 
            Optional<Boolean> includeStatus, Optional<Long> before, Optional<Long> after, 
            Optional<Integer> limit, Optional<Integer> offset, Optional<Boolean> reverse) {

        Stream<ArbitraryResourceData> stream = candidates.stream().filter(candidate -> candidate.name != null);

        if (service.isPresent()) {
            stream = stream.filter(candidate -> candidate.service.equals(service.get()));
        }

        if (query.isPresent()) {
            Predicate<String> predicate = prefixOnly ? getPrefixPredicate(query.get()) : getContainsPredicate(query.get());
            if (defaultResource) {
                stream = stream.filter(candidate -> DEFAULT_IDENTIFIER.equals(candidate.identifier) && predicate.test(candidate.name));
            } else {
                stream = stream.filter(candidate -> passQuery(predicate, candidate));
            }
        }

        stream = filterTerm(identifier, data -> data.identifier, prefixOnly, stream);
        stream = filterTerm(title, data -> Optional.ofNullable(data.metadata).map(ArbitraryResourceMetadata::getTitle).orElse(null), prefixOnly, stream);
        stream = filterTerm(description, data -> Optional.ofNullable(data.metadata).map(ArbitraryResourceMetadata::getDescription).orElse(null), prefixOnly, stream);

        if (exactMatchNames.isPresent() && !exactMatchNames.get().isEmpty()) {
            stream = filterExactNames(exactMatchNames.get(), stream);
        } else if (names.isPresent() && !names.get().isEmpty()) {
            stream = retainTerms(names.get(), data -> data.name, prefixOnly, stream);
        }

        minLevel.ifPresent(level -> stream = stream.filter(candidate -> levelByName.getOrDefault(candidate.name, 0) >= level));

        if (LATEST.equals(mode.orElse(LATEST))) {
            stream = stream.collect(Collectors.groupingBy(data -> new AbstractMap.SimpleEntry<>(data.name, data.service), 
                    Collectors.maxBy(Comparator.comparingLong(data -> data.created))))
                    .values().stream()
                    .filter(Optional::isPresent)
                    .map(Optional::get);
        }

        if (reverse.orElse(false)) {
            stream = stream.sorted(CREATED_WHEN_COMPARATOR.reversed());
        } else {
            stream = stream.sorted(CREATED_WHEN_COMPARATOR);
        }

        offset.ifPresent(stream::skip);
        limit.filter(l -> l > 0).ifPresent(stream::limit);

        if (includeMetadata.orElse(false)) {
            stream = stream.peek(candidate -> candidate.metadata = null);
        }

        if (includeStatus.orElse(false)) {
            stream = stream.peek(candidate -> candidate.status = null);
        }

        return stream.collect(Collectors.toList());
    }

    private static Stream<ArbitraryResourceData> filterTerm(
            Optional<String> term, Function<ArbitraryResourceData, String> stringSupplier, 
            boolean prefixOnly, Stream<ArbitraryResourceData> stream) {

        term.ifPresent(t -> {
            Predicate<String> predicate = prefixOnly ? getPrefixPredicate(t) : getContainsPredicate(t);
            stream.filter(candidate -> predicate.test(stringSupplier.apply(candidate)));
        });
        return stream;
    }

    private static Stream<ArbitraryResourceData> retainTerms(
            List<String> terms, Function<ArbitraryResourceData, String> stringSupplier, 
            boolean prefixOnly, Stream<ArbitraryResourceData> stream) {

        List<ArbitraryResourceData> toProcess = stream.collect(Collectors.toList());
        return terms.stream()
                .flatMap(term -> {
                    Predicate<String> predicate = prefixOnly ? getPrefixPredicate(term) : getContainsPredicate(term);
                    return toProcess.stream().filter(candidate -> predicate.test(stringSupplier.apply(candidate)));
                });
    }

    private static Stream<ArbitraryResourceData> filterExactNames(List<String> exactMatchNames, Stream<ArbitraryResourceData> stream) {
        Set<String> exactNamesSet = new HashSet<>(exactMatchNames.stream().map(String::toLowerCase).collect(Collectors.toList()));
        return stream.filter(candidate -> candidate.name != null && exactNamesSet.contains(candidate.name.toLowerCase()));
    }

    private static boolean passQuery(Predicate<String> predicate, ArbitraryResourceData candidate) {
        return predicate.test(candidate.name) || predicate.test(candidate.identifier) ||
                (candidate.metadata != null && (predicate.test(candidate.metadata.getTitle()) || predicate.test(candidate.metadata.getDescription())));
    }

    private static Predicate<String> getContainsPredicate(String term) {
        return value -> value != null && value.toLowerCase().contains(term.toLowerCase());
    }

    private static Predicate<String> getPrefixPredicate(String term) {
        return value -> value != null && value.toLowerCase().startsWith(term.toLowerCase());
    }

    public static void fillCache(ArbitraryResourceCache cache, HSQLDBRepository repository) {
        try {
            repository.saveChanges();
            List<ArbitraryResourceData> resources = getResources(repository);
            Map<Integer, List<ArbitraryResourceData>> dataByService = resources.stream().collect(Collectors.groupingBy(data -> data.service.value));

            synchronized (cache.getDataByService()) {
                cache.getDataByService().clear();
                cache.getDataByService().putAll(dataByService);
            }

            fillNameMap(cache.getLevelByName(), repository);
        } catch (Exception e) {
            LOGGER.error("Error while filling cache: ", e);
        }
    }

    private static void fillNameMap(Map<String, Integer> levelByName, HSQLDBRepository repository) throws SQLException {
        String sql = "SELECT name, level FROM NAMES INNER JOIN ACCOUNTS on owner = account";
        try (Statement statement = repository.connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                levelByName.put(resultSet.getString(1), resultSet.getInt(2));
            }
        }
    }

    private static List<ArbitraryResourceData> getResources(HSQLDBRepository repository) throws SQLException {
        String sql = "SELECT name, service, identifier, size, status, created_when, updated_when, title, description, category, tag1, tag2, tag3, tag4, tag5 " +
                "FROM ArbitraryResourcesCache LEFT JOIN ArbitraryMetadataCache USING (service, name, identifier) WHERE name IS NOT NULL";
        List<ArbitraryResourceData> resources = new ArrayList<>();
        try (Statement statement = repository.connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                ArbitraryResourceData resourceData = new ArbitraryResourceData();
                resourceData.name = resultSet.getString(1);
                resourceData.service = Service.valueOf(resultSet.getInt(2));
                resourceData.identifier = resultSet.getString(3);
                resourceData.size = resultSet.getInt(4);
                resourceData.status = ArbitraryResourceStatus.valueOf(resultSet.getInt(5));
                resourceData.created = resultSet.getLong(6);
                resourceData.updated = resultSet.getLong(7);
                ArbitraryResourceMetadata metadata = new ArbitraryResourceMetadata();
                metadata.setTitle(resultSet.getString(8));
                metadata.setDescription(resultSet.getString(9));
                resourceData.metadata = metadata;
                resourceData.category = Category.valueOf(resultSet.getInt(10));
                resourceData.tags = Arrays.asList(resultSet.getString(11), resultSet.getString(12),
                        resultSet.getString(13), resultSet.getString(14), resultSet.getString(15));
                resources.add(resourceData);
            }
        }
        return resources;
    }
}
