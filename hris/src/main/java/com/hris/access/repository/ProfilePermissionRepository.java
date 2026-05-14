package com.hris.access.repository;

import com.hris.access.entity.ProfilePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProfilePermissionRepository extends JpaRepository<ProfilePermission, UUID> {

    List<ProfilePermission> findByProfileId(UUID profileId);

    @Query("""
            SELECT pp FROM ProfilePermission pp
            JOIN FETCH pp.permission
            WHERE pp.profileId IN :profileIds
            """)
    List<ProfilePermission> findByProfileIdIn(@Param("profileIds") Collection<UUID> profileIds);

    boolean existsByPermissionId(UUID permissionId);

    boolean existsByGrantedById(UUID userId);

    void deleteByProfileId(UUID profileId);
}
