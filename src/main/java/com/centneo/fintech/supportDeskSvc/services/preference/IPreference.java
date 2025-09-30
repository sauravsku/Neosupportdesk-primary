package com.centneo.fintech.supportDeskSvc.services.preference;

import com.centneo.fintech.supportDeskSvc.dto.JourneyDataDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import org.springframework.http.ResponseEntity;

public interface IPreference {


    ResponseEntity<Object> getUserJourneyData(JourneyDataDto journeyDataDto);

    ResponseEntity<ResponseDto> getPrimaryData(String username);

    ResponseEntity<ResponseDto> getSecondaryData(Long pid);

    ResponseEntity<ResponseDto> getIssueDetailsData(Long sid);

    ResponseEntity<ResponseDto> getActionsData(Character mode);
}
