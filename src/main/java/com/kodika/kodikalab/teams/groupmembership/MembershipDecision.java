package com.kodika.kodikalab.teams.groupmembership;

/**
 * Decisión del coach sobre una solicitud de ingreso (US06).
 *
 * <ul>
 *   <li>{@code ACEPTAR}: la solicitud pasa a {@code ACTIVO}.</li>
 *   <li>{@code RECHAZAR}: la solicitud pasa a {@code RECHAZADO}.</li>
 *   <li>{@code SOLICITAR_ACLARACION}: la solicitud pasa a {@code ACLARACION_SOLICITADA} y se guarda el mensaje
 *       del coach. El postulante debe responder antes de que el coach pueda aceptar o rechazar.</li>
 * </ul>
 */
public enum MembershipDecision {
    ACEPTAR,
    RECHAZAR,
    SOLICITAR_ACLARACION
}
