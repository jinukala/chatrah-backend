package com.chatrah.school.service;

import com.chatrah.school.dto.EventDTO;
import com.chatrah.school.entity.Event;
import com.chatrah.school.repository.EventRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles school events for public page and dashboards.
 */
@ApplicationScoped
public class EventService {

    @Inject
    EventRepository eventRepository;

    public List<EventDTO> listUpcoming() {
        return eventRepository.findUpcoming()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public EventDTO createOrUpdate(EventDTO dto, Long creatorUserId) {
        Event e;
        if (dto.getId() != null) {
            e = eventRepository.findById(dto.getId());
            if (e == null) throw new NotFoundException("Event not found");
        } else {
            e = new Event();
        }
        e.setTitle(dto.getTitle());
        e.setDescription(dto.getDescription());
        e.setEventDate(dto.getEventDate());
        if (e.getCreatedBy() == null) {
            e.setCreatedBy(creatorUserId);
        }
        eventRepository.persist(e);
        return toDTO(e);
    }

    private EventDTO toDTO(Event e) {
        EventDTO dto = new EventDTO();
        dto.setId(e.getId());
        dto.setTitle(e.getTitle());
        dto.setDescription(e.getDescription());
        dto.setEventDate(e.getEventDate());
        return dto;
    }
}
