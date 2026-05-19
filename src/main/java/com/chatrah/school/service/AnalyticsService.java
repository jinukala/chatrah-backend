package com.chatrah.school.service;

import com.chatrah.school.dto.AttendanceAnalyticsDTO;
import com.chatrah.school.dto.FeeAnalyticsDTO;
import com.chatrah.school.dto.ExamAnalyticsDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Exam;
import com.chatrah.school.entity.ExamMark;
import com.chatrah.school.repository.AttendanceRepository;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.ExamMarkRepository;
import com.chatrah.school.repository.ExamRepository;
import com.chatrah.school.repository.StudentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class AnalyticsService {

    @Inject
    AttendanceRepository attendanceRepository;

    @Inject
    StudentRepository studentRepository;

    @Inject
    ClassRoomRepository classRoomRepository;

    @Inject
    ExamRepository examRepository;

    @Inject
    ExamMarkRepository examMarkRepository;

    @Inject
    FeeService feeService;

    // ------------- Attendance Analytics -------------

    @Transactional
    public AttendanceAnalyticsDTO computeAttendanceAnalytics() {
        long totalRecords = attendanceRepository.count();
        if (totalRecords == 0) {
            return new AttendanceAnalyticsDTO();
        }

        long totalPresent = attendanceRepository.count("present", true);

        AttendanceAnalyticsDTO dto = new AttendanceAnalyticsDTO();
        dto.setSchoolAverageAttendance((totalPresent * 100.0) / totalRecords);

        List<ClassRoom> classes = classRoomRepository.listAll();
        List<AttendanceAnalyticsDTO.ClassAttendance> perClass = new ArrayList<>();

        for (ClassRoom cr : classes) {
            long classTotal = attendanceRepository.count("classRoom", cr);
            if (classTotal == 0) continue;
            long classPresent = attendanceRepository.count("classRoom = ?1 and present = ?2", cr, true);
            double pct = (classPresent * 100.0) / classTotal;

            AttendanceAnalyticsDTO.ClassAttendance ca = new AttendanceAnalyticsDTO.ClassAttendance();
            ca.setClassName(cr.getClassName());
            ca.setSection(cr.getSection());
            ca.setAverageAttendancePercentage(pct);
            perClass.add(ca);
        }

        dto.setClassWise(perClass);
        return dto;
    }

    // ------------- Fee Analytics -------------

    @Transactional
    public FeeAnalyticsDTO computeFeeAnalytics() {
        List<ClassRoom> classes = classRoomRepository.listAll();
        FeeAnalyticsDTO dto = new FeeAnalyticsDTO();

        int schoolExpected = 0;
        int schoolCollected = 0;
        int schoolDue = 0;

        List<FeeAnalyticsDTO.ClassFeeStats> perClass = new ArrayList<>();

        for (ClassRoom cr : classes) {
            var students = studentRepository.find("classRoom", cr).list();
            int classExpected = 0;
            int classCollected = 0;
            int classDue = 0;

            for (var s : students) {
                var summary = feeService.computeFeeSummary(s.getId());
                classExpected += safe(summary.getTotalFee());
                classCollected += safe(summary.getTotalPaid());
                classDue += safe(summary.getDue());
            }

            FeeAnalyticsDTO.ClassFeeStats cs = new FeeAnalyticsDTO.ClassFeeStats();
            cs.setClassName(cr.getClassName());
            cs.setSection(cr.getSection());
            cs.setTotalExpected(classExpected);
            cs.setTotalCollected(classCollected);
            cs.setTotalDue(classDue);
            perClass.add(cs);

            schoolExpected += classExpected;
            schoolCollected += classCollected;
            schoolDue += classDue;
        }

        dto.setSchoolTotalExpected(schoolExpected);
        dto.setSchoolTotalCollected(schoolCollected);
        dto.setSchoolTotalDue(schoolDue);
        dto.setClassWise(perClass);
        return dto;
    }

    // ------------- Exam Analytics -------------

    @Transactional
    public ExamAnalyticsDTO computeExamAnalytics(Long examId) {
        Exam exam = examRepository.findById(examId);
        if (exam == null) {
            return new ExamAnalyticsDTO();
        }

        List<ExamMark> marks = examMarkRepository.find("exam", exam).list();
        ExamAnalyticsDTO dto = new ExamAnalyticsDTO();
        dto.setExamId(examId);
        dto.setExamName(exam.getName());

        if (marks.isEmpty()) {
            return dto;
        }

        int passCount = 0;
        int totalCount = marks.size();

        Map<String, List<ExamMark>> bySubject = marks.stream()
                .collect(Collectors.groupingBy(ExamMark::getSubject));

        List<ExamAnalyticsDTO.SubjectStats> subjectStats = new ArrayList<>();

        for (Map.Entry<String, List<ExamMark>> e : bySubject.entrySet()) {
            String subject = e.getKey();
            List<ExamMark> subjectMarks = e.getValue();

            double totalMarks = subjectMarks.stream()
                    .mapToInt(m -> safe(m.getMarks()))
                    .sum();
            double totalMax = subjectMarks.stream()
                    .mapToInt(m -> safe(m.getMaxMarks()))
                    .sum();

            double avg = totalMax > 0 ? (totalMarks * 100.0) / totalMax : 0.0;

            // consider pass if >= 35% of that subject max
            long subjectPass = subjectMarks.stream()
                    .filter(m -> safe(m.getMarks()) * 100.0 / safe(m.getMaxMarks(), 100) >= 35.0)
                    .count();

            double passPct = subjectMarks.isEmpty() ? 0.0 : (subjectPass * 100.0) / subjectMarks.size();
            passCount += subjectPass;

            ExamAnalyticsDTO.SubjectStats ss = new ExamAnalyticsDTO.SubjectStats();
            ss.setSubject(subject);
            ss.setAverageMarks(avg);
            ss.setPassPercentage(passPct);
            subjectStats.add(ss);
        }

        dto.setSubjects(subjectStats);
        dto.setOverallPassPercentage(totalCount > 0 ? (passCount * 100.0) / totalCount : 0.0);
        return dto;
    }

    private int safe(Integer v) { return v != null ? v : 0; }

    private int safe(Integer v, int defaultValue) { return v != null ? v : defaultValue; }
}
