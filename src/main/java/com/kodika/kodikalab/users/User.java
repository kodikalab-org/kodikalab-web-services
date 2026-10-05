package com.kodika.kodikalab.users;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "usuario", uniqueConstraints = @UniqueConstraint(name = "uq_usuario_correo", columnNames = "correo"))
@Getter
@Setter
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String fullName;

    @Column(name = "correo", nullable = false, length = 100)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVO'")
    @Column(name = "estado_cuenta", nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Role role;

    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "fecha_registro", nullable = false, columnDefinition = "timestamp with time zone")
    private OffsetDateTime createdAt;
}
