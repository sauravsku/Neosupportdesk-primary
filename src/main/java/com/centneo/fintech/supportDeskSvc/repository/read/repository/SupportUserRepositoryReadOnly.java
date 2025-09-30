package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.SupportUser;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.SupportUserRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportUserRepositoryReadOnly extends SupportUserRepository {

    // find eligible users for a level ordered by currentAssigned asc then lastAssignedAt asc
    @Query("SELECT u FROM SupportUser u WHERE u.supportLevel = :level AND u.active = true AND u.currentAssigned < u.capacity ORDER BY u.currentAssigned ASC, u.lastAssignedAt ASC NULLS FIRST")
    //@Lock(LockModeType.PESSIMISTIC_READ) //lock rows while deciding assignments. This reduces race conditions.Ordering ensures we pick the least-loaded and least-recently-used user (round-robin-ish)
    List<SupportUser> findEligibleForLevelForUpdate(@Param("level") String level);

    // fallback: find any active users (no capacity check) - also locked
    @Query("SELECT u FROM SupportUser u WHERE u.supportLevel = :level AND u.active = true ORDER BY u.currentAssigned ASC, u.lastAssignedAt ASC NULLS FIRST")
   // @Lock(LockModeType.PESSIMISTIC_READ)
    List<SupportUser> findActiveForLevelForUpdate(@Param("level") String level);
}
