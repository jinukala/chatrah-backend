package com.chatrah.school.resource;

import com.chatrah.school.dto.AttendanceAnalyticsDTO;
import com.chatrah.school.dto.FeeAnalyticsDTO;
import com.chatrah.school.dto.ExamAnalyticsDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.AnalyticsService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

@Path("/api/analytics")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AnalyticsResource {

    @Inject
    AnalyticsService analyticsService;

    @GET
    @Path("/attendance")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public AttendanceAnalyticsDTO getAttendanceAnalytics() {
        return analyticsService.computeAttendanceAnalytics();
    }

    @GET
    @Path("/fee")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public FeeAnalyticsDTO getFeeAnalytics() {
        return analyticsService.computeFeeAnalytics();
    }

    @GET
    @Path("/exams/{examId}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.TEACHER, SecurityRoles.SYS_ADMIN})
    public ExamAnalyticsDTO getExamAnalytics(@PathParam("examId") Long examId) {
        return analyticsService.computeExamAnalytics(examId);
    }
}
