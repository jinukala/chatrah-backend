package com.chatrah.school.repository;

import com.chatrah.school.entity.SubjectTeacher;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class SubjectTeacherRepository implements PanacheRepository<SubjectTeacher> {
    public List<SubjectTeacher> findByClassId(Long classId) {
        return list("classRoom.id", classId);
    }
}
