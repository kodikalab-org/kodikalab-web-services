package com.kodika.kodikalab.profiles.coach.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CoachProfileRequest(
        @NotBlank(message = "La especialidad principal es obligatoria")
        @Size(max = 120, message = "La especialidad principal debe tener como máximo 120 caracteres")
        String especialidadPrincipal,

        @Size(max = 150, message = "La organización o club debe tener como máximo 150 caracteres")
        String organizacionClub,

        @NotNull(message = "Los años de experiencia son obligatorios")
        @Min(value = 0, message = "Los años de experiencia no pueden ser negativos")
        @Max(value = 60, message = "Los años de experiencia deben ser como máximo 60")
        Integer aniosExperiencia,

        @Size(max = 500, message = "La presentación debe tener como máximo 500 caracteres")
        String presentacion
) {
    public CoachProfileRequest {
        especialidadPrincipal = especialidadPrincipal == null ? null : especialidadPrincipal.strip();
        organizacionClub = organizacionClub == null ? null : organizacionClub.strip();
        presentacion = presentacion == null ? null : presentacion.strip();
    }
}
