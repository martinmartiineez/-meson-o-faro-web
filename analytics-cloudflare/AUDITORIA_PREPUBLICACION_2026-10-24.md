# Mesón O Faro — Auditoría previa a publicación (24-10-2026)

**Ámbito:** `feature/analitica-seguridad-cloudflare`, Worker `meson-o-faro-seguridad-test`, D1 de pruebas. **No publicar ni fusionar en `main` todavía.**

## Resultado

**APTO PARA SEGUIR PROBANDO. NO APTO AÚN PARA MIGRACIÓN A PRODUCCIÓN SIN VERIFICAR LOS PUNTOS BLOQUEANTES.**

Esta revisión es técnica y preliminar; no constituye dictamen jurídico ni prueba de configuración de toda la cuenta Cloudflare.

## Confirmado mediante lectura del código y CI

- Validación en Worker de token firmado por Cloudflare Access (RS256, issuer, audience, caducidad, correo permitido).
- Privacidad opcional: no hay eventos seudónimos antes de aceptar; el rechazo persiste; se permite retirar consentimiento.
- IP técnica de seguridad separada de eventos consentidos y contadores agregados.
- En opt-in de red, almacenamiento por 7 días de IP y comunidad autónoma; desde esta revisión, no se recoge la ciudad en nuevos registros de esa tabla.
- D1: SQL parametrizado y esquemas separados, limitación de tamaño para solicitudes JSON; autenticación en APIs privadas.
- Exportación y filtros del dashboard iOS, gráficos y tablas respaldados por pruebas automáticas.
- Ciclo diario para eliminar registros de seguridad, visitas consentidas, consentimientos caducados, contadores antiabuso y reglas IP expiradas.
- Protección básica HTTP de páginas públicas: `nosniff`, `Referrer-Policy`; política restrictiva de cámara solo en páginas de clientes (no en `/gestion/` ni `/sorteo/`).
- Las tareas CI de GitHub construyen la web pública sin empaquetar archivos `.gs`, Worker interno, migraciones ni secretos.

## Bloqueantes para aprobar producción

1. **Protección de la aplicación de gestión.** La URL `/gestion/` está disponible sin pasar por la verificación Access específica del panel `/__ofaro/admin`. La app conserva su propia clave validada por Apps Script. Verificar y configurar una política Cloudflare Access para todo `/gestion/*` —también JS, PWA y service worker— o una alternativa equivalente, con prueba real en iPhone de funcionamiento de reservas, impresión y escáner QR. No endurecer rutas a ciegas, para no romper la app.
2. **Datos reales y responsables.** Confirmar el contenido definitivo de aviso legal, cookies y privacidad (datos identificativos, finalidades, plazos, derechos, legitimación, responsables/encargados). Comprobar que la sincronización desde Google Sheets no reescribe los textos revisados. Revisar Google y Cloudflare como proveedores, acuerdos de encargo del tratamiento, ubicación real de D1 y garantías de transferencias internacionales. Una cookie técnica de elección no autoriza seguimiento adicional.
3. **Pruebas funcionales de flujos reales.** Ejecutar en el entorno de pruebas: Inicio/Carta/Menú, consentimiento aceptado/rechazado/revocado, reserva completa desde web a Google Sheets, actualización de carta/menú mediante gestión, funciones de sorteo, impresión/PWA, Google Maps bajo acción explícita y navegadores móviles.
4. **Protección y continuidad.** Verificar en Cloudflare el estado exacto de las reglas WAF/bot/rate limiting (no inferirlo del dashboard), MFA de ambos administradores, accesos autorizados, estado de los cron, auditoría de errores y plan de copia/restauración de D1. Realizar al menos una restauración de prueba. Decidir política de retención para `page_hits`, `region_hits` y `metric_hits`: actualmente **no se purgan automáticamente**. No eliminar históricos sin autorización.
5. **Migración y DNS.** Antes de asociar el dominio, inventariar MX/TXT/SPF/DKIM/DMARC y DNS del proveedor actual, verificar HTTPS, www/apex y redirecciones, y planificar qué hacer con la publicación alternativa en GitHub Pages para que no evite las protecciones del dominio. Se requiere aprobación explícita del propietario para cualquier cambio productivo.

## Observaciones y limitaciones

- La aplicación `/gestion/` almacena una clave propia en `localStorage`; conservar su funcionamiento exige evaluar también seguridad del dispositivo, XSS y autenticación del backend Apps Script.
- La geolocalización por red es aproximada. No equivale a ubicación exacta ni a persona única.
- El escudo del Worker dispone de límites y reglas D1, pero eso **no demuestra** que exista una regla WAF desplegada en toda la zona Cloudflare.
- La cuenta y el estado efectivo de Zero Trust, dominio, WAF, D1/ubicación y copias no son verificables solo desde el código del repositorio.
- Los indicadores de sesiones únicas requieren consentimiento y no representan a toda la audiencia.
- La protección de activos privados bajo `/__ofaro/` se valida en los tests automáticos, pero falta prueba externa sin iniciar sesión y con credenciales reales tras cualquier configuración nueva.

## Cambios técnicos realizados en esta auditoría

- `src/worker.js`: cabeceras HTTP de seguridad en HTML público sin inutilizar la cámara del gestor QR.
- `src/worker.js`: no almacenar ciudad en nuevos opt-in IP; convertir la región a comunidad autónoma. Clasificación correcta de iPad/tabletas.
- Nuevos tests de seguridad y minimización geográfica. No se modificaron migraciones, datos D1 existentes, `main` ni DNS.

## Regla de publicación

Solo cuando las verificaciones anteriores estén documentadas, con compilación y despliegue de pruebas correctos y aprobación del propietario, se prepara un procedimiento reversible de migración. **No realizar cambios automáticos en producción.**
