package com.kodika.kodikalab.competitions.officialresult;

import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultRequest;
import com.kodika.kodikalab.competitions.officialresult.dto.OfficialResultResponse;
import java.util.List;

public interface OfficialResultService {
    OfficialResultResponse create(Integer competitionId, OfficialResultRequest request);

    OfficialResultResponse update(Integer competitionId, OfficialResultRequest request);

    OfficialResultResponse get(Integer competitionId);

    List<OfficialResultResponse> history(Integer teamId);
}
