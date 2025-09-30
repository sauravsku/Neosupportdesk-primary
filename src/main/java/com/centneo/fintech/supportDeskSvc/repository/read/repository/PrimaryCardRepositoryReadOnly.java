package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.PrimaryCard;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.PrimaryCardRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PrimaryCardRepositoryReadOnly extends PrimaryCardRepository {

    List<PrimaryCard> findByJourneyIdIn(List<Long> journeyId);

    Optional<PrimaryCard> findTopByOrderByJourneyIdDesc();

    @Query("SELECT p FROM PrimaryCard p WHERE pid=:pid")
    Optional<PrimaryCard> findByPid(@Param("pid") Long pid);
}
