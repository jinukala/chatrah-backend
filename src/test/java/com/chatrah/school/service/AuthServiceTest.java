package com.chatrah.school.service;

import com.chatrah.school.dto.LoginRequestDTO;
import com.chatrah.school.dto.LoginResponseDTO;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance;
import org.mindrot.jbcrypt.BCrypt;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AuthServiceTest {

    @Inject AuthService authService;
    @Inject UserRepository userRepository;

    @BeforeEach
    @Transactional
    void setup() {
        userRepository.deleteAll();
        User user = new User();
        user.setUsername("testuser");
        user.setPasswordHash(BCrypt.hashpw("Test@123", BCrypt.gensalt()));
        user.setRole("SYS_ADMIN");
        user.setIsActive(true);
        userRepository.persist(user);
    }

    @Test
    @Order(1)
    void testLoginSuccess() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername("testuser");
        req.setPassword("Test@123");
        LoginResponseDTO res = authService.login(req);
        assertNotNull(res.getToken());
        assertEquals("SYS_ADMIN", res.getRole());
    }

    @Test
    @Order(2)
    void testLoginInvalidPassword() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername("testuser");
        req.setPassword("wrong");
        assertThrows(WebApplicationException.class, () -> authService.login(req));
    }

    @Test
    @Order(3)
    void testLoginInvalidUsername() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername("nonexistent");
        req.setPassword("Test@123");
        assertThrows(WebApplicationException.class, () -> authService.login(req));
    }

    @Test
    @Order(4)
    void testAccountLockout() {
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername("testuser");
        req.setPassword("wrong");
        for (int i = 0; i < 5; i++) {
            assertThrows(WebApplicationException.class, () -> authService.login(req));
        }
        // 6th attempt should be locked (429 Too Many Requests)
        WebApplicationException ex = assertThrows(WebApplicationException.class, () -> authService.login(req));
        assertTrue(ex.getResponse().getStatus() == 429 || ex.getResponse().getStatus() == 401);
    }

    @Test
    @Order(5)
    @Transactional
    void testChangePassword() {
        User u = userRepository.findByUsername("testuser");
        authService.changePassword(u.getId(), "Test@123", "NewPass@123");
        // Login with new password
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername("testuser");
        req.setPassword("NewPass@123");
        assertNotNull(authService.login(req).getToken());
    }

    @Test
    @Order(6)
    @Transactional
    void testCreateUser() {
        authService.createUser("newadmin", "Admin@123", "PRINCIPAL");
        User u = userRepository.findByUsername("newadmin");
        assertNotNull(u);
        assertEquals("PRINCIPAL", u.getRole());
    }

    @Test
    @Order(7)
    void testResetPassword() {
        authService.resetPassword("testuser", "Reset@123");
        LoginRequestDTO req = new LoginRequestDTO();
        req.setUsername("testuser");
        req.setPassword("Reset@123");
        assertNotNull(authService.login(req).getToken());
    }
}
