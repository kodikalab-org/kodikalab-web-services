package com.kodika.kodikalab.teams.studygroup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Datos parciales para guardar un borrador de grupo (US04).
 *
 * <p>Todos los campos son opcionales: {@code null} significa "no modificar". Si se envía el nombre, no puede
 * quedar vacío (lo valida el servicio). A diferencia de {@link CreateStudyGroupRequest}, este contrato no exige
 * nombre, nivel ni cupo, porque el borrador puede guardarse incompleto.
 */
public record UpdateStudyGroupDraftRequest(

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

        GroupVisibility visibility
) {
}
