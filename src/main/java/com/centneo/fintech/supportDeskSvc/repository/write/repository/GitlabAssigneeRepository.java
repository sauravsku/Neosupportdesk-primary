package com.centneo.fintech.supportDeskSvc.repository.write.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.GitlabAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GitlabAssigneeRepository extends JpaRepository<GitlabAssignee, Long> {
}
