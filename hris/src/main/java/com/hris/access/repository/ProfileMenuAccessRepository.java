package com.hris.access.repository;

import com.hris.access.entity.ProfileMenuAccess;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProfileMenuAccessRepository extends JpaRepository<ProfileMenuAccess, UUID> {

    List<ProfileMenuAccess> findByProfileId(UUID profileId);

    List<ProfileMenuAccess> findByProfileIdIn(Collection<UUID> profileIds);

    void deleteByProfileId(UUID profileId);
}
