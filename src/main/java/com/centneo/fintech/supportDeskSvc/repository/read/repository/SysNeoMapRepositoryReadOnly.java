package com.centneo.fintech.supportDeskSvc.repository.read.repository;


import com.centneo.fintech.supportDeskSvc.model.primary.SysSlaMap;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.SysNeoMapRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysNeoMapRepositoryReadOnly extends SysNeoMapRepository {

    @Query("SELECT s FROM SysSlaMap s WHERE LOWER(s.priorityName) = LOWER(:priorityName)")
    Optional<SysSlaMap> findByPriorityName(@Param("priorityName") String priorityName);

}
