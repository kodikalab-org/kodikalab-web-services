# Contrato del agente de KodikaLab (F1.2)

Este documento fija lo que backend y frontend construyen. Hoy funciona **`POST /agent/chat`** con el agente
`insights` (temas débiles y recomendación de problemas); el estado de cada pieza está en la sección 7.

## 1. Reglas generales

- Base: `/api/agent`. Todos los endpoints requieren `Authorization: Bearer <JWT>`.
- La identidad y el rol salen del token. Ningún cuerpo ni parámetro acepta el id del usuario.
- Cada usuario accede solo a sus conversaciones. Una conversación ajena responde `404`.
- Idioma: `Accept-Language` con `es-419` (por defecto) o `en-US`.
- Errores: el mismo cuerpo que el resto de la API, `{"message": "...", "errors": {}}`.

## 2. Endpoints

| Método y ruta | Para qué | Responsable |
|---|---|---|
| `POST /agent/chat` | Enviar un mensaje de texto | Matías (F2.1) |
| `POST /agent/voice` | Enviar audio | Matías (F6.2) |
| `GET /agent/conversations` | Listar mis conversaciones | Luciano (F2.3) |
| `GET /agent/conversations/{id}/messages` | Ver el historial | Luciano (F2.3) |
| `DELETE /agent/conversations/{id}` | Borrar una conversación (`204`) | Luciano (F2.3) |
| `POST /agent/confirm` | Ejecutar la acción pendiente | Luciano (F4.1) |
| `POST /agent/reject` | Descartar la acción pendiente | Luciano (F4.1) |
| `GET /agent/knowledge/search?q=` | Ver qué fragmentos recupera el RAG (acceso restringido) | Sergio (F5.5) |

## 3. `POST /agent/chat`

```json
{
  "message": "¿En qué temas está más débil mi equipo?",
  "conversationId": "7f2c1d9e-5b0a-4c3f-9a51-2f6f0d1b8e44",
  "teamId": 7
}
```

| Campo | Obligatorio | Regla |
|---|---|---|
| `message` | Sí | De 1 a 1000 caracteres, sin contar espacios en los extremos |
| `conversationId` | No | Si se omite, se crea una conversación y su id vuelve en la respuesta |
| `teamId` | No | Equipo abierto en la aplicación; entero positivo |

**Qué equipo se usa:**

1. Si el usuario nombra un equipo, se busca entre **sus** equipos (sin distinguir mayúsculas ni tildes). Gana sobre
   `teamId`.
2. Si no nombra ninguno, se usa `teamId`.
3. Si no hay `teamId`, el nombre no coincide o coincide con varios, el agente pregunta. Nunca elige otro equipo.

`teamId` no da permisos: el service comprueba que el usuario pueda consultar ese equipo.

### Respuesta `200`

Un solo objeto. `conversationId`, `agent` y `message` están siempre. Los demás bloques **solo aparecen cuando hay
datos**.

```json
{
  "conversationId": "7f2c1d9e-5b0a-4c3f-9a51-2f6f0d1b8e44",
  "agent": "insights",
  "message": "El tema con menor cobertura es Programación dinámica.",
  "chart": {
    "title": "Cobertura por tema",
    "unit": "%",
    "labels": ["Programación dinámica", "Grafos"],
    "values": [25.00, 60.00]
  }
}
```

| Campo | Tipo | Contenido |
|---|---|---|
| `agent` | texto | `teams`, `insights`, `actions`, `info`, o `none` si ninguno corresponde |
| `teams` | lista | `groupId`, `name`, `expectedLevel`, `sessionSchedule`, `differences` (vacía = coincidencia exacta) |
| `problems` | lista | `problemId`, `title`, `url`, `topic`, `difficulty`, `reason`, `source` (`CATALOG` o `CODEFORCES`) |
| `chart` | objeto | `title`, `unit`, `labels`, `values` (un valor por etiqueta) |
| `action` | objeto | `actionId`, `type`, `summary`, `expiresAt` (ISO 8601 con zona) |

El frontend **no calcula cifras**: dibuja lo que recibe.

### Errores

| Código | Cuándo |
|---|---|
| `400` | Cuerpo inválido o campo fuera de regla (detalle en `errors`) |
| `401` | Sin token o token inválido |
| `403` | La cuenta no está activa |
| `404` | La conversación no existe o es de otro usuario |
| `429` | Se superó el límite de uso |
| `503` | El proveedor de IA no responde o la IA está desactivada |

Una consulta que el rol no permite **no** es un error HTTP: responde `200` con un `message` que lo explica. Un agente
sin tools responde `200` indicando que la función todavía no está disponible.

**Memoria (pendiente, F2.3):** cada mensaje se atiende por separado. `conversationId` se genera o se devuelve tal
cual, y el `404` de conversación ajena todavía no existe. Si el agente pregunta de qué equipo se trata, hay que
repetir la solicitud completa con su nombre.

### Recomendación de problemas (US-16)

- **Temas:** los de menor cobertura (hasta 5, con 3 propuestas cada uno), o el tema que pida el coach.
- **Dificultad:** el rango pedido; si no, desde la mediana de lo que el equipo resolvió hasta 300 puntos más; sin
  historial con dificultad, de menor a mayor.
- **Fuente:** primero el catálogo, sin problemas ya asignados al equipo. Si faltan opciones, Codeforces. Un problema
  de Codeforces llega sin `problemId`: hay que importarlo (`POST /problems/codeforces/import`) antes de asignarlo.
- **Limitación:** Codeforces clasifica con etiquetas en inglés (`graphs`, `dp`). Un tema del catálogo con otro nombre
  (por ejemplo, «Grafos») no recibe respaldo de Codeforces.
- Sin datos suficientes, sin opciones o sin acceso, no se propone nada y el mensaje explica la condición.

### Protecciones

10 mensajes por usuario y minuto; como máximo 5 llamadas a tools por mensaje; 30 s y 1 reintento por llamada al
proveedor; un error inesperado de una tool no se envía al modelo.

## 4. `POST /agent/voice`

`multipart/form-data`: `audio` (obligatorio, máximo 5 MB), `conversationId` y `teamId` (opcionales).

```json
{
  "transcript": "¿En qué temas está más débil mi equipo?",
  "response": { "conversationId": "…", "agent": "insights", "message": "…" },
  "audio": null
}
```

- `audio` es base64 solo si el backend tiene texto a voz. En modo gratuito es `null` y el navegador lee
  `response.message`.
- Con una acción pendiente, el audio **no llega al agente**: la voz nunca confirma.
- Audio vacío o ininteligible: `400`.

## 5. Confirmar o rechazar una acción

```json
{ "conversationId": "7f2c1d9e-5b0a-4c3f-9a51-2f6f0d1b8e44", "actionId": "c1b0…" }
```

- Responden `200` con el mismo objeto de la sección 3.
- Una acción vence y se usa una sola vez: vencida o ya usada responde `409`.
- Solo puede haber una acción pendiente por conversación.

## 6. Cómo crear una tool

`InsightTools.getWeakTopics` es el ejemplo de referencia.

1. El método lleva `@Tool` con una descripción que diga **cuándo** usarlo.
2. Sus parámetros son solo lo que sale de la conversación. **Nunca** el usuario, el rol ni ids de permisos.
3. La identidad se obtiene con `CurrentUserResolver`.
4. La tool llama a un **service** existente: no repite reglas de negocio ni accede a repositorios.
5. Las cifras y tarjetas van a `ResponseData.from(context)`.
6. Devuelve un `ToolOutcome` (`OK`, `NEEDS_TEAM`, `INSUFFICIENT_DATA`, `DENIED`).
7. Los errores esperados se devuelven como resultado; los inesperados se propagan.
8. Sus pruebas la invocan con `ToolCallbacks.from(...)`, igual que el modelo, y comprueban que el esquema no exponga
   datos de identidad.

El contexto de la petición lo arma el orquestador:

```java
Map<String, Object> context = new HashMap<>();
context.put(ResponseData.CONTEXT_KEY, new ResponseData());   // siempre
context.put(ResponseData.TEAM_ID_KEY, teamId);               // solo si hay un equipo abierto
```

`CurrentUserResolver` lee la sesión del hilo actual. Si el orquestador ejecuta tools en otro hilo (por ejemplo con
respuestas en streaming), debe propagar el contexto de seguridad.

## 7. Qué existe hoy

| Pieza | Estado |
|---|---|
| `AgentDefinition` (`agent/core`) | Creada |
| `AgentResponse`, `ResponseData`, `TeamMatchInfo`, `ProblemInfo`, `ChartData`, `ActionInfo` | Creados |
| `ToolOutcome`, `InsightTools` (`getWeakTopics`, `recommendProblems`) | Funcionando |
| `POST /agent/chat`, router, catálogo, protecciones | Funcionando |
| Agentes `teams`, `actions`, `info`; resumen de US-17 | Declarados o pendientes |
| Memoria y conversaciones, confirmaciones, RAG, voz, traducción de mensajes | Pendientes |

`ProblemInfo` y `ToolOutcome` no estaban en la lista de 28 clases de DOC.05: la estructura de KodikaLab tendrá
30, salvo que el equipo decida otra cosa.

## 8. Nota sobre el proveedor de pruebas

Con `ai-groq` y el modelo `openai/gpt-oss-20b`, Groq devuelve `reasoning_content` y rechaza la petición cuando
Spring AI lo reenvía durante el ciclo de tools. El perfil envía `include_reasoning: false` para evitarlo. Si se cambia
de modelo, hay que repetir la prueba real:

```bash
./mvnw test -Dtest=InsightToolsLiveTests -Dai.smoke=ai-groq
```
