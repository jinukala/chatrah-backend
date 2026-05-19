// src/main/java/com/chatrah/school/resource/EventResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.EventDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.EventService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * REST resource for managing and listing school events.
 */
@Path("/api/events")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EventResource {

    @Inject
    EventService eventService;

    @Inject
    JsonWebToken jwt;


    @GET
    @Path("/upcoming")
    @PermitAll
    public List<EventDTO> listUpcoming() {
        return eventService.listUpcoming();
    }

    @POST
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public EventDTO create(EventDTO dto) {
        Long userId = resolveUserIdFromJwt();
        return eventService.createOrUpdate(dto, userId);
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public EventDTO update(@PathParam("id") Long id, EventDTO dto) {
        dto.setId(id);
        Long userId = resolveUserIdFromJwt();
        return eventService.createOrUpdate(dto, userId);
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
}
