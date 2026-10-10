import { writeFileSync } from 'node:fs';

// Complete, reproducible Postman collections (US-01, US-02 and JWT/roles). All credentials here are test-only.
const event = (listen, exec) => ({ listen, script: { type: 'text/javascript', exec } });
const quote = JSON.stringify;
const invalidRole = 'El rol debe ser PRACTICANTE o COACH';
const weakPassword = 'La contraseña debe contener al menos 8 caracteres, una mayúscula y un número';
const duplicate = 'El correo institucional ya está vinculado a una cuenta existente';
const byteLimit = 'La contraseña no debe superar 72 bytes en UTF-8';
const missingToken = 'Debe iniciar sesión: envíe el token en el encabezado Authorization (Bearer)';
const invalidToken = 'El token es inválido o expiró: inicie sesión nuevamente';
const forbidden = 'No tiene permisos para realizar esta acción';
const registration = (email, role = 'PRACTICANTE', extra = {}) => ({
  firstName: 'Usuario', lastName: 'Prueba', email, password: 'Password123', role, ...extra,
});
const login = (email, extra = {}) => ({ email, password: 'Password123', ...extra });
const without = (body, field) => Object.fromEntries(Object.entries(body).filter(([key]) => key !== field));

// POST /auth/register y POST /auth/login (públicos: sin token).
function request(name, endpoint, body, status, { message, role, email, description = '' } = {}) {
  const exec = [
    `pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`,
    'const body = pm.response.json();',
    "pm.test('Sin contraseñas ni hashes', () => { for (const key of ['password', 'passwordHash', 'password_hash']) pm.expect(body).not.to.have.property(key); });",
    "pm.test('Mensaje público', () => pm.expect(body.message).to.be.a('string').and.not.empty);",
  ];
  if (message) exec.push(`pm.test('Mensaje esperado', () => pm.expect(body.message).to.eql(${quote(message)}));`);
  if (role) exec.push(`pm.test('Rol esperado', () => pm.expect(body.role).to.eql(${quote(role)}));`);
  if (email) exec.push(`pm.test('Correo normalizado', () => pm.expect(body.email).to.eql(pm.variables.replaceIn(${quote(email)}).trim().toLowerCase()));`);
  if (status >= 400) exec.push(
    "pm.test('Error sin identidad ni token', () => { pm.expect(body).not.to.have.property('email'); pm.expect(body).not.to.have.property('role'); pm.expect(body).not.to.have.property('token'); pm.expect(pm.response.headers.get('Set-Cookie')).to.be.undefined; });",
  );
  if (status < 400 && endpoint === 'register') exec.push(
    "pm.test('Solo campos públicos', () => pm.expect(Object.keys(body).sort()).to.eql(['email', 'message', 'role']));",
    "pm.test('El registro no inicia sesión', () => { pm.expect(body).not.to.have.property('token'); pm.expect(pm.response.headers.get('Set-Cookie')).to.be.undefined; });",
  );
  if (endpoint === 'login' && status === 200) exec.push(
    "pm.test('Solo campos públicos', () => pm.expect(Object.keys(body).sort()).to.eql(['email', 'expiresIn', 'message', 'role', 'token', 'tokenType']));",
    "pm.test('Token Bearer (JWT)', () => { pm.expect(body.tokenType).to.eql('Bearer'); pm.expect(body.token).to.be.a('string'); pm.expect(body.token.split('.')).to.have.lengthOf(3); pm.expect(body.expiresIn).to.be.above(0); });",
    "pm.test('Sin cookie de sesión', () => pm.expect(pm.response.headers.get('Set-Cookie')).to.be.undefined);",
    "const previous = pm.collectionVariables.get('lastToken');",
    "pm.test('Cada login emite un token nuevo', () => { if (previous) pm.expect(body.token).not.to.eql(previous); });",
    "pm.collectionVariables.set('lastToken', body.token);",
    "pm.collectionVariables.set(body.role === 'COACH' ? 'coachToken' : 'practitionerToken', body.token);",
  );
  return {
    name,
    request: {
      method: 'POST', header: [{ key: 'Content-Type', value: 'application/json' }], auth: { type: 'noauth' },
      body: { mode: 'raw', raw: typeof body === 'string' ? body : JSON.stringify(body, null, 2), options: { raw: { language: 'json' } } },
      url: { raw: `{{baseUrl}}/auth/${endpoint}`, host: ['{{baseUrl}}'], path: ['auth', endpoint] },
      description,
    },
    event: [event('test', exec)],
  };
}

// Cualquier otro endpoint, con o sin token Bearer (el nombre de una variable de colección).
function call(name, method, path, status, { token, rawAuthorization, body, message, tests = [], pre = [], description = '' } = {}) {
  const [pathname, query] = path.split('?');
  const exec = [`pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`, 'const body = pm.response.json();'];
  if (status >= 400) {
    exec.push("pm.test('Cuerpo de error {message, errors}', () => { pm.expect(body.message).to.be.a('string').and.not.empty; pm.expect(body).to.have.property('errors'); });");
  }
  if (message) exec.push(`pm.test('Mensaje esperado', () => pm.expect(body.message).to.eql(${quote(message)}));`);
  if (status === 401) exec.push("pm.test('Desafío WWW-Authenticate: Bearer', () => pm.expect(pm.response.headers.get('WWW-Authenticate')).to.include('Bearer'));");
  exec.push("pm.test('Sin cookie de sesión', () => pm.expect(pm.response.headers.get('Set-Cookie')).to.be.undefined);", ...tests);
  const header = [];
  if (body !== undefined) header.push({ key: 'Content-Type', value: 'application/json' });
  if (rawAuthorization) header.push({ key: 'Authorization', value: rawAuthorization });
  const item = {
    name,
    request: {
      method, header, auth: token ? { type: 'bearer', bearer: [{ key: 'token', value: `{{${token}}}`, type: 'string' }] } : { type: 'noauth' },
      url: {
        raw: `{{baseUrl}}${path}`, host: ['{{baseUrl}}'], path: pathname.split('/').filter(Boolean),
        ...(query ? { query: query.split('&').map((pair) => { const [key, value] = pair.split('='); return { key, value }; }) } : {}),
      },
      description,
    },
    event: [event('test', exec)],
  };
  if (body !== undefined) item.request.body = { mode: 'raw', raw: JSON.stringify(body, null, 2), options: { raw: { language: 'json' } } };
  if (pre.length) item.event.unshift(event('prerequest', pre));
  return item;
}

function collection(file, title, description, variables, items, setup) {
  const output = {
    info: { name: title, schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json', description },
    variable: [{ key: 'baseUrl', value: 'http://localhost:8080/api', type: 'string' }, ...variables],
    event: [event('prerequest', setup)],
    item: items.map((item, i) => ({ ...item, name: `${String(i + 1).padStart(2, '0')} ${item.name}` })),
  };
  writeFileSync(new URL(file, import.meta.url), JSON.stringify(output, null, 2) + '\n');
  console.log(`${file}: ${items.length} solicitudes`);
}

// ---------------------------------------------------------------------------------------------------------------
// US-01 — Registro
// ---------------------------------------------------------------------------------------------------------------
const registerItems = [];
const addRegister = (name, body, status, options) => registerItems.push(request(name, 'register', body, status, options));
const base = registration('{{registrationEmail}}');
const invalid = registration('{{invalidEmail}}');
addRegister('Registro PRACTICANTE', base, 201, { message: 'Registro exitoso', role: 'PRACTICANTE', email: base.email });
addRegister('Correo duplicado', base, 409, { message: duplicate });
addRegister('Duplicado normalizado', { ...base, email: '  {{uppercaseEmail}}  ' }, 409, { message: duplicate });
addRegister('Registro COACH', registration('{{coachEmail}}', 'COACH'), 201, { message: 'Registro exitoso', role: 'COACH', email: '{{coachEmail}}' });
addRegister('ADMIN rechazado', { ...invalid, role: 'ADMIN' }, 400, { message: invalidRole });
for (const password of ['12345', 'password123', 'Password']) {
  addRegister('Contraseña débil', { ...invalid, password }, 400, { message: weakPassword });
}
addRegister('Correo inválido', { ...invalid, email: 'correo-invalido' }, 400);
for (const field of ['firstName', 'lastName', 'email', 'password', 'role']) {
  addRegister(`${field} ausente`, without(invalid, field), 400);
}
addRegister('Rol desconocido', { ...invalid, role: 'ROOT' }, 400, { message: invalidRole });
addRegister('Rol inglés anterior rechazado', { ...invalid, role: 'PRACTITIONER' }, 400, { message: invalidRole });
addRegister('Rol nulo', { ...invalid, role: null }, 400);
addRegister('Rol numérico', { ...invalid, role: 0 }, 400, { message: invalidRole });
addRegister('Nombre en blanco', { ...invalid, firstName: '   ' }, 400);
addRegister('BCrypt ASCII supera 72 bytes', { ...invalid, password: 'A1' + 'a'.repeat(71) }, 400, { message: byteLimit });
addRegister('BCrypt UTF-8 supera 72 bytes', { ...invalid, password: 'A1' + 'ñ'.repeat(36) }, 400, { message: byteLimit });
addRegister('JSON mal formado', '{broken}', 400);
addRegister('Nombre completo 151 caracteres', { ...invalid, firstName: 'a'.repeat(80), lastName: 'b'.repeat(70) }, 400,
  { message: 'El nombre completo debe tener como máximo 150 caracteres' });
addRegister('Nombre completo 150 caracteres', registration('{{nameLimitEmail}}', 'PRACTICANTE', { firstName: 'a'.repeat(80), lastName: 'b'.repeat(69) }), 201,
  { message: 'Registro exitoso', role: 'PRACTICANTE', email: '{{nameLimitEmail}}' });
addRegister('Correo 101 caracteres', { ...invalid, email: '{{emailOverflow}}' }, 400, { message: 'El correo debe tener como máximo 100 caracteres' });
addRegister('Correo 100 caracteres', registration('{{emailLimit}}'), 201,
  { message: 'Registro exitoso', role: 'PRACTICANTE', email: '{{emailLimit}}' });
collection('US01-register.postman_collection.json', 'KodikaLab - US01 Auth ERD oficial',
  'Ejecutar en orden en una base exclusiva de pruebas. Crea cuatro cuentas, solo PRACTICANTE/COACH. Sin datos personales ni migraciones; el registro no inicia sesión ni entrega token. Ver tests/README.md.', [], registerItems, [
    "if (pm.info.requestName.startsWith('01 ') || !pm.collectionVariables.get('registrationEmail')) {",
    "  const id = pm.variables.replaceIn('{{$guid}}');",
    "  const email = `test.us01.${id}@gmail.com`;",
    "  pm.collectionVariables.set('registrationEmail', email);",
    "  pm.collectionVariables.set('uppercaseEmail', email.toUpperCase());",
    "  pm.collectionVariables.set('coachEmail', `test.us01.coach.${id}@gmail.com`);",
    "  pm.collectionVariables.set('invalidEmail', `test.us01.invalid.${id}@gmail.com`);",
    "  pm.collectionVariables.set('nameLimitEmail', `test.us01.name.${id}@gmail.com`);",
    "  const local = `test.us01.limit.${id}`;",
    "  const limit = local + '@' + 'a'.repeat(100 - local.length - 11) + '.gmail.com';",
    "  pm.collectionVariables.set('emailLimit', limit);",
    "  pm.collectionVariables.set('emailOverflow', 'a' + limit);",
    '}',
  ]);

// ---------------------------------------------------------------------------------------------------------------
// US-02 — Inicio de sesión con token JWT
// ---------------------------------------------------------------------------------------------------------------
const loginItems = [];
const addLogin = (name, body, status, options) => loginItems.push(request(name, 'login', body, status, options));
const normal = login('{{loginEmail}}');
const ok = { message: 'Inicio de sesión exitoso', role: 'PRACTICANTE', email: normal.email };
const unauthorized = { message: 'Credenciales inválidas' };
loginItems.push(request('Preparar PRACTICANTE', 'register', registration(normal.email), 201,
  { message: 'Registro exitoso', role: 'PRACTICANTE', email: normal.email }));
addLogin('Login PRACTICANTE', normal, 200, ok);
addLogin('Contraseña incorrecta', { ...normal, password: 'Wrong123' }, 401, unauthorized);
addLogin('Usuario inexistente', login('{{missingLoginEmail}}'), 401, unauthorized);
addLogin('Reintento correcto', normal, 200, ok);
addLogin('Correo normalizado', { ...normal, email: '  {{uppercaseLoginEmail}}  ' }, 200, ok);
loginItems.push(request('Preparar COACH', 'register', registration('{{coachLoginEmail}}', 'COACH'), 201,
  { message: 'Registro exitoso', role: 'COACH', email: '{{coachLoginEmail}}' }));
addLogin('Login COACH', login('{{coachLoginEmail}}'), 200,
  { message: 'Inicio de sesión exitoso', role: 'COACH', email: '{{coachLoginEmail}}' });
loginItems.push(request('Registro ADMIN rechazado', 'register', registration('{{missingLoginEmail}}', 'ADMIN'), 400,
  { message: invalidRole }));
addLogin('Cuenta ADMIN no creada', login('{{missingLoginEmail}}'), 401, unauthorized);
addLogin('Correo ausente', without(normal, 'email'), 400);
addLogin('Contraseña ausente', without(normal, 'password'), 400);
addLogin('Correo inválido', { ...normal, email: 'correo-invalido' }, 400);
addLogin('Contraseña en blanco', { ...normal, password: '   ' }, 400);
addLogin('Correo nulo', { ...normal, email: null }, 400);
addLogin('BCrypt supera 72 bytes', { ...normal, password: 'A1' + 'a'.repeat(71) }, 401, unauthorized);
addLogin('El cliente no puede elevar rol', { ...normal, role: 'ADMIN' }, 200, ok);
addLogin('Nuevo token en cada login', normal, 200, ok);
addLogin('JSON mal formado', '{broken}', 400);
addLogin('Cuenta SUSPENDIDO existente', login('{{suspendedLoginEmail}}'), 401,
  { ...unauthorized, description: 'Requiere fixture SQL y comprobar su fila SUSPENDIDO: un correo ausente también devuelve 401.' });
addLogin('No recortar contraseña', { ...normal, password: ' Password123 ' }, 401, unauthorized);
addLogin('Correo supera 100 caracteres', { ...normal, email: 'a'.repeat(60) + '@' + 'b'.repeat(30) + '.gmail.com' }, 400,
  { message: 'El correo debe tener como máximo 100 caracteres' });
collection('US02-login.postman_collection.json', 'KodikaLab - US02 Auth ERD oficial',
  'Ejecutar en orden en una base exclusiva de pruebas. Preparar la fixture SUSPENDIDO. Crea dos cuentas activas; no modifica otras. El login devuelve un token JWT (Bearer) y no crea cookies. Ver tests/US02-login.md.',
  [{ key: 'suspendedLoginEmail', value: 'test.us02.suspended@gmail.com', type: 'string' }], loginItems, [
    "if (pm.info.requestName.startsWith('01 ') || !pm.collectionVariables.get('loginEmail')) {",
    "  const id = pm.variables.replaceIn('{{$guid}}');",
    "  const email = `test.us02.${id}@gmail.com`;",
    "  pm.collectionVariables.set('loginEmail', email);",
    "  pm.collectionVariables.set('uppercaseLoginEmail', email.toUpperCase());",
    "  pm.collectionVariables.set('coachLoginEmail', `test.us02.coach.${id}@gmail.com`);",
    "  pm.collectionVariables.set('missingLoginEmail', `test.us02.missing.${id}@gmail.com`);",
    "  pm.collectionVariables.unset('lastToken');",
    '}',
  ]);

// ---------------------------------------------------------------------------------------------------------------
// Seguridad — JWT y autorización por rol (Spring Security)
// ---------------------------------------------------------------------------------------------------------------
const sec = [];
const tamper = [
  "const t = pm.collectionVariables.get('practitionerToken') || '';",
  "pm.collectionVariables.set('tamperedToken', t.slice(0, -4) + (t.endsWith('AAAA') ? 'BBBB' : 'AAAA'));",
];
sec.push(request('Registrar COACH', 'register', registration('{{secCoachEmail}}', 'COACH'), 201,
  { message: 'Registro exitoso', role: 'COACH', email: '{{secCoachEmail}}' }));
sec.push(request('Registrar PRACTICANTE', 'register', registration('{{secPractitionerEmail}}'), 201,
  { message: 'Registro exitoso', role: 'PRACTICANTE', email: '{{secPractitionerEmail}}' }));
sec.push(request('Login COACH (guarda coachToken)', 'login', login('{{secCoachEmail}}'), 200,
  { message: 'Inicio de sesión exitoso', role: 'COACH', email: '{{secCoachEmail}}' }));
sec.push(request('Login PRACTICANTE (guarda practitionerToken)', 'login', login('{{secPractitionerEmail}}'), 200,
  { message: 'Inicio de sesión exitoso', role: 'PRACTICANTE', email: '{{secPractitionerEmail}}' }));
sec.push(call('Documentación OpenAPI pública y con esquema Bearer', 'GET', '/v3/api-docs', 200, {
  tests: [
    "pm.test('Declara el esquema bearerAuth (JWT)', () => { const s = body.components.securitySchemes.bearerAuth; pm.expect(s.type).to.eql('http'); pm.expect(s.scheme).to.eql('bearer'); pm.expect(s.bearerFormat).to.eql('JWT'); });",
    "pm.test('Título de la API', () => pm.expect(body.info.title).to.eql('KodikaLab API'));",
  ],
}));
for (const [name, method, path, body] of [
  ['GET /teams', 'GET', '/teams'], ['GET /users/me', 'GET', '/users/me'], ['GET /problems', 'GET', '/problems'],
  ['POST /teams', 'POST', '/teams', {}], ['GET /analytics/teams/1/standings', 'GET', '/analytics/teams/1/standings'],
]) {
  sec.push(call(`Sin token: ${name} → 401`, method, path, 401, { message: missingToken, body }));
}
sec.push(call('Token manipulado → 401', 'GET', '/teams', 401, { token: 'tamperedToken', message: invalidToken, pre: tamper }));
sec.push(call('Token basura → 401', 'GET', '/teams', 401, { rawAuthorization: 'Bearer esto.no.es-un-jwt', message: invalidToken }));
sec.push(call('Esquema distinto de Bearer se ignora → 401', 'GET', '/teams', 401, { rawAuthorization: 'Basic dGVzdDp0ZXN0', message: missingToken }));
for (const [name, method, path, body] of [
  ['POST /teams', 'POST', '/teams', {}], ['POST /problems', 'POST', '/problems', {}],
  ['POST /problems/assign', 'POST', '/problems/assign', {}], ['POST /competitions', 'POST', '/competitions', {}],
  ['GET /teams/1/memberships', 'GET', '/teams/1/memberships?status=PENDIENTE'],
  ['GET /analytics/teams/1/weaknesses', 'GET', '/analytics/teams/1/weaknesses'],
  ['GET /competitions/teams/1/official-results', 'GET', '/competitions/teams/1/official-results'],
]) {
  sec.push(call(`PRACTICANTE no puede ${name} → 403`, method, path, 403, { token: 'practitionerToken', message: forbidden, body }));
}
for (const [name, method, path, body] of [
  ['POST /teams/1/join', 'POST', '/teams/1/join'],
  ['GET /analytics/teams/1/progress/me', 'GET', '/analytics/teams/1/progress/me'],
  ['POST /competitions/teams/1/problems/1/resolutions', 'POST', '/competitions/teams/1/problems/1/resolutions', { language: 'Java 21' }],
]) {
  sec.push(call(`COACH no puede ${name} → 403`, method, path, 403, { token: 'coachToken', message: forbidden, body }));
}
sec.push(call('COACH: GET /teams → 200', 'GET', '/teams', 200, { token: 'coachToken', tests: ["pm.test('Lista de grupos', () => pm.expect(body).to.be.an('array'));"] }));
sec.push(call('PRACTICANTE: GET /teams → 200', 'GET', '/teams', 200, { token: 'practitionerToken', tests: ["pm.test('Lista de grupos', () => pm.expect(body).to.be.an('array'));"] }));
sec.push(call('PRACTICANTE: GET /problems → 200', 'GET', '/problems', 200, { token: 'practitionerToken' }));
sec.push(call('COACH con rol correcto llega al servicio: POST /teams vacío → 400', 'POST', '/teams', 400, { token: 'coachToken', body: {} }));
collection('SEC-jwt-roles.postman_collection.json', 'KodikaLab - Seguridad JWT y roles',
  'Ejecutar en orden en una base exclusiva de pruebas. Crea un COACH y un PRACTICANTE nuevos, inicia sesión con ambos y comprueba: documentación pública, 401 sin token o con token inválido, 403 por rol y acceso correcto. No requiere fixtures ni datos previos.',
  [{ key: 'coachToken', value: '', type: 'string' }, { key: 'practitionerToken', value: '', type: 'string' }, { key: 'tamperedToken', value: '', type: 'string' }],
  sec, [
    "if (pm.info.requestName.startsWith('01 ') || !pm.collectionVariables.get('secCoachEmail')) {",
    "  const id = pm.variables.replaceIn('{{$guid}}');",
    "  pm.collectionVariables.set('secCoachEmail', `test.sec.coach.${id}@gmail.com`);",
    "  pm.collectionVariables.set('secPractitionerEmail', `test.sec.practitioner.${id}@gmail.com`);",
    "  pm.collectionVariables.unset('lastToken');",
    '}',
  ]);
