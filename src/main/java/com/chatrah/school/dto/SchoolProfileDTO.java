// src/main/java/com/chatrah/school/dto/SchoolProfileDTO.java
package com.chatrah.school.dto;

/**
 * DTO representing the school's branding and contact information.
 * Used in the header, login page, and principal's settings screen.
 */
public class SchoolProfileDTO {

    private Long id;

    /** Full name of the school as seen by all users. */
    private String schoolName;

    /** Optional URL of the school logo. */
    private String logoUrl;

    /** School motto or tagline. */
    private String motto;

    /** Postal address (single formatted string). */
    private String address;

    /** Primary contact phone number of the school. */
    private String contactPhone;

    /** Primary contact email address of the school. */
    private String contactEmail;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSchoolName() {
        return schoolName;
    }

    public void setSchoolName(String schoolName) {
        this.schoolName = schoolName;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getMotto() {
        return motto;
    }

    public void setMotto(String motto) {
        this.motto = motto;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
}
