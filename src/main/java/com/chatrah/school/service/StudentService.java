package com.chatrah.school.service;

import com.chatrah.school.dto.StudentDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Student;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.repository.UserRepository;
import com.chatrah.school.resource.AuditLogResource;
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

    public List<StudentDTO> listByClass(Long classId) {
        List<Student> students = studentRepository.findByClassRoomId(classId);
        return students.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<StudentDTO> listFilteredByClass(Long classId, int page, int size, String search, String sortBy, String sortDir) {
        boolean desc = "desc".equalsIgnoreCase(sortDir);
        String dir = desc ? "DESC" : "ASC";
        String order = "rollNo".equals(sortBy) ? "ORDER BY rollNo " + dir : "ORDER BY name " + dir;
        String where = buildWhere(search);
        if (where != null) {
            Object[] p = buildParams(search);
            // prepend classRoom condition — shift param indices
            String fullWhere = "classRoom.id = ?1 AND (" + where.replace("?1", "?2").replace("?2", "?3") + ") " + order;
            Object[] fullParams = prependParam(classId, p);
            return studentRepository.find(fullWhere, fullParams).page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
        }
        return studentRepository.find("classRoom.id = ?1 " + order, classId).page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long countFilteredByClass(Long classId, String search) {
        String where = buildWhere(search);
        if (where != null) {
            Object[] p = buildParams(search);
            String fullWhere = "classRoom.id = ?1 AND (" + where.replace("?1", "?2").replace("?2", "?3") + ")";
            return studentRepository.count(fullWhere, prependParam(classId, p));
        }
        return studentRepository.count("classRoom.id", classId);
    }

    private Object[] prependParam(Object first, Object[] rest) {
        Object[] result = new Object[rest.length + 1];
        result[0] = first;
        System.arraycopy(rest, 0, result, 1, rest.length);
        return result;
    }

    public List<StudentDTO> listAll() {
        return studentRepository.listAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<StudentDTO> listAllPaginated(int page, int size) {
        return studentRepository.findAll().page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<StudentDTO> listFiltered(int page, int size, String search, String sortBy, String sortDir) {
        boolean desc = "desc".equalsIgnoreCase(sortDir);
        String dir = desc ? "DESC" : "ASC";

        // Build ORDER BY — numeric for rollNo, string for others; classRoom needs join alias
        String order = switch (sortBy) {
            case "rollNo"           -> "ORDER BY s.rollNo " + dir;
            case "studentUniqueId"  -> "ORDER BY s.studentUniqueId " + dir;
            case "classRoom"        -> "ORDER BY s.classRoom.className " + dir + ", s.classRoom.section " + dir;
            default                 -> "ORDER BY s.name " + dir;
        };

        String where = buildWhere(search);
        if (where != null) {
            Object[] params = buildParams(search);
            return studentRepository.find(where + " " + order, params)
                    .page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
        }
        // No search — use Panache sort (avoids manual JPQL for simple case)
        var sort = desc ? io.quarkus.panache.common.Sort.descending("name") : io.quarkus.panache.common.Sort.ascending("name");
        if ("rollNo".equals(sortBy)) sort = desc ? io.quarkus.panache.common.Sort.descending("rollNo") : io.quarkus.panache.common.Sort.ascending("rollNo");
        else if ("studentUniqueId".equals(sortBy)) sort = desc ? io.quarkus.panache.common.Sort.descending("studentUniqueId") : io.quarkus.panache.common.Sort.ascending("studentUniqueId");
        return studentRepository.findAll(sort).page(page, size).list().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public long countFiltered(String search) {
        String where = buildWhere(search);
        if (where != null) return studentRepository.count(where, buildParams(search));
        return studentRepository.count();
    }

    /** Builds JPQL WHERE clause for search (name, rollNo, studentUniqueId). */
    private String buildWhere(String search) {
        if (search == null || search.isBlank()) return null;
        String s = search.trim();
        if (s.toUpperCase().startsWith("SVV")) {
            return "UPPER(studentUniqueId) LIKE ?1";
        }
        if (s.matches("\\d+")) {
            return "LOWER(name) LIKE ?1 OR rollNo = ?2";
        }
        return "LOWER(name) LIKE ?1";
    }

    private Object[] buildParams(String search) {
        String s = search.trim();
        if (s.toUpperCase().startsWith("SVV")) {
            return new Object[]{"%" + s.toUpperCase() + "%"};
        }
        if (s.matches("\\d+")) {
            return new Object[]{"%" + s.toLowerCase() + "%", Integer.parseInt(s)};
        }
        return new Object[]{"%" + s.toLowerCase() + "%"};
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
    public StudentDTO createOrUpdate(StudentDTO dto, String performedBy, String role) {
        Student entity;
        boolean isNew = dto.getId() == null;
        if (!isNew) {
            entity = studentRepository.findById(dto.getId());
            if (entity == null) throw new NotFoundException("Student not found");
        } else {
            entity = new Student();
        }

        // Capture changed fields before overwriting
        List<String> changes = new java.util.ArrayList<>();
        if (!isNew) {
            if (!eq(entity.getName(), dto.getName())) changes.add("name");
            if (!eq(entity.getEmail(), dto.getEmail())) changes.add("email");
            if (!eq(entity.getParentMobile(), dto.getParentMobile())) changes.add("mobile");
            if (!eq(entity.getGender(), dto.getGender())) changes.add("gender");
            if (!eq(entity.getFatherName(), dto.getFatherName())) changes.add("fatherName");
            if (!eq(entity.getMotherName(), dto.getMotherName())) changes.add("motherName");
            if (!eq(entity.getAddress(), dto.getAddress())) changes.add("address");
            if (!java.util.Objects.equals(entity.getRollNo(), dto.getRollNo())) changes.add("rollNo");
            Long currentClassId = entity.getClassRoom() != null ? entity.getClassRoom().getId() : null;
            if (!java.util.Objects.equals(currentClassId, dto.getClassId())) changes.add("class");
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
        studentRepository.flush();

        // Auto-generate studentUniqueId: SVV + class(2) + section(1) + roll(3)
        if (entity.getStudentUniqueId() == null && entity.getClassRoom() != null && entity.getRollNo() != null) {
            String cls = String.format("%02d", Integer.parseInt(entity.getClassRoom().getClassName().replaceAll("\\D", "")));
            String sec = entity.getClassRoom().getSection().substring(0, 1);
            String roll = String.format("%03d", entity.getRollNo());
            entity.setStudentUniqueId("SVV" + cls + sec + roll);
        }

        // Auto-create login account for new students
        if (isNew) {
            createStudentUser(entity);
            String desc = "Added student " + entity.getName() + " by " + performedBy + " (" + role + ")";
            auditService.log("CREATED", "Student", String.valueOf(entity.getId()), desc, performedBy, role);
            liveEventService.studentCreated(entity.getName());
        } else {
            String what = changes.isEmpty() ? "no field changes" : String.join(", ", changes);
            String desc = "Updated " + what + " of " + entity.getName() + " by " + performedBy + " (" + role + ")";
            auditService.log("UPDATED", "Student", String.valueOf(entity.getId()), desc, performedBy, role);
        }

        return toDTO(entity);
    }

    private boolean eq(Object a, Object b) {
        String sa = a == null ? "" : a.toString().trim();
        String sb = b == null ? "" : b.toString().trim();
        return sa.equals(sb);
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
    public void delete(Long id, String performedBy, String role) {
        Student s = studentRepository.findById(id);
        if (s != null) {
            auditService.log("DELETED", "Student", String.valueOf(id),
                "Deleted student " + s.getName() + " by " + performedBy + " (" + role + ")", performedBy, role);
            studentRepository.delete(s);
        }
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
