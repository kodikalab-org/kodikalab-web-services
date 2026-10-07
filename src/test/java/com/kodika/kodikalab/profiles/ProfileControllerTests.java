package com.kodika.kodikalab.profiles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.common.exception.UnauthorizedException;
import com.kodika.kodikalab.profiles.coach.CoachProfileService;
import com.kodika.kodikalab.profiles.coach.dto.CoachProfileResponse;
import com.kodika.kodikalab.profiles.practitioner.PractitionerLevel;
import com.kodika.kodikalab.profiles.practitioner.PractitionerProfileService;
import com.kodika.kodikalab.profiles.practitioner.dto.PractitionerProfileResponse;
import com.kodika.kodikalab.users.Role;
import com.kodika.kodikalab.users.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProfileControllerTests {
    private static final String VALID = """
            {"codigoEstudiante":"20240001","carrera":"Ingeniería de Software","cicloAcademico":5,
             "nivelCompetitivo":"INTERMEDIO","codeforcesHandle":"tourist","codeforcesRating":0,
             "atcoderHandle":"tourist_atcoder","vjudgeHandle":"usuario_vjudge"}
            """;
    private static final String VALID_COACH = """
            {"especialidadPrincipal":"Grafos y Programación Dinámica",
             "organizacionClub":"Club de Programación Competitiva","aniosExperiencia":4,
             "presentacion":"Entrenador de maratones ICPC."}
            """;
    CurrentUserResolver currentUser;
    PractitionerProfileService service;
    CoachProfileService coachService;
    MockMvc mvc;
    LocalValidatorFactoryBean validator;
    ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
    User practitioner = user(7, Role.PRACTICANTE);
    User coach = user(8, Role.COACH);

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUserResolver.class);
        service = mock(PractitionerProfileService.class);
        coachService = mock(CoachProfileService.class);
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        ProfileRequestReader reader = new ProfileRequestReader(mapper, validator);
        mvc = MockMvcBuilders.standaloneSetup(new ProfileController(currentUser, reader, service, coachService))
                .setControllerAdvice(new ProfileExceptionHandler()).build();
        when(currentUser.currentUser()).thenReturn(practitioner);
    }

    @AfterEach
    void close() {
        validator.close();
    }

    @Test
    void updateAcceptsOnlyErdProfileFields() throws Exception {
        when(service.savePractitionerProfile(eq(practitioner), any())).thenReturn(new PractitionerProfileResponse(
                "Perfil actualizado correctamente",
                new PractitionerProfileResponse.ProfileData("test@gmail.com", Role.PRACTICANTE, "20240001",
                        "Ingeniería de Software", 5, PractitionerLevel.INTERMEDIO, "tourist", 0,
                        "tourist_atcoder", "usuario_vjudge")));
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Perfil actualizado correctamente"))
                .andExpect(jsonPath("$.profile.role").value("PRACTICANTE"))
                .andExpect(jsonPath("$.profile.codeforcesHandle").value("tourist"))
                .andExpect(jsonPath("$.profile.atcoderHandle").value("tourist_atcoder"))
                .andExpect(jsonPath("$.profile.vjudgeHandle").value("usuario_vjudge"))
                .andExpect(jsonPath("$.profile.leetcodeHandle").doesNotExist())
                .andExpect(jsonPath("$.profile.passwordHash").doesNotExist());
        verifyNoInteractions(coachService);
    }

    @Test
    void invalidHandleDoesNotCallService() throws Exception {
        ObjectNode request = (ObjectNode) mapper.readTree(VALID);
        request.put("codeforcesHandle", "handle con espacios");
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "El identificador de Codeforces no cumple el formato permitido"))
                .andExpect(jsonPath("$.errors.codeforcesHandle").value(
                        "El identificador de Codeforces no cumple el formato permitido"));
        verifyNoInteractions(service, coachService);
    }

    @Test
    void invalidLevelReturnsSpanishMessage() throws Exception {
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON)
                        .content(VALID.replace("INTERMEDIO", "EXPERTO")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "El nivel competitivo debe ser PRINCIPIANTE, INTERMEDIO o AVANZADO"));
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturnsGenericMessage() throws Exception {
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content("{\"carrera\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "La solicitud debe contener un JSON válido con los campos esperados"));
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content("[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        "La solicitud debe contener un JSON válido con los campos esperados"));
        verifyNoInteractions(service);
    }

    @Test
    void unauthenticatedUserReceives401() throws Exception {
        when(currentUser.currentUser())
                .thenThrow(new UnauthorizedException("Debe iniciar sesión para gestionar su perfil"));
        mvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Debe iniciar sesión para gestionar su perfil"));
        verifyNoInteractions(service, coachService);
    }

    @Test
    void coachUpdatesCoachProfile() throws Exception {
        when(currentUser.currentUser()).thenReturn(coach);
        when(coachService.saveCoachProfile(eq(coach), any())).thenReturn(new CoachProfileResponse(
                "Perfil actualizado correctamente",
                new CoachProfileResponse.CoachProfileData("coach@gmail.com", Role.COACH,
                        "Grafos y Programación Dinámica", "Club de Programación Competitiva", 4,
                        "Entrenador de maratones ICPC.")));
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content(VALID_COACH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.role").value("COACH"))
                .andExpect(jsonPath("$.profile.especialidadPrincipal").value("Grafos y Programación Dinámica"))
                .andExpect(jsonPath("$.profile.aniosExperiencia").value(4))
                .andExpect(jsonPath("$.profile.codigoEstudiante").doesNotExist())
                .andExpect(jsonPath("$.profile.passwordHash").doesNotExist());
        verify(coachService).saveCoachProfile(eq(coach), argThat(request ->
                "Club de Programación Competitiva".equals(request.organizacionClub())));
        verifyNoInteractions(service);
    }

    @Test
    void coachGetsCoachProfile() throws Exception {
        when(currentUser.currentUser()).thenReturn(coach);
        when(coachService.getCoachProfile(coach))
                .thenThrow(new NotFoundException("El perfil de coach aún no está registrado"));
        mvc.perform(get("/users/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("El perfil de coach aún no está registrado"));
        verifyNoInteractions(service);
    }

    @Test
    void coachWithBlankSpecialtyIsRejected() throws Exception {
        when(currentUser.currentUser()).thenReturn(coach);
        ObjectNode request = (ObjectNode) mapper.readTree(VALID_COACH);
        request.put("especialidadPrincipal", "   ");
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.especialidadPrincipal").value("La especialidad principal es obligatoria"));
        verifyNoInteractions(coachService);
    }

    @Test
    void coachWithNegativeExperienceIsRejected() throws Exception {
        when(currentUser.currentUser()).thenReturn(coach);
        ObjectNode request = (ObjectNode) mapper.readTree(VALID_COACH);
        request.put("aniosExperiencia", -1);
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.aniosExperiencia").value("Los años de experiencia no pueden ser negativos"));
        verifyNoInteractions(coachService);
    }

    @Test
    void coachSendingPractitionerBodyIsRejectedWithoutTouchingPractitioner() throws Exception {
        when(currentUser.currentUser()).thenReturn(coach);
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.especialidadPrincipal").exists())
                .andExpect(jsonPath("$.errors.aniosExperiencia").exists());
        verifyNoInteractions(service, coachService);
    }

    @Test
    void practitionerSendingCoachBodyIsRejectedWithoutTouchingCoach() throws Exception {
        mvc.perform(put("/users/me").contentType(MediaType.APPLICATION_JSON).content(VALID_COACH))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.codigoEstudiante").value("El código de estudiante es obligatorio"));
        verifyNoInteractions(service, coachService);
    }

    private static User user(int id, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(role == Role.COACH ? "coach@gmail.com" : "test@gmail.com");
        user.setRole(role);
        return user;
    }
}
