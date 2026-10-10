package com.kodika.kodikalab.profiles.practitioner;

import com.kodika.kodikalab.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "practicante", uniqueConstraints = @UniqueConstraint(
        name = "uq_practicante_codigo_estudiante", columnNames = "codigo_estudiante"))
@Getter
@Setter
public class PractitionerProfile {
    @Id
    @Column(name = "usuario_id")
    private Integer userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "usuario_id", nullable = false)
    private User user;

    @Column(name = "codigo_estudiante", nullable = false, length = 20)
    private String studentCode;

    @Column(name = "carrera", nullable = false, length = 100)
    private String career;

    @ColumnDefault("1")
    @Column(name = "ciclo_academico", nullable = false)
    private Integer academicCycle;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'PRINCIPIANTE'")
    @Column(name = "nivel_competitivo", nullable = false, length = 30)
    private PractitionerLevel competitiveLevel;

    @Column(name = "codeforces_handle", length = 50)
    private String codeforcesHandle;

    @ColumnDefault("0")
    @Column(name = "codeforces_rating")
    private Integer codeforcesRating;

    @Column(name = "atcoder_handle", length = 50)
    private String atcoderHandle;

    @Column(name = "vjudge_handle", length = 50)
    private String vjudgeHandle;
}
