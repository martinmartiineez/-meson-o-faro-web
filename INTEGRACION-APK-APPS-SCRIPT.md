# Mesón O Faro · Integración segura APK + Apps Script

## Objetivo

Añadir la API interna de la APK sin sustituir ni borrar el backend que ya usa la web.

## 1. Crear un archivo nuevo en Apps Script

En el proyecto de Apps Script conectado a `Mesón O Faro · Control Web`:

1. Pulsa `+` → `Secuencia de comandos`.
2. Nómbralo `GestionInterna`.
3. Copia dentro el contenido de `gestion-interna-apps-script-addon.gs`.
4. Guarda.

El módulo no define `doGet()` ni `doPost()`, por lo que por sí solo no altera la web.

## 2. Añadir el enrutador al doPost actual

Dentro del `doPost(e)` que esté realmente desplegado, añade al PRINCIPIO de la función, antes de la lógica actual:

```javascript
const respuestaOFAroV2 = ofaroV2_tryHandlePost(e);
if (respuestaOFAroV2) return respuestaOFAroV2;
```

No borres ni sustituyas el resto del `doPost`.

El módulo solo intercepta estas acciones privadas:

- `appPing`
- `participationPing`
- `appBootstrap`
- `terminalPing`
- `reservationList`
- `reservationCreate`
- `reservationUpdate`
- `reservationMarkPrinted`
- `qrList`
- `templateList`
- `historyList`
- `historyAdd`
- `participationCreate`
- `participationMarkPrinted`
- `participationValidate`
- `participationRedeem`

Cualquier otra acción, incluida `reserve` de la web, devuelve `null` al enrutador y continúa por el código anterior.

## 3. Inicializar

Ejecuta manualmente una vez:

```javascript
ofaroV2_instalarGestionInterna
```

La función verifica la clave privada y la versión de API en la pestaña `Configuracion`. No es necesario regenerar la clave si ya existe.

## 4. Publicar de forma conservadora

En Apps Script:

`Implementar` → `Gestionar implementaciones` → editar la implementación existente → `Nueva versión` → `Implementar`.

Mantener la implementación existente conserva la URL `/exec` usada por la web y precargada en la APK.

## 5. Pruebas antes de usar en servicio

1. Abrir la web y comprobar Carta y Menú.
2. Enviar una reserva de prueba desde la web y comprobar que llega a `Reservas`.
3. En la APK: `Ajustes` → `Probar conexión con Google`.
4. Consultar `Reservas` desde la APK.
5. Imprimir un QR rápido.
6. Generar una única participación de prueba.
7. Validar el código y comprobar que un segundo canje queda bloqueado.

## Seguridad

- La web pública no recibe la clave de la APK.
- Las acciones internas exigen `Clave app gestión`.
- El canje usa `LockService`, evitando dos canjes simultáneos del mismo código.
- La rama `feature/participaciones-apk` es de desarrollo; no mezclarla con `main` hasta validar la integración real en Apps Script.
