// src/main/java/com/chatrah/school/resource/SchoolProfileResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.SchoolProfileDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.SchoolProfileService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST resource for managing school branding and profile information.
 */
@Path("/api/school/profile")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class SchoolProfileResource {

    @Inject
    SchoolProfileService schoolProfileService;

    /**
     * Get current school profile settings.
     */
    @GET
    @PermitAll
    public SchoolProfileDTO getProfile() {
        return schoolProfileService.getProfile();
    }

    /**
     * Update school profile (principal/clerk).
     */
    @PUT
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public SchoolProfileDTO updateProfile(SchoolProfileDTO dto) {
        return schoolProfileService.updateProfile(dto);
    }
}
