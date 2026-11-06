package com.kodika.kodikalab.competitions;

import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.profiles.CurrentUserResolver;
import com.kodika.kodikalab.teams.groupmembership.GroupMembershipService;
import jakarta.persistence.EntityManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.kodika.kodikalab.competitions.competition.*;
import com.kodika.kodikalab.competitions.competitionproblem.*;
import com.kodika.kodikalab.teams.studygroup.StudyGroupService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeamNavigationServiceTests {
    StudyGroupService groups = mock(StudyGroupService.class);
    CompetitionRepository competitions = mock(CompetitionRepository.class);
    CompetitionProblemRepository problems = mock(CompetitionProblemRepository.class);
    CompetitionServiceImpl competitionService = new CompetitionServiceImpl(competitions, groups,
            mock(CurrentUserResolver.class), mock(PasswordEncoder.class), mock(EntityManager.class),
            mock(GroupMembershipService.class));
    CompetitionProblemServiceImpl problemService = new CompetitionProblemServiceImpl(problems, mock(EntityManager.class), groups);

    @Test
    void authorizesBeforeReadingAnyCompetitionOrAssignment() {
        doThrow(new ForbiddenException("Equipo ajeno")).when(groups).requireAccessibleTeam(2);
        assertThatThrownBy(() -> competitionService.findByTeamId(2)).isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> problemService.listByTeamId(2)).isInstanceOf(ForbiddenException.class);
        verifyNoInteractions(competitions, problems);
    }

    @Test
    void listsCompetitionFieldsWithoutAccessKeysAndKeepsEmptyLists() {
        Competition competition = new Competition();
        competition.setId(3);
        competition.setEventName("Evento");
        competition.setStatus(CompetitionStatus.FINALIZADA);
        competition.setAccessKey("private");
        when(competitions.findByGroupIdOrderByStartsAtDescIdDesc(1)).thenReturn(List.of(competition));
        var result = competitionService.findByTeamId(1).getFirst();
        assertThat(result.competitionId()).isEqualTo(3);
        assertThat(result.teamId()).isEqualTo(1);
        assertThat(result.eventName()).isEqualTo("Evento");
        when(problems.findAvailableByTeamId(1)).thenReturn(List.of());
        assertThat(problemService.listByTeamId(1)).isEmpty();
        verify(groups, times(2)).requireAccessibleTeam(1);
    }
}
