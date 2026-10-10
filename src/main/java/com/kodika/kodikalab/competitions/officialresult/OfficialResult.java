package com.kodika.kodikalab.competitions.officialresult;

import com.kodika.kodikalab.competitions.competition.Competition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "resultado_oficial_competencia", uniqueConstraints =
        @UniqueConstraint(name = "uq_resultado_oficial_competencia", columnNames = "competencia_id"))
@Check(name = "ck_resultado_oficial_valido", constraints =
        "(posicion_final IS NULL OR posicion_final > 0) AND "
        + "(problemas_resueltos IS NULL OR problemas_resueltos >= 0) AND "
        + "((estado = 'PENDIENTE' AND confirmado_en IS NULL) OR "
        + "(estado = 'CONFIRMADO' AND posicion_final IS NOT NULL AND problemas_resueltos IS NOT NULL "
        + "AND confirmado_en IS NOT NULL))")
@Getter
@Setter
public class OfficialResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "competencia_id", nullable = false)
    private Competition competition;

    @Column(name = "posicion_final")
    private Integer finalPosition;

    @Column(name = "problemas_resueltos")
    private Integer solvedProblems;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private OfficialResultStatus status;

    @Column(name = "registrado_en", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime registeredAt;

    @Column(name = "confirmado_en", columnDefinition = "timestamp with time zone")
    private OffsetDateTime confirmedAt;
}
