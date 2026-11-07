package com.kodika.kodikalab.problems.codeforces.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.common.json.StrictJson;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cuerpo de {@code POST /problems/codeforces/import}: el código ({@code 1850A}) o la URL del problema. La lectura es
 * estricta (ver {@link StrictJson}); el formato del código lo valida el servicio.
 */
public record ImportCodeforcesProblemRequest(String code) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static ImportCodeforcesProblemRequest read(JsonNode body) {
        StrictJson.requireObject(body);
        Map<String, String> errors = new LinkedHashMap<>();
        ImportCodeforcesProblemRequest request = new ImportCodeforcesProblemRequest(
                StrictJson.text(body, "code", "code", errors));
        StrictJson.failIfAny(errors);
        return request;
    }
}
