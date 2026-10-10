package com.kodika.kodikalab.competitions.competition;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/** Tabla {@code competencia}: contests / maratones estilo VJudge de un grupo. */
@Entity
@Table(name = "competencia", indexes = @Index(name = "idx_competencia_grupo_id", columnList = "grupo_id"))
@Getter
@Setter
public class Competition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private StudyGroup group;

    @Column(name = "nombre_evento", nullable = false, length = 150)
    private String eventName;

    @Column(name = "descripcion", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PUBLICO_GRUPO'")
    @Column(name = "tipo_acceso", nullable = false, length = 30)
    private CompetitionAccessType accessType;

    /** Contraseña del contest (PRIVADO_PASS); no exponer en respuestas. */
    @JsonIgnore
    @Column(name = "clave_acceso", length = 100)
    private String accessKey;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ICPC_20_MIN'")
    @Column(name = "regla_penalizacion", nullable = false, length = 30)
    private PenaltyRule penaltyRule;

    @ColumnDefault("300")
    @Column(name = "duracion_minutos", nullable = false)
    private Integer durationMinutes;

    /** Minutos previos al cierre en que se congela el scoreboard. */
    @ColumnDefault("60")
    @Column(name = "congelar_scoreboard_min", nullable = false)
    private Integer scoreboardFreezeMinutes;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PROGRAMADA'")
    @Column(name = "estado", nullable = false, length = 20)
    private CompetitionStatus status;

    @Column(name = "fecha_inicio", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime startsAt;

    @Column(name = "fecha_fin", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime endsAt;
}
