package com.hris.auth.repository;

import com.hris.auth.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

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
