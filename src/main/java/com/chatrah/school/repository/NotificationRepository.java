// src/main/java/com/chatrah/school/repository/NotificationRepository.java
package com.chatrah.school.repository;

import com.chatrah.school.entity.Notification;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NotificationRepository implements PanacheRepository<Notification> {
}
