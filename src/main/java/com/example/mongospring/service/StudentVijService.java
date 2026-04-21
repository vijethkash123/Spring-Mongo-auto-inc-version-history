package com.example.mongospring.service;

import com.example.mongospring.model.Counter;
import com.example.mongospring.model.StudentVij;
import org.springframework.beans.factory.annotation.Autowired;
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
 * Handles append-only versioning for the StudentVij collection.
 *
 * <p><b>Concurrency safety:</b> version increments are performed with a single
 * atomic {@code findAndModify} call ({@code $inc} + upsert). MongoDB guarantees
 * that only one caller will receive each version number, so duplicate versions
 * cannot be inserted even under heavy concurrent load or across multiple
 * application instances.
 */
@Service
public class StudentVijService {

    private final MongoTemplate mongoTemplate;

    @Autowired
    public StudentVijService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Atomically increments the version counter for {@code entityId} and inserts
     * a brand-new StudentVij document — existing documents are never touched.
     *
     * @param entityId business identifier for the student
     * @param name     student name for this version
     * @return the newly inserted {@link StudentVij} document
     */
    public StudentVij createNewVersion(String entityId, String name) {
        long nextVersion = incrementAndGetVersion(entityId);

        StudentVij newVersion = StudentVij.builder()
                .entityId(entityId)
                .version(nextVersion)
                .name(name)
                .createdAt(Instant.now())
                .build();

        return mongoTemplate.insert(newVersion);
    }

    /**
     * Returns all versions for a given entityId, ordered by version ascending.
     *
     * @param entityId business identifier for the student
     * @return list of all {@link StudentVij} documents for that entityId
     */
    public List<StudentVij> getAllVersions(String entityId) {
        Query query = Query.query(Criteria.where("entityId").is(entityId))
                .with(org.springframework.data.domain.Sort.by("version"));
        return mongoTemplate.find(query, StudentVij.class);
    }

    /**
     * Returns the latest version document for a given entityId.
     *
     * @param entityId business identifier for the student
     * @return the most recent {@link StudentVij} document, or {@code null} if none exists
     */
    public StudentVij getLatestVersion(String entityId) {
        Query query = Query.query(Criteria.where("entityId").is(entityId))
                .with(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "version"))
                .limit(1);
        return mongoTemplate.findOne(query, StudentVij.class);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Atomically increments the per-entityId counter using {@code $inc} and
     * returns the updated (post-increment) value.
     *
     * <p>Using {@code upsert = true} means the counter is initialised to 1 the
     * first time this method is called for a given entityId — no separate
     * initialisation step is required.
     *
     * @param entityId the counter key
     * @return the next version number (starts at 1)
     */
    private long incrementAndGetVersion(String entityId) {
        Query query = Query.query(Criteria.where("_id").is(entityId));

        Update update = new Update().inc("seqValue", 1L);

        FindAndModifyOptions opts = FindAndModifyOptions.options()
                .returnNew(true)   // return the document AFTER the increment
                .upsert(true);     // create the counter if it doesn't exist yet

        Counter counter = mongoTemplate.findAndModify(query, update, opts, Counter.class);

        // findAndModify with upsert+returnNew should never return null here,
        // but guard defensively to avoid a NullPointerException.
        if (Objects.isNull(counter)) {
            throw new IllegalStateException(
                    "Failed to obtain version counter for entityId: " + entityId);
        }
        return counter.getSeqValue();
    }
}

// 1. entity_id -> unique
// 2. entity_id -> duplicated  
// Backfill:
// a , a, b
// 2, 1, 1

// a, b, c ->
// a, a, b, v, c, c, c ->
