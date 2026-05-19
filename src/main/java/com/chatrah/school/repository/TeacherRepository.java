package com.chatrah.school.repository;

import com.chatrah.school.entity.Teacher;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for teacher entities.
 */
@ApplicationScoped
public class TeacherRepository implements PanacheRepository<Teacher> {

}
