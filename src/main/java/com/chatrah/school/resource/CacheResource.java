// src/main/java/com/chatrah/school/resource/CacheResource.java
package com.chatrah.school.resource;

import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.CacheManagementService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoints to clear server-side caches.
 * Restricted to PRINCIPAL and SYS_ADMIN.
 */
@Path("/api/cache")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class CacheResource {

    @Inject
    CacheManagementService cacheManagementService;

    @POST
    @Path("/clear/all")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public Response clearAll() {
        cacheManagementService.clearAllCaches();
        return Response.ok("{\"message\":\"All caches cleared\"}").build();
    }

    @POST
    @Path("/clear/fee")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public Response clearFee() {
        cacheManagementService.clearAllFeeSummaries();
        return Response.ok("{\"message\":\"Fee summary cache cleared\"}").build();
    }

    @POST
    @Path("/clear/attendance")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public Response clearAttendance() {
        cacheManagementService.clearAllAttendanceSummaries();
        return Response.ok("{\"message\":\"Attendance cache cleared\"}").build();
    }

    @POST
    @Path("/clear/class-students")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public Response clearClassStudents() {
        cacheManagementService.clearAllClassStudents();
        return Response.ok("{\"message\":\"Class-students cache cleared\"}").build();
    }

    @POST
    @Path("/clear/school-profile")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public Response clearSchoolProfile() {
        cacheManagementService.clearSchoolProfile();
        return Response.ok("{\"message\":\"School profile cache cleared\"}").build();
    }
}
