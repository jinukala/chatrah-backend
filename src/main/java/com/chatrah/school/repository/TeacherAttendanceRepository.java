// src/main/java/com/chatrah/school/repository/TeacherAttendanceRepository.java
package com.chatrah.school.repository;

import com.chatrah.school.entity.TeacherAttendance;
import com.chatrah.school.entity.Teacher;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class TeacherAttendanceRepository implements PanacheRepository<TeacherAttendance> {

    public TeacherAttendance findByTeacherAndDate(Teacher teacher, LocalDate date) {
        return find("teacher = ?1 and date = ?2", teacher, date).firstResult();
    }

    public List<TeacherAttendance> findForTeacher(Teacher teacher) {
        return list("teacher", teacher);
    }
}
