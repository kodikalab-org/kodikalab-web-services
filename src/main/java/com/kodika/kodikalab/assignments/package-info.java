/**
 * Módulo assignments: asignación de problemas a los equipos (US-07) y vista de problemas asignados (US-08).
 *
 * <p>No tiene tablas propias: coordina agregados de otros módulos mediante sus servicios públicos, igual que
 * {@code analytics}. Una asignación es una fila de {@code competencia_problema} (módulo {@code competitions}); el
 * catálogo es de {@code problems}; los intentos de resolución, de {@code competitions}; las membresías y los coaches,
 * de {@code teams}.
 *
 * <p>Rutas (contrato en {@code docs/sdd/03-api-contracts.md}):
 * <ul>
 *   <li>{@code POST /problems/assign}: el coach responsable asigna problemas del catálogo a una competencia;</li>
 *   <li>{@code GET /problems/assigned}: problemas asignados de un equipo, con estado, filtros y orden;</li>
 *   <li>{@code GET /problems/assigned/{competitionProblemId}}: detalle de una asignación con el historial de
 *       intentos.</li>
 * </ul>
 */
package com.kodika.kodikalab.assignments;
