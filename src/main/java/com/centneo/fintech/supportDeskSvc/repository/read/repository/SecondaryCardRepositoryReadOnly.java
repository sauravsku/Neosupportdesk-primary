package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.SecondaryCard;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.SecondaryCardRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SecondaryCardRepositoryReadOnly extends SecondaryCardRepository {

    List<SecondaryCard> findAllByPrimaryCardPid(Long pid);

    Optional<SecondaryCard> findBySid(Long sid);
}
