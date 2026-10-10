/**
 * Módulo problems: Catálogo de problemas, temas y material de apoyo.
 *
 * <p>Tablas de {@code docs/sdd/assets/oficial.erd} mapeadas en este módulo, una carpeta por entidad:
 * <ul>
 *   <li>problema &rarr; {@code problem/Problem}</li>
 *   <li>tema &rarr; {@code topic/Topic}</li>
 *   <li>problema_tema &rarr; {@code problemtopic/ProblemTopic}</li>
 *   <li>material &rarr; {@code material/Material}</li>
 * </ul>
 *
 * <p>Cada carpeta contiene la entidad, sus enums, su repositorio y su servicio plantilla
 * ({@code XxxService} / {@code XxxServiceImpl}). El controller del módulo vive en la raíz y aún no
 * declara endpoints. Todo sin lógica de negocio. Al implementar cada historia:
 * <ul>
 *   <li>declarar la operación en el servicio de la entidad e implementarla en {@code XxxServiceImpl};</li>
 *   <li>agregar el endpoint al controller del módulo, delegando en el servicio, con su contrato en
 *       {@code docs/sdd/03-api-contracts.md};</li>
 *   <li>crear los DTOs en {@code <entidad>/dto} y un {@code @RestControllerAdvice} propio del módulo,
 *       siguiendo el patrón de {@code profiles}.</li>
 * </ul>
 */
package com.kodika.kodikalab.problems;
