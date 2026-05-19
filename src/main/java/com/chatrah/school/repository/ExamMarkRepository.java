package com.chatrah.school.repository;

import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Exam;
import com.chatrah.school.entity.ExamMark;
import com.chatrah.school.entity.Student;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for exam marks per student and subject.
 */
@ApplicationScoped
public class ExamMarkRepository implements PanacheRepository<ExamMark> {

    public List<ExamMark> findByExamAndStudent(Exam exam, Student student) {
        return list("exam = ?1 and student = ?2", exam, student);
    }

    public List<ExamMark> findByExamAndClassRoom(Exam exam, ClassRoom classRoom) {
        return list("exam = ?1 and classRoom = ?2", exam, classRoom);
    }

    public List<ExamMark> findByExamIdAndStudentId(Long examId, Long studentId) {
        return list("exam.id = ?1 and student.id = ?2", examId, studentId);
    }
}
