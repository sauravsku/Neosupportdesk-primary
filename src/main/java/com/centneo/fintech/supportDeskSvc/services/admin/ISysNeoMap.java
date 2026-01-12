package com.centneo.fintech.supportDeskSvc.services.admin;

import com.centneo.fintech.supportDeskSvc.dto.FeedbackDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import com.centneo.fintech.supportDeskSvc.dto.admin.CnsdMapDto;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface ISysNeoMap {

    ResponseEntity<ResponseDto> setCnsdMap(List<CnsdMapDto> cnsdMapDto);

    ResponseEntity<ResponseDto> getAllCnsdMap();

    ResponseEntity<ResponseDto> getSearchAssignee(String query, String supportLevel, String currentUserLevel,
                                                  String prioritySelected);


    ResponseEntity<ResponseDto> getBranchInfo(String query);

    ResponseEntity<ResponseDto> getStagesCount(String username);

    ResponseEntity<ResponseDto> getDashboardData(String username);

    ResponseEntity<ResponseDto> getUserInsights(String username);

    ResponseEntity<ResponseDto> setUserFeedback(FeedbackDto feedbackDto);
}
