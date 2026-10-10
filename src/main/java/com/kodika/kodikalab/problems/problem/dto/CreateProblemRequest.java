package com.kodika.kodikalab.problems.problem.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.kodika.kodikalab.common.json.StrictJson;
import com.kodika.kodikalab.problems.problem.SourcePlatform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos para registrar un problema en el catálogo. La lectura es estricta (ver {@link StrictJson}); las reglas de
 * valor (longitudes, URL, duplicados) las valida el servicio.
 */
public record CreateProblemRequest(String title, String url, SourcePlatform sourcePlatform, String sourceCode,
                                   String difficultyRating, Integer timeLimitMs, Integer memoryLimitMb,
                                   List<String> topics) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CreateProblemRequest read(JsonNode body) {
        StrictJson.requireObject(body);
        Map<String, String> errors = new LinkedHashMap<>();
        List<String> topics = null;
        JsonNode topicNodes = StrictJson.array(body, "topics", "topics", errors);
        if (topicNodes != null) {
            topics = new ArrayList<>();
            for (int index = 0; index < topicNodes.size(); index++) {
                JsonNode topic = topicNodes.get(index);
                if (topic.isTextual()) {
                    topics.add(topic.textValue());
                } else {
                    errors.put("topics[" + index + "]", "Debe ser una cadena de texto");
                }
            }
        }
        CreateProblemRequest request = new CreateProblemRequest(
                StrictJson.text(body, "title", "title", errors), StrictJson.text(body, "url", "url", errors),
                StrictJson.enumeration(body, "sourcePlatform", SourcePlatform.class, "sourcePlatform", errors),
                StrictJson.text(body, "sourceCode", "sourceCode", errors),
                StrictJson.text(body, "difficultyRating", "difficultyRating", errors),
                StrictJson.integer(body, "timeLimitMs", "timeLimitMs", errors),
                StrictJson.integer(body, "memoryLimitMb", "memoryLimitMb", errors), topics);
        StrictJson.failIfAny(errors);
        return request;
    }
}
