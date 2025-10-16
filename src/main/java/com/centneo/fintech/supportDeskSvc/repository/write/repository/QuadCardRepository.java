package com.centneo.fintech.supportDeskSvc.repository.write.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.QuadCard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface QuadCardRepository extends JpaRepository<QuadCard, Long> {
}
