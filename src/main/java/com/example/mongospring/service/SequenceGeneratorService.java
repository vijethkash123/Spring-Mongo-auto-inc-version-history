package com.example.mongospring.service;

import com.example.mongospring.model.Counter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static org.springframework.data.mongodb.core.FindAndModifyOptions.options;
import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

/**
 * Generates auto-incrementing IDs using the shared "counters" collection.
 *
 * <p>Each call to {@link #generateSequence(String)} atomically finds the
 * counter document identified by {@code seqName}, increments its
 * {@code seqValue} field by 1, and returns the new value.
 * If no counter document exists yet it is created on-the-fly (upsert).
 *
 * <p>Multiple collections can share this service — just use a unique
 * sequence name for each (e.g. "users_sequence", "courses_sequence").
 */
@Service
public class SequenceGeneratorService {

    private final MongoOperations mongoOperations;

    @Autowired
    public SequenceGeneratorService(MongoOperations mongoOperations) {
        this.mongoOperations = mongoOperations;
    }

    /**
     * Returns the next sequence value for the given sequence name.
     *
     * @param seqName the logical name of the counter (e.g. {@code Student.SEQUENCE_NAME})
     * @return the next auto-incremented ID
     */
    public long generateSequence(String seqName) {
        Counter counter = mongoOperations.findAndModify(
                query(where("_id").is(seqName)),
                new Update().inc("seqValue", 1),
                options().returnNew(true).upsert(true),
                Counter.class
        );
        return !Objects.isNull(counter) ? counter.getSeqValue() : 1;
    }
}
