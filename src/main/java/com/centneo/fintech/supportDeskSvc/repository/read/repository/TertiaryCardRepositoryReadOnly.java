package com.centneo.fintech.supportDeskSvc.repository.read.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.TertiaryCard;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.TertiaryCardRepository;

import java.util.List;

public interface TertiaryCardRepositoryReadOnly extends TertiaryCardRepository {

    List<TertiaryCard> findAllBySecondaryCardSid(Long sid);
}
