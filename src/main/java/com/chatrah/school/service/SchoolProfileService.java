package com.chatrah.school.service;

import com.chatrah.school.dto.SchoolProfileDTO;
import com.chatrah.school.entity.SchoolProfile;
import com.chatrah.school.repository.SchoolProfileRepository;
import io.quarkus.cache.CacheInvalidateAll;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Manages school branding & contact details.
 */
@ApplicationScoped
public class SchoolProfileService {

    @Inject
    SchoolProfileRepository schoolProfileRepository;

    @CacheResult(cacheName = "school-profile")
    public SchoolProfileDTO getProfile() {
        SchoolProfile entity = schoolProfileRepository.getCurrentProfile();
        if (entity == null) {
            return new SchoolProfileDTO(); // empty defaults
        }
        return toDTO(entity);
    }

    @Transactional
    @CacheInvalidateAll(cacheName = "school-profile")
    public SchoolProfileDTO updateProfile(SchoolProfileDTO dto) {
        SchoolProfile entity = schoolProfileRepository.getCurrentProfile();
        if (entity == null) {
            entity = new SchoolProfile();
        }
        entity.setSchoolName(dto.getSchoolName());
        entity.setLogoUrl(dto.getLogoUrl());
        entity.setMotto(dto.getMotto());
        entity.setAddress(dto.getAddress());
        entity.setContactPhone(dto.getContactPhone());
        entity.setContactEmail(dto.getContactEmail());
        schoolProfileRepository.persist(entity);
        return toDTO(entity);
    }

    private SchoolProfileDTO toDTO(SchoolProfile entity) {
        SchoolProfileDTO dto = new SchoolProfileDTO();
        dto.setId(entity.getId());
        dto.setSchoolName(entity.getSchoolName());
        dto.setLogoUrl(entity.getLogoUrl());
        dto.setMotto(entity.getMotto());
        dto.setAddress(entity.getAddress());
        dto.setContactPhone(entity.getContactPhone());
        dto.setContactEmail(entity.getContactEmail());
        return dto;
    }
    @CacheInvalidateAll(cacheName = "school-profile")
    public void updateProfile(SchoolProfile updated) {
        schoolProfileRepository.persist(updated);
    }
}
