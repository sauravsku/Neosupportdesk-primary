package com.centneo.fintech.supportDeskSvc.dto;

public record IssueSubDetailDto(
        Long issueSubTypeId,
        String issueName,
        String issueDesc,
        String issueExt1,
        String issueExt2,
        String issueExt3,
        String issueExt4,
        String issueExt5,
        Long issueDetailId   // reference to parent IssueDetail
) {}
