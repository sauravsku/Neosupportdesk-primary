package com.centneo.fintech.supportDeskSvc.services.gitlab;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class GitlabServiceFactory {

    private final DlpGitlabService dlpService;
    private final KycGitlabService kycService;
    private final OmniGitlabService omniService;

    public GitlabServiceFactory(
            @Qualifier("gitlabDlpService") DlpGitlabService dlpService,
            @Qualifier("gitlabKycService") KycGitlabService kycService,
            @Qualifier("gitlabOmniService") OmniGitlabService omniService
    ) {
        this.dlpService = dlpService;
        this.kycService = kycService;
        this.omniService = omniService;
    }

    public AbstractGitlabService getServiceByProjectId(int id) {
        return switch (id) {
            case 1 -> dlpService;
            case 2 -> omniService;
            case 3 -> kycService;
            default -> throw new IllegalArgumentException("Invalid project id: " + id);
        };
    }
}
