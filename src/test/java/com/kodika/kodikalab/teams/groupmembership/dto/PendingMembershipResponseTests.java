package com.kodika.kodikalab.teams.groupmembership.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.kodika.kodikalab.profiles.practitioner.PractitionerLevel;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
import com.kodika.kodikalab.teams.groupmembership.MembershipStatus;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import com.kodika.kodikalab.users.User;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PendingMembershipResponseTests {
    static final OffsetDateTime REQUESTED = OffsetDateTime.parse("2026-10-09T19:00:00-05:00");
    final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void carriesTheApplicantsAcademicAndCompetitiveProfile() {
        var response = PendingMembershipResponse.from(pending());

        assertThat(response.membershipId()).isEqualTo(2);
        assertThat(response.groupId()).isEqualTo(1);
        assertThat(response.practitionerId()).isEqualTo(5);
        assertThat(response.status()).isEqualTo(MembershipStatus.PENDIENTE);
        assertThat(response.requestedAt()).isEqualTo(REQUESTED);
        assertThat(response.practitioner()).isEqualTo(new PendingMembershipResponse.Applicant("Ana Prueba", "U2020001",
                "Ingeniería de Software", 5, PractitionerLevel.INTERMEDIO, "tourist", 3800, null, "ana_vj"));
    }

    @Test
    void serializedFormKeepsTheOriginalFieldsAndNeverRevealsTheEmailOrThePasswordHash() throws Exception {
        JsonNode node = json.readTree(json.writeValueAsString(PendingMembershipResponse.from(pending())));

        assertThat(node.get("membershipId").asInt()).isEqualTo(2);
        assertThat(node.get("practitionerId").asInt()).isEqualTo(5);
        assertThat(node.get("practitioner").get("fullName").asText()).isEqualTo("Ana Prueba");
        assertThat(node.get("practitioner").get("studentCode").asText()).isEqualTo("U2020001");
        assertThat(node.get("practitioner").get("competitiveLevel").asText()).isEqualTo("INTERMEDIO");
        assertThat(node.get("practitioner").get("codeforcesRating").asInt()).isEqualTo(3800);
        assertThat(node.get("practitioner").get("atcoderHandle").isNull()).isTrue();
        assertThat(node.toString()).doesNotContain("ana@gmail.com").doesNotContain("$2a$").doesNotContain("email")
                .doesNotContain("password");
    }

    private static GroupMembership pending() {
        User user = new User();
        user.setId(5);
        user.setFullName("Ana Prueba");
        user.setEmail("ana@gmail.com");
        user.setPasswordHash("$2a$10$hash-que-no-debe-salir");
        PractitionerProfile profile = new PractitionerProfile();
        profile.setUserId(5);
        profile.setUser(user);
        profile.setStudentCode("U2020001");
        profile.setCareer("Ingeniería de Software");
        profile.setAcademicCycle(5);
        profile.setCompetitiveLevel(PractitionerLevel.INTERMEDIO);
        profile.setCodeforcesHandle("tourist");
        profile.setCodeforcesRating(3800);
        profile.setVjudgeHandle("ana_vj");
        StudyGroup group = new StudyGroup();
        group.setId(1);
        GroupMembership membership = new GroupMembership();
        membership.setId(2);
        membership.setGroup(group);
        membership.setPractitioner(profile);
        membership.setStatus(MembershipStatus.PENDIENTE);
        membership.setJoinedAt(REQUESTED);
        return membership;
    }
}
