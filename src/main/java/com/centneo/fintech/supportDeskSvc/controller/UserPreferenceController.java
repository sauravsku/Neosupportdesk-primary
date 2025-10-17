// com.centneo.fintech.supportDeskSvc.controller.UserPreferenceController.java
package com.centneo.fintech.supportDeskSvc.controller;

import com.centneo.fintech.supportDeskSvc.dto.HeaderDetailsDto;
import com.centneo.fintech.supportDeskSvc.dto.JourneyDataDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.services.preference.IPreference;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("preference")
@RequiredArgsConstructor
public class UserPreferenceController {

    private final IPreference preference;

    @PostMapping(value = "/data", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> getUserJourneyData(@RequestBody JourneyDataDto journeyDataDto) {
        try {
            return preference.getUserJourneyData(journeyDataDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/getPrimaryData")
    public ResponseEntity<ResponseDto> getPrimaryData(@RequestParam("username") String username) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getPrimaryData(username);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching primary data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/getSecondaryData")
    public ResponseEntity<ResponseDto> getSecondaryData(@RequestParam("pid") Long pid) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getSecondaryData(pid);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching primary data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/getHeaderDetails")
    public ResponseEntity<ResponseDto> getHeaderDetails(@RequestBody HeaderDetailsDto headerDetailsDto) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getHeaderDetails(headerDetailsDto);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching primary data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }


    @GetMapping("/getTertiaryData")
    public ResponseEntity<ResponseDto> getTertiaryData(@RequestParam("sid") Long sid) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getTertiaryData(sid);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching primary data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }


    @GetMapping("/getCardRules")
    public Boolean getCardRules(@RequestParam("quadCardName") Long quadCardName) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getCardRules(quadCardName);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            return false;
        }
    }

    @GetMapping("/getQuadData")
    public ResponseEntity<ResponseDto> getQuadData(@RequestParam("tid") Long tid) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getQuadData(tid);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching primary data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/getIssueDetailsData")
    public ResponseEntity<ResponseDto> getIssueDetailsData(@RequestParam("sid") Long sid) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getIssueDetailsData(sid);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching primary data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/getDlpIssueDetailsData")
    public ResponseEntity<ResponseDto> getDlpIssueDetailsData(@RequestParam("fid") Long fid) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getDlpIssueDetailsData(fid);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching primary data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("getActionsData")
    public ResponseEntity<ResponseDto> getActionsData(@RequestParam("mode") Character mode) {
        try {
            // Call service method that already returns ResponseEntity<ResponseDto>
            return preference.getActionsData(mode);
        } catch (Exception e) {
            // Return a proper ResponseDto for errors
            ResponseDto errorResponse = new ResponseDto(
                    false,
                    "Error fetching primary data: " + e.getMessage(),
                    null,
                    400
            );
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}
