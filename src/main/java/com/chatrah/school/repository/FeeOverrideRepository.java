package com.chatrah.school.repository;

import com.chatrah.school.entity.FeeOverride;
import com.chatrah.school.entity.Student;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for per-student fee overrides (concessions).
 */
@ApplicationScoped
public class FeeOverrideRepository implements PanacheRepository<FeeOverride> {

    public FeeOverride findByStudent(Student student) {
        return find("student", student).firstResult();
    }

    public FeeOverride findByStudentId(Long studentId) {
        return find("student.id", studentId).firstResult();
    }
}
