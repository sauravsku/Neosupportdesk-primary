package com.centneo.fintech.supportDeskSvc.services.preference;

import com.centneo.fintech.supportDeskSvc.dto.HeaderDetailsDto;
import com.centneo.fintech.supportDeskSvc.dto.JourneyDataDto;
import com.centneo.fintech.supportDeskSvc.dto.ResponseDto;
import org.springframework.http.ResponseEntity;

public interface IPreference {


    ResponseEntity<Object> getUserJourneyData(JourneyDataDto journeyDataDto);

    ResponseEntity<ResponseDto> getPrimaryData(String username);

    ResponseEntity<ResponseDto> getSecondaryData(Long pid);

    ResponseEntity<ResponseDto> getIssueDetailsData(Long sid);

    ResponseEntity<ResponseDto> getActionsData(Character mode);

    ResponseEntity<ResponseDto> getTertiaryData(Long sid);

    ResponseEntity<ResponseDto> getHeaderDetails(HeaderDetailsDto headerDetailsDto);

    ResponseEntity<ResponseDto> getQuadData(Long tid);

    Boolean getCardRules(Long quadCardName);

    ResponseEntity<ResponseDto> getDlpIssueDetailsData(Long fid);

    ResponseEntity<ResponseDto> getMenuCounts(String username);

    ResponseEntity<ResponseDto> getFaqs();
}
