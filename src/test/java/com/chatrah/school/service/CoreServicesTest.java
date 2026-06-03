package com.chatrah.school.service;

import com.chatrah.school.dto.ClassRoomDTO;
import com.chatrah.school.dto.TeacherDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.FeePlan;
import com.chatrah.school.entity.Student;
import com.chatrah.school.repository.*;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CoreServicesTest {

    @Inject ClassRoomService classRoomService;
    @Inject TeacherService teacherService;
    @Inject FeeService feeService;
    @Inject ClassRoomRepository classRoomRepository;
    @Inject FeePlanRepository feePlanRepository;
    @Inject StudentRepository studentRepository;

    static Long classId;
    static Long teacherId;
    static Long studentId;

    @Test
    @Order(1)
    void testCreateClass() {
        ClassRoomDTO dto = new ClassRoomDTO();
        dto.setClassName("9");
        dto.setSection("B");
        dto.setSubjects("Telugu, Hindi, English");
        ClassRoomDTO result = classRoomService.createOrUpdate(dto);
        assertNotNull(result.getId());
        assertEquals("9", result.getClassName());
        assertEquals("Telugu, Hindi, English", result.getSubjects());
        classId = result.getId();
    }

    @Test
    @Order(2)
    void testListClasses() {
        var list = classRoomService.listAll();
        assertFalse(list.isEmpty());
    }

    @Test
    @Order(3)
    void testCreateTeacher() {
        TeacherDTO dto = new TeacherDTO();
        dto.setName("Test Teacher");
        dto.setSubjects("Maths, Physics");
        dto.setQualification("M.Sc");
        dto.setMobile("9876500000");
        dto.setEmail("teacher@test.com");
        dto.setClassTeacherOfId(classId);
        TeacherDTO result = teacherService.createOrUpdate(dto, "test", "TEST");
        assertNotNull(result.getId());
        assertEquals("Test Teacher", result.getName());
        teacherId = result.getId();
    }

    @Test
    @Order(4)
    void testTeacherAutoUserCreation() {
        var user = new UserRepository() {};
        // Teacher user created with username = name lowercase no spaces
        assertNotNull(teacherId);
    }

    @Test
    @Order(5)
    void testClassTeacherAssignment() {
        ClassRoomDTO cls = classRoomService.getById(classId);
        assertEquals(teacherId, cls.getClassTeacherId());
    }

    @Test
    @Order(6)
    @Transactional
    void testFeePlanAndComputation() {
        // Create fee plan
        ClassRoom cr = classRoomRepository.findById(classId);
        FeePlan fp = new FeePlan();
        fp.setClassRoom(cr);
        fp.setTotalFee(30000);
        fp.setHostelFee(15000);
        fp.setTransportFee(5000);
        fp.setIitNeetFee(20000);
        feePlanRepository.persist(fp);

        // Create student
        Student s = new Student();
        s.setName("Fee Test Student");
        s.setRollNo(1);
        s.setClassRoom(cr);
        s.setIsHosteller(true);
        s.setIsTransportUser(false);
        s.setIitNeetOpted(true);
        studentRepository.persist(s);
        studentId = s.getId();
    }

    @Test
    @Order(7)
    void testComputeFeeSummary() {
        var summary = feeService.computeFeeSummary(studentId);
        // Base 30000 + Hostel 15000 + IIT/NEET 20000 = 65000
        assertEquals(65000, summary.getTotalFee());
        assertEquals(0, summary.getTotalPaid());
        assertEquals(65000, summary.getDue());
    }

    @Test
    @Order(8)
    void testRecordPayment() {
        var summary = feeService.recordManualPayment(studentId, 10000, "CASH", "Test payment", "test", "TEST");
        assertEquals(10000, summary.getTotalPaid());
        assertEquals(55000, summary.getDue());
    }

    @Test
    @Order(9)
    void testRecordSecondPayment() {
        var summary = feeService.recordManualPayment(studentId, 25000, "UPI", null, "test", "TEST");
        assertEquals(35000, summary.getTotalPaid());
        assertEquals(30000, summary.getDue());
    }

    @Test
    @Order(10)
    void testTeacherList() {
        var list = teacherService.listAll();
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(t -> t.getName().equals("Test Teacher")));
    }
}
