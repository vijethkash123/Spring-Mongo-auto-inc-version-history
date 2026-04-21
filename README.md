# Mongo Spring — Setup & Run Guide

Spring Boot + MongoDB application with auto-increment IDs and append-only versioning.

---

## Prerequisites

| Tool | Version |
|------|---------|
| Java | 21+ |
| Maven | 3.8+ |
| Docker Desktop | Any recent version |

---

## Step 1 — Start MongoDB

```bash
docker run -d \
  --name mongodb \
  -p 27017:27017 \
  mongodb/mongodb-community-server:latest
```

Verify it is running:

```bash
docker ps
```

Expected output shows `0.0.0.0:27017->27017/tcp` in the PORTS column.

---

## Step 2 — Start MongoDB Web UI (mongo-express)

```bash
docker run -d \
  --name mongo-express \
  -p 8081:8081 \
  -e ME_CONFIG_MONGODB_URL="mongodb://host.docker.internal:27017" \
  -e ME_CONFIG_BASICAUTH_USERNAME="" \
  -e ME_CONFIG_BASICAUTH_PASSWORD="" \
  mongo-express
```

Open in your browser: **http://localhost:8081**

| Field    | Value   |
|----------|---------|
| Username | `admin` |
| Password | `pass`  |

> `host.docker.internal` is how the Docker container reaches MongoDB on your Mac's localhost. This works automatically with Docker Desktop.

---

## Step 3 — Run the Spring Boot Application

```bash
cd "Mongo Spring"
mvn spring-boot:run
```

The app starts on **http://localhost:8080**

You should see this in the terminal when it is ready:

```
Started MongoSpringApplication in X.XXX seconds
```

---

## Step 4 — Verify Everything Is Working

```bash
# List all students (returns empty array on fresh start)
curl http://localhost:8080/api/students

# Create a versioned StudentVij document
curl -X POST http://localhost:8080/studentvij/stu-001 \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice"}'
```

Then refresh **http://localhost:8081** to see the `studentdb` database and its collections.

---

## API Reference

### Student API — `/api/students`

| Method | URL | Description |
|--------|-----|-------------|
| `GET` | `/api/students` | List all students |
| `GET` | `/api/students/{id}` | Get student by ID |
| `POST` | `/api/students` | Create student (auto-increments ID) |
| `PUT` | `/api/students/{id}` | Update student |
| `DELETE` | `/api/students/{id}` | Delete student |

**POST / PUT body:**
```json
{
  "firstName": "Jane",
  "lastName": "Doe",
  "email": "jane@example.com"
}
```

---

### StudentVij API — `/studentvij` (Append-Only Versioning)

| Method | URL | Description |
|--------|-----|-------------|
| `POST` | `/studentvij/{entityId}` | Create version 1 for a new student |
| `PATCH` | `/studentvij/{entityId}` | Append a new version (logical update) |
| `GET` | `/studentvij/{entityId}` | Get the latest version |
| `GET` | `/studentvij/{entityId}/history` | Get all versions in order |

**POST / PATCH body:**
```json
{
  "name": "Alice"
}
```

---

## Postman Collection

Import `VIJ_MONGO.postman_collection.json` into Postman to get pre-built requests with automated test scripts for all endpoints.

**Postman → Import → select the file**

---

## MongoDB Collections

| Collection | Purpose |
|-----------|---------|
| `students` | Original student documents with Long auto-increment IDs |
| `studentvij` | Append-only versioned student documents |
| `counters` | Shared sequence/version counters for both features |

---

## Stop Everything

```bash
docker stop mongodb mongo-express
docker rm mongodb mongo-express
```

---

## Restart After a Machine Reboot

```bash
# 1. Start MongoDB
docker start mongodb

# 2. Start mongo-express
docker start mongo-express

# 3. Run the app
cd "Mongo Spring"
mvn spring-boot:run
```
