package com.kodika.kodikalab.analytics;

import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "ranking_equipo_actual")
@Getter
@Setter
public class TeamRankingSnapshot {
    @Id
    @Column(name = "grupo_id")
    private Integer teamId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", insertable = false, updatable = false, nullable = false)
    private StudyGroup group;

    @Column(name = "calculado_en", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime calculatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resultado", nullable = false, columnDefinition = "jsonb")
    private String result;
}
