package com.kodika.kodikalab.assignments;

import com.kodika.kodikalab.assignments.dto.PlatformSubmissionsResponse;
import com.kodika.kodikalab.assignments.dto.SubmissionFile;
import com.kodika.kodikalab.assignments.dto.VerificationResponse;
import com.kodika.kodikalab.common.exception.FieldValidationException;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Verificación de problemas asignados con Codeforces, solo lectura (solo PRACTICANTE), bajo {@code /api/problems}. */
@RestController
@RequestMapping("/problems/assigned/{competitionProblemId}")
@Tag(name = "Verificación de envíos", description = "Lee los últimos envíos de Codeforces del practicante y registra su veredicto real.")
public class SubmissionVerificationController {
    private final SubmissionVerificationService verificationService;

    public SubmissionVerificationController(SubmissionVerificationService verificationService) {
        this.verificationService = verificationService;
    }

    /** {@code file} es opcional; también se acepta la petición sin cuerpo. */
    @PostMapping("/verification")
    public VerificationResponse verify(@PathVariable Integer competitionProblemId,
                                       @RequestPart(name = "file", required = false) MultipartFile file,
                                       @RequestParam(required = false) Integer count) {
        return verificationService.verify(competitionProblemId, toSubmissionFile(file), count);
    }

    @GetMapping("/platform-submissions")
    public PlatformSubmissionsResponse platformSubmissions(@PathVariable Integer competitionProblemId,
                                                           @RequestParam(required = false) Integer count) {
        return verificationService.platformSubmissions(competitionProblemId, count);
    }

    private static SubmissionFile toSubmissionFile(MultipartFile file) {
        // Los navegadores envían una parte vacía y sin nombre cuando no se elige archivo: equivale a no adjuntar.
        if (file == null || (file.isEmpty() && (file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()))) {
            return null;
        }
        try {
            return new SubmissionFile(file.getOriginalFilename(), file.getBytes());
        } catch (IOException exception) {
            throw new FieldValidationException("El archivo adjunto no es válido",
                    Map.of("file", "No se pudo leer el archivo"));
        }
    }
}
