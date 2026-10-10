package com.kodika.kodikalab.assignments.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.common.json.StrictJson;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Asignación de problemas del catálogo a una competencia. La lectura es estricta (ver {@link StrictJson}); las reglas
 * de valor y de pertenencia las valida el servicio. No admite destinatarios: la asignación es para todo el equipo.
 */
public record AssignProblemsRequest(Integer competitionId, List<Item> problems) {
    public record Item(Integer problemId, String letter, Integer score, String balloonColor) {
    }

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static AssignProblemsRequest read(JsonNode body) {
        StrictJson.requireObject(body);
        Map<String, String> errors = new LinkedHashMap<>();
        Integer competitionId = StrictJson.integer(body, "competitionId", "competitionId", errors);
        List<Item> items = null;
        JsonNode nodes = StrictJson.array(body, "problems", "problems", errors);
        if (nodes != null) {
            items = new ArrayList<>();
            for (int index = 0; index < nodes.size(); index++) {
                JsonNode node = nodes.get(index);
                String path = "problems[" + index + "]";
                if (!node.isObject()) {
                    errors.put(path, "Debe ser un objeto");
                    continue;
                }
                items.add(new Item(StrictJson.integer(node, "problemId", path + ".problemId", errors),
                        StrictJson.text(node, "letter", path + ".letter", errors),
                        StrictJson.integer(node, "score", path + ".score", errors),
                        StrictJson.text(node, "balloonColor", path + ".balloonColor", errors)));
            }
        }
        StrictJson.failIfAny(errors);
        return new AssignProblemsRequest(competitionId, items);
    }
}
