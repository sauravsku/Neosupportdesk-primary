package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.*;
import com.centneo.fintech.supportDeskSvc.dto.admin.BranchMasterDto;
import com.centneo.fintech.supportDeskSvc.model.primary.BranchMaster;
import com.centneo.fintech.supportDeskSvc.services.IDataInput;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("input")
public class DataInputController {

    @Autowired
    private IDataInput iDataInput;


    @PostMapping("primary")
    public ResponseEntity<ResponseDto> setPrimaryData(@RequestBody PrimaryCardDto primaryCardDto) {

        try {
            return iDataInput.setPrimaryData(primaryCardDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("secondary")
    public ResponseEntity<ResponseDto> setSecondaryData(@RequestBody SecondaryCardDto secondaryCardDto) {

        try {
            return iDataInput.setSecondaryData(secondaryCardDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("tertiary")
    public ResponseEntity<ResponseDto> setTertiaryData(@RequestBody TertiaryCardDto tertiaryCardDto) {

        try {
            return iDataInput.setTertiaryData(tertiaryCardDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("quad")
    public ResponseEntity<ResponseDto> setQuadData(@RequestBody QuadCardDto quadCardDto) {

        try {
            return iDataInput.setQuadData(quadCardDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("issue-detail")
    public ResponseEntity<ResponseDto> setIssueData(@RequestBody List<IssueDetailDto> issueDetailDto) {

        try {
            return iDataInput.setIssueDetailsData(issueDetailDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("set-actions")
    public ResponseEntity<ResponseDto> setActions(@RequestBody List<ActionsDto> actionsDtos) {

        try {
            return iDataInput.setActions(actionsDtos);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("issue-sub-detail")
    public ResponseEntity<ResponseDto> setSubIssueData(@RequestBody List<IssueSubDetailDto> issueSubDetailDto) {

        try {
            return iDataInput.setSubIssueData(issueSubDetailDto);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("escalation/history")
    public ResponseEntity<ResponseDto> getEscHistory(@RequestParam("username") String username) {

        try {
            return iDataInput.getEscHistory(username);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("setBranchMaster")
    public ResponseEntity<ResponseDto> setBranchMasterData(@RequestBody List<BranchMasterDto>  branchMasterDtos) {

        try {
            return iDataInput.setBranchMasterData(branchMasterDtos);
        } catch (Exception e) {
            return null;
        }
    }
}
