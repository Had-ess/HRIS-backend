package com.hris.auth.repository;

import com.hris.auth.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    List<Permission> findAllByOrderByResourceAscActionAsc();

    List<Permission> findByIdInAndIsActiveTrue(Collection<UUID> ids);

    Optional<Permission> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    boolean existsByResourceIgnoreCaseAndActionIgnoreCaseAndScopeIgnoreCase(
        String resource,
        String action,
        String scope
    );

    boolean existsByResourceIgnoreCaseAndActionIgnoreCaseAndScopeIgnoreCaseAndIdNot(
        String resource,
        String action,
        String scope,
        UUID id
    );
}
