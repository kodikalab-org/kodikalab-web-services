package com.kodika.kodikalab.service;

import com.kodika.kodikalab.dto.TeamResponse;
import com.kodika.kodikalab.dto.CreateTeamRequest;
import com.kodika.kodikalab.dto.JoinRequestDto;
import java.util.List;

public interface TeamService {

    TeamResponse createTeam(CreateTeamRequest request);

    Void requestJoin(Long teamId, JoinRequestDto request);

    Void reviewApplication(Long teamId, Long memberId);

    List<TeamResponse> listMembers(Long teamId);
}
