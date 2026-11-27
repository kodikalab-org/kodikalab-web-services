package com.kodika.kodikalab.recommendations;

/**
 * Un problema propuesto y el motivo. Los de {@link Source#CODEFORCES} no están en el catálogo: no tienen
 * {@code problemId} y hay que importarlos antes de asignarlos.
 */
public record ProblemRecommendation(Integer problemId, String title, String url, String topic, String difficulty,
                                    Source source, String reason) {
    public enum Source {
        CATALOG,
        CODEFORCES
    }
}
