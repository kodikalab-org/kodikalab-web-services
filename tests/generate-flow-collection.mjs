import { writeFileSync } from 'node:fs';

// Colección de punta a punta para US-07 a US-14. Todo se crea por la API (usuarios, perfiles, grupo, problemas,
// competencias, asignación, resoluciones y resultados): no requiere fixtures ni datos previos.
const event = (listen, exec) => ({ listen, script: { type: 'text/javascript', exec } });
const missingToken = 'Debe iniciar sesión: envíe el token en el encabezado Authorization (Bearer)';

const registration = (email, role) => `{
  "firstName": "Usuario",
  "lastName": "Flujo",
  "email": "${email}",
  "password": "Password123",
  "role": "${role}"
}`;

// Una solicitud con sus pruebas. auth: nombre de la variable con el token (sin auth = sin token).
function step(name, method, path, status, { auth, body, tests = [], save = {}, saveToken, pre = [], description = '' } = {}) {
  const [pathname, query] = path.split('?');
  const exec = [`pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`, 'const body = pm.response.json();'];
  if (status >= 400) {
    exec.push("pm.test('Cuerpo de error {message, errors}', () => { pm.expect(body.message).to.be.a('string').and.not.empty; pm.expect(body).to.have.property('errors'); });");
  }
  if (saveToken) {
    exec.push(
      "pm.test('Token Bearer (JWT)', () => { pm.expect(body.tokenType).to.eql('Bearer'); pm.expect(body.token.split('.')).to.have.lengthOf(3); });",
      `pm.collectionVariables.set(${JSON.stringify(saveToken)}, body.token);`,
    );
  }
  exec.push(...tests);
  for (const [variable, expression] of Object.entries(save)) {
    exec.push(`pm.collectionVariables.set(${JSON.stringify(variable)}, ${expression});`);
  }
  const item = {
    name,
    request: {
      method,
      header: body === undefined ? [] : [{ key: 'Content-Type', value: 'application/json' }],
      auth: auth ? { type: 'bearer', bearer: [{ key: 'token', value: `{{${auth}}}`, type: 'string' }] } : { type: 'noauth' },
      url: {
        raw: `{{baseUrl}}${path}`, host: ['{{baseUrl}}'], path: pathname.split('/').filter(Boolean),
        ...(query ? { query: query.split('&').map((pair) => { const [key, value] = pair.split('='); return { key, value }; }) } : {}),
      },
      description,
    },
    event: [event('test', exec)],
  };
  if (body !== undefined) item.request.body = { mode: 'raw', raw: body, options: { raw: { language: 'json' } } };
  if (pre.length) item.event.unshift(event('prerequest', pre));
  return item;
}

const items = [];
const add = (...args) => items.push(step(...args));

// --- Preparación: cuentas, perfiles y equipo (US-01, US-02, US-03, US-04, US-05) -----------------------------------
add('Registrar COACH', 'POST', '/auth/register', 201, { body: registration('{{coachEmail}}', 'COACH') });
add('Registrar PRACTICANTE A', 'POST', '/auth/register', 201, { body: registration('{{practAEmail}}', 'PRACTICANTE') });
add('Registrar PRACTICANTE B', 'POST', '/auth/register', 201, { body: registration('{{practBEmail}}', 'PRACTICANTE') });
add('Login COACH', 'POST', '/auth/login', 200, { body: '{ "email": "{{coachEmail}}", "password": "Password123" }', saveToken: 'coachToken' });
add('Login PRACTICANTE A', 'POST', '/auth/login', 200, { body: '{ "email": "{{practAEmail}}", "password": "Password123" }', saveToken: 'practAToken' });
add('Login PRACTICANTE B', 'POST', '/auth/login', 200, { body: '{ "email": "{{practBEmail}}", "password": "Password123" }', saveToken: 'practBToken' });
add('Perfil del COACH', 'PUT', '/users/me', 200, { auth: 'coachToken', body: '{ "especialidadPrincipal": "Grafos", "aniosExperiencia": 3 }' });
add('Perfil de PRACTICANTE A', 'PUT', '/users/me', 200, {
  auth: 'practAToken',
  body: '{ "codigoEstudiante": "{{studentCodeA}}", "carrera": "Ingeniería de Software", "cicloAcademico": 5, "nivelCompetitivo": "INTERMEDIO" }',
});
add('Perfil de PRACTICANTE B', 'PUT', '/users/me', 200, {
  auth: 'practBToken',
  body: '{ "codigoEstudiante": "{{studentCodeB}}", "carrera": "Ingeniería de Software", "cicloAcademico": 4, "nivelCompetitivo": "PRINCIPIANTE" }',
});
add('Crear grupo público', 'POST', '/teams', 201, {
  auth: 'coachToken',
  body: '{ "name": "Flujo {{runId}}", "description": "Equipo de las pruebas US-07 a US-14", "expectedLevel": "Div3", "maxCapacity": 10, "sessionSchedule": "Lun 18:00", "visibility": "PUBLICO" }',
  tests: ["pm.test('Grupo creado', () => pm.expect(body.groupId).to.be.a('number'));"],
  save: { groupId: 'body.groupId' },
});
add('PRACTICANTE A ingresa al grupo (ACTIVO)', 'POST', '/teams/{{groupId}}/join', 201, {
  auth: 'practAToken', tests: ["pm.test('Membresía ACTIVO', () => pm.expect(body.status).to.eql('ACTIVO'));"],
});
add('PRACTICANTE B ingresa al grupo (ACTIVO)', 'POST', '/teams/{{groupId}}/join', 201, {
  auth: 'practBToken', tests: ["pm.test('Membresía ACTIVO', () => pm.expect(body.status).to.eql('ACTIVO'));"],
});

// --- US-07: catálogo de problemas ---------------------------------------------------------------------------------------
const problem = (n, title, topics, difficulty) => `{
  "title": "${title} {{runId}}",
  "url": "https://judge.example/p/{{runId}}-${n}",
  "sourcePlatform": "CODEFORCES",
  "sourceCode": "CF-{{runId}}-${n}",
  "difficultyRating": "${difficulty}",
  "timeLimitMs": 1000,
  "memoryLimitMb": 256,
  "topics": ${JSON.stringify(topics)}
}`;
add('Registrar problema 1 (Grafos, BFS)', 'POST', '/problems', 201, {
  auth: 'coachToken', body: problem(1, 'Camino mínimo', ['Grafos', 'BFS'], '1400'),
  tests: ["pm.test('Temas del problema', () => pm.expect(body.topics.map((t) => t.name)).to.include('Grafos'));"],
  save: { problem1: 'body.id' },
});
add('Registrar problema 2 (Programación dinámica)', 'POST', '/problems', 201, {
  auth: 'coachToken', body: problem(2, 'Mochila', ['Programación dinámica'], '1800'), save: { problem2: 'body.id' },
});
add('Registrar problema 3 (Grafos)', 'POST', '/problems', 201, {
  auth: 'coachToken', body: problem(3, 'Componentes conexas', ['Grafos'], '1200'), save: { problem3: 'body.id' },
});
add('Problema duplicado por URL → 409', 'POST', '/problems', 409, { auth: 'coachToken', body: problem(1, 'Otro título', ['Grafos'], '1400') });
add('Buscar el catálogo por texto', 'GET', '/problems?q={{runId}}', 200, {
  auth: 'practAToken', tests: ["pm.test('Encuentra los 3 problemas', () => pm.expect(body.totalItems).to.eql(3));"],
});

// --- US-13 (T1) y US-07: competencia y asignación -------------------------------------------------------------------------
add('Crear competencia en curso', 'POST', '/competitions', 201, {
  auth: 'coachToken',
  body: '{ "teamId": {{groupId}}, "eventName": "Simulacro {{runId}}", "description": "Flujo US-07 a US-14", "accessType": "PUBLICO_GRUPO", "penaltyRule": "ICPC_20_MIN", "scoreboardFreezeMinutes": 30, "status": "EN_CURSO", "startsAt": "{{startsAt}}", "endsAt": "{{endsAt}}" }',
  tests: ["pm.test('Sin clave de acceso en la respuesta', () => pm.expect(body).not.to.have.property('accessKey'));"],
  save: { competitionId: 'body.id' },
});
add('Asignar los 3 problemas a la competencia (US-07)', 'POST', '/problems/assign', 201, {
  auth: 'coachToken',
  body: '{ "competitionId": {{competitionId}}, "problems": [ { "problemId": {{problem1}} }, { "problemId": {{problem2}} }, { "problemId": {{problem3}} } ] }',
  tests: [
    "pm.test('Tres asignaciones con letras distintas', () => { pm.expect(body.assigned).to.have.lengthOf(3); pm.expect(new Set(body.assigned.map((a) => a.letter)).size).to.eql(3); });",
  ],
  save: { cp1: 'body.assigned[0].competitionProblemId', cp2: 'body.assigned[1].competitionProblemId', cp3: 'body.assigned[2].competitionProblemId' },
});
add('Asignar un problema ya asignado → 409 (no cambia nada)', 'POST', '/problems/assign', 409, {
  auth: 'coachToken', body: '{ "competitionId": {{competitionId}}, "problems": [ { "problemId": {{problem1}} } ] }',
});

// --- US-08: problemas asignados ---------------------------------------------------------------------------------------------
add('PRACTICANTE A ve sus problemas asignados', 'GET', '/problems/assigned?teamId={{groupId}}', 200, {
  auth: 'practAToken',
  tests: ["pm.test('3 problemas SIN_INTENTOS', () => { pm.expect(body.total).to.eql(3); for (const item of body.items) { pm.expect(item.status).to.eql('SIN_INTENTOS'); pm.expect(item.attemptCount).to.eql(0); } });"],
});
add('Detalle de un problema asignado', 'GET', '/problems/assigned/{{cp1}}', 200, {
  auth: 'practAToken',
  tests: ["pm.test('Detalle con intentos vacíos', () => { pm.expect(body.assignment.competitionProblemId).to.eql(Number(pm.collectionVariables.get('cp1'))); pm.expect(body.attempts).to.have.lengthOf(0); });"],
});
add('Filtrar por estado y texto', 'GET', '/problems/assigned?teamId={{groupId}}&status=SIN_INTENTOS&q=Mochila', 200, {
  auth: 'practAToken', tests: ["pm.test('Solo la Mochila', () => pm.expect(body.total).to.eql(1));"],
});
add('El COACH ve la asignación del equipo (sin avance personal)', 'GET', '/problems/assigned?teamId={{groupId}}', 200, {
  auth: 'coachToken', tests: ["pm.test('Sin estado personal', () => { pm.expect(body.total).to.eql(3); pm.expect(body.items[0].status).to.eql(null); });"],
});

// --- US-09 y US-14: registrar una resolución y avance independiente ------------------------------------------------------------
add('PRACTICANTE A registra su resolución (ACCEPTED)', 'POST', '/competitions/teams/{{groupId}}/problems/{{cp1}}/resolutions', 201, {
  auth: 'practAToken', body: '{ "language": "Java 21", "evidenceUrl": "https://judge.example/s/{{runId}}" }',
  tests: ["pm.test('Aceptada y avance actualizado', () => { pm.expect(body.resolution.verdict).to.eql('ACCEPTED'); pm.expect(body.progress.acceptedProblems).to.eql(1); });"],
});
add('Registro duplicado → 409', 'POST', '/competitions/teams/{{groupId}}/problems/{{cp1}}/resolutions', 409, {
  auth: 'practAToken', body: '{ "language": "Java 21" }',
});
add('Lenguaje ausente → 400', 'POST', '/competitions/teams/{{groupId}}/problems/{{cp2}}/resolutions', 400, {
  auth: 'practAToken', body: '{}', tests: ["pm.test('Indica el campo', () => pm.expect(body.errors).to.have.property('language'));"],
});
add('Avance de PRACTICANTE A', 'GET', '/analytics/teams/{{groupId}}/progress/me', 200, {
  auth: 'practAToken', tests: ["pm.test('1 problema aceptado', () => pm.expect(body.acceptedProblems).to.eql(1));"],
});
add('Avance de PRACTICANTE B: independiente, 0', 'GET', '/analytics/teams/{{groupId}}/progress/me', 200, {
  auth: 'practBToken', tests: ["pm.test('0 problemas aceptados', () => pm.expect(body.acceptedProblems).to.eql(0));"],
});
add('El problema pasa a RESUELTO para A', 'GET', '/problems/assigned/{{cp1}}', 200, {
  auth: 'practAToken',
  tests: ["pm.test('RESUELTO con 1 intento', () => { pm.expect(body.assignment.status).to.eql('RESUELTO'); pm.expect(body.attempts).to.have.lengthOf(1); });"],
});
add('Para B el mismo problema sigue SIN_INTENTOS', 'GET', '/problems/assigned/{{cp1}}', 200, {
  auth: 'practBToken', tests: ["pm.test('SIN_INTENTOS', () => pm.expect(body.assignment.status).to.eql('SIN_INTENTOS'));"],
});

// --- US-11: ranking interno --------------------------------------------------------------------------------------------------
add('Ranking del equipo (COACH)', 'GET', '/analytics/teams/{{groupId}}/standings', 200, {
  auth: 'coachToken',
  tests: ["pm.test('A primero con 1 problema', () => { pm.expect(body.status).to.eql('CALCULATED'); pm.expect(body.members[0].acceptedProblems).to.eql(1); pm.expect(body.members[0].position).to.eql(1); pm.expect(body.members[1].acceptedProblems).to.eql(0); });"],
});
add('Ranking visto por un integrante', 'GET', '/analytics/teams/{{groupId}}/standings', 200, {
  auth: 'practBToken', tests: ["pm.test('Dos integrantes', () => pm.expect(body.members).to.have.lengthOf(2));"],
});

// --- US-12: temas con menor resolución ----------------------------------------------------------------------------------------
// El reporte solo cuenta problemas asignados en competencias FINALIZADA y la API no permite asignar a una competencia ya
// finalizada: por la API pura se recorre el escenario de error. El cálculo completo lo cubre TopicReportIntegrationTests.
add('Reporte de temas sin competencias FINALIZADA con problemas → 409 (escenario de error de US-12)', 'GET', '/analytics/teams/{{groupId}}/weaknesses', 409, {
  auth: 'coachToken',
  tests: ["pm.test('Explica la causa', () => pm.expect(body.errors.assignments).to.include('FINALIZADA'));"],
});

// --- US-13: resultados oficiales ------------------------------------------------------------------------------------------------
add('Crear competencia FINALIZADA', 'POST', '/competitions', 201, {
  auth: 'coachToken',
  body: '{ "teamId": {{groupId}}, "eventName": "Oficial {{runId}}", "status": "FINALIZADA", "startsAt": "{{pastStartsAt}}", "endsAt": "{{pastEndsAt}}" }',
  save: { finishedCompetitionId: 'body.id' },
});
add('No se asignan problemas a una competencia FINALIZADA → 409', 'POST', '/problems/assign', 409, {
  auth: 'coachToken', body: '{ "competitionId": {{finishedCompetitionId}}, "problems": [ { "problemId": {{problem1}} } ] }',
  tests: ["pm.test('Mensaje', () => pm.expect(body.message).to.include('finalizó'));"],
});
add('Registrar resultado pendiente', 'POST', '/competitions/{{finishedCompetitionId}}/official-result', 201, {
  auth: 'coachToken', body: '{ "confirm": false }',
  tests: ["pm.test('PENDIENTE', () => pm.expect(body.status).to.eql('PENDIENTE'));"],
});
add('Resultado duplicado → 409', 'POST', '/competitions/{{finishedCompetitionId}}/official-result', 409, { auth: 'coachToken', body: '{ "confirm": false }' });
add('Completar y confirmar el resultado', 'PUT', '/competitions/{{finishedCompetitionId}}/official-result', 200, {
  auth: 'coachToken', body: '{ "finalPosition": 3, "solvedProblems": 5, "confirm": true }',
  tests: ["pm.test('CONFIRMADO', () => { pm.expect(body.status).to.eql('CONFIRMADO'); pm.expect(body.finalPosition).to.eql(3); pm.expect(body.solvedProblems).to.eql(5); });"],
});
add('Consultar el resultado', 'GET', '/competitions/{{finishedCompetitionId}}/official-result', 200, {
  auth: 'coachToken', tests: ["pm.test('Posición 3', () => pm.expect(body.finalPosition).to.eql(3));"],
});
add('Historial de participaciones del equipo', 'GET', '/competitions/teams/{{groupId}}/official-results', 200, {
  auth: 'coachToken', tests: ["pm.test('Una participación confirmada', () => pm.expect(body).to.have.lengthOf(1));"],
});
add('No se confirma una competencia que no está FINALIZADA → 400', 'POST', '/competitions/{{competitionId}}/official-result', 400, {
  auth: 'coachToken', body: '{ "finalPosition": 1, "solvedProblems": 1, "confirm": true }',
});

// --- Seguridad en el flujo --------------------------------------------------------------------------------------------------------
add('Sin token no se ve el ranking → 401', 'GET', '/analytics/teams/{{groupId}}/standings', 401, {
  tests: [`pm.test('Mensaje', () => pm.expect(body.message).to.eql(${JSON.stringify(missingToken)}));`],
});

const output = {
  info: {
    name: 'KodikaLab - US07 a US14 Flujo completo',
    schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json',
    description: 'Ejecutar en orden en una base exclusiva de pruebas. Crea por la API un COACH, dos PRACTICANTES, un grupo, tres problemas, dos competencias, la asignación, una resolución y un resultado oficial; no requiere fixtures. Cada paso guarda en variables de colección los ids y tokens que usa el siguiente. Cubre US-07, US-08, US-09, US-11, US-12, US-13 y US-14 (más lo mínimo de US-01 a US-05 para llegar hasta ahí).',
  },
  variable: [
    { key: 'baseUrl', value: 'http://localhost:8080/api', type: 'string' },
    ...['coachToken', 'practAToken', 'practBToken'].map((key) => ({ key, value: '', type: 'string' })),
  ],
  event: [event('prerequest', [
    "if (pm.info.requestName.startsWith('01 ') || !pm.collectionVariables.get('runId')) {",
    "  const id = pm.variables.replaceIn('{{$guid}}').replace(/-/g, '').slice(0, 8);",
    "  pm.collectionVariables.set('runId', id);",
    "  pm.collectionVariables.set('coachEmail', `test.flow.coach.${id}@gmail.com`);",
    "  pm.collectionVariables.set('practAEmail', `test.flow.a.${id}@gmail.com`);",
    "  pm.collectionVariables.set('practBEmail', `test.flow.b.${id}@gmail.com`);",
    "  pm.collectionVariables.set('studentCodeA', `F${id}A`);",
    "  pm.collectionVariables.set('studentCodeB', `F${id}B`);",
    "  pm.collectionVariables.set('startsAt', new Date(Date.now() - 3600000).toISOString());",
    "  pm.collectionVariables.set('endsAt', new Date(Date.now() + 7200000).toISOString());",
    "  pm.collectionVariables.set('pastStartsAt', new Date(Date.now() - 10800000).toISOString());",
    "  pm.collectionVariables.set('pastEndsAt', new Date(Date.now() - 3600000).toISOString());",
    '}',
  ])],
  item: items.map((item, i) => ({ ...item, name: `${String(i + 1).padStart(2, '0')} ${item.name}` })),
};
writeFileSync(new URL('US07-US14-flujo.postman_collection.json', import.meta.url), JSON.stringify(output, null, 2) + '\n');
console.log(`US07-US14-flujo.postman_collection.json: ${items.length} solicitudes`);
