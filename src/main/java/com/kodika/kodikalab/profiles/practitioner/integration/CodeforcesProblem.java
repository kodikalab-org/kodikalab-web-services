package com.kodika.kodikalab.profiles.practitioner.integration;

import java.util.List;

/** Problema tal como lo publica la API de Codeforces ({@code rating} puede faltar; {@code tags} en inglés). */
public record CodeforcesProblem(int contestId, String index, String name, Integer rating, List<String> tags) {
    public CodeforcesProblem {
        tags = List.copyOf(tags);
    }

    /** Código corto del problema, p. ej. {@code 1850A}. */
    public String code() {
        return contestId + index;
    }
}
