# 05 - Architecture

## Arquitectura por capas

El backend sigue una arquitectura simple por capas dentro del paquete raíz:

```txt
com.kodika.kodikalab
```

Estructura:

```txt
controller -> service -> repository -> entity
```

## Paquetes

| Paquete | Responsabilidad |
|---|---|
| `controller` | Exponer endpoints REST y delegar al servicio correspondiente. |
| `service` | Definir contratos de casos de uso. |
| `service.impl` | Implementar lógica de negocio. |
| `repository` | Acceder a persistencia mediante Spring Data JPA. |
| `entity` | Representar tablas del modelo relacional. |
| `dto` | Definir objetos de entrada y salida de la API. |
| `config` | Configuración transversal del backend. |

## Reglas de dependencia

- Un controller no debe acceder directamente a repositories.
- Un service puede usar repositories.
- Las entidades no deben depender de controllers o services.
- Los DTOs deben usarse para entrada/salida de API.
- La lógica de negocio debe vivir en services, no en controllers.

## Context path

El proyecto usa:

```yaml
server.servlet.context-path: /api
```

Por lo tanto, un controller con:

```java
@RequestMapping("/teams")
```

queda expuesto como:

```txt
/api/teams
```
