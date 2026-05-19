package com.chatrah.school.repository;

import com.chatrah.school.entity.ClassRoom;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for class & section definitions.
 */
@ApplicationScoped
public class ClassRoomRepository implements PanacheRepository<ClassRoom> {

    public List<ClassRoom> findByClassName(String className) {
        return list("className", className);
    }

    public ClassRoom findByClassAndSection(String className, String section) {
        return find("className = ?1 and section = ?2", className, section).firstResult();
    }
}
