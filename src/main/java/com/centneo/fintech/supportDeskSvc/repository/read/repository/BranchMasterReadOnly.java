package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.BranchMaster;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.BranchMasterRepository;

public interface BranchMasterReadOnly extends BranchMasterRepository {

    BranchMaster findByBrCo(Integer brC);
}
