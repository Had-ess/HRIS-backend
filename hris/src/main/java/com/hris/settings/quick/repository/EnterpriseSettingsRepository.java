package com.hris.settings.quick.repository;

import com.hris.settings.quick.entity.EnterpriseSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EnterpriseSettingsRepository extends JpaRepository<EnterpriseSettings, UUID> {

    Optional<EnterpriseSettings> findFirstBySingletonKeyTrue();
}
