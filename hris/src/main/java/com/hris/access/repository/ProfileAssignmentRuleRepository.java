package com.hris.access.repository;

import com.hris.access.entity.ProfileAssignmentRule;
import com.hris.access.enums.StructuralEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProfileAssignmentRuleRepository extends JpaRepository<ProfileAssignmentRule, UUID> {

    List<ProfileAssignmentRule> findByTriggerEventAndIsActiveTrueOrderByPriorityAsc(StructuralEventType triggerEvent);

    List<ProfileAssignmentRule> findAllByOrderByTriggerEventAscPriorityAsc();
}
