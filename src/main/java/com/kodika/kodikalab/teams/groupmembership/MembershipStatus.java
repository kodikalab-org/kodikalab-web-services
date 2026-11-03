package com.kodika.kodikalab.teams.groupmembership;

/**
 * Estado de practicante_grupo.estado.
 *
 * <p>{@code ACLARACION_SOLICITADA} es una solicitud que sigue abierta, pero cuya respuesta depende del postulante:
 * el coach le pidió información adicional. Cuando el postulante responde, la membresía vuelve a {@code PENDIENTE}
 * para que el coach decida.
 */
public enum MembershipStatus {
    PENDIENTE,
    ACLARACION_SOLICITADA,
    ACTIVO,
    RECHAZADO,
    RETIRADO,
    EXPULSADO
}
