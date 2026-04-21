package com.example.mongospring.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Append-only versioned configuration document stored in the "configadmin" collection.
 *
 * <p>Each mutation is represented as a NEW document with an incremented version.
 * Existing documents are never modified or deleted.
 *
 * <p>Business uniqueness rules:
 * <ul>
 *   <li>{@code appName} alone is NOT unique — one app can have many keys.</li>
 *   <li>{@code key} alone is NOT unique — the same key can exist across apps.</li>
 *   <li>The combination {@code (appName, key)} identifies a single logical config entry.</li>
 *   <li>The combination {@code (appName, key, version)} is globally unique — enforced by the DB index.</li>
 * </ul>
 *
 * <p>The version counter is scoped per {@code (appName, key)} pair and is managed
 * via the shared "counters" collection using an atomic {@code $inc} + upsert.
 */
@Document(collection = "configadmin")
@CompoundIndexes({
        @CompoundIndex(
                name = "appName_key_version_unique_idx",
                def = "{'appName': 1, 'key': 1, 'version': 1}",
                unique = true
        )
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConfigAdmin {

    /** MongoDB-generated ObjectId — unique per document (per version). */
    @Id
    private String id;

    /** Application name that owns this config entry. */
    private String appName;

    /** Config key within the application (e.g. "db.url", "feature.flag.x"). */
    private String key;

    /**
     * The config value / payload for this (appName, key) at this version.
     * Stored as a plain String — use JSON string for structured data.
     */
    private String data;

    /**
     * Monotonically increasing version number scoped to the (appName, key) pair.
     * Starts at 1 and increments by 1 on every logical update.
     */
    private Long version;

    /** Wall-clock timestamp of when this version was inserted. */
    private Instant createdAt;
}
