package com.hris.admin.repository;
import com.hris.admin.entity.AdminRequestType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;
@Repository
public interface AdminRequestTypeRepository extends JpaRepository<AdminRequestType, UUID> {}
