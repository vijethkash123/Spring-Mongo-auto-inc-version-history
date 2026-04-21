# Mongo Spring - Architecture And Behavior

This project is a Spring Boot + MongoDB REST API that demonstrates two data management patterns:

1. Standard CRUD updates in-place.
2. Append-only versioning where every change creates a new immutable document.

It now contains three functional areas.

---

## 1) Student (Standard CRUD)

Base path: `/api/students`

Behavior:

- Uses classic create/read/update/delete operations.
- On create, assigns a custom auto-increment Long ID via `SequenceGeneratorService`.
- On update, modifies the existing student document in-place.

Endpoints:

| Method | URL | Description |
|---|---|---|
| `GET` | `/api/students` | List all students |
| `GET` | `/api/students/{id}` | Get one student by ID |
| `POST` | `/api/students` | Create student with generated Long ID |
| `PUT` | `/api/students/{id}` | Update existing student |
| `DELETE` | `/api/students/{id}` | Delete student |

---

## 2) StudentVij (Append-Only Versioned Student)

Base path: `/studentvij`

Core rule:

> Never mutate an existing version. Every write appends a new document.

How it works:

- `entityId` is the business identifier shared by all versions.
- `version` increases monotonically per `entityId`.
- `createdAt` captures insertion time for each version.
- Version numbers are allocated atomically with MongoDB `findAndModify` + `$inc` + `upsert`.
- A unique compound index on `(entityId, version)` blocks duplicate versions.

Endpoints:

| Method | URL | Description |
|---|---|---|
| `POST` | `/studentvij/{entityId}` | Create a new version (first call creates v1) |
| `PATCH` | `/studentvij/{entityId}` | Append next version |
| `GET` | `/studentvij/{entityId}` | Get latest version |
| `GET` | `/studentvij/{entityId}/history` | Get all versions for entity |

---

## 3) ConfigAdmin (New Append-Only Versioned App Config)

Base path: `/configadmin`

Purpose:

Stores versioned configuration values per application and key, with full immutable history.

Key model fields:

- `appName`: application namespace (example: `payments`).
- `key`: config key within the app (example: `db.url`, `feature.flag.x`).
- `data`: config value payload (stored as String).
- `version`: monotonically increasing version scoped to `(appName, key)`.
- `createdAt`: insertion timestamp.

Versioning details:

- Counter IDs are namespaced in `counters` as:
	`configadmin::<appName>::<key>`
- Version increments use atomic `findAndModify` with `$inc` and `upsert`.
- This ensures concurrency-safe unique version allocation across threads and instances.

Endpoints:

| Method | URL | Description |
|---|---|---|
| `POST` | `/configadmin/{appName}/{key}` | Create/append next version with request body `{ "data": "..." }` |
| `PATCH` | `/configadmin/{appName}/{key}` | Logical update by appending next version |
| `GET` | `/configadmin/{appName}/{key}` | Get latest version for one key |
| `GET` | `/configadmin/{appName}/{key}/history` | Get full version history for one key |
| `GET` | `/configadmin/{appName}` | Get latest version of every key for the app |

For `GET /configadmin/{appName}`, latest-per-key is resolved with Mongo aggregation:

1. Match by `appName`.
2. Sort by `version` descending.
3. Group by `key` and keep first document.
4. Replace root with that kept document.

---

## Indexing And Data Integrity

`MongoIndexConfig` programmatically ensures both critical unique indexes at startup:

- `studentvij`: `(entityId, version)` unique.
- `configadmin`: `(appName, key, version)` unique.

Why this matters:

- Prevents silent duplicate versions under race conditions.
- Keeps correctness enforced by MongoDB, not only by app logic.
- Startup index creation is idempotent and safe to run repeatedly.

---

## Shared Counters Collection

Collection: `counters`

Used by:

- `Student` ID sequence generator.
- `StudentVij` per-entity version counters.
- `ConfigAdmin` per-(appName,key) version counters.

All counters are incremented atomically using MongoDB primitives.

---

## Practical Takeaway

This codebase demonstrates when to choose each pattern:

- Use standard CRUD (`Student`) when only current state matters.
- Use append-only versioning (`StudentVij`, `ConfigAdmin`) when you need immutable history, auditability, and safer concurrent write behavior.



We need to run this to update counters collection:

Lets say there's existing data in another system with no version column curently, but appName and key pair is unique, does this existing code work -> if we add a version column, if there's no value we have to use 1 else updaate version inc

Does upsert handle this right now? - Upsert is just to check the counter collection. It checks if that counter key exists, if not start from 1. It is not upsert on other db collections, but only on counter collection that's used here


**No — the current upsert does not handle this correctly for migrated data.** Here's exactly why:

---

### How the current counter upsert works

```
$inc seqValue by 1, upsert=true
```

When the counter document **does not exist** in the `counters` collection, MongoDB creates it as:
```json
{ "_id": "configadmin::payments::db.url", "seqValue": 1 }
```

It **always starts at 1**, regardless of what's already sitting in the `configadmin` collection.

---

### The two failure modes with migrated data

**Scenario: existing doc migrated in, no version set**

| Step | Happens |
|------|---------|
| Migrate row → stored in `configadmin` with `version = null` | ✔ |
| Someone calls `PATCH /configadmin/payments/db.url` | → |
| Counter doesn't exist yet → upsert creates `seqValue = 1` | → |
| Code inserts new doc with `version = 1` | → |
| **Unique index on `(appName, key, version)`** | `version=null` doc + `version=1` doc both exist — silent data ambiguity |

**Scenario: migration sets version = 1 on existing row**

| Step | Happens |
|------|---------|
| Migration sets `version = 1` on existing doc | ✔ |
| Someone calls `PATCH` | → counter upserts to `seqValue = 1` → tries to insert `version = 1` |
| **Unique index rejects it** | `MongoWriteException: E11000 duplicate key` 💥 |

---

### Root cause

The `counters` collection and the `configadmin` collection are **completely independent**. The `$inc` upsert has no awareness of what version numbers already exist in the data.

---

### The fix: pre-seed the counters at migration time

You don't need to change the service code. You need a **one-time migration step** that syncs the counter state to match the existing data:

```javascript
// Run in mongosh after migrating existing rows into configadmin

db.configadmin.aggregate([
  {
    $group: {
      _id: { appName: "$appName", key: "$key" },
      maxVersion: { $max: "$version" }
    }
  }
]).forEach(entry => {
  const counterId = `configadmin::${entry._id.appName}::${entry._id.key}`;
  const maxVer = entry.maxVersion ?? 1;  // if version was null, treat as 1

  db.counters.updateOne(
    { _id: counterId },
    { $setOnInsert: { seqValue: maxVer } },
    { upsert: true }
  );

  // Also backfill version=1 on docs that were migrated without a version
  if (entry.maxVersion == null) {
    db.configadmin.updateMany(
      { appName: entry._id.appName, key: entry._id.key, version: null },
      { $set: { version: 1 } }
    );
  }
});
```

**`$setOnInsert`** is the key — it only writes `seqValue` when the counter is being created fresh, so if a counter already exists (from prior real writes) it's left untouched.

---

### After migration, the service code works correctly

```
Existing migrated data: version = 1  (backfilled)
Counter seed:           seqValue = 1

Next PATCH call:
  $inc seqValue → 2
  insert version = 2   ✔  no conflict, correct ordering
```

No changes needed to ConfigAdminService.java — the logic is correct for the steady state. The gap is purely the **initial counter bootstrap** for migrated data.


### Like in SQL we add a new column -> what is equivalent in Mongo? If I add version col to collection, does it reflect as null in existing docs?


Yes, exactly — and this is one of MongoDB's biggest differences from SQL.

---

### SQL behavior
```sql
ALTER TABLE config_admin ADD COLUMN version INT;
-- Every existing row immediately gets version = NULL
-- The column physically exists on every row after this command
```

---

### MongoDB behavior

There is **no `ALTER COLLECTION` command**. MongoDB is schemaless — documents in the same collection don't need the same fields.

If you start inserting new documents with a `version` field, **existing documents are completely untouched** — the field simply **doesn't exist** on them (not even as `null`).

```
// Existing migrated document (no version field at all)
{ "_id": "abc", "appName": "payments", "key": "db.url", "data": "jdbc://..." }

// New document inserted by the service
{ "_id": "xyz", "appName": "payments", "key": "db.url", "data": "jdbc://...", "version": 1 }
```

When you query or map these old docs to your Java `ConfigAdmin` class:
- Lombok/Jackson sees no `version` field in the BSON
- Java maps it to `null` (for `Long version`) or `0` (for `long version`)
- **No error, no exception** — it silently comes back as null/0

---

### Practical difference summary

| | SQL | MongoDB |
|---|---|---|
| Add new field | `ALTER TABLE` — affects all rows immediately | No command needed — just start writing it |
| Existing rows | Get `NULL` in the new column | Field is **absent** (not even null) from old docs |
| Query for missing field | `WHERE version IS NULL` | `{ version: null }` or `{ version: { $exists: false } }` |
| Backfill old rows | `UPDATE SET version = 1` | `db.collection.updateMany({ version: null }, { $set: { version: 1 } })` |

---

### Why this matters for your migration scenario

Because existing docs have the field **absent**, not `null`, your Spring model will silently deserialize `version` as `null`. This means:

```java
// getLatestVersion() sorts by "version" DESC
// A doc with no version field sorts BELOW version=1 in MongoDB
// So it won't accidentally appear as "latest" — but it's still invisible junk in the collection
```

The `updateMany` backfill in the migration script I showed earlier is still the right move:
```javascript
db.configadmin.updateMany(
  { version: null },           // catches both null and absent
  { $set: { version: 1 } }
);
```

`{ version: null }` in a MongoDB query matches **both** documents where `version` is explicitly `null` **and** documents where `version` doesn't exist at all — so one query handles both cases.


Let me walk through the script piece by piece, then assess gaps for your specific scenario.

---

### What the script does — step by step

```javascript
// STEP 1: Scan every existing doc in configadmin
//         Group by (appName, key), find the highest version in each group
db.configadmin.aggregate([
  {
    $group: {
      _id: { appName: "$appName", key: "$key" },
      maxVersion: { $max: "$version" }   // null if field is absent on all docs in group
    }
  }
])
```

For your scenario (no version column in source system), every group returns `maxVersion: null`.

---

```javascript
.forEach(entry => {
  const counterId = `configadmin::${entry._id.appName}::${entry._id.key}`;
  const maxVer = entry.maxVersion ?? 1;   // null → 1
```

`??` is nullish coalescing — if `maxVersion` came back as `null`, treat it as `1`.

---

```javascript
  // STEP 2: Create the counter document — only if it doesn't already exist
  db.counters.updateOne(
    { _id: counterId },
    { $setOnInsert: { seqValue: maxVer } },   // ← key part
    { upsert: true }
  );
```

`$setOnInsert` is the critical piece. It only writes `seqValue` **when the document is being created for the first time** (the "insert" side of upsert). If a counter already exists — e.g., from someone already calling the API — it is **left completely untouched**. This is intentional and safe.

---

```javascript
  // STEP 3: Backfill version=1 onto the migrated docs that had no version field
  if (entry.maxVersion == null) {
    db.configadmin.updateMany(
      { appName: entry._id.appName, key: entry._id.key, version: null },
      { $set: { version: 1 } }
    );
  }
});
```

This updates all docs for that `(appName, key)` where `version` is absent or null → sets it to `1`. After this, the collection is consistent — every doc has a real version number.

---

### After migration, the state is:

```
counters collection:
{ _id: "configadmin::payments::db.url", seqValue: 1 }

configadmin collection:
{ appName: "payments", key: "db.url", data: "...", version: 1 }
```

Next API `PATCH` call → `$inc seqValue → 2` → inserts `version: 2` ✔

---

### Is it enough for your scenario?

**Your scenario:** source system has unique `(appName, key)`, no version column, migrating into a fresh/empty `configadmin` collection.

✅ **Yes, the script is sufficient** for this case. Every pair has exactly one migrated doc, it gets `version = 1`, the counter is seeded to `seqValue = 1`, and subsequent API writes continue from `2` onwards.

---

### Where it would break (scenarios NOT in your description)

| Situation | Problem |
|-----------|---------|
| App was already receiving API traffic for `payments/db.url` (version 1, 2 exist) AND you're migrating in a new row for the same pair | Counter stays at 2 (correct), but the migrated row gets `version = 1` → **unique index collision** 💥 |
| Source system had multiple rows for same `(appName, key)` with different versions already | `$max` would catch the highest, counter seeded correctly, but you'd need to NOT backfill those — they already have version numbers |

Since your source system has **unique `(appName, key)`** with **no version column** and `configadmin` is **new/empty**, neither of these applies. The script is correct as-is.

---

### One practical caution

Run the script **with the Spring Boot app stopped** (or before first deploy). If the app is live and the script hasn't finished seeding counters, a concurrent `PATCH` could race in, create the counter at `seqValue = 1`, and then the script's `$setOnInsert` would silently no-op — leaving the migrated doc un-backfilled at `version = null`. No data loss, but you'd have an inconsistent doc that needs manual cleanup.



// Run in mongosh after migrating existing rows into configadmin

db.configadmin.aggregate([
  {
    $group: {
      _id: { appName: "$appName", key: "$key" },
      maxVersion: { $max: "$version" }
    }
  }
]).forEach(entry => {
  const counterId = `configadmin::${entry._id.appName}::${entry._id.key}`;
  const maxVer = entry.maxVersion ?? 1;  // if version was null, treat as 1

  db.counters.updateOne(
    { _id: counterId },
    { $setOnInsert: { seqValue: maxVer } },
    { upsert: true }
  );

  // Also backfill version=1 on docs that were migrated without a version
  if (entry.maxVersion == null) {
    db.configadmin.updateMany(
      { appName: entry._id.appName, key: entry._id.key, version: null },
      { $set: { version: 1 } }
    );
  }
});
