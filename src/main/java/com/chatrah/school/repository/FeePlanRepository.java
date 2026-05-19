package com.chatrah.school.repository;

import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.entity.FeePlan;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for fee plans configured per class.
 */
@ApplicationScoped
public class FeePlanRepository implements PanacheRepository<FeePlan> {

    public FeePlan findByClassRoom(ClassRoom classRoom) {
        return find("classRoom", classRoom).firstResult();
    }

    public FeePlan findByClassRoomId(Long classId) {
        return find("classRoom.id", classId).firstResult();
    }
}
