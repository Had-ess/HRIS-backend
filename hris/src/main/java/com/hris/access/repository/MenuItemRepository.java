package com.hris.access.repository;

import com.hris.access.entity.MenuItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {

    Page<MenuItem> findAllByOrderBySectionCodeAscDisplayOrderAsc(Pageable pageable);

    List<MenuItem> findByIdIn(Collection<UUID> ids);

    List<MenuItem> findAllByIsActiveTrueOrderBySectionCodeAscDisplayOrderAsc();

    Optional<MenuItem> findByCodeIgnoreCase(String code);
}
