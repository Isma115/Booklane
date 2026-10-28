# Booklane

Portfolio local de reservas automáticas de salas y equipos ficticios. Arquitectura prevista: Angular/RxJS, API Spring WebFlux, Kafka y servicio booking con Hibernate/PostgreSQL. Las lecturas usan REST interno y las notificaciones SSE.

El proyecto está en preparación. Existen los tres módulos Java y la base Angular con pruebas. La configuración de infraestructura PostgreSQL/Kafka está preparada, pendiente de validación real. Las funciones de reservas siguen pendientes; todavía no hay un arranque completo de Booklane.

## Requisitos y versiones elegidas

Matriz contrastada el 27/10/2026. Los builds de base Java/Angular y contratos están verificados; todavía no hay integración completa de negocio.

| Componente | Versión elegida | Referencia |
| --- | --- | --- |
| JDK Eclipse Temurin | 21.0.12.1+1, Windows x64 | [Distribución oficial](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1) |
| Maven | 3.9.16, mediante Wrapper | [Descargas](https://maven.apache.org/download.cgi) |
| Spring Boot | 4.1.2, BOM común | [Requisitos](https://docs.spring.io/spring-boot/system-requirements.html) |
| Angular framework/compiler | 22.2.3 | [Paquete oficial](https://registry.npmjs.org/@angular/core/22.2.3) |
| Angular CLI/build | 22.2.4 | [Paquete oficial](https://registry.npmjs.org/@angular/build/22.2.4) |
| Angular Material/CDK | 22.0.7 | [Paquete oficial](https://registry.npmjs.org/@angular/material/22.0.7) |
| Node.js / npm | 24.19.0 / 11.17.0 | Disponibles; [compatibilidad Angular](https://angular.dev/reference/versions) |
| TypeScript / RxJS | 6.0.3 / 7.8.2 | [TypeScript](https://registry.npmjs.org/typescript/6.0.3), [RxJS](https://registry.npmjs.org/rxjs/7.8.2) |
| PostgreSQL | Imagen `postgres:18.6-bookworm` | [Imagen oficial](https://hub.docker.com/_/postgres) |
| Kafka | Imagen `apache/kafka:4.1.2`, KRaft | [Imagen oficial](https://kafka.apache.org/41/getting-started/docker/) |

Spring Framework, Spring Security, Spring Kafka, cliente Kafka, Hibernate y demás dependencias gestionadas quedan bajo el BOM de Spring Boot, sin versiones independientes. Compose fija las imágenes por etiqueta y digest; su funcionamiento queda pendiente de verificación real. El Wrapper y el lockfile Angular ya están versionados.

## Preparar Windows

Usar PowerShell, Git, un JDK 21 completo y Docker Desktop con backend WSL2 y contenedores Linux. Docker requiere virtualización habilitada y Windows dentro del soporte vigente; consultar los [requisitos oficiales de Windows/WSL](https://docs.docker.com/desktop/setup/install/windows-install/).

Comprobar desde una terminal nueva:

```powershell
java -version
javac -version
node --version
npm --version
wsl --version
wsl --status
docker compose version
docker info --format '{{.OSType}} {{.ServerVersion}}'
```

`java` y `javac` deben resolver al mismo JDK 21. Configurar `JAVA_HOME` con su directorio y anteponer su carpeta `bin` al `PATH`. Tener solamente un JRE o un compilador de otra versión no prepara el entorno elegido. Maven global no será necesario cuando exista el Wrapper.

La instalación aislada de este equipo se selecciona en cada terminal de PowerShell con:

```powershell
$env:JAVA_HOME = Join-Path $env:LOCALAPPDATA 'Booklane\tools\jdk-21.0.12.1+1'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
javac -version
```

En otro equipo, ajustar `JAVA_HOME` a la ubicación de su JDK. La selección por terminal conserva las instalaciones y variables globales existentes.

Si Docker está detenido, arrancarlo y repetir la comprobación del servidor:

```powershell
docker desktop start
docker info --format '{{.OSType}} {{.ServerVersion}}'
```

El último comando debe indicar `linux`; disponer del cliente Docker no basta. Para arrancar solo infraestructura, usar los comandos de la sección siguiente tras generar los secretos. Para el frontend, usar sus comandos más abajo. Mantener secretos y configuraciones generadas en `.local`, excluido de Git.

## Infraestructura local

Configuración preparada en T006, sin pruebas reales ejecutadas en esta continuación por el modo rápido del proyecto. Desde la raíz y con JDK 21/Docker Linux seleccionados:

```powershell
.\scripts\init-local.ps1
docker compose -f infra/compose.yaml up -d
docker compose -f infra/compose.yaml ps -a
```

PostgreSQL y Kafka usan volúmenes persistentes; Compose base no publica puertos al host. kafka-acl-bootstrap y kafka-provisioner deben terminar con código cero, además de la salud de ambos servidores. Son solo servicios de infraestructura: no se arrancan aplicaciones ni se habilitan reservas.

Para ejecutar aplicaciones desde el host:

```powershell
docker compose -f infra/compose.yaml -f infra/compose.dev.yaml up -d
```

El overlay dev publica exclusivamente 127.0.0.1:15432 (PostgreSQL) y 127.0.0.1:19092 (Kafka). Kafka anuncia kafka:9092 a contenedores y 127.0.0.1:19092 al host; autentica mediante SASL PLAIN y aplica ACL por servicio. Es un laboratorio local sin cifrado de transporte. No publicar fuera de esta máquina.

Para parar conservando datos, usar stop con los mismos archivos Compose. No usar down -v sobre datos que se quieran conservar ni borrar los secretos para corregir autenticación. init-local conserva los secretos existentes y genera también broker.env, exclusivo del bootstrap.

La verificación real preparada se ejecuta con `.\scripts\verify-infra.ps1`: utiliza un proyecto aislado, comprueba transporte y reinicio, y elimina sus propios volúmenes al terminar. Requiere los puertos dev libres. No se ha ejecutado en esta continuación y no demuestra todavía un recorrido de reservas.

## Compilar y comprobar Java

Desde la raíz, después de seleccionar el JDK 21:

```powershell
.\mvnw.cmd -B -ntp verify
```

En Linux/macOS con JDK 21: `./mvnw -B -ntp verify`. El Wrapper descarga Maven 3.9.16 y valida su SHA-256. Requiere acceso a Maven Central la primera vez.

El agregador construye `contracts-java`, `booklane-api` (WebFlux/Netty) y `booklane-booking` (MVC/Hibernate). Surefire ejecuta `*Test`; Failsafe reserva `*IT` para las pruebas de integración futuras. Actualmente hay 30 pruebas Java: diez de protocolo/huella, dieciséis de configuración/catálogos y cuatro de contexto/arquitectura, sin pruebas de persistencia o Kafka. contracts-java contiene records wire, normalización SHA-256 y serialización explícita.

Las pruebas arrancan servidores locales en puertos aleatorios y con perfil `test`. Solo en los recursos de test de booking se excluye DataSource: no se usa H2 y no se simula una conexión satisfactoria a PostgreSQL. Fuera de las pruebas, booking requiere su futura configuración PostgreSQL. No hay endpoints de negocio ni autenticación definitiva.

Para comprobar que Enforcer rechaza MVC/JDBC/JPA en api, dependencias cruzadas entre aplicaciones y Spring en contracts-java:

```powershell
.\mvnw.cmd -B -ntp install
.\scripts\check-dependency-boundaries.ps1
```

Las seis pruebas negativas generan POM y registros en `.local/dependency-probes`, sin modificar módulos. Necesitan el `install` previo para resolver los artefactos locales entre aplicaciones. Las reglas ArchUnit se amplían conforme aparezcan dominio, aplicación y adaptadores; sus reglas sobre paquetes futuros admiten temporalmente coincidencias vacías.

## Comprobación del entorno de desarrollo

El 27/10/2026 se verificaron Node 24.19.0, npm 11.17.0, WSL 2.7.11.0 y Docker Linux 29.6.2 con Compose 5.3.1 después de arrancar Docker Desktop. No se descargaron imágenes ni se iniciaron servicios de Booklane.

Tras autorización de instalación se descargó Temurin 21.0.12.1+1 y se verificó su SHA-256 oficial: `f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`. Con la selección por terminal indicada, `java -version` devuelve Temurin 21.0.12.1+1 y `javac -version` devuelve 21.0.12.1. Docker Linux y Compose se comprobaron de nuevo correctamente. No se modificó el Java global.

La planificación y el registro detallado se conservan localmente, excluidos de Git por la configuración existente. Entorno y base Java verificados; véase también la base Angular. Las funciones de negocio siguen pendientes.

## Base Angular

Desde frontend:

~~~powershell
npm ci
npm run build
npm run lint
npm test
npx playwright install chromium
npm run test:e2e
npm start
~~~

Node 24.19.0/npm 11.17.0. La página inicial standalone carga de forma lazy. El diseño funcional comienza en T021: todavía no hay acceso, calendario o acciones de reserva. TypeScript/templates son estrictos; lint ejecuta los chequeos de tipos de app, tests y E2E. Build valida además templates y tamaños.

El servidor solo escucha en http://127.0.0.1:4200 y reenvía /api/** a la API local en 127.0.0.1:8081, sin conectar con booking. Playwright inicia/detiene su servidor y requiere el puerto libre. El humo funciona sin backend. Más detalle en [frontend/README.md](frontend/README.md).

T003 ajusta la matriz con autorización del desarrollador: Angular framework/compiler 22.2.3, CLI/build 22.2.4 y Vitest 4.1.12 corrigen alertas de la resolución 22.0. Material/CDK permanece en 22.0.7, con peers compatibles. Un override acotado fija Vite de Angular build en 8.3.3 por sus [correcciones de seguridad](https://github.com/vitejs/vite/releases/tag/v8.3.3); retirarlo cuando build incorpore una versión corregida y repetir las comprobaciones. No se aplicó audit fix --force.

## Contratos y verificación de preparación

Los contratos HTTP público/interno y JSON Schema v1 están en [contracts](contracts/README.md), con ejemplos positivos y negativos. 202 es acuse Kafka; el resultado persistido se recuperará por consulta propia. No hay endpoints funcionales todavía.

Para verificar contratos y sus consumidores, seleccionar JDK 21, ejecutar npm ci en frontend y disponer de Python 3.11+ (comprobado con 3.11.0). Desde la raíz:

~~~powershell
.\scripts\verify-contracts.ps1
~~~

Los validadores Python se instalan con versiones exactas en .local/contracts-venv, sin cambios globales. El comando ejecuta Maven, valida contratos/ejemplos y JSON realmente serializado, comprueba los tipos Angular y ejecuta sus pruebas/build. En T004 pasaron 14 tests Java, dos Angular, ambos OpenAPI, tres JSON Schema y seis rechazos de dependencias prohibidas. La prueba Chromium de T003 sigue siendo solo humo de base, no una reserva real.

## Configuración local y cuentas ficticias

Seleccionar JDK 21 como se indica arriba y ejecutar desde la raíz, en Windows PowerShell 5.1:

~~~powershell
.\scripts\init-local.ps1
.\scripts\check-local-config.ps1
~~~

init-local comprueba herramientas y Docker Linux, sin instalar ni arrancar servicios. Genera siete secretos técnicos aleatorios en .local/runtime/secrets.json y env separados para API, booking, PostgreSQL, Kafka y provisión. Una segunda ejecución conserva las credenciales; un origen corrupto o desaparecido falla sin rotarlas. Los ficheros quedan restringidos al usuario actual y SYSTEM y excluidos de Git. No subir .local ni usar contraseñas personales. El inventario de variables está en [.env.example](.env.example).

El catálogo compartido [infra/demo/users.json](infra/demo/users.json) incluye únicamente estas cuentas públicas de demostración:

| Usuario | Contraseña demo pública | Rol |
| --- | --- | --- |
| alicia | AliciaDemo!2026 | USER |
| bruno | BrunoDemo!2026 | USER |
| admin | AdminDemo!2026 | ADMIN |

Los UUID completos y estables figuran en el catálogo. Son cuentas ficticias, sin registro público. API carga hashes BCrypt coste 12; booking conserva solo identidad y rol. Todavía no hay endpoint de acceso.

Para preparar la API en una terminal nueva, desde la raíz, cargar solo su fichero:

~~~powershell
Get-ChildItem Env:BOOKLANE_* | ForEach-Object { Remove-Item -LiteralPath ('Env:' + $_.Name) }
foreach ($line in Get-Content .local/runtime/api.env) {
    if ($line -match '^BOOKLANE_[A-Z_]+=') {
        $parts = $line -split '=', 2
        [Environment]::SetEnvironmentVariable($parts[0], $parts[1], 'Process')
    }
}
$env:SPRING_PROFILES_ACTIVE = 'dev'
~~~

Para booking, usar otra terminal y booking.env. El código anterior no ejecuta el contenido del fichero ni modifica variables globales. Los env de API/booking contienen la ruta absoluta del catálogo en el host; el futuro Compose deberá montar ese mismo catálogo como solo lectura y sobreescribir su ruta dentro del contenedor.

dev usa loopback: Kafka 19092, PostgreSQL 15432 y booking HTTP 8082; local usa nombres de contenedor kafka, postgres y booklane-booking. Las credenciales no tienen valores por defecto y la configuración/catálogo se valida antes de abrir conexiones. Se conservan los enlaces de servidor a loopback. T006 prepara la infraestructura descrita arriba; su validación real sigue pendiente y no habilita aún reservas/login.
