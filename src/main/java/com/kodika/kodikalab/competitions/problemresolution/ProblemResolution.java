package com.kodika.kodikalab.competitions.problemresolution;

import com.kodika.kodikalab.competitions.competitionproblem.CompetitionProblem;
import com.kodika.kodikalab.teams.groupmembership.GroupMembership;
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

/**
 * Tabla {@code resolucion_problema}: intentos de resolución por integrante y problema de competencia.
 * Según el ERD, la membresía y el problema deben pertenecer al mismo grupo, y los rankings cuentan
 * problemas distintos aceptados (reglas a implementar en el servicio de la historia correspondiente).
 */
@Entity
@Table(name = "resolucion_problema",
        indexes = @Index(name = "idx_resolucion_problema_competencia_problema_id",
                columnList = "competencia_problema_id"))
@Getter
@Setter
public class ProblemResolution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PENDIENTE'")
    @Column(name = "veredicto", nullable = false, length = 30)
    private Verdict verdict;

    /** Texto libre según el ERD: C++20, Python 3, Java 17. */
    @ColumnDefault("'C++20'")
    @Column(name = "lenguaje", nullable = false, length = 30)
    private String language;

    @ColumnDefault("0")
    @Column(name = "tiempo_ejecucion_ms")
    private Integer executionTimeMs;

    @ColumnDefault("0")
    @Column(name = "memoria_usada_kb")
    private Integer memoryUsedKb;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "fecha_envio", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competencia_problema_id", nullable = false)
    private CompetitionProblem competitionProblem;

    /**
     * FK a {@code practicante_grupo.id}. El ERD la dibuja como SERIAL; una FK no puede autogenerarse,
     * por eso se mapea como INT (ver docs/sdd/04-database-model.md).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practicante_grupo_id", nullable = false)
    private GroupMembership membership;

    @Column(name = "url_evidencia", length = 500)
    private String evidenceUrl;
}
