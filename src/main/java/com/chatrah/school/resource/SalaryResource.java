// src/main/java/com/chatrah/school/resource/SalaryResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.SalaryPaymentDTO;
import com.chatrah.school.dto.SalaryStructureDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.SalaryService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * REST resource for teacher salary configuration and payments.
 */
@Path("/api/salary")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SalaryResource {

    @Inject
    SalaryService salaryService;

    @Inject
    JsonWebToken jwt;

    /**
     * Set or update salary structure for a teacher.
     */
    @POST
    @Path("/structure")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public SalaryStructureDTO setStructure(SalaryStructureDTO dto) {
        return salaryService.setSalaryStructure(dto);
    }

    /**
     * Pay salary to a teacher for a specific month.
     */
    @POST
    @Path("/pay")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public SalaryPaymentDTO paySalary(@QueryParam("teacherId") Long teacherId,
                                      @QueryParam("amount") Integer amount,
                                      @QueryParam("month") String month,
                                      @QueryParam("mode") String mode) {
        return salaryService.paySalary(teacherId, amount, month, mode);
    }

    /**
     * List all salary payments for a teacher (principal/clerk or teacher self).
     */
    @GET
    @Path("/teacher/{teacherId}/payments")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK,
            SecurityRoles.SYS_ADMIN, SecurityRoles.TEACHER})
    public List<SalaryPaymentDTO> listPayments(@PathParam("teacherId") Long teacherId) {
        return salaryService.listPaymentsForTeacher(teacherId);
    }

    /**
     * Teacher self-view: list own salary payments using teacherId from JWT.
     */
    @GET
    @Path("/me/payments")
    @RolesAllowed(SecurityRoles.TEACHER)
    public List<SalaryPaymentDTO> listMyPayments() {
        Long teacherId = resolveTeacherIdFromJwt();
        if (teacherId == null) {
            throw new ForbiddenException("No teacherId present in token");
        }
        return salaryService.listPaymentsForTeacher(teacherId);
    }

    private Long resolveTeacherIdFromJwt() {
        if (jwt == null) return null;
        Object claim = jwt.getClaim("teacherId");
        if (claim instanceof Number) {
            return ((Number) claim).longValue();
        }
        if (claim instanceof String) {
            try {
                return Long.parseLong((String) claim);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
