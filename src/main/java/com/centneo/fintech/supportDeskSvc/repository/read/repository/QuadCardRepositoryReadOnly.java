package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.QuadCard;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.QuadCardRepository;

import java.util.List;

public interface QuadCardRepositoryReadOnly extends QuadCardRepository {

    List<QuadCard> findAllByTertiaryCardTid(Long tid);
}
