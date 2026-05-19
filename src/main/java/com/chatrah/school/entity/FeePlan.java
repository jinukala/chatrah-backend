package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents default fee structure for a class.
 * totalFee = base academic fee for a day-scholar (no hostel, no transport).
 * hostelFee and transportFee are additional components applied per student.
 */
@Entity
@Table(name = "fee_plan")
public class FeePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Class to which this fee plan applies. */
    @ManyToOne
    @JoinColumn(name = "class_room_id", nullable = false)
    private ClassRoom classRoom;

    /**
     * Base fee amount for the class (day scholar academic fee).
     * Hostel and transport will be added on top of this if applicable.
     */
    @Column(nullable = false)
    private Integer totalFee;

    /** Additional fee per student if they are a hosteller. */
    @Column(name = "hostel_fee", nullable = false)
    private Integer hostelFee = 0;

    /** Additional fee per student if they use school transport. */
    @Column(name = "transport_fee", nullable = false)
    private Integer transportFee = 0;

    /** Additional fee for IIT/NEET batch students. */
    @Column(name = "iit_neet_fee", nullable = false)
    private Integer iitNeetFee = 0;

    /** Optional description/notes about this fee plan. */
    private String description;

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

    public ClassRoom getClassRoom() { return classRoom; }

    public void setClassRoom(ClassRoom classRoom) { this.classRoom = classRoom; }

    public Integer getTotalFee() { return totalFee; }

    public void setTotalFee(Integer totalFee) { this.totalFee = totalFee; }

    public Integer getHostelFee() { return hostelFee; }

    public void setHostelFee(Integer hostelFee) { this.hostelFee = hostelFee; }

    public Integer getTransportFee() { return transportFee; }

    public void setTransportFee(Integer transportFee) { this.transportFee = transportFee; }

    public Integer getIitNeetFee() { return iitNeetFee; }
    public void setIitNeetFee(Integer iitNeetFee) { this.iitNeetFee = iitNeetFee; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
