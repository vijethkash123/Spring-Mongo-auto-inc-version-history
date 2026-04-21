package com.example.mongospring.service;

import com.example.mongospring.model.ConfigAdmin;
import com.example.mongospring.model.Counter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Handles append-only versioning for the ConfigAdmin collection.
 *
 * <p>The version counter is scoped per <b>(appName, key)</b> pair. The counter
 * document key stored in the "counters" collection uses the format
 * {@code "configadmin::<appName>::<key>"} — keeping it clearly namespaced and
 * collision-free with other collections that share the same counters collection.
 *
 * <p><b>Concurrency safety:</b> version increments are performed with a single
 * atomic {@code findAndModify} ({@code $inc} + upsert). Only one caller ever
 * receives each version number for a given (appName, key), even under concurrent
 * load across multiple application instances.
 */
@Service
public class ConfigAdminService {

    /** Counter key prefix — namespaces config counters from student counters. */
    private static final String COUNTER_PREFIX = "configadmin::";

    /** Separator between appName and key inside the counter document ID. */
    private static final String SEP = "::";

    private final MongoTemplate mongoTemplate;

    @Autowired
    public ConfigAdminService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Atomically increments the version counter for the (appName, key) pair and
     * inserts a brand-new ConfigAdmin document. Existing documents are never touched.
     *
     * @param appName application name
     * @param key     config key within the application
     * @param data    config value / payload for this version
     * @return the newly inserted {@link ConfigAdmin} document
     */
    public ConfigAdmin createNewVersion(String appName, String key, String data) {
        long nextVersion = incrementAndGetVersion(appName, key);

        ConfigAdmin doc = ConfigAdmin.builder()
                .appName(appName)
                .key(key)
                .data(data)
                .version(nextVersion)
                .createdAt(Instant.now())
                .build();

        return mongoTemplate.insert(doc);
    }

    /**
     * Returns the latest version document for a given (appName, key) pair.
     *
     * @param appName application name
     * @param key     config key within the application
     * @return the most recent {@link ConfigAdmin} document, or {@code null} if none exists
     */
    public ConfigAdmin getLatestVersion(String appName, String key) {
        Query query = Query.query(
                Criteria.where("appName").is(appName).and("key").is(key))
                .with(Sort.by(Sort.Direction.DESC, "version"))
                .limit(1);
        return mongoTemplate.findOne(query, ConfigAdmin.class);
    }

    /**
     * Returns all versions for a given (appName, key) pair in ascending order.
     *
     * @param appName application name
     * @param key     config key within the application
     * @return list of all {@link ConfigAdmin} documents for this pair
     */
    public List<ConfigAdmin> getAllVersions(String appName, String key) {
        Query query = Query.query(
                Criteria.where("appName").is(appName).and("key").is(key))
                .with(Sort.by(Sort.Direction.ASC, "version"));
        return mongoTemplate.find(query, ConfigAdmin.class);
    }

    /**
     * Returns the latest version of all distinct keys for a given appName.
     * Useful for fetching the current effective config of an entire application.
     *
     * @param appName application name
     * @return list of the most recent document per key for this appName
     */
    public List<ConfigAdmin> getLatestByAppName(String appName) {
        // Aggregate: filter by appName, sort desc by version, group by key keeping first
        org.springframework.data.mongodb.core.aggregation.Aggregation agg =
                org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation(
                        org.springframework.data.mongodb.core.aggregation.Aggregation.match(
                                Criteria.where("appName").is(appName)),
                        org.springframework.data.mongodb.core.aggregation.Aggregation.sort(
                                Sort.by(Sort.Direction.DESC, "version")),
                        org.springframework.data.mongodb.core.aggregation.Aggregation.group("key")
                                .first("$$ROOT").as("doc"),
                        org.springframework.data.mongodb.core.aggregation.Aggregation.replaceRoot("doc")
                );
        return mongoTemplate.aggregate(agg, "configadmin", ConfigAdmin.class).getMappedResults();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the counter document ID for a given (appName, key) pair.
     *
     * <p>Example: appName="payments", key="db.url"
     * → counter ID = "configadmin::payments::db.url"
     */
    private String counterKey(String appName, String key) {
        return COUNTER_PREFIX + appName + SEP + key;
    }

    /**
     * Atomically increments the per-(appName, key) counter and returns the new value.
     * Creates the counter document on first use (upsert).
     */
    private long incrementAndGetVersion(String appName, String key) {
        Query query = Query.query(Criteria.where("_id").is(counterKey(appName, key)));
        Update update = new Update().inc("seqValue", 1L);
        FindAndModifyOptions opts = FindAndModifyOptions.options()
                .returnNew(true)
                .upsert(true);

        Counter counter = mongoTemplate.findAndModify(query, update, opts, Counter.class);

        if (Objects.isNull(counter)) {
            throw new IllegalStateException(
                    "Failed to obtain version counter for appName=" + appName + ", key=" + key);
        }
        return counter.getSeqValue();
    }
}
