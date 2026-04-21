package com.example.mongospring.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Tracks the latest version number for each entityId.
 *
 * <p>Stored in the "counters" collection. One document per entityId:
 * <pre>
 *   { "_id": "stu-001", "seqValue": 3 }
 * </pre>
 *
 * <p>Increments are performed atomically via MongoTemplate.findAndModify
 * with upsert=true so the document is created on first use.
 */
@Document(collection = "counters")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Counter {

    /** entityId of the student this counter belongs to. */
    @Id
    private String id;

    /** The current (latest) version number for this entityId. */
    private Long seqValue;
}
