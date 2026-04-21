package com.example.mongospring.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Represents a student document stored in the "students" collection.
 * The {@code id} field is a Long that is populated by
 * {@link com.example.mongospring.service.SequenceGeneratorService}
 * before saving — giving us predictable, human-readable IDs.
 *
 * SEQUENCE_NAME is annotated @Transient so it is NOT persisted into MongoDB.
 */
@Document(collection = "students")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Student {

    /**
     * Logical name of the counter document inside the counters
     * collection that tracks IDs for this collection.
     */
    @Transient
    public static final String SEQUENCE_NAME = "users_sequence";

    @Id
    private Long id;

    private String firstName;
    private String lastName;
    private String email;
}
