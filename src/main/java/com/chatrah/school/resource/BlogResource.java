// src/main/java/com/chatrah/school/resource/BlogResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.BlogCreateRequestDTO;
import com.chatrah.school.dto.BlogDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.BlogService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * REST resource for blogs written by students, teachers, and principal.
 */
@Path("/api/blogs")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class BlogResource {

    @Inject
    BlogService blogService;

    @Inject
    JsonWebToken jwt;

    /**
     * Create a blog in PENDING status.
     */
    @POST
    @RolesAllowed({SecurityRoles.STUDENT, SecurityRoles.TEACHER,
            SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public BlogDTO createBlog( BlogCreateRequestDTO request) {
        Long userId = resolveUserIdFromJwt();
        String authorName = resolveAuthorNameFromJwt();
        return blogService.createBlog(userId, authorName, request);
    }

    /**
     * Approve a pending blog (principal).
     */
    @POST
    @Path("/{id}/approve")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public void approve(@PathParam("id") Long id) {
        blogService.approveBlog(id);
    }

    /**
     * Reject a pending blog (principal).
     */
    @POST
    @Path("/{id}/reject")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public void reject(@PathParam("id") Long id) {
        blogService.rejectBlog(id);
    }

    @GET
    @Path("/approved")
    @PermitAll
    public List<BlogDTO> listApproved(@QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("20") int size) {
        return blogService.listApprovedPaginated(page, size);
    }

    @GET
    @Path("/pending")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public List<BlogDTO> listPending() {
        return blogService.listPending();
    }

    private Long resolveUserIdFromJwt() {
        if (jwt != null && jwt.getSubject() != null) {
            try {
                return Long.parseLong(jwt.getSubject());
            } catch (NumberFormatException ignored) {
            }
        }
        return -1L;
    }

    private String resolveAuthorNameFromJwt() {
        // By MP-JWT spec, getName() returns "upn", which we set to username.
        if (jwt != null && jwt.getName() != null) {
            return jwt.getName();
        }
        return "Unknown";
    }
}
