# 03 - API Contracts

## Convenciones generales

URL base local:

```txt
http://localhost:8080/api
```

Todos los contratos son estructurales durante el scaffolding. Los DTOs pueden cambiar cuando se implemente cada historia.

## Auth

```txt
POST /auth/register
POST /auth/login
```

## Users/Profile

```txt
GET  /users/me
PUT  /users/me
POST /users/me/handles
```

## Teams

```txt
POST  /teams
POST  /teams/{id}/join
PATCH /teams/{id}/memberships/{memberId}
GET   /teams/{id}/members
```

## Problems

```txt
POST /problems/assign
GET  /problems/assigned
POST /problems/{id}/submit
GET  /problems/{id}/resources
```

## Analytics

```txt
GET  /analytics/teams/{teamId}/topics
GET  /analytics/teams/{teamId}/standings
GET  /analytics/teams/{teamId}/weaknesses
POST /analytics/teams/{teamId}/competitions
GET  /analytics/users/me/independent-progress
```

## AI Assistant

```txt
POST /assistant/query
```

## Validación manual

Usar Swagger UI para revisar y probar endpoints:

```txt
http://localhost:8080/api/swagger-ui.html
```
