package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.FeedbackDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.UserMetaDataDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.CnsdMapDto;
import com.centneo.fintech.supportDeskSvc.services.DataInputService;
import com.centneo.fintech.supportDeskSvc.services.IDataInput;
import com.centneo.fintech.supportDeskSvc.services.admin.ISysNeoMap;
import com.centneo.fintech.supportDeskSvc.services.businessRules.schedulers.SupportUserSyncService;
import com.centneo.fintech.supportDeskSvc.services.external.PrimaryApiSvc;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin")
@AllArgsConstructor
public class AdminController {

    private final ISysNeoMap iSysNeoMap;

    private final PrimaryApiSvc primaryApiSvc;

    private final SupportUserSyncService supportUserSyncService;

    private final IDataInput iDataInput;

    @PostMapping("set-cnsd-map")
    public ResponseEntity<ResponseDto> setCnsdMap(@RequestBody List<CnsdMapDto> cnsdMapDto) {

        try {
            return iSysNeoMap.setCnsdMap(cnsdMapDto);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("submit-feedback")
    public ResponseEntity<ResponseDto> submitFeedback(@RequestBody FeedbackDto feedbackDto) {

        try {
            return iSysNeoMap.setUserFeedback(feedbackDto);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("get-cnsd-map")
    public ResponseEntity<ResponseDto> getAllCnsdMap() {

        try {
            return iSysNeoMap.getAllCnsdMap();
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("searchAssignee")
    public ResponseEntity<ResponseDto> getSearchAssignee(@RequestParam("query") String query,
                                                         @RequestParam("supportLevel") String supportLevel,
                                                         @RequestParam("currentUserLevel") String currentUserLevel,
                                                         @RequestParam("prioritySelected") String prioritySelected) {

        try {
            return iSysNeoMap.getSearchAssignee(query, supportLevel, currentUserLevel, prioritySelected);
        } catch (Exception e) {
            return null;
        }

    }

    @GetMapping("searchBranchInfo")
    public ResponseEntity<ResponseDto> getBranchInfo(@RequestParam("brCo") String query) {

        try {
            return iSysNeoMap.getBranchInfo(query);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("getStagesCount")
    public ResponseEntity<ResponseDto> getStagesCount(@RequestParam("username") String username) {

        try {
            return iSysNeoMap.getStagesCount(username);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("getDashboardData")
    public ResponseEntity<ResponseDto> getDashboardData(@RequestParam("username") String username) {

        try {
            return iSysNeoMap.getDashboardData(username);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("user-insights")
    public ResponseEntity<ResponseDto> getUserInsights(@RequestParam("username") String username) {

        try {
            return iSysNeoMap.getUserInsights(username);
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("sync-support-user")
    public ResponseEntity<ResponseDto> syncSupportUser() {

        // Run the sync process
        supportUserSyncService.syncSupportUsers();

        // Fetch active support users
        List<UserMetaDataDto> userMetaDataDtos =
                primaryApiSvc.getAllActiveSupportUsers();

        // Prepare response body
        ResponseDto responseDto = new ResponseDto(true, "success", userMetaDataDtos, 200);

        // Return as ResponseEntity
        return ResponseEntity.ok(responseDto);
    }
}
