package com.chatrah.school.repository;

import com.chatrah.school.entity.SchoolProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for school branding and profile configuration.
 */
@ApplicationScoped
public class SchoolProfileRepository implements PanacheRepository<SchoolProfile> {

    /**
     * Retrieve the single SchoolProfile row, if any.
     */
    public SchoolProfile getCurrentProfile() {
        return findAll().firstResult();
    }
}
