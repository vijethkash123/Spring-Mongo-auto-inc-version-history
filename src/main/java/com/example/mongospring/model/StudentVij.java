package com.example.mongospring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Append-only versioned student document stored in the "studentvij" collection.
 *
 * <p>Each mutation is represented as a NEW document with an incremented version.
 * Existing documents are never modified or deleted.
 *
 * <p>The compound index on (entityId, version) is declared unique to prevent
 * duplicate version numbers under the same entityId, even under concurrent load.
 * The index is enforced at the database level via {@link com.example.mongospring.config.MongoIndexConfig}.
 *
 * <p>Note: {@code @Version} is intentionally NOT used — version management is
 * handled explicitly through the "counters" collection.
 */
@Document(collection = "studentvij")
@CompoundIndex(name = "entityId_version_unique_idx", def = "{'entityId': 1, 'version': 1}", unique = true)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StudentVij {

    /** MongoDB-generated ObjectId — unique per document (per version). */
    @Id
    private String id;

    /** Business identifier shared across all versions of the same student. */
    private String entityId;

    /** Monotonically increasing version number scoped to this entityId (1, 2, 3 …). */
    private Long version;

    /** Student name at the time this version was created. */
    private String name;

    /** Wall-clock timestamp of when this version was inserted. */
    private Instant createdAt;
}
