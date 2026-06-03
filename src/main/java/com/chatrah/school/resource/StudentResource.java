package com.chatrah.school.resource;

import com.chatrah.school.dto.StudentDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.StudentService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

@Path("/api/students")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class StudentResource {

    @Inject
    StudentService studentService;

    @Inject
    JsonWebToken jwt;

    @GET
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK,
            SecurityRoles.TEACHER, SecurityRoles.SYS_ADMIN})
    public jakarta.ws.rs.core.Response list(@QueryParam("classId") Long classId,
                                            @QueryParam("page") @DefaultValue("0") int page,
                                            @QueryParam("size") @DefaultValue("20") int size,
                                            @QueryParam("search") String search,
                                            @QueryParam("sortBy") @DefaultValue("name") String sortBy,
                                            @QueryParam("sortDir") @DefaultValue("asc") String sortDir) {
        if (classId != null) {
            List<StudentDTO> result = studentService.listFilteredByClass(classId, page, size, search, sortBy, sortDir);
            long total = studentService.countFilteredByClass(classId, search);
            return jakarta.ws.rs.core.Response.ok(result)
                    .header("X-Total-Count", total).build();
        }
        List<StudentDTO> result = studentService.listFiltered(page, size, search, sortBy, sortDir);
        long total = studentService.countFiltered(search);
        return jakarta.ws.rs.core.Response.ok(result)
                .header("X-Total-Count", total)
                .header("X-Page", page)
                .header("X-Size", size)
                .build();
    }

    @GET
    @Path("/birthdays/today")
    @jakarta.annotation.security.PermitAll
    public List<java.util.Map<String, String>> todayBirthdays() {
        return studentService.getTodayBirthdays().stream().map(s -> {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            m.put("name", s.getName().split(" ")[0]); // Only first name for privacy
            m.put("className", s.getClassName());
            m.put("section", s.getSection());
            return m;
        }).collect(java.util.stream.Collectors.toList());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK,
            SecurityRoles.TEACHER, SecurityRoles.SYS_ADMIN, SecurityRoles.STUDENT})
    public StudentDTO getById(@PathParam("id") Long id) {
        return studentService.getById(id);
    }

    @POST
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN, SecurityRoles.TEACHER})
    public StudentDTO create(StudentDTO dto) {
        return studentService.createOrUpdate(dto, jwt.getName(), jwt.getClaim("role"));
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN, SecurityRoles.TEACHER})
    public StudentDTO update(@PathParam("id") Long id, StudentDTO dto) {
        dto.setId(id);
        return studentService.createOrUpdate(dto, jwt.getName(), jwt.getClaim("role"));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public void delete(@PathParam("id") Long id) {
        studentService.delete(id, jwt.getName(), jwt.getClaim("role"));
    }
}
