package com.kodika.kodikalab.common.exception;

import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class ConstraintViolationsTests {
    @Test
    void detectsConstraintNameExtractedByHibernate() {
        var exception = wrap(new ConstraintViolationException("duplicate",
                new SQLException("duplicate key", "23505"), "uq_usuario_correo"));

        assertThat(ConstraintViolations.isUniqueViolationOf(exception, "uq_usuario_correo")).isTrue();
    }

    @Test
    void detectsSpanishPostgresMessageWhenHibernateCannotExtractName() {
        SQLException sql = new SQLException(
                "ERROR: llave duplicada viola restricción de unicidad «uq_practicante_codigo_estudiante»", "23505");
        var exception = wrap(new ConstraintViolationException("duplicate", sql, null));

        assertThat(ConstraintViolations.isUniqueViolationOf(exception, "uq_practicante_codigo_estudiante")).isTrue();
    }

    @Test
    void ignoresOtherConstraintsAndNonUniqueErrors() {
        SQLException otherUnique = new SQLException("llave duplicada «uq_usuario_correo»", "23505");
        SQLException notNull = new SQLException("uq_practicante_codigo_estudiante es nulo", "23502");

        assertThat(ConstraintViolations.isUniqueViolationOf(wrap(otherUnique), "uq_practicante_codigo_estudiante"))
                .isFalse();
        assertThat(ConstraintViolations.isUniqueViolationOf(wrap(notNull), "uq_practicante_codigo_estudiante"))
                .isFalse();
    }

    private static DataIntegrityViolationException wrap(Throwable cause) {
        return new DataIntegrityViolationException("could not execute statement", cause);
    }
}
