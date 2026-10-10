package com.kodika.kodikalab.competitions.competition;

/** Estado de competencia.estado. */
public enum CompetitionStatus {
    PROGRAMADA,
    EN_CURSO,
    FINALIZADA;

    /** El ciclo de vida solo avanza un paso a la vez: PROGRAMADA, EN_CURSO, FINALIZADA; nunca retrocede ni se salta uno. */
    public boolean canAdvanceTo(CompetitionStatus target) {
        return switch (this) {
            case PROGRAMADA -> target == EN_CURSO;
            case EN_CURSO -> target == FINALIZADA;
            case FINALIZADA -> false;
        };
    }
}
