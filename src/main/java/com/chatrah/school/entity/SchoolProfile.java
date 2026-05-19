package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Stores school-level branding and contact details.
 * There is typically a single row per school deployment.
 */
@Entity
@Table(name = "school_profile")
public class SchoolProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Display name of the school used across the UI. */
    @Column(nullable = false)
    private String schoolName;

    /** URL or path to the school logo image. */
    private String logoUrl;

    /** Short motto or tagline of the school. */
    private String motto;

    /** Postal address of the school. */
    private String address;

    /** Primary contact phone number. */
    private String contactPhone;

    /** Primary contact email address. */
    private String contactEmail;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and setters

    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public String getSchoolName() { return schoolName; }

    public void setSchoolName(String schoolName) { this.schoolName = schoolName; }

    public String getLogoUrl() { return logoUrl; }

    public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }

    public String getMotto() { return motto; }

    public void setMotto(String motto) { this.motto = motto; }

    public String getAddress() { return address; }

    public void setAddress(String address) { this.address = address; }

    public String getContactPhone() { return contactPhone; }

    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactEmail() { return contactEmail; }

    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
