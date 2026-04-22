package com.hris.auth.repository;

import com.hris.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    List<Role> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, UUID id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    @Query(value = """
        WITH RECURSIVE role_tree AS (
          SELECT id, code, parent_id, 0 AS level
          FROM roles WHERE parent_id IS NULL
          UNION ALL
          SELECT r.id, r.code, r.parent_id, rt.level + 1
          FROM roles r JOIN role_tree rt ON r.parent_id = rt.id
        )
        SELECT level FROM role_tree WHERE id = :roleId
        """, nativeQuery = true)
    Integer computeLevel(@Param("roleId") UUID roleId);
}
