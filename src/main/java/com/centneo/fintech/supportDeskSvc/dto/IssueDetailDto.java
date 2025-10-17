// ======= IssueDetailDto.java =======
package com.centneo.fintech.supportDeskSvc.dto;

import java.util.List;

public record IssueDetailDto(
        Long issueId,
        String issueName,
        String issueDesc,
        String issueExt1,
        String issueExt2,
        String issueExt3,
        String issueExt4,
        String issueExt5,
        Long catId,
        List<IssueSubDetailDto> issueSubDetails,
        Long sid,
        Long tid,
        Long fid

) {}
