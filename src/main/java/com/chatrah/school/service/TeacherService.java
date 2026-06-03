package com.chatrah.school.service;

import com.chatrah.school.dto.TeacherDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Teacher;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.ClassRoomRepository;
import com.chatrah.school.repository.TeacherRepository;
import com.chatrah.school.repository.UserRepository;
import com.chatrah.school.resource.AuditLogResource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.mindrot.jbcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class TeacherService {

    @Inject TeacherRepository teacherRepository;
    @Inject ClassRoomRepository classRoomRepository;
    @Inject UserRepository userRepository;
    @Inject AuditLogResource.AuditService auditService;

    public List<TeacherDTO> listAll() {
        return teacherRepository.listAll().stream().map(this::toDTO).collect(Collectors.toList());
    }

    public TeacherDTO getById(Long id) {
        Teacher t = teacherRepository.findById(id);
        if (t == null) throw new NotFoundException("Teacher not found");
        return toDTO(t);
    }

    @Transactional
    public TeacherDTO createOrUpdate(TeacherDTO dto, String performedBy, String role) {
        Teacher t;
        boolean isNew = dto.getId() == null;
        if (!isNew) {
            t = teacherRepository.findById(dto.getId());
            if (t == null) throw new NotFoundException("Teacher not found");
        } else {
            t = new Teacher();
        }

        // Capture changes before overwriting
        List<String> changes = new ArrayList<>();
        if (!isNew) {
            if (!eq(t.getName(), dto.getName())) changes.add("name");
            if (!eq(t.getEmail(), dto.getEmail())) changes.add("email");
            if (!eq(t.getMobile(), dto.getMobile())) changes.add("mobile");
            if (!eq(t.getSubject(), dto.getSubject())) changes.add("subject");
            if (!eq(t.getQualification(), dto.getQualification())) changes.add("qualification");
            if (!java.util.Objects.equals(t.getSalary(), dto.getSalary())) changes.add("salary");
        }

        t.setName(dto.getName());
        t.setSubject(dto.getSubject());
        t.setSubjects(dto.getSubjects());
        t.setQualification(dto.getQualification());
        t.setMobile(dto.getMobile());
        t.setEmail(dto.getEmail());
        t.setJoinDate(dto.getJoinDate());
        t.setSalary(dto.getSalary());
        t.setIsActive(dto.getActive() != null ? dto.getActive() : Boolean.TRUE);

        teacherRepository.persist(t);

        if (isNew) {
            createTeacherUser(t);
            auditService.log("CREATED", "Teacher", String.valueOf(t.getId()),
                "Added teacher " + t.getName() + " by " + performedBy + " (" + role + ")", performedBy, role);
        } else {
            String what = changes.isEmpty() ? "details" : String.join(", ", changes);
            auditService.log("UPDATED", "Teacher", String.valueOf(t.getId()),
                "Updated " + what + " of " + t.getName() + " by " + performedBy + " (" + role + ")", performedBy, role);
        }

        if (dto.getClassTeacherOfId() != null) {
            ClassRoom cr = classRoomRepository.findById(dto.getClassTeacherOfId());
            if (cr != null) {
                cr.setClassTeacherId(t.getId());
                classRoomRepository.persist(cr);
            }
        }

        return toDTO(t);
    }

    @Transactional
    public void delete(Long id, String performedBy, String role) {
        Teacher t = teacherRepository.findById(id);
        if (t != null) {
            auditService.log("DELETED", "Teacher", String.valueOf(id),
                "Deleted teacher " + t.getName() + " by " + performedBy + " (" + role + ")", performedBy, role);
            teacherRepository.delete(t);
        }
    }

    private boolean eq(Object a, Object b) {
        String sa = a == null ? "" : a.toString().trim();
        String sb = b == null ? "" : b.toString().trim();
        return sa.equals(sb);
    }

    private void createTeacherUser(Teacher teacher) {
        String username = teacher.getName().toLowerCase().replaceAll("\\s+", "");
        if (userRepository.findByUsername(username) != null) {
            username = username + teacher.getId();
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(BCrypt.hashpw("Teacher@123", BCrypt.gensalt(10)));
        user.setRole("TEACHER");
        user.setTeacherId(teacher.getId());
        user.setEmail(teacher.getEmail());
        user.setMobile(teacher.getMobile());
        user.setIsActive(true);
        userRepository.persist(user);
    }

    private TeacherDTO toDTO(Teacher t) {
        TeacherDTO dto = new TeacherDTO();
        dto.setId(t.getId());
        dto.setTeacherUniqueId("SVVT" + String.format("%03d", t.getId()));
        dto.setName(t.getName());
        dto.setSubject(t.getSubject());
        dto.setSubjects(t.getSubjects());
        dto.setQualification(t.getQualification());
        dto.setMobile(t.getMobile());
        dto.setEmail(t.getEmail());
        dto.setJoinDate(t.getJoinDate());
        dto.setSalary(t.getSalary());
        dto.setActive(t.getIsActive());
        ClassRoom assigned = classRoomRepository.find("classTeacherId", t.getId()).firstResult();
        if (assigned != null) {
            dto.setClassTeacherOfId(assigned.getId());
            dto.setClassTeacherOfName("Class " + assigned.getClassName() + " - " + assigned.getSection());
        }
        return dto;
    }
}
