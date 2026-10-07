package com.kodika.kodikalab.profiles.practitioner.dto;

import com.kodika.kodikalab.profiles.practitioner.PractitionerLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PractitionerProfileRequest(
        @NotBlank(message = "El código de estudiante es obligatorio")
        @Size(max = 20, message = "El código de estudiante debe tener como máximo 20 caracteres")
        String codigoEstudiante,

        @NotBlank(message = "La carrera es obligatoria")
        @Size(max = 100, message = "La carrera debe tener como máximo 100 caracteres")
        String carrera,

        @NotNull(message = "El ciclo académico es obligatorio")
        @Min(value = 1, message = "El ciclo académico debe ser mayor o igual a 1")
        Integer cicloAcademico,

        @NotNull(message = "El nivel competitivo es obligatorio")
        PractitionerLevel nivelCompetitivo,

        @Size(max = 50, message = "El identificador de Codeforces debe tener como máximo 50 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "El identificador de Codeforces no cumple el formato permitido")
        String codeforcesHandle,


        @Size(max = 50, message = "El identificador de AtCoder debe tener como máximo 50 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "El identificador de AtCoder no cumple el formato permitido")
        String atcoderHandle,

        @Size(max = 50, message = "El identificador de VJudge debe tener como máximo 50 caracteres")
        @Pattern(regexp = "^[A-Za-z0-9._-]*$", message = "El identificador de VJudge no cumple el formato permitido")
        String vjudgeHandle
) {
    public PractitionerProfileRequest {
        codigoEstudiante = codigoEstudiante == null ? null : codigoEstudiante.strip();
        carrera = carrera == null ? null : carrera.strip();
        codeforcesHandle = codeforcesHandle == null ? null : codeforcesHandle.strip();
        atcoderHandle = atcoderHandle == null ? null : atcoderHandle.strip();
        vjudgeHandle = vjudgeHandle == null ? null : vjudgeHandle.strip();
    }
}
