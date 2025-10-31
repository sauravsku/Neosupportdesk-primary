package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.TicketDetails;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TicketDetailRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TicketDetailRepositoryReadOnly extends TicketDetailRepository {

    List<TicketDetails> findByTicketId(String ticketId);

}
