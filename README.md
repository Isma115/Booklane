# Booklane

Portfolio local de reservas automáticas de salas y equipos ficticios. Arquitectura prevista: Angular/RxJS, API Spring WebFlux, Kafka y servicio booking con Hibernate/PostgreSQL. Las lecturas usan REST interno y las notificaciones SSE.

El proyecto está en preparación. Todavía no existen aplicaciones, Maven Wrapper, frontend ni Compose; no hay un comando de arranque de Booklane disponible.

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

Si Docker está detenido, arrancarlo y repetir la comprobación del servidor:

```powershell
docker desktop start
docker info --format '{{.OSType}} {{.ServerVersion}}'
```

El último comando debe indicar `linux`; disponer del cliente Docker no basta. No ejecutar todavía `docker compose up`, `mvnw` o `npm ci`: sus ficheros aún no existen. Mantener secretos y configuraciones generadas en `.local`, excluido de Git.

## Comprobación del entorno de desarrollo

El 27/10/2026 se verificaron Node 24.19.0, npm 11.17.0, WSL 2.7.11.0 y Docker Linux 29.6.2 con Compose 5.3.1 después de arrancar Docker Desktop. No se descargaron imágenes ni se iniciaron servicios de Booklane.

**Bloqueo de preparación:** `java -version` devuelve 1.8.0_401, `javac -version` devuelve 17.0.11 y `JAVA_HOME` no está definido. No se encontró JDK 21 en las ubicaciones habituales revisadas; Ubuntu WSL tampoco dispone de Java. Falta provisionar el JDK elegido y comprobar de nuevo ambos comandos. No se instaló ni sustituyó software durante este inventario.

La planificación y el registro detallado se conservan localmente, excluidos de Git por la configuración existente. El avance permanece en T001; no se ha completado ninguna tarea ni se han ejecutado pruebas de aplicación.
