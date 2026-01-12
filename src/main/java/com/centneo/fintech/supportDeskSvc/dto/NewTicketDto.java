package com.centneo.fintech.supportDeskSvc.dto;

import java.time.LocalDateTime;

public record NewTicketDto(

        String ticketId,
        String sid,
        Long tid,
        Long qid,

        String cif,
        String issueTitle,
        String contactNumber,
        String issueId,
        String issueSubTypeId,
        String issueCategory,
        String actionId,
        String priority,
        LocalDateTime loggedDatetime,

        String callLog,
        String followUpComments,
        Boolean ckccRenewalValue,

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
        BranchInfoDto branchInfo,

        String department,
        String module,
        String product,
        String subProduct
) { }
