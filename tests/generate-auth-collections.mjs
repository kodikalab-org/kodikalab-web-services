import { writeFileSync } from 'node:fs';

// Complete, reproducible Postman collections. All credentials here are test-only.
const event = (listen, exec) => ({ listen, script: { type: 'text/javascript', exec } });
const quote = JSON.stringify;
const invalidRole = 'El rol debe ser PRACTICANTE o COACH';
const weakPassword = 'La contraseña debe contener al menos 8 caracteres, una mayúscula y un número';
const duplicate = 'El correo institucional ya está vinculado a una cuenta existente';
const byteLimit = 'La contraseña no debe superar 72 bytes en UTF-8';
const registration = (email, role = 'PRACTICANTE', extra = {}) => ({
  firstName: 'Usuario', lastName: 'Prueba', email, password: 'Password123', role, ...extra,
});
const login = (email, extra = {}) => ({ email, password: 'Password123', ...extra });
const without = (body, field) => Object.fromEntries(Object.entries(body).filter(([key]) => key !== field));

function request(name, endpoint, body, status, { message, role, email, description = '' } = {}) {
  const exec = [
    `pm.test('HTTP ${status}', () => pm.response.to.have.status(${status}));`,
    'const body = pm.response.json();',
    "pm.test('Sin datos sensibles', () => { for (const key of ['password', 'passwordHash', 'password_hash', 'token']) pm.expect(body).not.to.have.property(key); });",
    "pm.test('Mensaje público', () => pm.expect(body.message).to.be.a('string').and.not.empty);",
  ];
  if (message) exec.push(`pm.test('Mensaje esperado', () => pm.expect(body.message).to.eql(${quote(message)}));`);
  if (role) exec.push(`pm.test('Rol esperado', () => pm.expect(body.role).to.eql(${quote(role)}));`);
  if (email) exec.push(`pm.test('Correo normalizado', () => pm.expect(body.email).to.eql(pm.variables.replaceIn(${quote(email)}).trim().toLowerCase()));`);
  if (status >= 400) exec.push(
    "pm.test('Error sin identidad ni nueva sesión', () => { pm.expect(body).not.to.have.property('email'); pm.expect(body).not.to.have.property('role'); pm.expect(pm.response.headers.get('Set-Cookie')).to.be.undefined; });",
  );
  if (status < 400) exec.push(
    "pm.test('Solo campos públicos', () => pm.expect(Object.keys(body).sort()).to.eql(['email', 'message', 'role']));",
  );
  if (endpoint === 'login' && status === 200) exec.push(
    "const cookie = pm.response.headers.get('Set-Cookie');",
    "pm.test('Cookie segura para HTTP local', () => { pm.expect(cookie).to.include('JSESSIONID='); pm.expect(cookie).to.match(/HttpOnly/i); pm.expect(cookie).to.match(/SameSite=Lax/i); });",
    "if (cookie) { const id = cookie.split(';')[0]; const old = pm.collectionVariables.get('lastSessionCookie'); pm.test('Renovación de sesión', () => { if (old) pm.expect(id).not.to.eql(old); }); pm.collectionVariables.set('lastSessionCookie', id); }",
  );
  return {
    name,
    request: {
      method: 'POST', header: [{ key: 'Content-Type', value: 'application/json' }],
      body: { mode: 'raw', raw: typeof body === 'string' ? body : JSON.stringify(body, null, 2), options: { raw: { language: 'json' } } },
      url: { raw: `{{baseUrl}}/auth/${endpoint}`, host: ['{{baseUrl}}'], path: ['auth', endpoint] },
      description,
    },
    event: [event('test', exec)],
  };
}

function collection(story, description, variables, items, setup) {
  const file = story === 'US01' ? 'US01-register.postman_collection.json' : 'US02-login.postman_collection.json';
  const output = {
    info: { name: `KodikaLab - ${story} Auth ERD oficial`, schema: 'https://schema.getpostman.com/json/collection/v2.1.0/collection.json', description },
    variable: [{ key: 'baseUrl', value: 'http://localhost:8080/api', type: 'string' }, ...variables],
    event: [event('prerequest', setup)],
    item: items.map((item, i) => ({ ...item, name: `${String(i + 1).padStart(2, '0')} ${item.name}` })),
  };
  writeFileSync(new URL(file, import.meta.url), JSON.stringify(output, null, 2) + '\n');
  console.log(`${file}: ${items.length} solicitudes`);
}

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
collection('US01', 'Ejecutar en orden en una base exclusiva de pruebas. Crea cuatro cuentas, solo PRACTICANTE/COACH. Sin datos personales, JWT ni migraciones. Ver tests/README.md.', [], registerItems, [
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
addLogin('Renovación de sesión', normal, 200, ok);
addLogin('JSON mal formado', '{broken}', 400);
addLogin('Cuenta SUSPENDIDO existente', login('{{suspendedLoginEmail}}'), 401,
  { ...unauthorized, description: 'Requiere fixture SQL y comprobar su fila SUSPENDIDO: un correo ausente también devuelve 401.' });
addLogin('No recortar contraseña', { ...normal, password: ' Password123 ' }, 401, unauthorized);
addLogin('Correo supera 100 caracteres', { ...normal, email: 'a'.repeat(60) + '@' + 'b'.repeat(30) + '.gmail.com' }, 400,
  { message: 'El correo debe tener como máximo 100 caracteres' });
collection('US02', 'Ejecutar en orden en una base exclusiva de pruebas. Preparar la fixture SUSPENDIDO. Crea dos cuentas activas; no modifica otras. Mantener cookie jar habilitado. Ver tests/US02-login.md.',
  [{ key: 'suspendedLoginEmail', value: 'test.us02.suspended@gmail.com', type: 'string' }], loginItems, [
    "if (pm.info.requestName.startsWith('01 ') || !pm.collectionVariables.get('loginEmail')) {",
    "  const id = pm.variables.replaceIn('{{$guid}}');",
    "  const email = `test.us02.${id}@gmail.com`;",
    "  pm.collectionVariables.set('loginEmail', email);",
    "  pm.collectionVariables.set('uppercaseLoginEmail', email.toUpperCase());",
    "  pm.collectionVariables.set('coachLoginEmail', `test.us02.coach.${id}@gmail.com`);",
    "  pm.collectionVariables.set('missingLoginEmail', `test.us02.missing.${id}@gmail.com`);",
    "  pm.collectionVariables.unset('lastSessionCookie');",
    '}',
  ]);
