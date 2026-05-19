package com.chatrah.school.repository;

import com.chatrah.school.entity.Student;
import com.chatrah.school.entity.StudentCourseEnrollment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository linking students to advanced courses.
 */
@ApplicationScoped
public class StudentCourseEnrollmentRepository implements PanacheRepository<StudentCourseEnrollment> {

    public List<StudentCourseEnrollment> findByStudent(Student student) {
        return list("student", student);
    }

    public List<StudentCourseEnrollment> findByStudentId(Long studentId) {
        return list("student.id", studentId);
    }
}
