package com.centneo.fintech.supportDeskSvc.repository.write.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.TicketDetails;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TicketDetailRepository extends JpaRepository<TicketDetails, Long> {
}
