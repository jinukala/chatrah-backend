package com.chatrah.school.repository;

import com.chatrah.school.entity.LeaveRequest;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class LeaveRequestRepository implements PanacheRepository<LeaveRequest> {

    public List<LeaveRequest> findByStudentId(Long studentId) {
        return list("student.id", studentId);
    }

    public List<LeaveRequest> findByClassTeacher(Long classTeacherId) {
        return list("student.classRoom.classTeacherId", classTeacherId);
    }

    public List<LeaveRequest> findPendingByClassTeacher(Long classTeacherId) {
        return list("student.classRoom.classTeacherId = ?1 and status = ?2", classTeacherId, LeaveRequest.Status.PENDING);
    }
}
