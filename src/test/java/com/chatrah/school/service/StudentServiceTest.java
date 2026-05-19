package com.chatrah.school.service;

import com.chatrah.school.dto.StudentDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.repository.UserRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StudentServiceTest {

    @Inject StudentService studentService;
    @Inject StudentRepository studentRepository;
    @Inject ClassRoomRepository classRoomRepository;
    @Inject UserRepository userRepository;

    static Long classId;
    static Long studentId;

    @BeforeEach
    @Transactional
    void init() {
        ClassRoom cr = new ClassRoom();
        cr.setClassName("10");
        cr.setSection("A");
        cr.setSubjects("Maths, Science");
        classRoomRepository.persist(cr);
        classId = cr.getId();
    }

    @Test
    @Order(1)
    void testCreateStudent() {
        StudentDTO dto = new StudentDTO();
        dto.setName("Test Student");
        dto.setRollNo(1);
        dto.setGender("Male");
        dto.setFatherName("Father");
        dto.setMotherName("Mother");
        dto.setParentMobile("9876543210");
        dto.setClassId(classId);
        dto.setIsHosteller(false);
        dto.setIsTransportUser(true);
        dto.setIitNeetOpted(false);

        StudentDTO result = studentService.createOrUpdate(dto);
        assertNotNull(result.getId());
        assertEquals("Test Student", result.getName());
        assertEquals("10", result.getClassName());
        assertEquals("A", result.getSection());
        assertNotNull(result.getStudentUniqueId());
        studentId = result.getId();
    }

    @Test
    @Order(2)
    void testAutoUserCreation() {
        // Student user should be auto-created
        var user = userRepository.find("studentId", studentId).firstResult();
        assertNotNull(user);
        assertEquals("STUDENT", user.getRole());
        assertEquals("teststudent", user.getUsername());
    }

    @Test
    @Order(3)
    void testGetById() {
        StudentDTO dto = studentService.getById(studentId);
        assertEquals("Test Student", dto.getName());
        assertEquals(1, dto.getRollNo());
    }

    @Test
    @Order(4)
    void testListByClass() {
        var list = studentService.listByClass(classId);
        assertTrue(list.stream().anyMatch(s -> s.getName().equals("Test Student")));
    }

    @Test
    @Order(5)
    void testListAllPaginated() {
        var list = studentService.listAllPaginated(0, 10);
        assertFalse(list.isEmpty());
    }

    @Test
    @Order(6)
    void testCountAll() {
        assertTrue(studentService.countAll() > 0);
    }

    @Test
    @Order(7)
    void testUpdateStudent() {
        StudentDTO dto = new StudentDTO();
        dto.setId(studentId);
        dto.setName("Updated Student");
        dto.setRollNo(1);
        dto.setClassId(classId);
        dto.setIsHosteller(true);
        dto.setIsTransportUser(false);
        dto.setIitNeetOpted(true);

        StudentDTO result = studentService.createOrUpdate(dto);
        assertEquals("Updated Student", result.getName());
        assertTrue(result.getIsHosteller());
        assertTrue(result.getIitNeetOpted());
    }

    @Test
    @Order(8)
    void testGetByIdNotFound() {
        assertThrows(NotFoundException.class, () -> studentService.getById(99999L));
    }

    @Test
    @Order(9)
    void testTodayBirthdays() {
        var list = studentService.getTodayBirthdays();
        assertNotNull(list); // may be empty but shouldn't throw
    }

    @Test
    @Order(10)
    @Transactional
    void testDeleteStudent() {
        studentService.delete(studentId);
        assertThrows(NotFoundException.class, () -> studentService.getById(studentId));
    }
}
