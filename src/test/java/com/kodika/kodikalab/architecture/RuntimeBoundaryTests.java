package com.kodika.kodikalab.architecture;

import com.kodika.kodikalab.profiles.coach.CoachProfile;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
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

/** Update these boundaries deliberately when a new domain is implemented, not for empty scaffolding. */
@SpringBootTest
@AutoConfigureMockMvc
class RuntimeBoundaryTests {
    @Autowired ApplicationContext context;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired RequestMappingHandlerMapping mappings;
    @Autowired MockMvc mvc;

    @Test
    void onlyImplementedPersistenceIsRegistered() {
        assertThat(entityManagerFactory.getMetamodel().getEntities().stream()
                .map(entity -> entity.getJavaType().getName()).toList())
                .containsExactlyInAnyOrder(User.class.getName(), PractitionerProfile.class.getName(),
                        CoachProfile.class.getName());
        assertThat(context.getBeansOfType(JpaRepository.class).keySet())
                .containsExactlyInAnyOrder("userRepository", "practitionerProfileRepository",
                        "coachProfileRepository");
        assertThat(context.containsBean("legacyAuthController")).isFalse();
        assertThat(context.containsBean("legacyAuthService")).isFalse();
    }

    @Test
    void onlyImplementedBusinessEndpointsArePublished() {
        assertThat(mappings.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName()
                        .startsWith("com.kodika.kodikalab"))
                .flatMap(entry -> entry.getKey().getPatternValues().stream()).toList())
                .containsExactlyInAnyOrder("/auth/register", "/auth/login", "/users/me", "/users/me");
    }

    @Test
    void removedPlaceholderEndpointsReturn404InsteadOfFakeSuccess() throws Exception {
        for (String path : new String[]{"/teams/1/members", "/problems/assigned",
                "/analytics/teams/1/topics"}) {
            mvc.perform(get("/api" + path).contextPath("/api")).andExpect(status().isNotFound());
        }
        mvc.perform(post("/api/assistant/query").contextPath("/api")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }
}
