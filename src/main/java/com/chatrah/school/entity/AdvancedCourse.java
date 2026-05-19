package com.chatrah.school.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "advanced_course")
public class AdvancedCourse {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String name;
    public String description;

    public Integer baseFee;

    public Boolean isActive = true;
}
