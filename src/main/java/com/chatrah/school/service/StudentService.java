package com.chatrah.school.service;

import com.chatrah.school.dto.StudentDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Student;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.repository.UserRepository;
import com.chatrah.school.resource.AuditLogResource;
import io.quarkus.cache.CacheInvalidate;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.mindrot.jbcrypt.BCrypt;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles student CRUD and class assignment.
 */
@ApplicationScoped
public class StudentService {

    @Inject
    StudentRepository studentRepository;

    @Inject
    ClassRoomRepository classRoomRepository;

    @Inject
    UserRepository userRepository;

    @Inject
    AuditLogResource.AuditService auditService;

    @Inject
    com.chatrah.school.websocket.LiveEventService liveEventService;

    @CacheInvalidate(cacheName = "fee-summary")
    void invalidateFeeSummary(Long studentId) {
        // no body needed
    }

    public List<StudentDTO> listByClass(Long classId) {
        List<Student> students = studentRepository.findByClassRoomId(classId);
        return students.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<StudentDTO> listAll() {
        return studentRepository.listAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<StudentDTO> listAllPaginated(int page, int size) {
        return studentRepository.findAll().page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<StudentDTO> listFiltered(int page, int size, String search, String sortBy, String sortDir) {
        io.quarkus.panache.common.Sort sort = "desc".equalsIgnoreCase(sortDir)
                ? io.quarkus.panache.common.Sort.descending(sortBy)
                : io.quarkus.panache.common.Sort.ascending(sortBy);
        if (search != null && !search.isBlank()) {
            return studentRepository.find("LOWER(name) LIKE ?1 OR CAST(rollNo AS string) LIKE ?1", sort, "%" + search.toLowerCase() + "%")
                    .page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
        }
        return studentRepository.findAll(sort).page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long countFiltered(String search) {
        if (search != null && !search.isBlank()) {
            return studentRepository.count("LOWER(name) LIKE ?1 OR CAST(rollNo AS string) LIKE ?1", "%" + search.toLowerCase() + "%");
        }
        return studentRepository.count();
    }

    public long countAll() {
        return studentRepository.count();
    }

    public List<StudentDTO> getTodayBirthdays() {
        java.time.LocalDate today = java.time.LocalDate.now();
        int month = today.getMonthValue();
        int day = today.getDayOfMonth();
        return studentRepository.list("EXTRACT(MONTH FROM dateOfBirth) = ?1 AND EXTRACT(DAY FROM dateOfBirth) = ?2", month, day)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    public StudentDTO getById(Long id) {
        Student s = studentRepository.findById(id);
        if (s == null) throw new NotFoundException("Student not found");
        return toDTO(s);
    }

    @Transactional
    public StudentDTO createOrUpdate(StudentDTO dto) {
        Student entity;
        if (dto.getId() != null) {
            entity = studentRepository.findById(dto.getId());
            if (entity == null) throw new NotFoundException("Student not found");
        } else {
            entity = new Student();
        }

        entity.setName(dto.getName());
        entity.setRollNo(dto.getRollNo());
        entity.setGender(dto.getGender());
        entity.setDateOfBirth(dto.getDateOfBirth());
        entity.setParentName(dto.getParentName());
        entity.setFatherName(dto.getFatherName());
        entity.setMotherName(dto.getMotherName());
        entity.setParentMobile(dto.getParentMobile());
        entity.setEmail(dto.getEmail());
        entity.setAddress(dto.getAddress());
        entity.setAdmissionDate(dto.getAdmissionDate());
        entity.setIsHosteller(Boolean.TRUE.equals(dto.getIsHosteller()));
        entity.setIsTransportUser(Boolean.TRUE.equals(dto.getIsTransportUser()));
        entity.setIitNeetOpted(Boolean.TRUE.equals(dto.getIitNeetOpted()));
        entity.setFeeConcession(dto.getFeeConcession() != null ? dto.getFeeConcession() : 0);

        if (dto.getClassId() != null) {
            ClassRoom cr = classRoomRepository.findById(dto.getClassId());
            if (cr == null) {
                throw new NotFoundException("Class not found");
            }
            entity.setClassRoom(cr);
        } else {
            entity.setClassRoom(null);
        }

        studentRepository.persist(entity);

        // Auto-generate studentUniqueId: SVV + class(2) + section(1) + roll(3)
        if (entity.getStudentUniqueId() == null && entity.getClassRoom() != null && entity.getRollNo() != null) {
            String cls = String.format("%02d", Integer.parseInt(entity.getClassRoom().getClassName().replaceAll("\\D", "")));
            String sec = entity.getClassRoom().getSection().substring(0, 1);
            String roll = String.format("%03d", entity.getRollNo());
            entity.setStudentUniqueId("SVV" + cls + sec + roll);
        }

        // Auto-create login account for new students
        if (dto.getId() == null) {
            createStudentUser(entity);
            auditService.log("CREATED", "Student", String.valueOf(entity.getId()), "New student: " + entity.getName(), "system", "SYSTEM");
            liveEventService.studentCreated(entity.getName());
        } else {
            auditService.log("UPDATED", "Student", String.valueOf(entity.getId()), "Updated: " + entity.getName(), "system", "SYSTEM");
        }

        return toDTO(entity);
    }

    private void createStudentUser(Student student) {
        // Username = student name (lowercase, spaces removed)
        String username = student.getName().toLowerCase().replaceAll("\\s+", "");

        // If username already exists, append student ID to make unique
        if (userRepository.findByUsername(username) != null) {
            username = username + student.getId();
        }

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw("Student@123", BCrypt.gensalt(10)));
        user.setRole("STUDENT");
        user.setStudentId(student.getId());
        user.setEmail(student.getEmail());
        user.setMobile(student.getParentMobile());
        user.setIsActive(true);
        userRepository.persist(user);
    }

    @Transactional
    public void delete(Long id) {
        Student s = studentRepository.findById(id);
        if (s != null) studentRepository.delete(s);
    }

    private StudentDTO toDTO(Student s) {
        StudentDTO dto = new StudentDTO();
        dto.setId(s.getId());
        dto.setStudentUniqueId(s.getStudentUniqueId());
        dto.setName(s.getName());
        dto.setRollNo(s.getRollNo());
        dto.setGender(s.getGender());
        dto.setDateOfBirth(s.getDateOfBirth());
        dto.setParentName(s.getParentName());
        dto.setFatherName(s.getFatherName());
        dto.setMotherName(s.getMotherName());
        dto.setParentMobile(s.getParentMobile());
        dto.setEmail(s.getEmail());
        dto.setAddress(s.getAddress());
        dto.setAdmissionDate(s.getAdmissionDate());
        if (s.getClassRoom() != null) {
            dto.setClassId(s.getClassRoom().getId());
            dto.setClassName(s.getClassRoom().getClassName());
            dto.setSection(s.getClassRoom().getSection());
        }
        dto.setIsHosteller(s.getIsHosteller());
        dto.setIsTransportUser(s.getIsTransportUser());
        dto.setIitNeetOpted(s.getIitNeetOpted());
        dto.setFeeConcession(s.getFeeConcession());
        return dto;
    }
}
