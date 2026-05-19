package com.chatrah.school.repository;

import com.chatrah.school.entity.AccessRequest;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for access requests raised by teachers (e.g. fee detail access).
 */
@ApplicationScoped
public class AccessRequestRepository implements PanacheRepository<AccessRequest> {

    public List<AccessRequest> findPending() {
        return list("status", "PENDING");
    }
}
