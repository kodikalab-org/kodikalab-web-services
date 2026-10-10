package com.kodika.kodikalab.problems.material;

import com.kodika.kodikalab.problems.problem.Problem;
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
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

/** Tabla {@code material}: biblioteca virtual y guías de apoyo (US19/US20). */
@Entity
@Table(name = "material", indexes = @Index(name = "idx_material_problema_id", columnList = "problema_id"))
@Getter
@Setter
public class Material {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** FK opcional: {@code null} = material de biblioteca libre, no asociado a un problema. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problema_id")
    private Problem problem;

    @Column(name = "titulo", nullable = false, length = 200)
    private String title;

    @Column(name = "autor", length = 150)
    private String author;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'LIBRO'")
    @Column(name = "tipo_recurso", nullable = false, length = 30)
    private ResourceType resourceType;

    /** Clasificador algorítmico en texto libre; el ERD no lo vincula con la tabla {@code tema}. */
    @Column(name = "tema", nullable = false, length = 80)
    private String topic;

    @Column(name = "enlace_url", nullable = false, length = 400)
    private String url;

    /** Capítulo sugerido o notas. */
    @Column(name = "descripcion", length = 300)
    private String description;
}
