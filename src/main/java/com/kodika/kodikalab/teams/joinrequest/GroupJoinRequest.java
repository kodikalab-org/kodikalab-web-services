
package com.kodika.kodikalab.teams.joinrequest;

import com.kodika.kodikalab.profiles.practitioner.PractitionerProfile;
import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(
        name = "solicitud_grupo",
        indexes = {
                @Index(
                        name = "idx_solicitud_grupo_estado",
                        columnList = "grupo_id, estado"
                )
        }
)
@Getter
@Setter
public class GroupJoinRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private StudyGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "practicante_id", nullable = false)
    private PractitionerProfile practitioner;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private JoinRequestStatus status;

    @Column(
            name = "fecha_solicitud",
            nullable = false,
            columnDefinition = "timestamp with time zone"
    )
    private OffsetDateTime requestedAt;

    @Column(
            name = "fecha_respuesta",
            columnDefinition = "timestamp with time zone"
    )
    private OffsetDateTime respondedAt;
}
