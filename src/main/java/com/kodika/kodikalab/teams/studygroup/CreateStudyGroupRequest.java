package com.kodika.kodikalab.teams.studygroup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o editar un grupo (US04).
 *
 * <p>Con {@code draft = true} el grupo se guarda como borrador y solo el nombre es obligatorio; el resto de campos
 * pueden completarse después con {@code PATCH /teams/{id}/draft}. Para publicarlo, {@code POST /teams/{id}/publish}
 * exige nombre, nivel esperado y cupo máximo válidos.
 */
public record CreateStudyGroupRequest(

        @Size(max = 120, message = "El nombre no puede superar 120 caracteres")
        String name,

        @Size(max = 500, message = "La descripción no puede superar 500 caracteres")
        String description,

        @Size(max = 50, message = "El nivel esperado no puede superar 50 caracteres")
        String expectedLevel,

        @Min(value = 1, message = "El cupo debe ser mayor que cero")
        @Max(value = 1000, message = "El cupo no puede superar 1000")
        Integer maxCapacity,

        @Size(max = 150, message = "El horario no puede superar 150 caracteres")
        String sessionSchedule,

        GroupVisibility visibility,

        /** {@code true} guarda el grupo como borrador; el valor por defecto publica el grupo. */
        Boolean draft
) {
    public boolean isDraft() {
        return Boolean.TRUE.equals(draft);
    }
}
