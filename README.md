# Booklane

Portfolio local de reservas automáticas de salas y equipos ficticios. Arquitectura prevista: Angular/RxJS, API Spring WebFlux, Kafka y servicio booking con Hibernate/PostgreSQL. Las lecturas usan REST interno y las notificaciones SSE.

El proyecto está en preparación. Existen los tres módulos Java y sus pruebas de contexto; el frontend, la infraestructura y las funciones de reservas se implementarán en las tareas siguientes. Todavía no hay un arranque completo de Booklane.

## Requisitos y versiones elegidas

Matriz contrastada el 27/10/2026. Esta base de implementación todavía no demuestra un build o una integración correctos.

| Componente | Versión elegida | Referencia |
| --- | --- | --- |
| JDK Eclipse Temurin | 21.0.12.1+1, Windows x64 | [Distribución oficial](https://github.com/adoptium/temurin21-binaries/releases/tag/jdk-21.0.12.1%2B1) |
| Maven | 3.9.16, mediante futuro Wrapper | [Descargas](https://maven.apache.org/download.cgi) |
| Spring Boot | 4.1.2, BOM común | [Requisitos](https://docs.spring.io/spring-boot/system-requirements.html) |
| Angular framework/compiler | 22.0.8 | [Paquete oficial](https://registry.npmjs.org/@angular/core/22.0.8) |
| Angular CLI/build | 22.0.9 | [Paquete oficial](https://registry.npmjs.org/@angular/build/22.0.9) |
| Angular Material/CDK | 22.0.7 | [Paquete oficial](https://registry.npmjs.org/@angular/material/22.0.7) |
| Node.js / npm | 24.19.0 / 11.17.0 | Disponibles; [compatibilidad Angular](https://angular.dev/reference/versions) |
| TypeScript / RxJS | 6.0.3 / 7.8.2 | [TypeScript](https://registry.npmjs.org/typescript/6.0.3), [RxJS](https://registry.npmjs.org/rxjs/7.8.2) |
| PostgreSQL | Imagen `postgres:18.6-bookworm` | [Imagen oficial](https://hub.docker.com/_/postgres) |
| Kafka | Imagen `apache/kafka:4.1.2`, KRaft | [Imagen oficial](https://kafka.apache.org/41/getting-started/docker/) |

Spring Framework, Spring Security, Spring Kafka, cliente Kafka, Hibernate y demás dependencias gestionadas quedan bajo el BOM de Spring Boot, sin versiones independientes. Las imágenes tienen etiquetas concretas; aún no se han descargado ni probado y sus digests quedan para la infraestructura. Los lockfiles y el Wrapper se crearán en sus tareas.

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

El último comando debe indicar `linux`; disponer del cliente Docker no basta. No ejecutar todavía `docker compose up` o `npm ci`: sus ficheros aún no existen. Mantener secretos y configuraciones generadas en `.local`, excluido de Git.

## Compilar y comprobar Java

Desde la raíz, después de seleccionar el JDK 21:

```powershell
.\mvnw.cmd -B -ntp verify
```

En Linux/macOS con JDK 21: `./mvnw -B -ntp verify`. El Wrapper descarga Maven 3.9.16 y valida su SHA-256. Requiere acceso a Maven Central la primera vez.

El agregador construye `contracts-java`, `booklane-api` (WebFlux/Netty) y `booklane-booking` (MVC/Hibernate). Surefire ejecuta `*Test`; Failsafe reserva `*IT` para las pruebas de integración futuras. Actualmente hay cuatro pruebas de contexto/arquitectura, sin pruebas de persistencia o Kafka. El módulo de protocolo todavía está vacío hasta T004.

Las pruebas arrancan servidores locales en puertos aleatorios y con perfil `isolated`. Solo en los recursos de test de booking se excluye DataSource: no se usa H2 y no se simula una conexión satisfactoria a PostgreSQL. Fuera de las pruebas, booking requiere su futura configuración PostgreSQL. No hay endpoints de negocio ni autenticación definitiva.

Para comprobar que Enforcer rechaza MVC/JDBC/JPA en api, dependencias cruzadas entre aplicaciones y Spring en contracts-java:

```powershell
.\mvnw.cmd -B -ntp install
.\scripts\check-dependency-boundaries.ps1
```

Las seis pruebas negativas generan POM y registros en `.local/dependency-probes`, sin modificar módulos. Necesitan el `install` previo para resolver los artefactos locales entre aplicaciones. Las reglas ArchUnit se amplían conforme aparezcan dominio, aplicación y adaptadores; sus reglas sobre paquetes futuros admiten temporalmente coincidencias vacías.

## Comprobación del entorno de desarrollo

El 27/10/2026 se verificaron Node 24.19.0, npm 11.17.0, WSL 2.7.11.0 y Docker Linux 29.6.2 con Compose 5.3.1 después de arrancar Docker Desktop. No se descargaron imágenes ni se iniciaron servicios de Booklane.

Tras autorización de instalación se descargó Temurin 21.0.12.1+1 y se verificó su SHA-256 oficial: `f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e`. Con la selección por terminal indicada, `java -version` devuelve Temurin 21.0.12.1+1 y `javac -version` devuelve 21.0.12.1. Docker Linux y Compose se comprobaron de nuevo correctamente. No se modificó el Java global.

La planificación y el registro detallado se conservan localmente, excluidos de Git por la configuración existente. T001 y T002 están completadas: entorno y base Java verificados. Las funciones de negocio y su infraestructura siguen pendientes.
