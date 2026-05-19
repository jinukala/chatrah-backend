package com.chatrah.school.repository;

import com.chatrah.school.entity.SalaryStructure;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for teacher salary structures.
 */
@ApplicationScoped
public class SalaryStructureRepository implements PanacheRepository<SalaryStructure> {

    public SalaryStructure findByTeacherId(Long teacherId) {
        return find("teacherId", teacherId).firstResult();
    }
}
