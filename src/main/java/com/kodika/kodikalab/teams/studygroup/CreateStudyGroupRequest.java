package com.kodika.kodikalab.teams.studygroup;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStudyGroupRequest(

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 120)
        String name,

        @Size(max = 500)
        String description,

        @NotBlank(message = "El nivel esperado es obligatorio")
        @Size(max = 50)
        String expectedLevel,

        @NotNull(message = "El cupo máximo es obligatorio")
        @Min(value = 1, message = "El cupo debe ser mayor que cero")
        @Max(value = 1000, message = "El cupo no puede superar 1000")
        Integer maxCapacity,

        @Size(max = 150)
        String sessionSchedule,

        GroupVisibility visibility
) {
}
