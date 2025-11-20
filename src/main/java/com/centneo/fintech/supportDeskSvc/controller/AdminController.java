package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.CnsdMapDto;
import com.centneo.fintech.supportDeskSvc.services.admin.ISysNeoMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin")
public class AdminController {

    @Autowired
    private ISysNeoMap iSysNeoMap;

    @PostMapping("set-cnsd-map")
    public ResponseEntity<ResponseDto> setCnsdMap(@RequestBody List<CnsdMapDto> cnsdMapDto) {

        try {
            return iSysNeoMap.setCnsdMap(cnsdMapDto);
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
}
