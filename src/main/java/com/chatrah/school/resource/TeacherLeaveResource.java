package com.chatrah.school.resource;

import com.chatrah.school.entity.Teacher;
import com.chatrah.school.entity.TeacherLeave;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.TeacherRepository;
import com.chatrah.school.repository.UserRepository;
import com.chatrah.school.security.SecurityRoles;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Path("/api/teacher-leaves")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TeacherLeaveResource {

    @Inject TeacherLeaveRepo repo;
    @Inject UserRepository userRepo;
    @Inject TeacherRepository teacherRepo;
    @Inject JsonWebToken jwt;

    @POST
    @RolesAllowed(SecurityRoles.TEACHER)
    @Transactional
    public Map<String, Object> apply(Map<String, Object> body) {
        User user = userRepo.findById(Long.parseLong(jwt.getSubject()));
        Teacher teacher = teacherRepo.findById(user.getTeacherId());

        TeacherLeave tl = new TeacherLeave();
        tl.setTeacherId(teacher.getId());
        tl.setTeacherName(teacher.getName());
        tl.setFromDate(LocalDate.parse((String) body.get("fromDate")));
        tl.setToDate(LocalDate.parse((String) body.get("toDate")));
        tl.setReason((String) body.get("reason"));
        tl.setStatus(TeacherLeave.Status.PENDING);
        repo.persist(tl);
        return Map.of("message", "Leave applied");
    }

    @GET
    @Path("/my")
    @RolesAllowed(SecurityRoles.TEACHER)
    public List<Map<String, Object>> myLeaves() {
        User user = userRepo.findById(Long.parseLong(jwt.getSubject()));
        return repo.list("teacherId", user.getTeacherId()).stream().map(this::toMap).collect(Collectors.toList());
    }

    @GET
    @Path("/pending")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public List<Map<String, Object>> pending() {
        return repo.list("status", TeacherLeave.Status.PENDING).stream().map(this::toMap).collect(Collectors.toList());
    }

    @PUT
    @Path("/{id}/approve")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @Transactional
    public Map<String, Object> approve(@PathParam("id") Long id) {
        TeacherLeave tl = repo.findById(id);
        if (tl == null) throw new NotFoundException("Not found");
        tl.setStatus(TeacherLeave.Status.APPROVED);
        return Map.of("message", "Approved");
    }

    @PUT
    @Path("/{id}/reject")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @Transactional
    public Map<String, Object> reject(@PathParam("id") Long id) {
        TeacherLeave tl = repo.findById(id);
        if (tl == null) throw new NotFoundException("Not found");
        tl.setStatus(TeacherLeave.Status.REJECTED);
        return Map.of("message", "Rejected");
    }

    private Map<String, Object> toMap(TeacherLeave tl) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", tl.getId()); m.put("teacherName", tl.getTeacherName());
        m.put("fromDate", tl.getFromDate().toString()); m.put("toDate", tl.getToDate().toString());
        m.put("reason", tl.getReason()); m.put("status", tl.getStatus().name());
        m.put("remarks", tl.getRemarks()); m.put("createdAt", tl.getCreatedAt().toString());
        return m;
    }

    @ApplicationScoped
    public static class TeacherLeaveRepo implements PanacheRepository<TeacherLeave> {}
}
