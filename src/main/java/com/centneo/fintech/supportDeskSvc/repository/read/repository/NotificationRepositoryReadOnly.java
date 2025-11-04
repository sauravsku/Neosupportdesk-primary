package com.centneo.fintech.supportDeskSvc.repository.read.repository;


import com.centneo.fintech.supportDeskSvc.model.primary.Notifications;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.NotificationRepository;

import java.util.List;

public interface NotificationRepositoryReadOnly extends NotificationRepository {

    List<Notifications> findAllByUsername(String username);
}
