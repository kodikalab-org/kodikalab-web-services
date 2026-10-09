package com.kodika.kodikalab.problems.topic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** Tabla {@code tema}: catálogo de temas algorítmicos compartidos por varios problemas. */
@Entity
@Table(name = "tema", uniqueConstraints = @UniqueConstraint(name = "uq_tema_nombre", columnNames = "nombre"))
@Getter
@Setter
public class Topic {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "nombre", nullable = false, length = 80)
    private String name;

    @Column(name = "descripcion", columnDefinition = "text")
    private String description;
}
