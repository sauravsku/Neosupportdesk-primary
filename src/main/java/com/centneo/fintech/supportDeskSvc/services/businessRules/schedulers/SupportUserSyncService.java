package com.centneo.fintech.supportDeskSvc.services.businessRules.schedulers;

import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.UserMetaDataDto;
import com.centneo.fintech.supportDeskSvc.model.primary.SupportUser;
import com.centneo.fintech.supportDeskSvc.repository.read.repository.SupportUserRepositoryReadOnly;
import com.centneo.fintech.supportDeskSvc.repository.write.repository.SupportUserRepository;
import com.centneo.fintech.supportDeskSvc.services.external.PrimaryApiSvc;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SupportUserSyncService {

    private final PrimaryApiSvc primaryApiSvc;
    private final SupportUserRepository supportUserRepository;
    private final SupportUserRepositoryReadOnly supportUserRepositoryReadOnly;

    /**
     * Scheduled method to sync users every hour.
     */
//    @Scheduled(fixedDelay = 30000) // every hour
//    public void scheduledSync() {
//        syncSupportUsers();
//    }

    @Scheduled(fixedDelayString = "${userSync.scheduler.delay-ms:30000}")
    @Transactional
    public void syncSupportUsers() {

        List<UserMetaDataDto> userMetaDataDtos = primaryApiSvc.getAllActiveSupportUsers();
        List<SupportUser> supportUsers = supportUserRepositoryReadOnly.findAll();

        for (UserMetaDataDto userMetaDataDto : userMetaDataDtos) {
            boolean found = supportUsers.stream()
                    .anyMatch(supportUser -> supportUser.getUserId().equals(userMetaDataDto.userId()));
            if (found) {
                SupportUser su = supportUserRepositoryReadOnly.findById(userMetaDataDto.userId())
                        .get();

                su.setUserId(userMetaDataDto.userId());
                su.setUsername(userMetaDataDto.username());
                su.setSupportLevel(userMetaDataDto.supportLevel());
                su.setActive(true); // Default
                su.setCapacity(100); // Default or configurable
                if (su.getCurrentAssigned() == null) su.setCurrentAssigned(0);
                supportUserRepository.save(su);
            } else {
                SupportUser su = new SupportUser();
                su.setUserId(userMetaDataDto.userId());
                su.setUsername(userMetaDataDto.username());
                su.setSupportLevel(userMetaDataDto.supportLevel());
                su.setActive(true); // Default
                su.setCapacity(100); // Default or configurable
                if (su.getCurrentAssigned() == null)
                    su.setCurrentAssigned(0);
                supportUserRepository.save(su);
            }

        }
    }
}
