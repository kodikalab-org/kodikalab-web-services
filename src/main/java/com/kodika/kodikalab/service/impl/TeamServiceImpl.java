package com.kodika.kodikalab.service.impl;

import com.kodika.kodikalab.dto.TeamResponse;
import com.kodika.kodikalab.dto.CreateTeamRequest;
import com.kodika.kodikalab.dto.JoinRequestDto;
import java.util.List;
import com.kodika.kodikalab.service.TeamService;
import org.springframework.stereotype.Service;

@Service
public class TeamServiceImpl implements TeamService {

    @Override
    public TeamResponse createTeam(CreateTeamRequest request) {
        return null;
    }
    @Override
    public Void requestJoin(Long teamId, JoinRequestDto request) {
        throw new UnsupportedOperationException("TODO: Implementar en Sprint 1");
    }
    @Override
    public Void reviewApplication(Long teamId, Long memberId) {
        throw new UnsupportedOperationException("TODO: Implementar en Sprint 1");
    }
    @Override
    public List<TeamResponse> listMembers(Long teamId) {
        return null;
    }
}
