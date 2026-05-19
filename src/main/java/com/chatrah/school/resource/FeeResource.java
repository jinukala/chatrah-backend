// src/main/java/com/chatrah/school/resource/FeeResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.FeeReceiptDTO;
import com.chatrah.school.dto.FeeSummaryDTO;
import com.chatrah.school.dto.OnlineFeePaymentRequestDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.FeePlan;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.FeePlanRepository;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.FeeService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST resource for fee calculation, payments, and receipt retrieval.
 */
@Path("/api/fees")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class FeeResource {

    @Inject
    FeeService feeService;

    @Inject
    FeePlanRepository feePlanRepository;

    @Inject
    ClassRoomRepository classRoomRepository;

    @Inject
    com.chatrah.school.repository.StudentRepository studentRepository;

    @Inject
    com.chatrah.school.repository.FeePaymentRepository feePaymentRepository;

    @Inject
    JsonWebToken jwt;

    // ===== FEE PLAN CRUD =====

    @GET
    @Path("/plans")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN, SecurityRoles.TEACHER})
    public List<Map<String, Object>> listFeePlans() {
        return feePlanRepository.listAll().stream().map(fp -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", fp.getId());
            m.put("classId", fp.getClassRoom().getId());
            m.put("className", fp.getClassRoom().getClassName());
            m.put("section", fp.getClassRoom().getSection());
            m.put("totalFee", fp.getTotalFee());
            m.put("hostelFee", fp.getHostelFee());
            m.put("transportFee", fp.getTransportFee());
            m.put("iitNeetFee", fp.getIitNeetFee());
            m.put("description", fp.getDescription());
            return m;
        }).collect(Collectors.toList());
    }

    @POST
    @Path("/plans")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    @Transactional
    public Map<String, Object> createOrUpdateFeePlan(Map<String, Object> body) {
        Long classId = ((Number) body.get("classId")).longValue();
        ClassRoom cr = classRoomRepository.findById(classId);
        if (cr == null) throw new NotFoundException("Class not found");

        FeePlan fp = feePlanRepository.find("classRoom", cr).firstResult();
        if (fp == null) { fp = new FeePlan(); fp.setClassRoom(cr); }

        fp.setTotalFee(((Number) body.getOrDefault("totalFee", 0)).intValue());
        fp.setHostelFee(((Number) body.getOrDefault("hostelFee", 0)).intValue());
        fp.setTransportFee(((Number) body.getOrDefault("transportFee", 0)).intValue());
        fp.setIitNeetFee(((Number) body.getOrDefault("iitNeetFee", 0)).intValue());
        fp.setDescription((String) body.get("description"));
        feePlanRepository.persist(fp);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("id", fp.getId());
        result.put("classId", classId);
        result.put("message", "Fee plan saved");
        return result;
    }

    // ===== ADMIN: Search student & record payment =====

    @GET
    @Path("/search")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public List<Map<String, Object>> searchStudentForFee(@QueryParam("q") String query) {
        if (query == null || query.isBlank()) return List.of();
        List<com.chatrah.school.entity.Student> students;
        try {
            Long id = Long.parseLong(query);
            com.chatrah.school.entity.Student s = studentRepository.findById(id);
            students = s != null ? List.of(s) : List.of();
        } catch (NumberFormatException e) {
            students = studentRepository.list("LOWER(name) LIKE ?1", "%" + query.toLowerCase() + "%");
        }
        return students.stream().map(s -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            m.put("rollNo", s.getRollNo());
            m.put("className", s.getClassRoom() != null ? s.getClassRoom().getClassName() : null);
            m.put("section", s.getClassRoom() != null ? s.getClassRoom().getSection() : null);
            return m;
        }).collect(Collectors.toList());
    }

    @POST
    @Path("/student/{studentId}/payment")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public FeeSummaryDTO recordPayment(@PathParam("studentId") Long studentId, Map<String, Object> body) {
        int amount = ((Number) body.get("amount")).intValue();
        String mode = body.get("mode") != null ? body.get("mode").toString() : "CASH";
        String remarks = body.get("remarks") != null ? body.get("remarks").toString() : null;
        return feeService.recordManualPayment(studentId, amount, mode, remarks);
    }

    /**
     * Get fee summary for a specific student.
     * For student self-view, studentId would usually come from JWT.
     */
    @GET
    @Path("/student/{studentId}/summary")
    @RolesAllowed({SecurityRoles.STUDENT, SecurityRoles.TEACHER,
            SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public FeeSummaryDTO getFeeSummary(@PathParam("studentId") Long studentId) {
        return feeService.computeFeeSummary(studentId);
    }

    /**
     * Initiate an online payment for a student with custom amount.
     * Currently behaves as immediate success (mock integration).
     */
    @POST
    @Path("/student/{studentId}/pay/online")
    @RolesAllowed(SecurityRoles.STUDENT)
    public FeeSummaryDTO payOnline(@PathParam("studentId") Long studentId,
                                   OnlineFeePaymentRequestDTO request) {
        return feeService.initiateOnlinePayment(studentId, request);
    }

    /**
     * Get a fully populated receipt DTO for a specific payment.
     */
    @GET
    @Path("/receipt/{paymentId}")
    @RolesAllowed({SecurityRoles.STUDENT, SecurityRoles.PRINCIPAL,
            SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public FeeReceiptDTO getReceipt(@PathParam("paymentId") Long paymentId) {
        return feeService.buildReceipt(paymentId);
    }

    /**
     * Student self-fee summary using studentId from JWT.
     */
    @GET
    @Path("/me/summary")
    @RolesAllowed(SecurityRoles.STUDENT)
    public FeeSummaryDTO getMyFeeSummary() {
        Long studentId = resolveStudentIdFromJwt();
        if (studentId == null) {
            throw new ForbiddenException("No studentId present in token");
        }
        return feeService.computeFeeSummary(studentId);
    }

    private Long resolveStudentIdFromJwt() {
        if (jwt == null) return null;
        Object claim = jwt.getClaim("studentId");
        if (claim == null) return null;
        if (claim instanceof Number) return ((Number) claim).longValue();
        if (claim instanceof jakarta.json.JsonNumber) return ((jakarta.json.JsonNumber) claim).longValue();
        try { return Long.parseLong(claim.toString()); } catch (NumberFormatException e) { return null; }
    }
}
