// src/main/java/com/chatrah/school/resource/AuthResource.java
package com.chatrah.school.resource;

import com.chatrah.school.dto.AuthMeDTO;
import com.chatrah.school.dto.ForgotPasswordRequestDTO;
import com.chatrah.school.dto.LoginRequestDTO;
import com.chatrah.school.dto.LoginResponseDTO;
import com.chatrah.school.dto.ResetPasswordRequestDTO;
import com.chatrah.school.dto.VerifyOtpRequestDTO;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.repository.TeacherRepository;
import com.chatrah.school.security.SecurityRoles;
import com.chatrah.school.service.AuthService;
import com.chatrah.school.service.OtpService;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.Set;

/**
 * REST resource for authentication-related operations
 * such as login, /me, and password reset via OTP.
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    OtpService otpService;

    @Inject
    StudentRepository studentRepository;

    @Inject
    TeacherRepository teacherRepository;

    @Inject
    JsonWebToken jwt;

    @POST
    @Path("/login")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public LoginResponseDTO login(LoginRequestDTO request) {
        return authService.login(request);
    }

    @GET
    @Path("/me")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK,
            SecurityRoles.TEACHER, SecurityRoles.STUDENT, SecurityRoles.SYS_ADMIN})
    public AuthMeDTO me() {
        AuthMeDTO dto = new AuthMeDTO();

        if (jwt.getSubject() != null) {
            try {
                dto.setUserId(Long.parseLong(jwt.getSubject()));
            } catch (NumberFormatException ignored) {
                dto.setUserId(null);
            }
        }
        dto.setUsername(jwt.getName());

        Object roleClaim = jwt.getClaim("role");
        if (roleClaim instanceof String) {
            dto.setRole((String) roleClaim);
        }

        dto.setStudentId(extractLongClaim("studentId"));
        dto.setTeacherId(extractLongClaim("teacherId"));

        // Set displayName from linked student/teacher
        if (dto.getStudentId() != null) {
            var s = studentRepository.findById(dto.getStudentId());
            if (s != null) dto.setDisplayName(s.getName());
        } else if (dto.getTeacherId() != null) {
            var t = teacherRepository.findById(dto.getTeacherId());
            if (t != null) dto.setDisplayName(t.getName());
        } else {
            dto.setDisplayName(jwt.getName());
        }

        dto.setGroups(jwt.getGroups());
        return dto;
    }

    private Long extractLongClaim(String name) {
        Object claim = jwt.getClaim(name);
        if (claim == null) return null;
        if (claim instanceof Number) return ((Number) claim).longValue();
        if (claim instanceof jakarta.json.JsonNumber) return ((jakarta.json.JsonNumber) claim).longValue();
        try { return Long.parseLong(claim.toString()); } catch (Exception e) { return null; }
    }

    /**
     * Step 1: initiate forgot password – send OTP to user's email.
     */
    @POST
    @Path("/otp/send-reset")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public void sendResetOtp(ForgotPasswordRequestDTO request) {
        otpService.sendPasswordResetOtp(request.getUsername());
    }

    /**
     * Optional Step 2: verify OTP only (for a two-step UI).
     * If you want a one-step reset, you can skip this and only use /password/reset.
     */
    @POST
    @Path("/otp/verify-reset")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public void verifyResetOtp(VerifyOtpRequestDTO request) {
        otpService.validatePasswordResetOtp(request.getUsername(), request.getOtp());
    }

    /**
     * Step 3: reset password with OTP verification in a single call.
     * If OTP is correct and not expired, the password is updated.
     */
    @POST
    @Path("/password/reset")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public void resetPassword(ResetPasswordRequestDTO request) {
        otpService.validatePasswordResetOtp(request.getUsername(), request.getOtp());
        authService.resetPassword(request.getUsername(), request.getNewPassword());
        otpService.markOtpUsed(request.getUsername());
    }

    @POST
    @Path("/password/change")
    @RolesAllowed({SecurityRoles.PRINCIPAL, SecurityRoles.CLERK,
            SecurityRoles.TEACHER, SecurityRoles.STUDENT, SecurityRoles.SYS_ADMIN})
    @Consumes(MediaType.APPLICATION_JSON)
    public void changePassword(java.util.Map<String, String> body) {
        Long userId = Long.parseLong(jwt.getSubject());
        authService.changePassword(userId, body.get("currentPassword"), body.get("newPassword"));
    }

    @POST
    @Path("/create-user")
    @RolesAllowed(SecurityRoles.SYS_ADMIN)
    @Consumes(MediaType.APPLICATION_JSON)
    public void createUser(java.util.Map<String, String> body) {
        authService.createUser(body.get("username"), body.get("password"), body.get("role"));
    }
}
