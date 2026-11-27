package com.kodika.kodikalab.agent.app.dto;

import java.math.BigDecimal;
import java.util.List;

/** Gráfico de barras ya calculado: el frontend solo lo dibuja. */
public record ChartData(String title, String unit, List<String> labels, List<BigDecimal> values) {
    public ChartData {
        labels = List.copyOf(labels);
        values = List.copyOf(values);
        if (labels.size() != values.size()) {
            throw new IllegalArgumentException("A chart needs one value per label");
        }
    }
}
