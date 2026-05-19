// src/main/java/com/chatrah/school/repository/AdvancedCourseRepository.java
package com.chatrah.school.repository;

import com.chatrah.school.entity.AdvancedCourse;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class AdvancedCourseRepository implements PanacheRepository<AdvancedCourse> {

    public List<AdvancedCourse> findActive() {
        return list("isActive", true);
    }
}
