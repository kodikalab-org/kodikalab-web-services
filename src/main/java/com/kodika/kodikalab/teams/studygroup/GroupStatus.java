package com.kodika.kodikalab.teams.studygroup;

/**
 * Estado de grupo_estudio.estado.
 *
 * <p>{@code BORRADOR} permite guardar un grupo incompleto sin publicarlo: no aparece en el listado de grupos
 * disponibles y nadie puede solicitar ingreso. Solo el coach responsable lo ve en {@code GET /teams/me} y puede
 * editarlo hasta publicarlo con {@code POST /teams/{id}/publish}.
 */
public enum GroupStatus {
    BORRADOR,
    ACTIVO,
    ARCHIVADO
}
