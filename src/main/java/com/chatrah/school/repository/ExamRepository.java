package com.chatrah.school.repository;

import com.chatrah.school.entity.Exam;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for exam definitions.
 */
@ApplicationScoped
public class ExamRepository implements PanacheRepository<Exam> {

}
