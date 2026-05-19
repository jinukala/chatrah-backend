package com.chatrah.school.service;

import com.chatrah.school.dto.LoginRequestDTO;
import com.chatrah.school.dto.LoginResponseDTO;
import com.chatrah.school.entity.Student;
import com.chatrah.school.entity.Teacher;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.repository.TeacherRepository;
import com.chatrah.school.repository.UserRepository;
import com.chatrah.school.security.JwtService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDateTime;

/**
 * Handles authentication, password hashing, and password reset.
 * Includes account lockout after 5 consecutive failed attempts (15-minute lock).
 */
@ApplicationScoped
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    @Inject
    UserRepository userRepository;

    @Inject
    StudentRepository studentRepository;

    @Inject
    TeacherRepository teacherRepository;

    @Inject
    JwtService jwtService;

    /**
     * Login using username + password, returns a signed JWT and profile info.
     */
    @Transactional
    public LoginResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByUsername(request.getUsername());

        // If not found by username, try studentUniqueId
        if (user == null) {
            Student student = studentRepository.find("studentUniqueId", request.getUsername()).firstResult();
            if (student != null) {
                user = userRepository.find("studentId", student.getId()).firstResult();
            }
        }

        if (user == null || Boolean.FALSE.equals(user.getIsActive())) {
            throw new WebApplicationException("Invalid credentials", Response.Status.UNAUTHORIZED);
        }

        // Check if account is locked
        if (user.getLockedUntil() != null && LocalDateTime.now().isBefore(user.getLockedUntil())) {
            throw new WebApplicationException("Account locked. Try again later.", Response.Status.TOO_MANY_REQUESTS);
        }

        if (!BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            handleFailedAttempt(user);
            throw new WebApplicationException("Invalid credentials", Response.Status.UNAUTHORIZED);
        }

        // Successful login — reset failed attempts
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.persist(user);
        }

        String token = jwtService.generateToken(user);

        LoginResponseDTO response = new LoginResponseDTO();
        response.setRole(user.getRole());
        response.setToken(token);
        response.setStudentId(user.getStudentId());
        response.setTeacherId(user.getTeacherId());

        if ("STUDENT".equalsIgnoreCase(user.getRole()) && user.getStudentId() != null) {
            Student s = studentRepository.findById(user.getStudentId());
            response.setDisplayName(s != null ? s.getName() : user.getUsername());
        } else if ("TEACHER".equalsIgnoreCase(user.getRole()) && user.getTeacherId() != null) {
            Teacher t = teacherRepository.findById(user.getTeacherId());
            response.setDisplayName(t != null ? t.getName() : user.getUsername());
        } else {
            response.setDisplayName(user.getUsername());
        }

        return response;
    }

    private void handleFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
        }
        userRepository.persist(user);
    }

    /**
     * Hash a plain password using BCrypt.
     */
    public String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * Reset password after OTP validation.
     */
    @Transactional
    public void resetPassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username);
        if (user == null) {
            throw new WebApplicationException("User not found", Response.Status.NOT_FOUND);
        }
        user.setPasswordHash(hashPassword(newPassword));
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.persist(user);
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new WebApplicationException("User not found", Response.Status.NOT_FOUND);
        }
        if (!BCrypt.checkpw(currentPassword, user.getPasswordHash())) {
            throw new WebApplicationException("Current password is incorrect", Response.Status.BAD_REQUEST);
        }
        user.setPasswordHash(hashPassword(newPassword));
        userRepository.persist(user);
    }

    @Transactional
    public void createUser(String username, String password, String role) {
        if (userRepository.findByUsername(username) != null) {
            throw new WebApplicationException("Username already exists", Response.Status.CONFLICT);
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(hashPassword(password));
        user.setRole(role);
        user.setIsActive(true);
        userRepository.persist(user);
    }
}
