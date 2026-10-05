-- SOLO para una base exclusiva de pruebas con el esquema nuevo usuario.
-- Contraseña ficticia: Password123; hash BCrypt de prueba, nunca una credencial real.
-- No elimina ni sobrescribe cuentas existentes: verificar la fila antes de probar.
INSERT INTO usuario (nombre_completo, correo, password_hash, estado_cuenta, rol, fecha_registro)
VALUES
    ('Usuario Prueba', 'test.us02.suspended@gmail.com',
     '$2a$10$44hlsHlqfEFvKgX9iGEglunJtmSAVlAzxzm7ssxOH2QfWg1sCzzxK',
     'SUSPENDIDO', 'PRACTICANTE', CURRENT_TIMESTAMP)
ON CONFLICT (correo) DO NOTHING;

SELECT correo, estado_cuenta, rol FROM usuario
WHERE correo = 'test.us02.suspended@gmail.com';
