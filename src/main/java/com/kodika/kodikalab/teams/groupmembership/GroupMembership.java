package com.kodika.kodikalab.teams.groupmembership;

import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
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
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/**
 * Tabla {@code practicante_grupo}: gestiona solicitudes de ingreso
 * y membresias de los practicantes en los grupos de estudio.
 *
 * UNIQUE(grupo_id, practicante_id): cada practicante tiene un unico
 * registro por grupo, que se reutiliza al solicitar nuevamente.
 *
 * fecha_ingreso representa la fecha de solicitud cuando el estado
 * es PENDIENTE y la fecha de ingreso cuando es ACTIVO.
 */
@Entity
@Table(name = "practicante_grupo",
        uniqueConstraints = @UniqueConstraint(name = "uq_practicante_grupo",
                columnNames = {"grupo_id", "practicante_id"}),
        indexes = @Index(name = "idx_practicante_grupo_practicante_id", columnList = "practicante_id"))
@Getter
@Setter
public class GroupMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private StudyGroup group;

    /** FK a {@code practicante.usuario_id}. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practicante_id", nullable = false)
    private PractitionerProfile practitioner;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado", nullable = false, length = 20)
    private MembershipStatus status;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MIEMBRO'")
    @Column(name = "rol_equipo", nullable = false, length = 30)
    private TeamRole teamRole;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "fecha_ingreso", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime joinedAt;

    @Column(name = "fecha_salida", columnDefinition = "timestamp with time zone")
    private OffsetDateTime leftAt;
}
