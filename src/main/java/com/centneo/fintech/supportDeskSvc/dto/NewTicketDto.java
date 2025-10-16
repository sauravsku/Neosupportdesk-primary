package com.centneo.fintech.supportDeskSvc.dto;

import java.time.LocalDateTime;

public record NewTicketDto(

        String ticketId,
        String sid,

        String issueTitle,
        String ipPhoneDetails,
        String issueId,
        String issueSubTypeId,
        String issueCategory,
        String actionId,
        String priority,
        LocalDateTime loggedDatetime,

        String callLog,
        String followUpComments,

        String currentAssignee,
        String currentAssigneeSL,

        String ticketRequester,
        String ticketRequesterSL,

        String currStatus,
        LocalDateTime resolvedDt,

        Boolean breachedFlag,
        Boolean escalatedFlag,
        String currentEscLevel,
        Long currTat,
        Integer slaDays,
        LocalDateTime slaDueDatetime,
        String escalationPath,
        String resolutionNote,
        GitLabDto gitlab,
        BranchInfoDto branchInfo
) { }
