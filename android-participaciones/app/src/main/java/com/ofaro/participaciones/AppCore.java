package com.ofaro.participaciones;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class AppCore {
    static final String PREFS = "ofaro_participaciones";
    static final String DEFAULT_API = ApiEndpoint.URL;
    static final String APP_VERSION = "4.0.0";
    private static final int MAX_IMAGE_DOTS = 576;
    private static final Set<String> SAFE_RETRY_ACTIONS = new HashSet<>(Arrays.asList(
            "appPing","participationPing","appBootstrap","terminalPing",
            "reservationList","qrList","templateList","historyList",
            "webSections","webSectionRows","promotionList","prizeList","promotionStats",
            "wheelSegmentsList","promotionPrizeLinksList","promotionHistory",
            "promotionValidate","promoPublicGet","printQueueStatus","printQueueList"
    ));

    private final SharedPreferences prefs;
    private final Context context;

    AppCore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateDefaults();
    }

    private void migrateDefaults() {
        String stored = prefs.getString("api", "").trim();
        if (stored.isEmpty() || stored.contains("AKfycbwyICNMM0CHeSFQqOaO4d6g_d84vougY6OivfrMi6G5DIIVy7Y1qK_v2tBsZKmnQ2njkQ")) {
            prefs.edit().putString("api", DEFAULT_API).apply();
        }
    }

    SharedPreferences prefs() { return prefs; }
    String api() {
        String value = prefs.getString("api", DEFAULT_API).trim();
        return value.isEmpty() ? DEFAULT_API : value;
    }
    String key() { return prefs.getString("key", "").trim(); }
    String terminal() {
        String v = prefs.getString("terminal", "Caja O Faro").trim();
        return v.isEmpty() ? "Caja O Faro" : v;
    }
    String printerIp() { return prefs.getString("printerIp", "").trim(); }
    int printerPort() { return prefs.getInt("printerPort", 9100); }
    boolean configured() { return !api().isEmpty() && !key().isEmpty(); }

    JSONObject action(String action) throws Exception {
        return new JSONObject()
                .put("action", action)
                .put("key", key())
                .put("terminal", terminal())
                .put("appVersion", APP_VERSION)
                .put("printerIp", printerIp());
    }

    JSONObject post(JSONObject body) throws Exception {
        String action = body == null ? "" : body.optString("action", "");
        int attempts = SAFE_RETRY_ACTIONS.contains(action) ? 2 : 1;
        Exception last = null;
        for (int i=0; i<attempts; i++) {
            try { return postTo(api(), body); }
            catch (Exception e) {
                last = e;
                if (i+1 < attempts) try { Thread.sleep(350L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        throw last == null ? new Exception("No se pudo conectar con el servidor") : last;
    }

    JSONObject postTo(String endpoint, JSONObject body) throws Exception {
        if (endpoint == null || endpoint.trim().isEmpty()) throw new Exception("Endpoint vacío.");
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(25000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
            conn.setRequestProperty("User-Agent", "OFaroAndroid/" + APP_VERSION);
            byte[] payload = (body == null ? "{}" : body.toString()).getBytes(Charset.forName("UTF-8"));
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = conn.getOutputStream()) { os.write(payload); os.flush(); }
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(stream);
            if (text == null || text.trim().isEmpty()) throw new Exception("Respuesta vacía del servidor (HTTP " + status + ").");
            String trimmed = text.trim();
            if (trimmed.startsWith("<")) throw new Exception("Google devolvió HTML en lugar de datos. Revisa la implementación de Apps Script.");
            try { return new JSONObject(trimmed); }
            catch (Exception e) { throw new Exception("Respuesta no válida: " + trimmed.substring(0, Math.min(trimmed.length(), 180))); }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    void ensureOk(JSONObject json) throws Exception {
        if (json == null || !json.optBoolean("ok", false)) throw new Exception(json == null ? "Respuesta vacía" : json.optString("error", "Operación rechazada"));
    }

    Bitmap loadBitmap(String uriText) throws Exception {
        if (uriText == null || uriText.trim().isEmpty()) return null;
        Uri uri = Uri.parse(uriText.trim());
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("No se pudo abrir la imagen seleccionada.");
            Bitmap bitmap = BitmapFactory.decodeStream(in);
            if (bitmap == null) throw new Exception("El archivo seleccionado no es una imagen compatible.");
            return bitmap;
        }
    }

    void startPrinterWatchdog() {
        if (printerIp().isEmpty()) return;
        try {
            android.content.Intent service = new android.content.Intent(context, PrintReceiverService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
        } catch (Exception ignored) {}
    }

    String printerStatus() { return PrinterConnectionManager.get().statusText(); }
    boolean printerConnected() { return PrinterConnectionManager.get().isConnected(); }
    void reconnectPrinter() { PrinterConnectionManager.get().reconnect(printerIp(), printerPort()); }

    void printTicket(String title, String subtitle, String body, String qrData, String imageUri, int imagePosition) throws Exception {
        Bitmap bitmap = null;
        if (imagePosition != 0 && imageUri != null && !imageUri.trim().isEmpty()) bitmap = loadBitmap(imageUri);
        Bitmap finalBitmap = bitmap;
        PrinterConnectionManager.get().execute(printerIp(), printerPort(), out -> {
            escInit(out);
            if (finalBitmap != null && imagePosition == 1) {
                align(out, 1); writeBitmap(out, finalBitmap); writeText(out, "\n");
            }
            if (!safe(title).isEmpty()) {
                align(out,1); bold(out,true); doubleSize(out,true);
                writeText(out, safe(title).toUpperCase() + "\n");
                doubleSize(out,false); bold(out,false);
            }
            if (!safe(subtitle).isEmpty()) {
                align(out,1); bold(out,true); writeText(out, safe(subtitle).toUpperCase() + "\n"); bold(out,false);
            }
            if (!safe(title).isEmpty() || !safe(subtitle).isEmpty()) { align(out,1); writeText(out,"------------------------------\n"); }
            if (!safe(body).isEmpty()) { align(out,0); writeText(out,"\n" + body.trim() + "\n"); }
            if (!safe(qrData).isEmpty()) { align(out,1); writeText(out,"\n"); writeQr(out,qrData,7); writeText(out,"\n"); }
            if (finalBitmap != null && imagePosition == 2) { align(out,1); writeText(out,"\n"); writeBitmap(out,finalBitmap); writeText(out,"\n"); }
            writeText(out,"\n\n"); cut(out);
        });
    }

    void printQrTicket(String title, String text, String qrData) throws Exception { printTicket(title,"",text,qrData,"",0); }
    void printFreeText(String title, String text, String qrData) throws Exception { printTicket(title,"",text,qrData,"",0); }

    void printReservation(JSONObject r) throws Exception {
        StringBuilder body = new StringBuilder();
        body.append(r.optString("date", "")).append("   ").append(r.optString("time", "")).append("\n\n");
        body.append(r.optString("name", "").toUpperCase()).append("\n");
        body.append(r.optInt("people",0)).append(" PERSONAS\n");
        String table = r.optString("table",""); String zone = r.optString("zone","");
        if (!table.isEmpty()) body.append("Mesa: ").append(table).append("\n");
        if (!zone.isEmpty() && !"Sin asignar".equalsIgnoreCase(zone)) body.append("Zona: ").append(zone).append("\n");
        String phone = r.optString("phone",""); if (!phone.isEmpty()) body.append("Tel: ").append(phone).append("\n");
        String notes = r.optString("notes",""); if (!notes.isEmpty()) body.append("\nOBSERVACIONES\n").append(notes).append("\n");
        body.append("\n").append(r.optString("id",""));
        printTicket("MESON O FARO","RESERVA",body.toString(),"","",0);
    }

    void printTest(String ip, int port) throws Exception {
        JSONObject job = new JSONObject()
                .put("templateId","Minimal Premium")
                .put("typography","O Faro")
                .put("paperWidth",80)
                .put("title","MESÓN O FARO")
                .put("subtitle","PRUEBA ESC/POS")
                .put("text","Conexión directa correcta\n" + ip + ":" + port)
                .put("qr","OFARO:PRUEBA")
                .put("qrSize","M")
                .put("separator","line")
                .put("imagePosition","none")
                .put("copies",1);
        RemotePrinter.print(this, job);
    }

    private void writeBitmap(OutputStream out, Bitmap original) throws Exception {
        if (original == null) return;
        int sourceW = Math.max(1, original.getWidth());
        int sourceH = Math.max(1, original.getHeight());
        int targetW = Math.min(MAX_IMAGE_DOTS, sourceW);
        targetW -= targetW % 8; if (targetW < 8) targetW = 8;
        int targetH = Math.max(1, Math.round(sourceH * (targetW / (float) sourceW)));
        Bitmap bitmap = sourceW == targetW ? original : Bitmap.createScaledBitmap(original,targetW,targetH,true);
        int widthBytes = (targetW + 7) / 8;
        byte[] data = new byte[widthBytes * targetH];
        for (int y=0; y<targetH; y++) for (int x=0; x<targetW; x++) {
            int pixel = bitmap.getPixel(x,y); int alpha = Color.alpha(pixel);
            int gray = (Color.red(pixel)*30 + Color.green(pixel)*59 + Color.blue(pixel)*11) / 100;
            if (alpha > 80 && gray < 170) data[y*widthBytes + (x/8)] |= (byte)(0x80 >> (x%8));
        }
        int xL = widthBytes & 0xFF, xH = (widthBytes >> 8) & 0xFF, yL = targetH & 0xFF, yH = (targetH >> 8) & 0xFF;
        out.write(new byte[]{0x1D,0x76,0x30,0x00,(byte)xL,(byte)xH,(byte)yL,(byte)yH}); out.write(data);
        if (bitmap != original) bitmap.recycle();
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
    private void escInit(OutputStream out) throws Exception { out.write(new byte[]{0x1B,0x40}); }
    private void align(OutputStream out,int mode) throws Exception { out.write(new byte[]{0x1B,0x61,(byte)mode}); }
    private void bold(OutputStream out,boolean on) throws Exception { out.write(new byte[]{0x1B,0x45,(byte)(on?1:0)}); }
    private void doubleSize(OutputStream out,boolean on) throws Exception { out.write(new byte[]{0x1D,0x21,(byte)(on?0x11:0x00)}); }
    private void writeText(OutputStream out,String text) throws Exception { out.write(text.getBytes(Charset.forName("CP850"))); }
    private void cut(OutputStream out) throws Exception { out.write(new byte[]{0x1D,0x56,0x00}); }
    private void writeQr(OutputStream out,String data,int moduleSize) throws Exception {
        byte[] bytes = safe(data).getBytes(Charset.forName("UTF-8"));
        out.write(new byte[]{0x1D,0x28,0x6B,0x04,0x00,0x31,0x41,0x32,0x00});
        out.write(new byte[]{0x1D,0x28,0x6B,0x03,0x00,0x31,0x43,(byte)Math.max(2,Math.min(12,moduleSize))});
        out.write(new byte[]{0x1D,0x28,0x6B,0x03,0x00,0x31,0x45,0x31});
        int length = bytes.length + 3;
        ByteArrayOutputStream store = new ByteArrayOutputStream();
        store.write(new byte[]{0x1D,0x28,0x6B,(byte)(length&0xFF),(byte)((length>>8)&0xFF),0x31,0x50,0x30});
        store.write(bytes); out.write(store.toByteArray());
        out.write(new byte[]{0x1D,0x28,0x6B,0x03,0x00,0x31,0x51,0x30});
    }
    private String safe(String s) { return s == null ? "" : s.trim(); }
}
