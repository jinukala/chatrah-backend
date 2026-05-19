package com.chatrah.school.repository;

import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.Student;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for student data and class-wise queries.
 */
@ApplicationScoped
public class StudentRepository implements PanacheRepository<Student> {

    /**
     * List all students in a given ClassRoom.
     */
    public List<Student> findByClassRoom(ClassRoom classRoom) {
        return list("classRoom", classRoom);
    }

    /**
     * List all students by classRoomId (helper when only ID is known).
     */
    public List<Student> findByClassRoomId(Long classRoomId) {
        return list("classRoom.id", classRoomId);
    }
}
