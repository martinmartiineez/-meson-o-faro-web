# Mesón O Faro — Analítica, seguridad y panel privado

ESTADO: DESARROLLO, SIN PUBLICAR. La web pública en GitHub Pages sigue intacta.

## Arquitectura existente respetada

GitHub guarda la web original; el constructor copia sus HTML/CSS/JS y las subcarpetas gestion/ y sorteo/ a Cloudflare Workers Static Assets. Google Sheets, Apps Script, menús, carta, promociones y reservas no se reescriben.

Cloudflare Worker añade defensa y analítica de servidor. Cloudflare D1 mantiene tablas separadas:
* page_hits: solicitudes HTML agrupadas por fecha, ruta y país, sin IP, cookies o identificadores.
* consents: aceptación explícita, identificador seudónimo, versión y caducidad.
* visit_events: sesiones y clics después del consentimiento, nunca IP.
* security_events: incidencias con IP obtenida en Cloudflare, navegador aproximado, país/ciudad cuando estén disponibles y código HTTP.
* ip_rules: bloqueos y excepciones con expiración.
* request_counters: HMAC de IP con clave secreta para limitar abusos; no almacena IP.
* settings: plazos de conservación.

No se recuperan estadísticas históricas inexistentes; el dashboard muestra únicamente datos reales.

## Activación necesaria por el propietario

1. Crear Cloudflare y añadir el dominio mesonofaro.es. No cambiar todavía los nameservers: copiar y revisar todos los registros DNS de DonDominio, especialmente correo y validaciones.
2. Crear D1: npx wrangler d1 create ofaro-analytics --jurisdiction eu. Sustituir el ID ficticio 00000000-0000-0000-0000-000000000000 del wrangler.json por el UUID asignado. Aplicar: npx wrangler d1 migrations apply ofaro-analytics --remote.
3. En analytics-cloudflare: npm install, npm test, npm run build y npm run deploy. Hacer primero despliegue de pruebas sin dominio de producción.
4. Generar una clave HMAC de alta entropía y establecerla mediante npx wrangler secret put LOG_HMAC_SECRET. Nunca subir contraseñas ni secretos a GitHub.
5. En Cloudflare Zero Trust > Access crear una aplicación protegida para mesonofaro.es/__ofaro/admin* y mesonofaro.es/__ofaro/api/admin/*, restringida al correo autorizado y preferiblemente con MFA. Configurar en Worker ACCESS_TEAM_DOMAIN, ACCESS_AUD (Audience Tag de Access) y ADMIN_EMAILS (lista de correos permitidos). El Worker verifica la firma RS256 y los claims de cada petición, no confía solo en ocultar la URL.
6. Configurar reglas WAF y rate limiting en el borde para actividad automatizada y fuerza bruta; empezar por desafío u observación para reducir falsos positivos. Los bloqueos de IP del Worker se consultan en todas las peticiones al dominio.
7. Probar en un hostname de preproducción con base de datos separada: carta, menú, reservas, imágenes, promociones, sorteos, PWA de gestión, legal, carga de mapas, consentimiento rechazado y aceptado, retirada, exportaciones, IP bloqueadas, desbloqueos y seguridad de API. No activar sobre visitantes reales hasta finalizar.
8. Revisar textos de privacidad y cookies y la pestaña Textos legales de Google Sheets, que puede sobrescribir el HTML publicado. Validar identidad del responsable, contrato DPA art. 28 RGPD con Cloudflare, transferencias, balance de interés legítimo, conservación, derechos, Google Maps, cookies de terceros y alertas.
9. Asociar el Worker al dominio y migrar nameservers en DonDominio solo tras las pruebas. Revisar HTTPS y www. Evaluar desactivar la publicación pública alternativa de GitHub Pages cuando Cloudflare funcione, para evitar vías que eludan el WAF (GitHub continúa siendo el repositorio y sigue siendo editable).
10. Las alertas externas están desactivadas salvo que se configure ALERT_WEBHOOK_URL y se valore la privacidad del receptor. El webhook jamás transmite la IP.

## Privacidad

La primera capa ofrece Aceptar, Rechazar y Configurar sin bloquear la web. El rechazo se recuerda solo localmente para no repetir avisos; no hay identificador analítico antes de aceptar. Tras aceptar se crea un UUID aleatorio persistente durante un máximo de 180 días, no renovable por nuevas visitas. La retirada desactiva de inmediato el cliente y solicita borrar los eventos, reintentando si falla la red. Google Maps se activa únicamente tras pulsar expresamente Cargar mapa.

Se aplica minimización: los accesos ordinarios contribuyen a estadísticas anónimas; los incidentes de seguridad guardan IP por defecto siete días. Una IP no identifica de forma inequívoca a un usuario. Los visitantes únicos estimados se limitan a quienes han consentido. No se ofrece historial íntegro de IP legítimas sin necesidad justificada.

El dashboard real se servirá en https://mesonofaro.es/__ofaro/admin, exclusivamente tras autenticar en Cloudflare Access. El código es público en GitHub, nunca las credenciales, datos D1 ni las IP. El administrador tendrá filtros y exportación CSV. Los bloqueos de IP caducan y pueden retirarse.

La exención AEPD para cookies de medición solo es posible bajo condiciones estrictas. Este desarrollo, por prudencia, exige consentimiento previo al seguimiento individualizado. La política legal necesita revisión antes de publicar. La disponibilidad de servicios gratuitos de Cloudflare depende del uso.

## Referencias

AEPD, guía general: https://www.aepd.es/guias/guia-cookies.pdf
AEPD, medición de audiencia: https://www.aepd.es/guias/guia-cookies-analiticas-externas.pdf
RGPD UE 2016/679; LOPDGDD 3/2018; LSSI-CE 34/2002, art. 22.2.
