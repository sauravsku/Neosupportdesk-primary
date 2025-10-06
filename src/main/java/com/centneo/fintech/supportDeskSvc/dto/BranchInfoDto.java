package com.centneo.fintech.supportDeskSvc.dto;

public record BranchInfoDto(
        String branchCode,
        String branchName,
        String branchNo,
        String category,
        String district,
        String emailId,
        String liveFlag
) {
}
