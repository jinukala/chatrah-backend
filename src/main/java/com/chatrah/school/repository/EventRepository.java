package com.chatrah.school.repository;

import com.chatrah.school.entity.Event;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository for school events.
 */
@ApplicationScoped
public class EventRepository implements PanacheRepository<Event> {

    /**
     * List upcoming events (today or future), ordered by date ascending.
     */
    public List<Event> findUpcoming() {
        return list("eventDate >= ?1 order by eventDate asc", LocalDate.now());
    }
}
