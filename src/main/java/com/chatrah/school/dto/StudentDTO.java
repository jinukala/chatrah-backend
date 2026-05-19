// src/main/java/com/chatrah/school/dto/StudentDTO.java
package com.chatrah.school.dto;

import java.time.LocalDate;

/**
 * DTO representing a student record for listing and detail views.
 * Used by principal, clerk, teacher dashboards, and admin forms.
 */
public class StudentDTO {

    private Long id;
    private String studentUniqueId;
    private Integer rollNo;
    private String name;
    private String gender;
    private LocalDate dateOfBirth;
    private String parentName;
    private String fatherName;
    private String motherName;
    private String parentMobile;
    private String email;
    private String address;
    private LocalDate admissionDate;
    private Long classId;
    private String className;
    private String section;
    private Boolean isHosteller;
    private Boolean isTransportUser;
    private Boolean iitNeetOpted;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudentUniqueId() { return studentUniqueId; }
    public void setStudentUniqueId(String studentUniqueId) { this.studentUniqueId = studentUniqueId; }

    public Integer getRollNo() {
        return rollNo;
    }

    public void setRollNo(Integer rollNo) {
        this.rollNo = rollNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getParentName() {
        return parentName;
    }

    public void setParentName(String parentName) {
        this.parentName = parentName;
    }

    public String getFatherName() {
        return fatherName;
    }

    public void setFatherName(String fatherName) {
        this.fatherName = fatherName;
    }

    public String getMotherName() {
        return motherName;
    }

    public void setMotherName(String motherName) {
        this.motherName = motherName;
    }

    public String getParentMobile() {
        return parentMobile;
    }

    public void setParentMobile(String parentMobile) {
        this.parentMobile = parentMobile;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public LocalDate getAdmissionDate() {
        return admissionDate;
    }

    public void setAdmissionDate(LocalDate admissionDate) {
        this.admissionDate = admissionDate;
    }

    public Long getClassId() {
        return classId;
    }

    public void setClassId(Long classId) {
        this.classId = classId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

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

    private Integer feeConcession;
    public Integer getFeeConcession() { return feeConcession; }
    public void setFeeConcession(Integer feeConcession) { this.feeConcession = feeConcession; }
}
