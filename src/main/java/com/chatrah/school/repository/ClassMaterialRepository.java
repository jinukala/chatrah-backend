package com.chatrah.school.repository;

import com.chatrah.school.entity.ClassMaterial;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;

@ApplicationScoped
public class ClassMaterialRepository implements PanacheRepository<ClassMaterial> {
    public List<ClassMaterial> findByClassId(Long classId) {
        return list("classRoom.id", io.quarkus.panache.common.Sort.descending("createdAt"), classId);
    }
    public List<ClassMaterial> findByClassAndSubject(Long classId, String subject) {
        return list("classRoom.id = ?1 and subject = ?2", io.quarkus.panache.common.Sort.descending("createdAt"), classId, subject);
    }
}
