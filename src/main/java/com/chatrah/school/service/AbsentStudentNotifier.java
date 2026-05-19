package com.chatrah.school.service;

import com.chatrah.school.entity.Attendance;
import com.chatrah.school.entity.Student;
import com.chatrah.school.repository.AttendanceRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.util.List;
import java.util.logging.Logger;

/**
 * Scheduled job that sends SMS to parents of students marked absent today.
 * Runs daily at 9 PM (configurable via attendance.alert.cron).
 *
 * Enable by setting ATTENDANCE_ALERT_ENABLED=true and SMS_ENABLED=true.
 */
@ApplicationScoped
public class AbsentStudentNotifier {

    private static final Logger LOG = Logger.getLogger(AbsentStudentNotifier.class.getName());

    @Inject
    AttendanceRepository attendanceRepository;

    @Inject
    SmsService smsService;

    @ConfigProperty(name = "attendance.alert.enabled", defaultValue = "false")
    boolean alertEnabled;

    @Scheduled(cron = "{attendance.alert.cron}")
    void notifyAbsentStudentParents() {
        if (!alertEnabled) {
            return;
        }

        LocalDate today = LocalDate.now();
        List<Attendance> absentRecords = attendanceRepository
                .list("date = ?1 and present = false", today);

        LOG.info("Absent student alert: found " + absentRecords.size() + " absent records for " + today);

        for (Attendance record : absentRecords) {
            Student student = record.getStudent();
            if (student == null) continue;

            String parentMobile = student.getParentMobile();
            if (parentMobile == null || parentMobile.isBlank()) {
                LOG.fine("No parent mobile for student: " + student.getName());
                continue;
            }

            String parentName = student.getParentName() != null ? student.getParentName() : "Parent";
            smsService.sendAbsenceAlert(parentMobile, parentName, student.getName(), today.toString());
        }
    }
}
