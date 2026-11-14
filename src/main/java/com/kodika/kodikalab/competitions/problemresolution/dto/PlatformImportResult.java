package com.kodika.kodikalab.competitions.problemresolution.dto;

import java.util.Set;

/**
 * Resultado de importar envíos: ids externos que se crearon ahora ({@code imported}), los que existían y cambiaron
 * de veredicto ({@code updated}) y si el archivo quedó guardado.
 */
public record PlatformImportResult(Set<Long> imported, Set<Long> updated, boolean fileStored) {
    public PlatformImportResult {
        imported = Set.copyOf(imported);
        updated = Set.copyOf(updated);
    }
}
