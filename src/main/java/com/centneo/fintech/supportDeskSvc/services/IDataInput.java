package com.centneo.fintech.supportDeskSvc.services;

import com.centneo.fintech.supportDeskSvc.dto.*;
import com.centneo.fintech.supportDeskSvc.dto.admin.BranchMasterDto;
import com.centneo.fintech.supportDeskSvc.model.primary.BranchMaster;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface IDataInput {

    ResponseEntity<ResponseDto> setPrimaryData(PrimaryCardDto primaryCardDto);

    ResponseEntity<ResponseDto> setSecondaryData(SecondaryCardDto secondaryCardDto);

    ResponseEntity<ResponseDto> setIssueDetailsData(List<IssueDetailDto> issueDetailDto);

    ResponseEntity<ResponseDto> setSubIssueData(List<IssueSubDetailDto> issueSubDetailDto);

    ResponseEntity<ResponseDto> setActions(List<ActionsDto> actionsDtos);

    ResponseEntity<ResponseDto> getEscHistory(String username);

    ResponseEntity<ResponseDto> setBranchMasterData(List<BranchMasterDto>  branchMasterDtos);
}
