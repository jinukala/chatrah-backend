// src/main/java/com/chatrah/school/resource/NotificationResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.FeeSummaryDTO;
import com.chatrah.school.entity.FeePayment;
import com.chatrah.school.entity.Student;
import com.chatrah.school.repository.FeePaymentRepository;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.FeeService;
import com.chatrah.school.service.NotificationService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Manual notification triggers (mostly for admin / principal).
 * Regular flows (fee payment, attendance) call NotificationService directly.
 */
@Path("/api/notifications")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class NotificationResource {

    @Inject
    NotificationService notificationService;

    @Inject
    StudentRepository studentRepository;

    @Inject
    FeePaymentRepository feePaymentRepository;

    @Inject
    FeeService feeService;

    // DTOs for API requests
    public static class FeePaymentNotificationRequest {
        public Long studentId;
        public Long paymentId;
    }

    public static class ExamResultNotificationRequest {
        public Long studentId;
        public String examName;
    }

    public static class EventNotificationRequest {
        public String eventTitle;
        public String body;
        public Long classId; // optional: null means whole school
    }

    @POST
    @Path("/sendFeePayment")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public Response sendFeePayment(FeePaymentNotificationRequest req) {
        Student student = studentRepository.findById(req.studentId);
        FeePayment payment = feePaymentRepository.findById(req.paymentId);
        if (student == null || payment == null) {
            throw new NotFoundException("Student or payment not found");
        }
        FeeSummaryDTO summary = feeService.computeFeeSummary(student.getId());
        notificationService.sendFeePaymentNotification(student, payment, summary);
        return Response.ok().entity("{\"message\":\"Fee payment notification triggered\"}").build();
    }

    @POST
    @Path("/sendExamResult")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.TEACHER, SecurityRoles.SYS_ADMIN})
    public Response sendExamResult(ExamResultNotificationRequest req) {
        Student student = studentRepository.findById(req.studentId);
        if (student == null) throw new NotFoundException("Student not found");
        notificationService.sendExamResultNotification(student, req.examName);
        return Response.ok().entity("{\"message\":\"Exam result notification triggered\"}").build();
    }

    // Event notification endpoints can later use ClassRoomRepository to find students
}
