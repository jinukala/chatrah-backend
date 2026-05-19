package com.chatrah.school.repository;

import com.chatrah.school.entity.NotificationLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Repository for notification logs (SMS/email).
 */
@ApplicationScoped
public class NotificationLogRepository implements PanacheRepository<NotificationLog> {

}
