package com.hris.access.repository;

import com.hris.access.entity.AccessProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessProfileRepository extends JpaRepository<AccessProfile, UUID> {

    Page<AccessProfile> findAllByOrderByDisplayKeyAsc(Pageable pageable);

    List<AccessProfile> findByIdIn(Collection<UUID> ids);

    Optional<AccessProfile> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);
}
