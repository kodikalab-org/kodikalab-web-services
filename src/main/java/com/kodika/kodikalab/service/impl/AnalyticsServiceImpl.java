package com.kodika.kodikalab.service.impl;

import com.kodika.kodikalab.dto.ProgressResponse;
import com.kodika.kodikalab.dto.StandingDto;
import com.kodika.kodikalab.dto.WeaknessReportDto;
import java.util.List;
import com.kodika.kodikalab.service.AnalyticsService;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    @Override
    public List<ProgressResponse> getProgressByTopic(Long teamId) {
        return null;
    }
    @Override
    public List<StandingDto> getTeamRanking(Long teamId) {
        return null;
    }
    @Override
    public WeaknessReportDto getWeaknesses(Long teamId) {
        return null;
    }
    @Override
    public ProgressResponse getIndependentProgress() {
        return null;
    }
}
