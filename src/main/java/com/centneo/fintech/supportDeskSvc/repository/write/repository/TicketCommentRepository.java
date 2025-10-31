package com.centneo.fintech.supportDeskSvc.repository.write.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.TicketComments;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketCommentRepository extends JpaRepository<TicketComments, Long> {
    // you can also add projections to load only subset of fields if needed
}
