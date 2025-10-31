package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.GitlabAssignee;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.GitlabAssigneeRepository;

import java.util.List;
import java.util.Optional;

public interface GitlabAssigneeRepositoryReadOnly extends GitlabAssigneeRepository {

    List<GitlabAssignee> findByPid(Long aLong);
}
