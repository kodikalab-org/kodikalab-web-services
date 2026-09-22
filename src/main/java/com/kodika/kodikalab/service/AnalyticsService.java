package com.kodika.kodikalab.service;

import com.kodika.kodikalab.dto.ProgressResponse;
import com.kodika.kodikalab.dto.StandingDto;
import com.kodika.kodikalab.dto.WeaknessReportDto;
import java.util.List;

public interface AnalyticsService {

    List<ProgressResponse> getProgressByTopic(Long teamId);

    List<StandingDto> getTeamRanking(Long teamId);

    WeaknessReportDto getWeaknesses(Long teamId);

    ProgressResponse getIndependentProgress();
}
