package com.chatrah.school.service;

import com.chatrah.school.dto.ExamDTO;
import com.chatrah.school.dto.ExamUploadRequestDTO;
import com.chatrah.school.dto.ExamMarkRowDTO;
import com.chatrah.school.dto.StudentExamResultDTO;
import com.chatrah.school.dto.AttendanceMarkRequestDTO;
import com.chatrah.school.dto.StudentAttendanceRowDTO;
import com.chatrah.school.dto.AttendanceSummaryDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Student;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.StudentRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExamAttendanceTest {

    @Inject ExamService examService;
    @Inject AttendanceService attendanceService;
    @Inject ClassRoomRepository classRoomRepository;
    @Inject StudentRepository studentRepository;

    static Long classId;
    static Long studentId;
    static Long examId;

    @BeforeEach
    @Transactional
    void setup() {
        ClassRoom cr = new ClassRoom();
        cr.setClassName("8");
        cr.setSection("A");
        cr.setSubjects("Maths, Science");
        classRoomRepository.persist(cr);
        classId = cr.getId();

        Student s = new Student();
        s.setName("Exam Test Student");
        s.setRollNo(1);
        s.setClassRoom(cr);
        s.setIsHosteller(false);
        s.setIsTransportUser(false);
        s.setIitNeetOpted(false);
        studentRepository.persist(s);
        studentId = s.getId();
    }

    @Test
    @Order(1)
    void testCreateExam() {
        ExamDTO dto = new ExamDTO();
        dto.setName("Unit Test Exam");
        dto.setAcademicYear("2025-26");
        dto.setDescription("Test exam");
        ExamDTO result = examService.createExam(dto, 1L);
        assertNotNull(result.getId());
        examId = result.getId();
    }

    @Test
    @Order(2)
    void testListExams() {
        var list = examService.listAll();
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(e -> e.getName().equals("Unit Test Exam")));
    }

    @Test
    @Order(3)
    void testUploadMarks() {
        ExamUploadRequestDTO req = new ExamUploadRequestDTO();
        req.setExamId(examId);
        req.setClassId(classId);
        req.setSubject("Maths");
        ExamMarkRowDTO row = new ExamMarkRowDTO();
        row.setStudentId(studentId);
        row.setMarks(85);
        row.setMaxMarks(100);
        req.setRows(List.of(row));
        examService.uploadExamMarks(req);
    }

    @Test
    @Order(4)
    void testGetStudentResult() {
        StudentExamResultDTO result = examService.getStudentResult(examId, studentId);
        assertEquals("Exam Test Student", result.getStudentName());
        assertTrue(result.getTotalMarksObtained() >= 85);
        assertTrue(result.getSubjects().size() >= 1);
    }

    @Test
    @Order(5)
    void testMarkAttendance() {
        AttendanceMarkRequestDTO req = new AttendanceMarkRequestDTO();
        req.setClassId(classId);
        req.setDate(java.time.LocalDate.parse("2026-05-17"));
        req.setSession("MORNING");
        StudentAttendanceRowDTO row = new StudentAttendanceRowDTO();
        row.setStudentId(studentId);
        row.setPresent(true);
        req.setStudents(List.of(row));
        attendanceService.markAttendance(req, 1L);
    }

    @Test
    @Order(6)
    void testGetAttendanceSummary() {
        AttendanceSummaryDTO summary = attendanceService.getStudentSummary(studentId);
        assertNotNull(summary);
        assertTrue(summary.getTotalDays() >= 1);
        assertTrue(summary.getPresentDays() >= 1);
    }

    @Test
    @Order(7)
    void testMarkAbsent() {
        AttendanceMarkRequestDTO req = new AttendanceMarkRequestDTO();
        req.setClassId(classId);
        req.setDate(java.time.LocalDate.parse("2026-05-18"));
        req.setSession("MORNING");
        StudentAttendanceRowDTO row = new StudentAttendanceRowDTO();
        row.setStudentId(studentId);
        row.setPresent(false);
        req.setStudents(List.of(row));
        attendanceService.markAttendance(req, 1L);

        AttendanceSummaryDTO summary = attendanceService.getStudentSummary(studentId);
        assertTrue(summary.getTotalDays() >= 2);
        assertTrue(summary.getAbsentDays() >= 1);
    }
}
