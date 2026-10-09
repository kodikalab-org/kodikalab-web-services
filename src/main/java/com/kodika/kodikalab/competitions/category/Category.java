package com.kodika.kodikalab.competitions.category;

import com.kodika.kodikalab.teams.studygroup.StudyGroup;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Tabla {@code Categoria} de {@code oficial.erd}: engloba únicamente al grupo de estudio (1:1 con
 * {@code grupo_estudio}); no se relaciona con competencias ni problemas.
 *
 * <p>El ERD usa identificadores camelCase sin comillas; PostgreSQL los guarda en minúsculas
 * ({@code categoria}, {@code idcategoria}...), y así se mapean aquí. {@code idCategoria} es INT sin
 * autoincremento en el ERD, por eso no usa {@code @GeneratedValue}: el ID se asigna al crear.
 * {@code idGrupo} figura como SERIAL, pero es FK y se mapea como INT.
 */
@Entity
@Table(name = "categoria")
@Getter
@Setter
public class Category {
    @Id
    @Column(name = "idcategoria")
    private Integer id;

    @Column(name = "descripcioncategoria", length = 100)
    private String description;

    @Column(name = "nombrecategoria", length = 10)
    private String name;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "idgrupo", nullable = false)
    private StudyGroup group;
}
