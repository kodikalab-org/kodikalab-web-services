package com.kodika.kodikalab.common.exception;

import java.sql.SQLException;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;

/**
 * Identifica qué restricción de base de datos se violó, sin depender del idioma del servidor.
 * Hibernate extrae el nombre con una plantilla en inglés; con PostgreSQL en español
 * ("viola restricción de unicidad «uq_x»") el nombre queda {@code null}, por eso también se
 * revisa el SQLState de unicidad (23505) y el nombre dentro del mensaje original.
 */
public final class ConstraintViolations {
    private static final String UNIQUE_VIOLATION = "23505";

    private ConstraintViolations() {
    }

    public static boolean isUniqueViolationOf(Throwable exception, String... constraintNames) {
        Set<String> names = Set.of(constraintNames);
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && violation.getConstraintName() != null && names.contains(violation.getConstraintName())) {
                return true;
            }
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null && names.stream().anyMatch(sql.getMessage()::contains)) {
                return true;
            }
        }
        return false;
    }
}
