package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.BranchMaster;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.BranchMasterRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BranchMasterReadOnly extends BranchMasterRepository {

    BranchMaster findByBrCo(Integer brC);

    Optional<BranchMaster> findById(String trim);


    @Query(value =
            "SELECT b FROM BranchMaster b " +
                    "WHERE (:brCo IS NOT NULL AND b.brCo = :brCo) " +
                    "  OR (:brName IS NOT NULL AND LOWER(FUNCTION('TO_CHAR', b.brName)) LIKE :brName)"
    )
    List<BranchMaster> searchByBrCoOrBrName(
            @Param("brCo") Integer brCo,
            @Param("brName") String brName
    );
}
