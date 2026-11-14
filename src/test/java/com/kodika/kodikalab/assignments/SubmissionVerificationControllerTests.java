package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.PlatformSubmissionsResponse;
import com.kodika.kodikalab.assignments.dto.SubmissionFile;
import com.kodika.kodikalab.assignments.dto.VerificationResponse;
import com.kodika.kodikalab.common.exception.ConflictException;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import com.kodika.kodikalab.common.exception.ForbiddenException;
import com.kodika.kodikalab.common.exception.NotFoundException;
import com.kodika.kodikalab.competitions.problemresolution.Verdict;
import com.kodika.kodikalab.profiles.practitioner.integration.CodeforcesUnavailableException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SubmissionVerificationControllerTests {
    static final OffsetDateTime AT = OffsetDateTime.parse("2026-10-20T14:10:00Z");

    SubmissionVerificationService service;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(SubmissionVerificationService.class);
        mvc = MockMvcBuilders.standaloneSetup(new SubmissionVerificationController(service))
                .setControllerAdvice(new AssignmentExceptionHandler()).build();
    }

    private void failVerify(RuntimeException exception) {
        org.mockito.Mockito.doThrow(exception).when(service).verify(any(), any(), any());
    }

    private void failPlatform(RuntimeException exception) {
        org.mockito.Mockito.doThrow(exception).when(service).platformSubmissions(any(), any());
    }

    static VerificationResponse verified() {
        return new VerificationResponse(7, true, AssignmentStatus.RESUELTO, true, List.of(
                new VerificationResponse.Submission(300L, Verdict.ACCEPTED, "GNU C++17 (64)", 46, 2048, 7, AT,
                        "https://codeforces.com/contest/1850/submission/300", true, false),
                new VerificationResponse.Submission(299L, Verdict.WRONG_ANSWER, "GNU C++17 (64)", 15, 100, 2, AT,
                        "https://codeforces.com/contest/1850/submission/299", false, true)));
    }

    // ---- POST verification

    @Test
    void verifiesWithoutAnyBody() throws Exception {
        when(service.verify(7, null, null)).thenReturn(verified());

        mvc.perform(post("/problems/assigned/7/verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competitionProblemId").value(7))
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.status").value("RESUELTO"))
                .andExpect(jsonPath("$.fileStored").value(true))
                .andExpect(jsonPath("$.submissions[0].externalId").value(300))
                .andExpect(jsonPath("$.submissions[0].verdict").value("ACCEPTED"))
                .andExpect(jsonPath("$.submissions[0].language").value("GNU C++17 (64)"))
                .andExpect(jsonPath("$.submissions[0].timeMs").value(46))
                .andExpect(jsonPath("$.submissions[0].memoryKb").value(2048))
                .andExpect(jsonPath("$.submissions[0].passedTests").value(7))
                .andExpect(jsonPath("$.submissions[0].url").value("https://codeforces.com/contest/1850/submission/300"))
                .andExpect(jsonPath("$.submissions[0].imported").value(true))
                .andExpect(jsonPath("$.submissions[0].beforeCompetition").value(false))
                .andExpect(jsonPath("$.submissions[1].beforeCompetition").value(true));
    }

    @Test
    void verifiesWithAnEmptyMultipartAndForwardsTheCount() throws Exception {
        when(service.verify(7, null, 25)).thenReturn(verified());

        mvc.perform(multipart("/problems/assigned/7/verification").param("count", "25"))
                .andExpect(status().isOk());
        // El navegador envía una parte de archivo vacía y sin nombre cuando no se elige nada.
        mvc.perform(multipart("/problems/assigned/7/verification").file(new MockMultipartFile("file", "", "text/plain",
                new byte[0])).param("count", "25")).andExpect(status().isOk());
        verify(service, org.mockito.Mockito.times(2)).verify(7, null, 25);
    }

    @Test
    void forwardsTheUploadedFile() throws Exception {
        when(service.verify(any(), any(), any())).thenReturn(verified());
        byte[] code = "print(1)\n".getBytes(StandardCharsets.UTF_8);

        mvc.perform(multipart("/problems/assigned/7/verification")
                        .file(new MockMultipartFile("file", "sol.py", "text/x-python", code)))
                .andExpect(status().isOk());

        ArgumentCaptor<SubmissionFile> captor = ArgumentCaptor.forClass(SubmissionFile.class);
        verify(service).verify(org.mockito.ArgumentMatchers.eq(7), captor.capture(), isNull());
        assertThat(captor.getValue().fileName()).isEqualTo("sol.py");
        assertThat(captor.getValue().content()).isEqualTo(code);
    }

    @Test
    void mapsServiceErrorsToTheDocumentedStatuses() throws Exception {
        failVerify(
                new ConflictException("Vincula tu usuario de Codeforces en tu perfil para verificar"));
        mvc.perform(post("/problems/assigned/7/verification")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Vincula tu usuario de Codeforces en tu perfil para verificar"));

        failVerify(new CodeforcesUnavailableException());
        mvc.perform(post("/problems/assigned/7/verification")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Codeforces no está disponible temporalmente. Inténtelo de nuevo más tarde."));

        failVerify(new FieldValidationException(
                "El archivo adjunto no es válido", Map.of("file", "El archivo supera el máximo de 64 kB")));
        mvc.perform(post("/problems/assigned/7/verification")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El archivo adjunto no es válido"))
                .andExpect(jsonPath("$.errors.file").value("El archivo supera el máximo de 64 kB"));

        failVerify(new NotFoundException("La asignación no existe"));
        mvc.perform(post("/problems/assigned/99/verification")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("La asignación no existe"));

        failVerify(new ForbiddenException("No pertenece a este equipo"));
        mvc.perform(post("/problems/assigned/7/verification")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No pertenece a este equipo"));
    }

    @Test
    void nonNumericIdsAndCountsAreBadRequests() throws Exception {
        mvc.perform(post("/problems/assigned/abc/verification")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.competitionProblemId").exists());
        mvc.perform(post("/problems/assigned/7/verification").param("count", "many"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.count").exists());
    }

    // ---- GET platform-submissions

    @Test
    void listsPlatformSubmissions() throws Exception {
        when(service.platformSubmissions(7, 10)).thenReturn(new PlatformSubmissionsResponse(7, "test_handle", 10,
                List.of(new PlatformSubmissionsResponse.Submission(300L, 1850, "A", Verdict.TLE,
                        "TIME_LIMIT_EXCEEDED", "Java 21 64bit", 2000, 4096, 5, AT,
                        "https://codeforces.com/contest/1850/submission/300", true))));

        mvc.perform(get("/problems/assigned/7/platform-submissions").param("count", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("test_handle"))
                .andExpect(jsonPath("$.count").value(10))
                .andExpect(jsonPath("$.submissions[0].externalId").value(300))
                .andExpect(jsonPath("$.submissions[0].verdict").value("TLE"))
                .andExpect(jsonPath("$.submissions[0].platformVerdict").value("TIME_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.submissions[0].matchesProblem").value(true));
    }

    @Test
    void platformSubmissionsErrorsUseTheSameFormat() throws Exception {
        failPlatform(new CodeforcesUnavailableException());
        mvc.perform(get("/problems/assigned/7/platform-submissions")).andExpect(status().isServiceUnavailable());

        failPlatform(new FieldValidationException(
                "Los datos de la verificación deben corregirse", Map.of("count", "Debe estar entre 1 y 100")));
        mvc.perform(get("/problems/assigned/7/platform-submissions").param("count", "500"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.count").exists());
    }
}
