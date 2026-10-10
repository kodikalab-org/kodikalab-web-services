package com.kodika.kodikalab.assignments;

/**
 * Estado personal de un practicante frente a un problema asignado, derivado de sus intentos; no se guarda.
 * Prioridad: {@code RESUELTO} (algún intento aceptado) sobre {@code PENDIENTE} (hay un intento por verificar)
 * sobre {@code EN_PROGRESO} (solo intentos fallidos) sobre {@code SIN_INTENTOS}. El orden de las constantes es el
 * del criterio de ordenamiento por estado.
 */
public enum AssignmentStatus {
    SIN_INTENTOS,
    EN_PROGRESO,
    PENDIENTE,
    RESUELTO
}
