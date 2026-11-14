package com.kodika.kodikalab.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Estado", description = "Comprobación de disponibilidad. Público: no requiere token.")
@SecurityRequirements
public class HealthController {

    @Operation(summary = "Comprobar que el servicio responde",
            description = "Público y sin acceso a la base de datos. Sirve para despertar el servicio en Render.")
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
