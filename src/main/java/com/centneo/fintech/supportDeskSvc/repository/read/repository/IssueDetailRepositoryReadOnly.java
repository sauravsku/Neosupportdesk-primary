package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.IssueDetail;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.IssueDetailRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IssueDetailRepositoryReadOnly extends IssueDetailRepository {

    List<IssueDetail> findAllBySecondaryCardSid(Long sid);
}
