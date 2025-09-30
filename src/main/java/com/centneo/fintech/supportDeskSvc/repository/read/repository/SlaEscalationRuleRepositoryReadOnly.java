package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.SlaEscalationRule;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.SlaEscalationRuleRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SlaEscalationRuleRepositoryReadOnly extends SlaEscalationRuleRepository {

    // This matches findFirstByPriorityIgnoreCase
    Optional<SlaEscalationRule> findFirstByPriorityIgnoreCase(String priority);

    Optional<SlaEscalationRule> findByPriorityAndCreatedByLevel(String priority, String createdByLevel);

    Optional<SlaEscalationRule> findFirstByPriorityIgnoreCaseAndInitialAssigneeLevelIgnoreCase(String priority, String currentLevel);
}
