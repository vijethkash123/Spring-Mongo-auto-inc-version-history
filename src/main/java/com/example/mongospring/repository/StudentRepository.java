package com.example.mongospring.repository;

import com.example.mongospring.model.Student;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * Provides CRUD operations for the {@link Student} collection.
 * The ID type is {@code Long} to match the auto-increment sequence counter.
 */
@Repository
public interface StudentRepository extends MongoRepository<Student, Long> {
}
