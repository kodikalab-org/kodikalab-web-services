package com.kodika.kodikalab.teams;

import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import com.kodika.kodikalab.teams.studygroup.dto.MyTeamResponse;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/teams")
public class StudyGroupController {
    private final StudyGroupService studyGroupService;
    private final GroupMembershipService groupMembershipService;

    public StudyGroupController(StudyGroupService studyGroupService,
                                GroupMembershipService groupMembershipService) {
        this.studyGroupService = studyGroupService;
        this.groupMembershipService = groupMembershipService;
    }
    @GetMapping("/me")
    public List<MyTeamResponse> myTeams() {
        return studyGroupService.findMyTeams();
    }
}
