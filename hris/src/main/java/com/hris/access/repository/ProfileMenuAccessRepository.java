package com.hris.access.repository;

import com.hris.access.entity.ProfileMenuAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProfileMenuAccessRepository extends JpaRepository<ProfileMenuAccess, UUID> {

    List<ProfileMenuAccess> findByProfileId(UUID profileId);

    List<ProfileMenuAccess> findByProfileIdIn(Collection<UUID> profileIds);

    @Modifying
    @Query("DELETE FROM ProfileMenuAccess pma WHERE pma.profileId = :profileId")
    void deleteByProfileId(@Param("profileId") UUID profileId);
}
