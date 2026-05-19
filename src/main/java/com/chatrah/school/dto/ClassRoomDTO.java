// src/main/java/com/chatrah/school/dto/ClassRoomDTO.java
package com.chatrah.school.dto;

/**
 * DTO for representing a class and section combination
 * such as "10 - A" that is used in dropdowns and filters.
 */
public class ClassRoomDTO {

    private Long id;

    /** The class name (e.g., "1", "2", "10"). */
    private String className;

    /** The section name (e.g., "A", "B"). */
    private String section;

    /** Optional assigned class teacher ID. */
    private Long classTeacherId;

    /** Comma-separated subjects. */
    private String subjects;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Long getClassTeacherId() {
        return classTeacherId;
    }

    public void setClassTeacherId(Long classTeacherId) {
        this.classTeacherId = classTeacherId;
    }

    public String getSubjects() { return subjects; }
    public void setSubjects(String subjects) { this.subjects = subjects; }
}
