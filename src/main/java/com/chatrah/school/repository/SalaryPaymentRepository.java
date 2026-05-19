package com.chatrah.school.repository;

import com.chatrah.school.entity.SalaryPayment;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for salary payment transactions.
 */
@ApplicationScoped
public class SalaryPaymentRepository implements PanacheRepository<SalaryPayment> {

    public List<SalaryPayment> findByTeacherId(Long teacherId) {
        return list("teacherId", teacherId);
    }
}
