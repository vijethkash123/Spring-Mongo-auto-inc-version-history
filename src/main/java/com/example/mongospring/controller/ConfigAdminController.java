package com.example.mongospring.controller;

import com.example.mongospring.model.ConfigAdmin;
import com.example.mongospring.service.ConfigAdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the append-only versioned ConfigAdmin resource.
 *
 * <p>Every write creates a new immutable version — existing documents are
 * never modified. Reads allow fetching the latest version, full history,
 * or the current effective config for an entire application.
 *
 * <p>URL structure uses two path variables to capture the composite key:
 * <pre>
 *   /configadmin/{appName}/{key}
 * </pre>
 *
 * <p>Examples:
 * <ul>
 *   <li>{@code POST /configadmin/payments/db.url}   — set config for "payments" app, key "db.url"</li>
 *   <li>{@code GET  /configadmin/payments/db.url}   — get latest value</li>
 *   <li>{@code GET  /configadmin/payments}           — get latest value for every key of "payments"</li>
 * </ul>
 */
@RestController
@RequestMapping("/configadmin")
public class ConfigAdminController {

    private final ConfigAdminService configAdminService;

    @Autowired
    public ConfigAdminController(ConfigAdminService configAdminService) {
        this.configAdminService = configAdminService;
    }

    // -------------------------------------------------------------------------
    // POST /configadmin/{appName}/{key}
    // Create the first version (or any subsequent version) for an (appName, key).
    // Body: { "data": "<config value>" }
    // -------------------------------------------------------------------------
    @PostMapping("/{appName}/{key}")
    public ResponseEntity<ConfigAdmin> createVersion(
            @PathVariable String appName,
            @PathVariable String key,
            @RequestBody Map<String, String> body) {

        String data = body.get("data");
        if (data == null || data.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ConfigAdmin created = configAdminService.createNewVersion(appName, key, data);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // -------------------------------------------------------------------------
    // PATCH /configadmin/{appName}/{key}
    // Append a new version for an existing (appName, key) — logical update.
    // Body: { "data": "<new config value>" }
    // -------------------------------------------------------------------------
    @PatchMapping("/{appName}/{key}")
    public ResponseEntity<ConfigAdmin> updateVersion(
            @PathVariable String appName,
            @PathVariable String key,
            @RequestBody Map<String, String> body) {

        String data = body.get("data");
        if (data == null || data.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        ConfigAdmin newVersion = configAdminService.createNewVersion(appName, key, data);
        return ResponseEntity.ok(newVersion);
    }

    // -------------------------------------------------------------------------
    // GET /configadmin/{appName}/{key}
    // Returns the latest version document for the given (appName, key).
    // -------------------------------------------------------------------------
    @GetMapping("/{appName}/{key}")
    public ResponseEntity<ConfigAdmin> getLatest(
            @PathVariable String appName,
            @PathVariable String key) {

        ConfigAdmin latest = configAdminService.getLatestVersion(appName, key);
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }

    // -------------------------------------------------------------------------
    // GET /configadmin/{appName}/{key}/history
    // Returns all versions for the given (appName, key) in ascending version order.
    // -------------------------------------------------------------------------
    @GetMapping("/{appName}/{key}/history")
    public ResponseEntity<List<ConfigAdmin>> getHistory(
            @PathVariable String appName,
            @PathVariable String key) {

        List<ConfigAdmin> history = configAdminService.getAllVersions(appName, key);
        if (history.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(history);
    }

    // -------------------------------------------------------------------------
    // GET /configadmin/{appName}
    // Returns the latest version of EVERY key for the given appName.
    // Gives a full snapshot of the current effective config for an application.
    // -------------------------------------------------------------------------
    @GetMapping("/{appName}")
    public ResponseEntity<List<ConfigAdmin>> getLatestByAppName(
            @PathVariable String appName) {

        List<ConfigAdmin> latest = configAdminService.getLatestByAppName(appName);
        if (latest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }
}
