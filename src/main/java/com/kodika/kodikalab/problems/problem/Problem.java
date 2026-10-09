package com.kodika.kodikalab.problems.problem;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/** Tabla {@code problema}: catálogo general de ejercicios. */
@Entity
@Table(name = "problema")
@Getter
@Setter
public class Problem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** Ej. CF-158A, CSES-1633. */
    @Column(name = "codigo_origen", length = 50)
    private String sourceCode;

    @Column(name = "titulo", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'CODEFORCES'")
    @Column(name = "plataforma_origen", nullable = false, length = 50)
    private SourcePlatform sourcePlatform;

    @Column(name = "url_problema", nullable = false, length = 300)
    private String url;

    /** VARCHAR en el ERD: 800, 1400, 2100. */
    @Column(name = "dificultad_rating", length = 30)
    private String difficultyRating;

    @ColumnDefault("1000")
    @Column(name = "limite_tiempo_ms", nullable = false)
    private Integer timeLimitMs;

    @ColumnDefault("256")
    @Column(name = "limite_memoria_mb", nullable = false)
    private Integer memoryLimitMb;
}
