import com.chatrah.school.dto.TeacherDTO;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.TeacherService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;

/**
 * REST resource for managing teacher profiles.
 */
@Path("/api/teachers")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class TeacherResource {

    @Inject TeacherService teacherService;
    @Inject JsonWebToken jwt;

    @GET
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN, SecurityRoles.TEACHER, SecurityRoles.STUDENT})
    public List<TeacherDTO> listAll() { return teacherService.listAll(); }

    @GET
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.TEACHER, SecurityRoles.SYS_ADMIN, SecurityRoles.STUDENT})
    public TeacherDTO getById(@PathParam("id") Long id) { return teacherService.getById(id); }

    @POST
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public TeacherDTO create(TeacherDTO dto) {
        return teacherService.createOrUpdate(dto, jwt.getName(), jwt.getClaim("role"));
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public TeacherDTO update(@PathParam("id") Long id, TeacherDTO dto) {
        dto.setId(id);
        return teacherService.createOrUpdate(dto, jwt.getName(), jwt.getClaim("role"));
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    public void delete(@PathParam("id") Long id) {
        teacherService.delete(id, jwt.getName(), jwt.getClaim("role"));
    }
}
