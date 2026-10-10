package com.kodika.kodikalab.competitions.competitionproblem;

import com.kodika.kodikalab.competitions.competition.Competition;
import com.kodika.kodikalab.problems.problem.Problem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Tabla {@code competencia_problema}: problemas asignados dentro de una competencia.
 * UNIQUE(competencia_id, problema_id) y UNIQUE(competencia_id, orden_letra).
 */
@Entity
@Table(name = "competencia_problema",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_competencia_problema", columnNames = {"competencia_id", "problema_id"}),
                @UniqueConstraint(name = "uq_competencia_orden_letra", columnNames = {"competencia_id", "orden_letra"})
        },
        indexes = @Index(name = "idx_competencia_problema_problema_id", columnList = "problema_id"))
@Getter
@Setter
public class CompetitionProblem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competencia_id", nullable = false)
    private Competition competition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problema_id", nullable = false)
    private Problem problem;

    /** A, B, C, D... */
    @Column(name = "orden_letra", nullable = false, length = 5)
    private String letter;

    /** Base del scoreboard. */
    @ColumnDefault("1")
    @Column(name = "puntaje", nullable = false)
    private Integer score;

    /** Color hex RGB del globo ICPC. */
    @ColumnDefault("'#FF0000'")
    @Column(name = "color_globo", length = 20)
    private String balloonColor;

    /** Momento en que el coach agrega el problema a la competencia. */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "fecha_asignacion", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime assignedAt;
}
