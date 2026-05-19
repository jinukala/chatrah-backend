package com.chatrah.school.repository;

import com.chatrah.school.entity.Attendance;
import com.chatrah.school.entity.Attendance.Session;
import com.chatrah.school.entity.Student;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for attendance records and simple analytics.
 */
@ApplicationScoped
public class AttendanceRepository implements PanacheRepository<Attendance> {

    public Attendance findByStudentDateSession(Student student, LocalDate date, Session session) {
        return find("student = ?1 and date = ?2 and session = ?3",
                student, date, session).firstResult();
    }

    public List<Attendance> findByStudent(Student student) {
        return list("student", student);
    }

    public long countPresentForStudent(Student student) {
        return count("student = ?1 and present = true", student);
    }

    public long countTotalForStudent(Student student) {
        return count("student = ?1", student);
    }
}
