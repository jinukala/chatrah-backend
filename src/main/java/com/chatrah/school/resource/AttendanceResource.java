package com.chatrah.school.resource;

import com.chatrah.school.dto.AttendanceMarkRequestDTO;
import com.chatrah.school.dto.AttendanceSummaryDTO;
import com.chatrah.school.entity.Attendance;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.AttendanceRepository;
import com.chatrah.school.repository.UserRepository;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.AttendanceService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/attendance")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AttendanceResource {

    @Inject
    AttendanceService attendanceService;

    @Inject
    AttendanceRepository attendanceRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/mark")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public void markAttendance(AttendanceMarkRequestDTO request) {
        Long markedByUserId = resolveUserIdFromJwt();
        attendanceService.markAttendance(request, markedByUserId);
    }

    @GET
    @Path("/student/{studentId}/summary")
    @RolesAllowed({SecurityRoles.STUDENT, SecurityRoles.TEACHER,
            SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public AttendanceSummaryDTO getStudentSummary(@PathParam("studentId") Long studentId) {
        return attendanceService.getStudentSummary(studentId);
    }

    @GET
    @Path("/class/{classId}")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public List<Map<String, Object>> getClassAttendance(@PathParam("classId") Long classId, @QueryParam("date") String dateStr) {
        java.time.LocalDate date = dateStr != null ? java.time.LocalDate.parse(dateStr) : java.time.LocalDate.now();
        List<Attendance> records = attendanceRepository.list("classRoom.id = ?1 and date = ?2", classId, date);
        return records.stream().map(a -> Map.<String, Object>of(
                "studentId", a.getStudent().getId(),
                "studentName", a.getStudent().getName(),
                "present", a.getPresent()
        )).collect(Collectors.toList());
    }

    @GET
    @Path("/student/me")
    @RolesAllowed({SecurityRoles.STUDENT})
    public List<Map<String, Object>> getMyAttendance(@QueryParam("month") Integer month, @QueryParam("year") Integer year) {
        User user = userRepository.findById(Long.parseLong(jwt.getSubject()));
        Long studentId = user.getStudentId();

        List<Attendance> records;
        if (month != null && year != null) {
            records = attendanceRepository.list("student.id = ?1 and EXTRACT(MONTH FROM date) = ?2 and EXTRACT(YEAR FROM date) = ?3",
                    studentId, month, year);
        } else {
            records = attendanceRepository.list("student.id", studentId);
        }

        return records.stream().map(a -> Map.<String, Object>of(
                "date", a.getDate().toString(),
                "present", a.getPresent(),
                "session", a.getSession().name()
        )).collect(Collectors.toList());
    }

    private Long resolveUserIdFromJwt() {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException e) {
                throw new NotAuthorizedException("Invalid JWT subject");
            }
        }
        throw new NotAuthorizedException("Missing JWT");
    }
}
