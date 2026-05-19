package com.chatrah.school.service;

import com.chatrah.school.dto.SalaryPaymentDTO;
import com.chatrah.school.dto.SalaryStructureDTO;
import com.chatrah.school.entity.SalaryPayment;
import com.chatrah.school.entity.SalaryStructure;
import com.chatrah.school.repository.SalaryPaymentRepository;
import com.chatrah.school.repository.SalaryStructureRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages salary structure and payments to teachers.
 */
@ApplicationScoped
public class SalaryService {

    @Inject
    SalaryStructureRepository salaryStructureRepository;

    @Inject
    SalaryPaymentRepository salaryPaymentRepository;

    @Transactional
    public SalaryStructureDTO setSalaryStructure(SalaryStructureDTO dto) {
        SalaryStructure s = salaryStructureRepository.findByTeacherId(dto.getTeacherId());
        if (s == null) {
            s = new SalaryStructure();
        }
        s.setTeacherId(dto.getTeacherId());
        s.setBaseSalary(dto.getBaseSalary());
        s.setPaidLeaves(dto.getPaidLeaves());
        salaryStructureRepository.persist(s);

        dto.setId(s.getId());
        return dto;
    }

    @Transactional
    public SalaryPaymentDTO paySalary(Long teacherId, Integer amount, String month, String mode) {
        SalaryPayment sp = new SalaryPayment();
        sp.setTeacherId(teacherId);
        sp.setAmount(amount);
        sp.setMonth(month);
        sp.setMode(mode != null ? mode : "BANK_TRANSFER");
        sp.setStatus("SUCCESS");
        sp.setPaidOn(LocalDateTime.now());
        sp.setTransactionId("SAL-" + UUID.randomUUID());
        sp.setUtrNumber("UTR-" + UUID.randomUUID());
        salaryPaymentRepository.persist(sp);

        return toDTO(sp);
    }

    public List<SalaryPaymentDTO> listPaymentsForTeacher(Long teacherId) {
        return salaryPaymentRepository.findByTeacherId(teacherId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    private SalaryPaymentDTO toDTO(SalaryPayment sp) {
        SalaryPaymentDTO dto = new SalaryPaymentDTO();
        dto.setId(sp.getId());
        dto.setTeacherId(sp.getTeacherId());
        dto.setAmount(sp.getAmount());
        dto.setMonth(sp.getMonth());
        dto.setMode(sp.getMode());
        dto.setStatus(sp.getStatus());
        dto.setPaidOn(sp.getPaidOn());
        dto.setTransactionId(sp.getTransactionId());
        dto.setUtrNumber(sp.getUtrNumber());
        return dto;
    }
}
