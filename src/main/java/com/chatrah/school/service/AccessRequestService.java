package com.chatrah.school.service;

import com.chatrah.school.dto.AccessRequestDTO;
import com.chatrah.school.entity.AccessRequest;
import com.chatrah.school.repository.AccessRequestRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles teacher access requests (e.g., fee view permissions).
 */
@ApplicationScoped
public class AccessRequestService {

    @Inject
    AccessRequestRepository accessRequestRepository;

    @Transactional
    public AccessRequestDTO requestFeeAccess(Long teacherId, Long classId) {
        AccessRequest ar = new AccessRequest();
        ar.setTeacherId(teacherId);
        ar.setClassId(classId);
        ar.setRequestType("FEE_ACCESS");
        ar.setStatus("PENDING");
        ar.setRequestedAt(LocalDateTime.now());
        accessRequestRepository.persist(ar);
        return toDTO(ar);
    }

    public List<AccessRequestDTO> listPending() {
        return accessRequestRepository.findPending().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public void approveRequest(Long id, Long approverUserId) {
        AccessRequest ar = accessRequestRepository.findById(id);
        if (ar == null) throw new NotFoundException("Access request not found");
        ar.setStatus("APPROVED");
        ar.setApprovedBy(approverUserId);
        ar.setApprovedAt(LocalDateTime.now());
        accessRequestRepository.persist(ar);
        // Future: actually record permissions in a separate table
    }

    @Transactional
    public void rejectRequest(Long id, Long approverUserId) {
        AccessRequest ar = accessRequestRepository.findById(id);
        if (ar == null) throw new NotFoundException("Access request not found");
        ar.setStatus("REJECTED");
        ar.setApprovedBy(approverUserId);
        ar.setApprovedAt(LocalDateTime.now());
        accessRequestRepository.persist(ar);
    }

    private AccessRequestDTO toDTO(AccessRequest ar) {
        AccessRequestDTO dto = new AccessRequestDTO();
        dto.setId(ar.getId());
        dto.setTeacherId(ar.getTeacherId());
        dto.setClassId(ar.getClassId());
        dto.setRequestType(ar.getRequestType());
        dto.setStatus(ar.getStatus());
        dto.setRequestedAt(ar.getRequestedAt());
        dto.setApprovedBy(ar.getApprovedBy());
        dto.setApprovedAt(ar.getApprovedAt());
        return dto;
    }
}
