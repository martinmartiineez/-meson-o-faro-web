# Mesón O Faro · Contrato APK v4.1.0 + Apps Script

## Estado actual

La APK oficial usa módulos **aditivos** sobre el Apps Script que ya atiende la web pública.

No se debe sustituir el proyecto, borrar archivos existentes, cambiar `doGet()` ni crear una segunda implementación para la APK.

La implementación de producción que debe conservarse es la misma URL `/exec` que ya utiliza O Faro.

## Orden del `doPost(e)` de producción

El orden actual debe mantenerse:

```javascript
function doPost(e) {
  try {
    const respuestaOFAroV2 = ofaroV2_tryHandlePost(e);
    if (respuestaOFAroV2) return respuestaOFAroV2;

    const respuestaOFAroWeb = ofaroWeb_tryHandlePost(e);
    if (respuestaOFAroWeb) return respuestaOFAroWeb;

    const respuestaOFAroPrint = ofaroPrint_tryHandlePost(e);
    if (respuestaOFAroPrint) return respuestaOFAroPrint;

    const respuestaOFAroPromo = ofaroPromo_tryHandlePost(e);
    if (respuestaOFAroPromo) return respuestaOFAroPromo;

    const respuestaOFAroPromoPublic = ofaroPromoPublic_tryHandlePost(e);
    if (respuestaOFAroPromoPublic) return respuestaOFAroPromoPublic;

    // Después continúa la lógica pública anterior, incluida action:'reserve'.
  } catch (err) {
    // Mantener el manejo de errores actual del proyecto.
  }
}
```

Todos los routers añadidos deben devolver `null` cuando no reconocen una acción. De esta forma la reserva pública `action:'reserve'` y cualquier ruta histórica siguen llegando a su lógica original.

## Módulos que forman el backend actual

### Gestión interna APK

Responsable de:

- conexión y heartbeat del terminal;
- reservas;
- QR rápidos;
- plantillas guardadas;
- historial.

Las antiguas acciones de Participaciones pueden permanecer por compatibilidad histórica, pero **la APK v4.1.0 no las usa**.

### Gestión Web

Responsable de los apartados editables de la web desde la APK/PWA.

### Impresión remota histórica

Puede permanecer en Apps Script por compatibilidad con sistemas anteriores, pero la APK v4.1.0 imprime **directamente por TCP/ESC-POS a la térmica**. El flujo oficial no contiene “Enviar a Android”.

### Promociones

Responsable de campañas, premios, ruleta, validación, canjes, estadísticas e historial.

### Promociones públicas

Responsable de:

- emitir invitaciones QR únicas;
- consultar una invitación sin consumirla;
- ejecutar la jugada del cliente;
- impedir reutilización de la invitación.

El resultado lo decide el servidor, no el navegador del cliente.

## Único módulo pendiente antes de aceptar v4.1.0

Archivo del repositorio:

`reservas-apk-v3-apps-script-addon.gs`

Debe añadirse al proyecto Apps Script como archivo nuevo, por ejemplo `ReservasAPKv3.gs`.

Su router solo intercepta:

```text
reservationFullUpdate
```

y devuelve `null` para cualquier otra acción.

Este módulo permite editar desde Android:

- fecha;
- hora;
- nombre;
- teléfono;
- correo;
- personas;
- mesa;
- zona;
- observaciones.

Está preparado para usar los helpers reales de `GestionInterna`, `LockService` y el historial existente.

### Integración en `doPost`

Después de `ofaroV2_tryHandlePost(e)` y antes de continuar con los demás módulos, añadir exactamente:

```javascript
const respuestaReservasV3 = ofaroReservationsV3_tryHandlePost(e);
if (respuestaReservasV3) return respuestaReservasV3;
```

No eliminar ni modificar las rutas existentes.

## Publicación segura en Apps Script

1. Guardar todos los archivos.
2. No modificar `doGet()`.
3. `Implementar` → `Gestionar implementaciones`.
4. Editar **la implementación de producción existente**.
5. Elegir `Nueva versión`.
6. Implementar manteniendo la misma URL `/exec`.
7. Ejecutar desde Android `Diagnóstico` → prueba completa de contratos.

No crear una URL `/exec` nueva salvo que se decida migrar deliberadamente todos los clientes.

## Diagnóstico obligatorio en Android

La APK v4.1.0 incorpora dos pruebas independientes:

### Contrato Apps Script

Comprueba sin crear/canjear/eliminar datos:

- servidor interno;
- reservas;
- QR rápidos;
- plantillas guardadas;
- historial;
- Gestión Web;
- Promociones privadas;
- backend público de QR/promociones;
- lista y detalle de campañas;
- premios.

### Motor gráfico

Renderiza localmente:

- 28 plantillas;
- en 58 mm y 80 mm;
- total: 56 composiciones;
- varios tamaños de QR;
- composición con imagen.

Debe terminar `56/56` antes de considerar la APK candidata a producción.

## Prueba física de aceptación

En este orden:

1. Reiniciar Android y comprobar que el watchdog de impresora vuelve solo.
2. Confirmar estado `Impresora · lista`.
3. Imprimir ticket de diagnóstico.
4. Probar corte completo, parcial y sin corte.
5. Probar QR y escanearlo con otro teléfono.
6. Crear una reserva desde la APK.
7. Editar la reserva completa.
8. Imprimir el ticket de reserva.
9. Generar un QR de promoción de un solo uso.
10. Abrirlo sin jugar y comprobar que sigue válido.
11. Jugar una vez.
12. Validar/canjear un premio si corresponde.
13. Comprobar que un segundo canje queda rechazado.
14. Comprobar que la web pública y su formulario de reservas siguen funcionando.

## Firma de la APK de producción

No usar un APK debug como instalación permanente.

La rama incluye el workflow:

`.github/workflows/ofaro-production-apk.yml`

La release se dispara mediante una etiqueta con este formato:

```text
ofaro-prod-v4.1.0
```

La etiqueta debe coincidir exactamente con `versionName`.

El workflow exige cuatro GitHub Actions Secrets:

- `OFARO_ANDROID_KEYSTORE_BASE64`
- `OFARO_ANDROID_KEYSTORE_PASSWORD`
- `OFARO_ANDROID_KEY_ALIAS`
- `OFARO_ANDROID_KEY_PASSWORD`

La clave privada y sus contraseñas **nunca deben guardarse en el repositorio**.

La release ejecuta `lintRelease`, compila, verifica la firma con `apksigner` y publica el APK junto a su SHA-256 y certificado público.

## Reglas de seguridad

- No exponer la clave de gestión en documentación, tickets, QR o logs.
- `allowBackup=false` en Android.
- La API de producción usa HTTPS.
- La impresora usa TCP local ESC/POS, no HTTP.
- Las escrituras de negocio no se reintentan automáticamente desde Android.
- Los canjes y operaciones críticas del servidor usan bloqueo cuando corresponde.
- No sustituir `doGet()` ni el formulario público de reservas.
