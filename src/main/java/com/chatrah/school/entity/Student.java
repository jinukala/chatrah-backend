package com.chatrah.school.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a student enrolled in the school.
 * Linked to a ClassRoom and optionally to advanced courses.
 */
@Entity
@Table(name = "students")
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique student ID like SVV01A001 (School+Class+Section+Roll) */
    @Column(name = "student_unique_id", unique = true)
    private String studentUniqueId;

    /** Roll number within the class. */
    private Integer rollNo;

    /** Full name of the student. */
    @Column(nullable = false)
    private String name;

    /** Gender for basic demographic analytics (optional). */
    private String gender;

    private LocalDate dateOfBirth;

    /** Parent or guardian name. */
    private String parentName;

    /** Father's name. */
    private String fatherName;

    /** Mother's name. */
    private String motherName;

    /** Parent or guardian mobile number. */
    private String parentMobile;

    private String email;

    private String address;

    private LocalDate admissionDate;

    /** Class and section where the student currently belongs. */
    @ManyToOne
    @JoinColumn(name = "class_room_id")
    private ClassRoom classRoom;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(name = "is_hosteller")
    private Boolean isHosteller = false;

    @Column(name = "is_transport_user")
    private Boolean isTransportUser = false;

    @Column(name = "iit_neet_opted")
    private Boolean iitNeetOpted = false;

    /** Fee concession/discount amount for this student */
    @Column(name = "fee_concession")
    private Integer feeConcession = 0;

    // getters/setters

    public Boolean getIsHosteller() {
        return isHosteller;
    }

    public void setIsHosteller(Boolean isHosteller) {
        this.isHosteller = isHosteller;
    }

    public Boolean getIsTransportUser() {
        return isTransportUser;
    }

    public void setIsTransportUser(Boolean isTransportUser) {
        this.isTransportUser = isTransportUser;
    }

    public Boolean getIitNeetOpted() { return iitNeetOpted; }
    public void setIitNeetOpted(Boolean iitNeetOpted) { this.iitNeetOpted = iitNeetOpted; }

    public Integer getFeeConcession() { return feeConcession; }
    public void setFeeConcession(Integer feeConcession) { this.feeConcession = feeConcession; }



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


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentUniqueId() { return studentUniqueId; }
    public void setStudentUniqueId(String studentUniqueId) { this.studentUniqueId = studentUniqueId; }

    public Integer getRollNo() { return rollNo; }

    public void setRollNo(Integer rollNo) { this.rollNo = rollNo; }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public String getGender() { return gender; }

    public void setGender(String gender) { this.gender = gender; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }

    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

    public String getParentName() { return parentName; }

    public void setParentName(String parentName) { this.parentName = parentName; }

    public String getFatherName() { return fatherName; }

    public void setFatherName(String fatherName) { this.fatherName = fatherName; }

    public String getMotherName() { return motherName; }

    public void setMotherName(String motherName) { this.motherName = motherName; }

    public String getParentMobile() { return parentMobile; }

    public void setParentMobile(String parentMobile) { this.parentMobile = parentMobile; }

    public String getEmail() { return email; }

    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }

    public void setAddress(String address) { this.address = address; }

    public LocalDate getAdmissionDate() { return admissionDate; }

    public void setAdmissionDate(LocalDate admissionDate) { this.admissionDate = admissionDate; }

    public ClassRoom getClassRoom() { return classRoom; }

    public void setClassRoom(ClassRoom classRoom) { this.classRoom = classRoom; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
