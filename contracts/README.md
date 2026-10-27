# Contratos Booklane v1

[HTTP público](http/public-api.yaml) y [HTTP interno](http/booking-internal.yaml) usan OpenAPI 3.1.1, escrito como JSON (subconjunto válido de YAML). Los schemas internos referencian los modelos públicos; no existe un endpoint interno de confirmación de reservas.

[Comandos](events/commands-v1.schema.json), [eventos](events/events-v1.schema.json) y [diagnóstico](events/failed-v1.schema.json) usan JSON Schema 2020-12. Los objetos son cerrados. Los ejemplos y sus errores esperados están en events/examples/manifest.json y http/examples/invalid.json. Son datos sintéticos de contrato, con fechas fijas; no prueban disponibilidad o permisos reales.

## Identidad y normalización

commandId identifica una intención estable por actor; messageId identifica un envío y cambia al reenviar. Kafka usa resourceId como clave. El navegador nunca elige actorId/actorRole: sesión en api, credencial técnica y catálogo común en booking. Una huella no autentica ni firma.

RequestFingerprint fija JSON UTF-8 compacto con este orden:

- Raíz: type, resourceId, payload.
- CreateBooking: startsAt, endsAt, title.
- CancelBooking: bookingId, reason.
- SetResourceStatus: active, reason, expectedVersion.

UUID en minúscula. Fechas con segundos, sin subsegundos, normalizadas al instante UTC. Texto recortado mediante String.strip (Character.isWhitespace); se conservan espacios interiores, mayúsculas y representación Unicode, sin normalización NFC. Motivo opcional ausente equivale a null; vacío permanece distinto de null. No participan actorRole, commandId, messageId, issuedAt o traceId; actorId acota la identidad SQL.

El resultado es SHA-256 hexadecimal minúsculo, 64 caracteres. Los tres vectores golden están en RequestFingerprintTest y en los ejemplos de comandos. La API normalizará antes de validar longitudes/publicar; booking recalculará la huella. El módulo no decide horario, disponibilidad, permisos o negocio.

WireJson usa una lista explícita de tipos del protocolo, sin resolver nombres de clases enviados. Rechaza claves duplicadas, campos desconocidos y JSON extra. Sus records son representación: los adaptadores deben validar el schema y límites antes de usarlos; no sustituye esa validación. FailedMessage solo admite metadatos de origen y código acotado, sin cuerpo original ni secretos.

## HTTP y privacidad

202 solo tras acuse Kafka, con Location y operación propia; no confirma una reserva. 503 NOT_ACCEPTED describe fallo antes de publicar; 504 DELIVERY_UNCERTAIN incluye commandId/requestHash y podría terminar en efecto. Reenvío conserva identidad/contenido.

GET de operación devuelve 200 SUCCEEDED/REJECTED, 404 OPERATION_NOT_OBSERVED o 409 IDEMPOTENCY_CONFLICT. ADMIN también consulta solo operaciones propias. La intención local permite esperar ante 404, sin recibos. Disponibilidad contiene franjas anónimas y resourceVersion; detalle de reserva contiene datos privados autorizados. La versión de reserva se llama version.

Rangos civiles from inclusivo/to exclusivo en Europe/Madrid, hasta 31 días. Anclas UTC evitan cálculos del huso del navegador. Páginas desde 0, size 20 por defecto/máximo 100, sort enumerado y desempate por id. Mis reservas separa status y period (FUTURE/PAST/ALL respecto al inicio y reloj servidor). Versiones públicas enteras hasta 2^53-1, representables en TypeScript.

Cookie SESSION y CSRF X-XSRF-TOKEN desde cookie XSRF-TOKEN. HTTP interno requiere Basic técnico y X-Actor-Id/X-Actor-Role conocidos; no se acepta principal del navegador. SSE avisa y dispara GET, no es historial durable. Las rutas demo exigen ADMIN y perfil habilitado. Estos son contratos previos; todavía no hay endpoints funcionales.

## Comprobación

Requiere JDK 21 seleccionado, Node/npm de la matriz, npm ci en frontend y Python 3.11+ (comprobado con 3.11.0). Desde la raíz:

~~~powershell
.\scripts\verify-contracts.ps1
~~~

Ejecuta Maven verify, instala los validadores fijados en un venv bajo .local, valida ambos OpenAPI, schemas, positivos/negativos y seis JSON realmente serializados por Java. Después comprueba TypeScript, ejemplos tipados Angular y build. No instala paquetes Python globales.

api.models.ts refleja mecánicamente los schemas públicos; verify-contracts.py comprueba su texto generado. Al cambiar contratos, actualizar también tipos y ejemplos tipados, manteniendo el orden de los schemas y ejecutando la verificación. La validación de mensajes de runtime, integración SQL/Kafka y CI se incorporan en sus tareas posteriores.
