package com.chatrah.school.resource;

import com.chatrah.school.entity.AuditLog;
import com.chatrah.school.security.SecurityRoles;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/audit-logs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AuditLogResource {

    @Inject AuditLogRepo repo;

    @GET
    @RolesAllowed({SecurityRoles.SYS_ADMIN, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK})
    public List<Map<String, Object>> list(@QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("50") int size) {
        return repo.findAll(Sort.descending("createdAt")).page(page, size).list().stream().map(l -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", l.getId()); m.put("action", l.getAction()); m.put("entity", l.getEntity());
            m.put("entityId", l.getEntityId()); m.put("description", l.getDescription());
            m.put("performedBy", l.getPerformedBy()); m.put("role", l.getRole());
            m.put("createdAt", l.getCreatedAt().toString());
            return m;
        }).collect(Collectors.toList());
    }

    @ApplicationScoped
    public static class AuditLogRepo implements PanacheRepository<AuditLog> {}

    @ApplicationScoped
    public static class AuditService {
        @Inject AuditLogRepo repo;

        @Transactional
        public void log(String action, String entity, String entityId, String description, String performedBy, String role) {
            AuditLog l = new AuditLog();
            l.setAction(action); l.setEntity(entity); l.setEntityId(entityId);
            l.setDescription(description); l.setPerformedBy(performedBy); l.setRole(role);
            repo.persist(l);
        }
    }
}
