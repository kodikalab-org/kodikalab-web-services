package com.kodika.kodikalab.profiles.coach;

import com.kodika.kodikalab.users.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "coach")
@Getter
@Setter
public class CoachProfile {
    @Id
    @Column(name = "usuario_id")
    private Integer userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "usuario_id", nullable = false)
    private User user;

    @Column(name = "especialidad_principal", nullable = false, length = 120)
    private String mainSpecialty;

    @Column(name = "organizacion_club", length = 150)
    private String organization;

    @ColumnDefault("0")
    @Column(name = "anios_experiencia", nullable = false)
    private Integer yearsOfExperience;

    @Column(name = "presentacion", length = 500)
    private String presentation;
}
