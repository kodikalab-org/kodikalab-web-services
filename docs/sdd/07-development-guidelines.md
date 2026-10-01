# 07 - Development Guidelines

## Reglas generales

- No commitear `.env`.
- No commitear `target/`.
- No trabajar directamente sobre `main` o `develop`.
- Crear ramas desde `develop`.
- Ejecutar `./mvnw clean compile` antes de abrir Pull Request.

## Convención de ramas

```txt
feature/US<id>-<nombre-corto>
```

Ejemplo:

```txt
feature/US01-registro-usuarios
```

## Conventional Commits

Usar:

- `feat:` para nuevas funcionalidades.
- `fix:` para correcciones.
- `chore:` para configuración o tareas auxiliares.
- `refactor:` para refactorizaciones.
- `test:` para pruebas.

## Validación local

Compilar:

```bash
./mvnw clean compile
```

Ejecutar:

```bash
./mvnw spring-boot:run
```

Swagger UI:

```txt
http://localhost:8080/api/swagger-ui.html
```
