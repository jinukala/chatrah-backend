package com.chatrah.school.repository;

import com.chatrah.school.entity.Course;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for advanced courses such as IIT/NEET, etc.
 */
@ApplicationScoped
public class CourseRepository implements PanacheRepository<Course> {

}
