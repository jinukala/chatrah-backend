// src/main/java/com/chatrah/school/service/CacheManagementService.java
package com.chatrah.school.service;

import io.quarkus.cache.CacheInvalidateAll;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Central service for clearing application caches.
 * Methods are triggered via REST by PRINCIPAL / SYS_ADMIN when needed.
 */
@ApplicationScoped
public class CacheManagementService {

    @CacheInvalidateAll(cacheName = "fee-summary")
    public void clearAllFeeSummaries() {
        // no-op body
    }

    @CacheInvalidateAll(cacheName = "attendance-summary")
    public void clearAllAttendanceSummaries() {
        // no-op body
    }

    @CacheInvalidateAll(cacheName = "class-students")
    public void clearAllClassStudents() {
        // no-op body
    }

    @CacheInvalidateAll(cacheName = "school-profile")
    public void clearSchoolProfile() {
        // no-op body
    }

    public void clearAllCaches() {
        clearAllFeeSummaries();
        clearAllAttendanceSummaries();
        clearAllClassStudents();
        clearSchoolProfile();
    }
}
