package com.centneo.fintech.supportDeskSvc.repository.write.repository;

import com.centneo.fintech.supportDeskSvc.model.primary.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notifications, Long> {
}
