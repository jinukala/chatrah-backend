package com.chatrah.school.resource;

import com.chatrah.school.dto.LeaveRequestDTO;
import com.chatrah.school.entity.LeaveRequest;
import com.chatrah.school.entity.Student;
import com.chatrah.school.entity.User;
import com.chatrah.school.repository.LeaveRequestRepository;
import com.chatrah.school.repository.StudentRepository;
import com.chatrah.school.repository.UserRepository;
import com.chatrah.school.security.SecurityRoles;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.stream.Collectors;

@Path("/api/leaves")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LeaveRequestResource {

    @Inject LeaveRequestRepository leaveRepo;
    @Inject StudentRepository studentRepo;
    @Inject UserRepository userRepo;
    @Inject JsonWebToken jwt;

    @POST
    @RolesAllowed(SecurityRoles.STUDENT)
    @Transactional
    public LeaveRequestDTO apply(LeaveRequestDTO dto) {
        User user = userRepo.findById(Long.parseLong(jwt.getSubject()));
        Student student = studentRepo.findById(user.getStudentId());
        if (student == null) throw new BadRequestException("Student not found");

        LeaveRequest lr = new LeaveRequest();
        lr.setStudent(student);
        lr.setFromDate(dto.getFromDate());
        lr.setToDate(dto.getToDate());
        lr.setReason(dto.getReason());
        lr.setStatus(LeaveRequest.Status.PENDING);
        leaveRepo.persist(lr);
        return toDTO(lr);
    }

    @GET
    @Path("/my")
    @RolesAllowed(SecurityRoles.STUDENT)
    public List<LeaveRequestDTO> myLeaves() {
        User user = userRepo.findById(Long.parseLong(jwt.getSubject()));
        return leaveRepo.findByStudentId(user.getStudentId()).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @GET
    @Path("/pending")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.SYS_ADMIN})
    public List<LeaveRequestDTO> pendingForTeacher() {
        User user = userRepo.findById(Long.parseLong(jwt.getSubject()));
        if (user.getTeacherId() != null) {
            return leaveRepo.findPendingByClassTeacher(user.getTeacherId()).stream().map(this::toDTO).collect(Collectors.toList());
        }
        // Principal/SysAdmin see all pending
        return leaveRepo.list("status", LeaveRequest.Status.PENDING).stream().map(this::toDTO).collect(Collectors.toList());
    }

    @PUT
    @Path("/{id}/approve")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    @Transactional
    public LeaveRequestDTO approve(@PathParam("id") Long id, LeaveRequestDTO dto) {
        LeaveRequest lr = leaveRepo.findById(id);
        if (lr == null) throw new NotFoundException("Leave request not found");
        User user = userRepo.findById(Long.parseLong(jwt.getSubject()));
        lr.setStatus(LeaveRequest.Status.APPROVED);
        lr.setApprovedByTeacherId(user.getTeacherId());
        lr.setRemarks(dto.getRemarks());
        return toDTO(lr);
    }

    @PUT
    @Path("/{id}/reject")
    @RolesAllowed({SecurityRoles.TEACHER, SecurityRoles.PRINCIPAL, SecurityRoles.CLERK, SecurityRoles.SYS_ADMIN})
    @Transactional
    public LeaveRequestDTO reject(@PathParam("id") Long id, LeaveRequestDTO dto) {
        LeaveRequest lr = leaveRepo.findById(id);
        if (lr == null) throw new NotFoundException("Leave request not found");
        lr.setStatus(LeaveRequest.Status.REJECTED);
        lr.setRemarks(dto.getRemarks());
        return toDTO(lr);
    }

    private LeaveRequestDTO toDTO(LeaveRequest lr) {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setId(lr.getId());
        dto.setStudentId(lr.getStudent().getId());
        dto.setStudentName(lr.getStudent().getName());
        if (lr.getStudent().getClassRoom() != null) {
            dto.setClassName(lr.getStudent().getClassRoom().getClassName());
            dto.setSection(lr.getStudent().getClassRoom().getSection());
        }
        dto.setFromDate(lr.getFromDate());
        dto.setToDate(lr.getToDate());
        dto.setReason(lr.getReason());
        dto.setStatus(lr.getStatus().name());
        dto.setRemarks(lr.getRemarks());
        dto.setCreatedAt(lr.getCreatedAt());
        return dto;
    }
}
