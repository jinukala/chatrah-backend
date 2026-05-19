// src/main/java/com/chatrah/school/resource/ClassRoomResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.ClassRoomDTO;
import com.chatrah.school.dto.StudentDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.ClassRoomService;
import com.chatrah.school.service.StudentService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * REST resource for managing classes and sections.
 */
@Path("/api/classes")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClassRoomResource {

    @Inject
    ClassRoomService classRoomService;

    @Inject
    StudentService studentService;

    @GET
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK,
            SecurityRoles.TEACHER, SecurityRoles.SYS_ADMIN})
    public List<ClassRoomDTO> listAll() {
        return classRoomService.listAll();
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK,
            SecurityRoles.TEACHER, SecurityRoles.SYS_ADMIN})
    public ClassRoomDTO getById(@PathParam("id") Long id) {
        return classRoomService.getById(id);
    }

    @POST
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public ClassRoomDTO create(ClassRoomDTO dto) {
        return classRoomService.createOrUpdate(dto);
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public ClassRoomDTO update(@PathParam("id") Long id, ClassRoomDTO dto) {
        dto.setId(id);
        return classRoomService.createOrUpdate(dto);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public void delete(@PathParam("id") Long id) {
        classRoomService.delete(id);
    }

    /**
     * List all students in a given class for teacher to see in the UI.
     */
    @GET
    @Path("/{classId}/students")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public List<StudentDTO> listStudents(@PathParam("classId") Long classId) {
        return studentService.listByClass(classId);
    }

    // ===== Subject-Teacher Assignments =====

    @Inject
    com.chatrah.school.repository.SubjectTeacherRepository subjectTeacherRepository;

    @Inject
    com.chatrah.school.repository.TeacherRepository teacherRepository;

    @GET
    @Path("/{classId}/subject-teachers")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN, SecurityRoles.TEACHER, SecurityRoles.STUDENT})
    public List<java.util.Map<String, Object>> getSubjectTeachers(@PathParam("classId") Long classId) {
        return subjectTeacherRepository.findByClassId(classId).stream().map(st -> {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put("id", st.getId());
            m.put("subject", st.getSubject());
            m.put("teacherId", st.getTeacher().getId());
            m.put("teacherName", st.getTeacher().getName());
            m.put("teacherMobile", st.getTeacher().getMobile());
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    @POST
    @Path("/{classId}/subject-teachers")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @jakarta.transaction.Transactional
    public java.util.Map<String, Object> assignSubjectTeacher(@PathParam("classId") Long classId, java.util.Map<String, Object> body) {
        com.chatrah.school.entity.ClassRoom cr = classRoomService.findById(classId);
        if (cr == null) throw new NotFoundException("Class not found");
        String subject = (String) body.get("subject");
        Long teacherId = ((Number) body.get("teacherId")).longValue();
        com.chatrah.school.entity.Teacher teacher = teacherRepository.findById(teacherId);
        if (teacher == null) throw new NotFoundException("Teacher not found");

        // Upsert: delete existing assignment for this class+subject, then insert
        subjectTeacherRepository.delete("classRoom.id = ?1 and subject = ?2", classId, subject);
        com.chatrah.school.entity.SubjectTeacher st = new com.chatrah.school.entity.SubjectTeacher();
        st.setClassRoom(cr);
        st.setSubject(subject);
        st.setTeacher(teacher);
        subjectTeacherRepository.persist(st);

        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("message", "Assigned " + teacher.getName() + " to " + subject);
        return result;
    }

    @DELETE
    @Path("/{classId}/subject-teachers/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    @jakarta.transaction.Transactional
    public void removeSubjectTeacher(@PathParam("classId") Long classId, @PathParam("id") Long id) {
        com.chatrah.school.entity.SubjectTeacher st = subjectTeacherRepository.findById(id);
        if (st != null) subjectTeacherRepository.delete(st);
    }
}
