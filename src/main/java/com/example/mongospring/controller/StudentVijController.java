package com.example.mongospring.controller;

import com.example.mongospring.model.StudentVij;
import com.example.mongospring.service.StudentVijService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for the append-only versioned StudentVij resource.
 *
 * <p>Every write creates a new immutable version — existing documents are
 * never modified. Reads let callers inspect the full history or retrieve
 * only the current (latest) version.
 *
 * Base URL: /studentvij
 */
@RestController
@RequestMapping("/studentvij")
public class StudentVijController {

    private final StudentVijService studentVijService;

    @Autowired
    public StudentVijController(StudentVijService studentVijService) {
        this.studentVijService = studentVijService;
    }

    // -------------------------------------------------------------------------
    // POST /studentvij/{entityId}
    // Create the first version of a student.
    // Body: { "name": "Alice" }
    // -------------------------------------------------------------------------
    @PostMapping("/{entityId}")
    public ResponseEntity<StudentVij> createVersion(
            @PathVariable String entityId,
            @RequestBody Map<String, String> body) {

        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        StudentVij created = studentVijService.createNewVersion(entityId, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // -------------------------------------------------------------------------
    // PATCH /studentvij/{entityId}
    // Append a new version for an existing student (logical update).
    // Body: { "name": "Alice Updated" }
    // -------------------------------------------------------------------------
    @PatchMapping("/{entityId}")
    public ResponseEntity<StudentVij> updateVersion(
            @PathVariable String entityId,
            @RequestBody Map<String, String> body) {

        String name = body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        StudentVij newVersion = studentVijService.createNewVersion(entityId, name);
        return ResponseEntity.ok(newVersion);
    }

    // -------------------------------------------------------------------------
    // GET /studentvij/{entityId}
    // Returns the latest version document for the given entityId.
    // -------------------------------------------------------------------------
    @GetMapping("/{entityId}")
    public ResponseEntity<StudentVij> getLatest(@PathVariable String entityId) {
        StudentVij latest = studentVijService.getLatestVersion(entityId);
        if (latest == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(latest);
    }

    // -------------------------------------------------------------------------
    // GET /studentvij/{entityId}/history
    // Returns all versions for the given entityId in ascending version order.
    // -------------------------------------------------------------------------
    @GetMapping("/{entityId}/history")
    public ResponseEntity<List<StudentVij>> getHistory(@PathVariable String entityId) {
        List<StudentVij> history = studentVijService.getAllVersions(entityId);
        if (history.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(history);
    }
}
