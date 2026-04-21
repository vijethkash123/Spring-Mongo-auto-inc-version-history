package com.example.mongospring.controller;

import com.example.mongospring.model.Student;
import com.example.mongospring.repository.StudentRepository;
import com.example.mongospring.service.SequenceGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing CRUD endpoints for the Student resource.
 *
 * <p>On POST, the ID is populated via {@link SequenceGeneratorService}
 * before the document is saved, producing a predictable Long sequence
 * (1, 2, 3 …) instead of MongoDB's default ObjectId string.
 *
 * Base URL: /api/students
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

    private final StudentRepository studentRepository;
    private final SequenceGeneratorService sequenceGenerator;

    @Autowired
    public StudentController(StudentRepository studentRepository,
                             SequenceGeneratorService sequenceGenerator) {
        this.studentRepository = studentRepository;
        this.sequenceGenerator = sequenceGenerator;
    }

    // -------------------------------------------------------------------------
    // GET /api/students  — retrieve all students
    // -------------------------------------------------------------------------
    @GetMapping
    public ResponseEntity<List<Student>> getAllStudents() {
        return ResponseEntity.ok(studentRepository.findAll());
    }

    // -------------------------------------------------------------------------
    // GET /api/students/{id}  — retrieve one student by ID
    // -------------------------------------------------------------------------
    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudentById(@PathVariable Long id) {
        return studentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // POST /api/students  — create a new student with an auto-incremented ID
    // -------------------------------------------------------------------------
    @PostMapping
    public ResponseEntity<Student> createStudent(@RequestBody Student student) {
        // Generate and assign the next sequence value before persisting
        student.setId(sequenceGenerator.generateSequence(Student.SEQUENCE_NAME));
        Student saved = studentRepository.save(student);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // -------------------------------------------------------------------------
    // PUT /api/students/{id}  — update an existing student
    // -------------------------------------------------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable Long id,
                                                 @RequestBody Student updatedStudent) {
        return studentRepository.findById(id)
                .map(existing -> {
                    existing.setFirstName(updatedStudent.getFirstName());
                    existing.setLastName(updatedStudent.getLastName());
                    existing.setEmail(updatedStudent.getEmail());
                    return ResponseEntity.ok(studentRepository.save(existing));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/students/{id}  — delete a student by ID
    // -------------------------------------------------------------------------
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable Long id) {
        if (!studentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        studentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
