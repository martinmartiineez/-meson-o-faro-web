# Mesón O Faro · Participaciones Android

APK interna para generar participaciones, imprimir tickets QR por ESC/POS TCP/IP y validar/canjear premios de un solo uso.

## Funciones

- Generación de códigos únicos mediante Google Apps Script + Google Sheets.
- Impresión ESC/POS por IP, puerto 9100 por defecto, con QR nativo.
- Escaneo con Google Code Scanner.
- Validación en servidor antes del canje.
- Canje atómico de un solo uso.
- Ajustes locales para endpoint, clave, IP/puerto de impresora y nombre de terminal.

## Backend

Acciones JSON esperadas:

- `participationPing`
- `participationCreate`
- `participationValidate`
- `participationRedeem`
- `participationMarkPrinted`

Todas las acciones internas requieren `key`.

## Compilar

JDK 17, Android SDK 35 y Gradle 8.9.

`gradle :app:assembleDebug`

Salida: `app/build/outputs/apk/debug/app-debug.apk`
