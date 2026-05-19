package com.chatrah.school.resource;

import com.chatrah.school.entity.ClassMaterial;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.repository.ClassMaterialRepository;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.security.SecurityRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/api/class-materials")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClassMaterialResource {

    @Inject ClassMaterialRepository repo;
    @Inject ClassRoomRepository classRoomRepo;
    @Inject JsonWebToken jwt;

    @GET
    @Path("/class/{classId}")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.STUDENT, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public List<Map<String, Object>> listByClass(@PathParam("classId") Long classId, @QueryParam("subject") String subject) {
        List<ClassMaterial> materials = subject != null && !subject.isBlank()
                ? repo.findByClassAndSubject(classId, subject)
                : repo.findByClassId(classId);
        return materials.stream().map(this::toMap).collect(Collectors.toList());
    }

    @POST
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        Long classId = ((Number) body.get("classId")).longValue();
        ClassRoom cr = classRoomRepo.findById(classId);
        if (cr == null) throw new NotFoundException("Class not found");

        ClassMaterial m = new ClassMaterial();
        m.setClassRoom(cr);
        m.setSubject((String) body.get("subject"));
        m.setType(ClassMaterial.Type.valueOf(((String) body.get("type")).toUpperCase()));
        m.setTitle((String) body.get("title"));
        m.setContent((String) body.get("content"));
        if (body.get("fileData") != null) m.setFileData((String) body.get("fileData"));
        if (body.get("fileName") != null) m.setFileName((String) body.get("fileName"));
        if (body.get("dueDate") != null) m.setDueDate(LocalDate.parse((String) body.get("dueDate")));

        Object teacherClaim = jwt.getClaim("teacherId");
        if (teacherClaim instanceof Number) m.setUploadedByTeacherId(((Number) teacherClaim).longValue());
        else if (teacherClaim instanceof jakarta.json.JsonNumber) m.setUploadedByTeacherId(((jakarta.json.JsonNumber) teacherClaim).longValue());

        repo.persist(m);
        return toMap(m);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @Transactional
    public void delete(@PathParam("id") Long id) {
        ClassMaterial m = repo.findById(id);
        if (m != null) repo.delete(m);
    }

    @GET
    @Path("/{id}/file")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.STUDENT, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public Map<String, Object> getFile(@PathParam("id") Long id) {
        ClassMaterial m = repo.findById(id);
        if (m == null || m.getFileData() == null) throw new NotFoundException("File not found");
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("fileName", m.getFileName());
        result.put("fileData", m.getFileData());
        return result;
    }

    private Map<String, Object> toMap(ClassMaterial m) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", m.getId());
        map.put("classId", m.getClassRoom().getId());
        map.put("subject", m.getSubject());
        map.put("type", m.getType().name());
        map.put("title", m.getTitle());
        map.put("content", m.getContent());
        map.put("fileName", m.getFileName());
        map.put("hasFile", m.getFileData() != null && !m.getFileData().isEmpty());
        map.put("dueDate", m.getDueDate() != null ? m.getDueDate().toString() : null);
        map.put("createdAt", m.getCreatedAt().toString());
        return map;
    }
}
