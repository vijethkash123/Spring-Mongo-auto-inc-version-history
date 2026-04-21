package com.example.mongospring.config;

import com.example.mongospring.model.ConfigAdmin;
import com.example.mongospring.model.StudentVij;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;

/**
 * Ensures critical MongoDB indexes are present at application startup.
 *
 * <p>The unique compound index on (entityId, version) in the "studentvij"
 * collection is the primary concurrency-safety guard: even if two threads
 * manage to compute the same version number for the same entityId, the
 * database will reject the second insert with a duplicate-key error,
 * preventing silent data corruption.
 *
 * <p>Index creation is idempotent — if the index already exists with the
 * same specification MongoDB is a no-op, so this is safe to run on every
 * startup.
 */
@Configuration
public class MongoIndexConfig {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public MongoIndexConfig(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @PostConstruct
    public void ensureIndexes() {
        // --- StudentVij: unique (entityId, version) ---
        IndexOperations studentVijOps = mongoTemplate.indexOps(StudentVij.class);
        studentVijOps.ensureIndex(
                new Index()
                        .on("entityId", Sort.Direction.ASC)
                        .on("version", Sort.Direction.ASC)
                        .unique()
                        .named("entityId_version_unique_idx")
        );

        // --- ConfigAdmin: unique (appName, key, version) ---
        IndexOperations configAdminOps = mongoTemplate.indexOps(ConfigAdmin.class);
        configAdminOps.ensureIndex(
                new Index()
                        .on("appName", Sort.Direction.ASC)
                        .on("key", Sort.Direction.ASC)
                        .on("version", Sort.Direction.ASC)
                        .unique()
                        .named("appName_key_version_unique_idx")
        );
    }
}
