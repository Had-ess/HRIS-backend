package com.hris.access.repository;

import com.hris.access.entity.ProfilePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProfilePermissionRepository extends JpaRepository<ProfilePermission, UUID> {

    List<ProfilePermission> findByProfileId(UUID profileId);

    List<ProfilePermission> findByProfileIdIn(Collection<UUID> profileIds);

    boolean existsByPermissionId(UUID permissionId);

    boolean existsByGrantedById(UUID userId);

    void deleteByProfileId(UUID profileId);
}
