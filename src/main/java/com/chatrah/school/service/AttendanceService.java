package com.chatrah.school.service;

import com.chatrah.school.dto.AttendanceMarkRequestDTO;
import com.chatrah.school.dto.StudentAttendanceRowDTO;
import com.chatrah.school.dto.AttendanceSummaryDTO;
import com.chatrah.school.entity.Attendance;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Student;
import com.chatrah.school.repository.AttendanceRepository;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.StudentRepository;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDate;
import java.util.List;

/**
 * Handles marking and summarizing attendance.
 */
@ApplicationScoped
public class AttendanceService {

    @Inject
    AttendanceRepository attendanceRepository;

    @Inject
    StudentRepository studentRepository;

    @Inject
    ClassRoomRepository classRoomRepository;

    /**
     * Mark attendance for a given class, date, and session.
     * Uses AttendanceMarkRequestDTO:
     *  - session: String ("MORNING" / "AFTERNOON")
     *  - students: List<StudentAttendanceRowDTO> { studentId, present }
     */
    @Transactional
    public void markAttendance(AttendanceMarkRequestDTO request, Long markedByUserId) {
        ClassRoom cr = classRoomRepository.findById(request.getClassId());
        if (cr == null) {
            throw new NotFoundException("Class not found");
        }

        LocalDate date = request.getDate();
        if (date == null) {
            date = LocalDate.now();
        }

        if (request.getSession() == null) {
            throw new IllegalArgumentException("Session is required (MORNING/AFTERNOON)");
        }

        Attendance.Session session = Attendance.Session.valueOf(
                request.getSession().toUpperCase()
        );

        List<StudentAttendanceRowDTO> students = request.getStudents();
        if (students == null || students.isEmpty()) {
            return; // nothing to mark
        }

        for (StudentAttendanceRowDTO row : students) {
            Long studentId = row.getStudentId();
            Boolean present = row.getPresent();

            if (studentId == null) continue;

            Student student = studentRepository.findById(studentId);
            if (student == null) continue;

            Attendance existing =
                    attendanceRepository.findByStudentDateSession(student, date, session);

            Attendance att = existing != null ? existing : new Attendance();
            att.setDate(date);
            att.setSession(session);
            att.setStudent(student);
            att.setClassRoom(cr);
            att.setPresent(present != null ? present : Boolean.FALSE);
            att.setMarkedByUserId(markedByUserId);

            attendanceRepository.persist(att);
        }
    }

    /**
     * Get summarized attendance for a student: total, present, absent, percentage.
     */
    @CacheResult(cacheName = "attendance-summary")
    public AttendanceSummaryDTO getStudentSummary(Long studentId) {
        Student s = studentRepository.findById(studentId);
        if (s == null) throw new NotFoundException("Student not found");

        long total = attendanceRepository.countTotalForStudent(s);
        long present = attendanceRepository.countPresentForStudent(s);

        AttendanceSummaryDTO dto = new AttendanceSummaryDTO();
        dto.setTargetId(studentId);
        dto.setTotalDays((int) total);
        dto.setPresentDays((int) present);
        dto.setAbsentDays((int) (total - present));
        dto.setAttendancePercentage(total > 0 ? (present * 100.0) / total : 0.0);
        return dto;
    }
}
