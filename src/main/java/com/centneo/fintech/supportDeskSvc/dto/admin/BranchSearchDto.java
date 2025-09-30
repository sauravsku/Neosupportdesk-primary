package com.centneo.fintech.supportDeskSvc.dto.admin;

public record BranchSearchDto(
        String branchCode,
        String branchName,
        String regionalOffice,
        String zonalOffice,
        String state,
        String district,
        String emailId,
        String category,
        String brSize,
        String liveFag) {
}
