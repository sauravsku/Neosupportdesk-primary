package com.centneo.fintech.supportDeskSvc.services.businessRules;

import com.centneo.fintech.supportDeskSvc.enums.SupportLevelEnum;
import com.centneo.fintech.supportDeskSvc.model.primary.SlaEscalationRule;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.SlaEscalationRuleRepositoryReadOnly;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SlaRuleService {


    private final SlaEscalationRuleRepositoryReadOnly slaEscalationRuleRepositoryReadOnly;

    /**
     * Resolve initial rule for priority + createdByLevel.
     * Returns Optional.empty() when no rule found (caller handles fallback).
     */
    public Optional<SlaEscalationRule> resolveInitialRule(String priority, String createdByLevel) {
        if (priority == null || priority.isBlank()) return Optional.empty();
        final String p = priority.trim().toUpperCase();

        String levelNormalized = null;
        try {
            if (createdByLevel != null && !createdByLevel.isBlank()) {
                // SupportLevelEnum.fromLabel may throw for unknown labels: guard it
                SupportLevelEnum sl = SupportLevelEnum.fromLabel(createdByLevel);
                if (sl != null) {
                    levelNormalized = sl.name(); // use enum name (L1/L2...) — adapt if you prefer getCode()
                } else {
                    levelNormalized = createdByLevel.trim().toUpperCase();
                }
            }
        } catch (Exception ex) {
            // unknown createdByLevel — fallback to uppercased raw string
            levelNormalized = createdByLevel == null ? null : createdByLevel.trim().toUpperCase();
        }

        try {
            if (levelNormalized != null) {
                return slaEscalationRuleRepositoryReadOnly.findByPriorityAndCreatedByLevel(p, levelNormalized);
            }
            // try priority only
            return slaEscalationRuleRepositoryReadOnly.findFirstByPriorityIgnoreCase(p);
        } catch (Exception ex) {
            // repository error -> return empty and let caller decide
            return Optional.empty();
        }
    }

    public Integer resolveSlaDaysFor(String priority, String level) {
        try {
            return slaEscalationRuleRepositoryReadOnly.findFirstByPriorityIgnoreCaseAndInitialAssigneeLevelIgnoreCase(priority, level)
                    .map(SlaEscalationRule::getSlaDays)
                    .orElse(null);
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Path examples supported: "L1 -> L2 -> L3" or "L2 → L3" or "L1,L2,L3"
     * Returns next level as Optional<SupportLevelEnum>
     */
    public Optional<SupportLevelEnum> nextLevelFromPath(String path, String currentLevel) {
        if (path == null || path.isBlank()) return Optional.empty();

        String norm = path.replace("→", "->").replace("—", "->").replace("–", "->");
        String[] steps = Arrays.stream(norm.split("->|,"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);

        if (steps.length == 0) return Optional.empty();

        if (currentLevel == null || currentLevel.isBlank()) {
            // return first step
            return safeParseSupportLevel(steps[0]);
        }

        // find current and return next
        for (int i = 0; i < steps.length - 1; i++) {
            if (steps[i].equalsIgnoreCase(currentLevel.trim())) {
                return safeParseSupportLevel(steps[i + 1]);
            }
        }

        // current level not found or already last -> empty
        return Optional.empty();
    }

    /**
     * Helper: safely convert token (like "L1" or "Level-1") into SupportLevelEnum if possible.
     */
    private Optional<SupportLevelEnum> safeParseSupportLevel(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            // try fromCode then fromLabel or name depending on your enum API
            // adapt these calls to your SupportLevelEnum impl if methods differ
            try {
                return Optional.of(SupportLevelEnum.fromCode(token.trim()));
            } catch (Exception ex) {
                // ignore and try fromLabel
            }
            try {
                return Optional.of(SupportLevelEnum.fromLabel(token.trim()));
            } catch (Exception ex) {
                // ignore and fallback to name
            }
            // fallback: try matching by enum name (L1, L2, etc.)
            return Optional.of(SupportLevelEnum.valueOf(token.trim().toUpperCase()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    public Optional<SupportLevelEnum> nextLevelFor(String priority, String currentLevel) {
        try {
            return slaEscalationRuleRepositoryReadOnly.findFirstByPriorityIgnoreCaseAndInitialAssigneeLevelIgnoreCase(priority, currentLevel)
                    .flatMap(r -> nextLevelFromPath(r.getEscalationPath(), currentLevel));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }


    public Optional<SlaEscalationRule> getNextEscalationPath(String bumpedPriority, String nextLevel) {

        try {
            return slaEscalationRuleRepositoryReadOnly.findFirstByPriorityIgnoreCaseAndInitialAssigneeLevelIgnoreCase(bumpedPriority, nextLevel);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
