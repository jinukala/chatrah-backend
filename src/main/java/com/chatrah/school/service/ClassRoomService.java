package com.chatrah.school.service;

import com.chatrah.school.dto.ClassRoomDTO;
import com.chatrah.school.entity.ClassRoom;
import com.chatrah.school.repository.ClassRoomRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD operations for classes & sections.
 */
@ApplicationScoped
public class ClassRoomService {

    @Inject
    ClassRoomRepository classRoomRepository;

    public List<ClassRoomDTO> listAll() {
        return classRoomRepository.listAll()
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    public ClassRoomDTO getById(Long id) {
        ClassRoom cr = classRoomRepository.findById(id);
        if (cr == null) throw new NotFoundException("Class not found");
        return toDTO(cr);
    }

    @Transactional
    public ClassRoomDTO createOrUpdate(ClassRoomDTO dto) {
        ClassRoom entity;
        if (dto.getId() != null) {
            entity = classRoomRepository.findById(dto.getId());
            if (entity == null) throw new NotFoundException("Class not found");
        } else {
            entity = new ClassRoom();
        }
        entity.setClassName(dto.getClassName());
        entity.setSection(dto.getSection());
        entity.setClassTeacherId(dto.getClassTeacherId());
        entity.setSubjects(dto.getSubjects());
        classRoomRepository.persist(entity);
        return toDTO(entity);
    }

    @Transactional
    public void delete(Long id) {
        ClassRoom entity = classRoomRepository.findById(id);
        if (entity != null) {
            classRoomRepository.delete(entity);
        }
    }

    private ClassRoomDTO toDTO(ClassRoom cr) {
        ClassRoomDTO dto = new ClassRoomDTO();
        dto.setId(cr.getId());
        dto.setClassName(cr.getClassName());
        dto.setSection(cr.getSection());
        dto.setClassTeacherId(cr.getClassTeacherId());
        dto.setSubjects(cr.getSubjects());
        return dto;
    }

    public ClassRoom findById(Long classId) {
        return classRoomRepository.findById(classId);
    }

    public List<ClassRoom> findAll() {
        return classRoomRepository.listAll();
    }
}
