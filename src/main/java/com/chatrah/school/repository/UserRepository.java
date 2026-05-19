package com.chatrah.school.repository;

import com.chatrah.school.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for managing User entities and login lookups.
 */
@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    /**
     * Find a user by username (used during login and password reset).
     *
     * @param username unique login username
     * @return matching User or null if not found
     */
    public User findByUsername(String username) {
        return find("username", username).firstResult();
    }
}
