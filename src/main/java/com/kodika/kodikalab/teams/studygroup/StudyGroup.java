package com.kodika.kodikalab.teams.studygroup;

import com.kodika.kodikalab.profiles.coach.CoachProfile;
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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/** Tabla {@code grupo_estudio}: equipos de entrenamiento a cargo de un coach. */
@Entity
@Table(name = "grupo_estudio",
        uniqueConstraints = @UniqueConstraint(name = "uq_grupo_estudio_codigo_invitacion",
                columnNames = "codigo_invitacion"),
        indexes = @Index(name = "idx_grupo_estudio_coach_id", columnList = "coach_id"))
@Getter
@Setter
public class StudyGroup {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** FK a {@code coach.usuario_id}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private CoachProfile coach;

    @Column(name = "nombre", nullable = false, length = 120)
    private String name;

    @Column(name = "descripcion", length = 500)
    private String description;

    /** Texto libre según el ERD: Div3 / Div2 / Regional. */
    @Column(name = "nivel_esperado", nullable = false, length = 50)
    private String expectedLevel;

    @ColumnDefault("15")
    @Column(name = "cupo_maximo", nullable = false)
    private Integer maxCapacity;

    /** Días y horas de práctica; el ERD no define una tabla de horarios. */
    @Column(name = "horario_sesiones", length = 150)
    private String sessionSchedule;

    @Column(name = "codigo_invitacion", nullable = false, length = 20)
    private String invitationCode;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado", nullable = false, length = 20)
    private GroupStatus status;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "fecha_creacion", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PUBLICO'")
    @Column(name = "visibilidad", length = 50)
    private GroupVisibility visibility;
}
