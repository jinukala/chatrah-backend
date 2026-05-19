package com.chatrah.school.repository;

import com.chatrah.school.entity.FeePayment;
import com.chatrah.school.entity.Student;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for fee payment transactions.
 */
@ApplicationScoped
public class FeePaymentRepository implements PanacheRepository<FeePayment> {

    public List<FeePayment> findByStudent(Student student) {
        return list("student", student);
    }

    public List<FeePayment> findByStudentId(Long studentId) {
        return list("student.id", studentId);
    }

    /**
     * Compute total amount paid by a student so far.
     */
    public long sumPaidForStudent(Long studentId) {
        return find("student.id = ?1 and status = ?2", studentId, "SUCCESS")
                .project(Long.class)
                .stream()
                .mapToLong(fpId -> {
                    FeePayment fp = findById(fpId);
                    return fp != null ? fp.getAmount() : 0;
                })
                .sum();
    }
}
