# Frontend Booklane

Base Angular standalone; todavía sin autenticación, calendario ni reservas. La interfaz funcional se construye a partir de T021. Solo se muestra la página inicial.

Requiere Node 24.19.0 y npm 11.17.0; las versiones exactas están en package.json y package-lock.json.

Desde este directorio:

~~~powershell
npm ci
npm run build
npm run lint
npm test
npx playwright install chromium
npm run test:e2e
npm start
~~~

start sirve exclusivamente en http://127.0.0.1:4200. El proxy reenvía /api/** a http://127.0.0.1:8081 conservando el prefijo. La API debe arrancarse aparte para esas rutas; el frontend nunca se conecta directamente a booking o Kafka. La prueba de humo no necesita backend y Playwright arranca y detiene su propio servidor. No reutiliza otro proceso en el mismo puerto.

TypeScript y templates usan modo estricto. lint ejecuta los chequeos TypeScript de aplicación, tests y Playwright, sin añadir un framework de lint. build comprueba además los templates Angular y presupuestos de tamaño. Las pruebas unitarias usan el ejecutor Vitest de Angular CLI.

El componente raíz solo aloja router-outlet; la ruta inicial carga shell/app-shell.component de forma lazy y las rutas desconocidas vuelven a inicio. Las funcionalidades crearán sus rutas y carpetas cuando se implementen, sin componentes vacíos anticipados. app.config.ts reúne los providers.

SCSS define una base oscura con tipografía del sistema; no descarga fuentes ni recursos remotos. No hay credenciales, almacenamiento de sesión o acciones de negocio ficticias.
