package com.chatrah.school.resource;

import com.chatrah.school.dto.AccessRequestDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.AccessRequestService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/access-requests")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AccessRequestResource {

    @Inject
    AccessRequestService accessRequestService;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/fee")
    @RolesAllowed(SecurityRoles.TEACHER)
    public AccessRequestDTO requestFeeAccess(@QueryParam("classId") Long classId) {
        Long teacherId = resolveTeacherIdFromJwt();
        if (teacherId == null) {
            throw new ForbiddenException("No teacherId present in token");
        }
        return accessRequestService.requestFeeAccess(teacherId, classId);
    }

    @GET
    @Path("/pending")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public List<AccessRequestDTO> listPending() {
        return accessRequestService.listPending();
    }

    @POST
    @Path("/{id}/approve")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public void approve(@PathParam("id") Long id) {
        Long approverUserId = resolveUserIdFromJwt();
        accessRequestService.approveRequest(id, approverUserId);
    }

    @POST
    @Path("/{id}/reject")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public void reject(@PathParam("id") Long id) {
        Long approverUserId = resolveUserIdFromJwt();
        accessRequestService.rejectRequest(id, approverUserId);
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

    private Long resolveTeacherIdFromJwt() {
        if (jwt != null) {
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
        }
        return null;
    }
}
