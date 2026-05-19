package com.chatrah.school.service;

import com.chatrah.school.dto.ExamDTO;
import com.chatrah.school.dto.ExamUploadRequestDTO;
import com.chatrah.school.dto.ExamMarkRowDTO;
import com.chatrah.school.dto.StudentExamResultDTO;
import com.chatrah.school.entity.*;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.ExamMarkRepository;
import com.chatrah.school.repository.ExamRepository;
import com.chatrah.school.repository.StudentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages exams, marks upload, and student result views.
 */
@ApplicationScoped
public class ExamService {

    @Inject
    ExamRepository examRepository;

    @Inject
    ExamMarkRepository examMarkRepository;

    @Inject
    StudentRepository studentRepository;

    @Inject
    ClassRoomRepository classRoomRepository;

    public List<ExamDTO> listAll() {
        return examRepository.listAll().stream().map(e -> {
            ExamDTO dto = new ExamDTO();
            dto.setId(e.getId());
            dto.setName(e.getName());
            dto.setAcademicYear(e.getAcademicYear());
            dto.setDescription(e.getDescription());
            dto.setCreatedAt(e.getCreatedAt());
            dto.setCreatedBy(e.getPublished() != null && e.getPublished() ? 1L : 0L); // reuse createdBy as published flag for frontend
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public ExamDTO createExam(ExamDTO dto, Long createdBy) {
        Exam exam = new Exam();
        exam.setName(dto.getName());
        exam.setAcademicYear(dto.getAcademicYear());
        exam.setDescription(dto.getDescription());
        exam.setCreatedBy(createdBy);
        examRepository.persist(exam);
        dto.setId(exam.getId());
        return dto;
    }

    /**
     * Upload marks from an Excel-parsed structure:
     * - examId
     * - classId
     * - subject
     * - rows: [ { studentId, marksObtained, maxMarks }, ... ]
     */
    @Transactional
    public void uploadExamMarks(ExamUploadRequestDTO request) {
        Exam exam = examRepository.findById(request.getExamId());
        if (exam == null) throw new NotFoundException("Exam not found");

        ClassRoom classRoom = classRoomRepository.findById(request.getClassId());
        if (classRoom == null) throw new NotFoundException("Class not found");

        String subject = request.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject is required");
        }

        if (request.getRows() == null || request.getRows().isEmpty()) {
            return;
        }

        for (ExamMarkRowDTO row : request.getRows()) {
            Long studentId = row.getStudentId();
            if (studentId == null) continue;

            Student student = studentRepository.findById(studentId);
            if (student == null) continue;

            ExamMark mark = new ExamMark();
            mark.setExam(exam);
            mark.setStudent(student);
            mark.setClassRoom(classRoom);
            mark.setSubject(subject);
            mark.setMarks(row.getMarks());
            // if maxMarks is null in row, default to 100
            Integer max = row.getMaxMarks() != null ? row.getMaxMarks() : 100;
            mark.setMaxMarks(max);

            examMarkRepository.persist(mark);
        }
    }

    public StudentExamResultDTO getStudentResult(Long examId, Long studentId) {
        Exam exam = examRepository.findById(examId);
        if (exam == null) throw new NotFoundException("Exam not found");
        Student student = studentRepository.findById(studentId);
        if (student == null) throw new NotFoundException("Student not found");

        List<ExamMark> marks = examMarkRepository.findByExamIdAndStudentId(examId, studentId);
        int totalObtained = marks.stream().mapToInt(ExamMark::getMarks).sum();
        int totalMax = marks.stream().mapToInt(ExamMark::getMaxMarks).sum();

        StudentExamResultDTO dto = new StudentExamResultDTO();
        dto.setExamId(exam.getId());
        dto.setExamName(exam.getName());
        dto.setStudentId(student.getId());
        dto.setStudentName(student.getName());
        dto.setTotalMarksObtained(totalObtained);
        dto.setTotalMaxMarks(totalMax);
        dto.setPercentage(totalMax > 0 ? (totalObtained * 100.0) / totalMax : 0.0);
        dto.setSubjects(marks.stream().map(m -> {
            StudentExamResultDTO.SubjectMark sm = new StudentExamResultDTO.SubjectMark();
            sm.setSubject(m.getSubject());
            sm.setMarks(m.getMarks());
            sm.setMaxMarks(m.getMaxMarks());
            return sm;
        }).collect(Collectors.toList()));
        return dto;
    }

    public Exam findById(Long examId) {
        return examRepository.findById(examId);
    }

    public List<Exam> listAllEntities() {
        return examRepository.listAll();
    }
}
