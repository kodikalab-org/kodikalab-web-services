
package com.kodika.kodikalab.architecture;

import com.kodika.kodikalab.competitions.category.Category;
import com.kodika.kodikalab.competitions.competition.Competition;
import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblem;
import com.kodika.kodikalab.competitions.problemresolution.ProblemResolution;
import com.kodika.kodikalab.problems.material.Material;
import com.kodika.kodikalab.problems.problem.Problem;
import com.kodika.kodikalab.problems.problemtopic.ProblemTopic;
import com.kodika.kodikalab.problems.topic.Topic;
import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.users.User;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RuntimeBoundaryTests {

    @Autowired
    ApplicationContext context;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Autowired
    RequestMappingHandlerMapping mappings;

    @Autowired
    MockMvc mvc;

    @Test
    void onlyOfficialErdPersistenceIsRegistered() {

        assertThat(entityManagerFactory.getMetamodel().getEntities().stream()
                .map(entity -> entity.getJavaType().getName()).toList())
                .containsExactlyInAnyOrder(
                        User.class.getName(),
                        PractitionerProfile.class.getName(),
                        CoachProfile.class.getName(),
                        StudyGroup.class.getName(),
                        GroupMembership.class.getName(),
                        Competition.class.getName(),
                        CompetitionProblem.class.getName(),
                        ProblemResolution.class.getName(),
                        Category.class.getName(),
                        Problem.class.getName(),
                        Topic.class.getName(),
                        ProblemTopic.class.getName(),
                        Material.class.getName()
                );

        assertThat(context.getBeansOfType(JpaRepository.class).keySet())
                .containsExactlyInAnyOrder(
                        "userRepository",
                        "practitionerProfileRepository",
                        "coachProfileRepository",
                        "studyGroupRepository",
                        "groupMembershipRepository",
                        "competitionRepository",
                        "competitionProblemRepository",
                        "problemResolutionRepository",
                        "categoryRepository",
                        "problemRepository",
                        "topicRepository",
                        "problemTopicRepository",
                        "materialRepository"
                );

        assertThat(context.containsBean("legacyAuthController")).isFalse();
        assertThat(context.containsBean("legacyAuthService")).isFalse();
    }

    @Test
    void onlyImplementedBusinessEndpointsArePublished() {

        assertThat(mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName()
                        .startsWith("com.kodika.kodikalab"))
                .flatMap(entry -> entry.getKey().getPatternValues().stream())
                .toList())
                .containsExactlyInAnyOrder(
                        "/auth/register",
                        "/auth/login",
                        "/users/me",
                        "/users/me",
                        "/teams",
                        "/teams",
                        "/teams/{id}/join",
                        "/teams/{id}/memberships",
                        "/teams/{id}/memberships/{memberId}"
                );
    }

    @Test
    void removedPlaceholderEndpointsReturn404InsteadOfFakeSuccess()
            throws Exception {

        // GET /teams ahora lista los grupos disponibles.
        mvc.perform(get("/api/teams").contextPath("/api"))
                .andExpect(status().isOk());

        for (String path : new String[]{
                "/teams/1/members",
                "/problems",
                "/problems/assigned",
                "/competitions",
                "/analytics/teams/1/topics"
        }) {
            mvc.perform(get("/api" + path).contextPath("/api"))
                    .andExpect(status().isNotFound());
        }

        mvc.perform(post("/api/assistant/query").contextPath("/api")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
