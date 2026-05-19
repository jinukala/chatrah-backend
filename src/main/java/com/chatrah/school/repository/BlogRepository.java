package com.chatrah.school.repository;

import com.chatrah.school.entity.Blog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Repository for blog posts and moderation.
 */
@ApplicationScoped
public class BlogRepository implements PanacheRepository<Blog> {

    public List<Blog> findApproved() {
        return list("status", "APPROVED");
    }

    public List<Blog> findPending() {
        return list("status", "PENDING");
    }
}
