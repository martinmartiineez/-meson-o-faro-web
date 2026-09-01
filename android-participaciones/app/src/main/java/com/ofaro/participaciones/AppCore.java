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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;

final class AppCore {
    static final String PREFS = "ofaro_participaciones";
    static final String DEFAULT_API = "https://script.google.com/macros/s/AKfycbwyICNMM0CHeSFQqOaO4d6g_d84vougY6OivfrMi6G5DIIVy7Y1qK_v2tBsZKmnQ2njkQ/exec";
    static final String APP_VERSION = "2.2.0";
    private static final int MAX_IMAGE_DOTS = 512;

    private final SharedPreferences prefs;
    private final Context context;

    AppCore(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    SharedPreferences prefs() { return prefs; }
    String api() { return prefs.getString("api", DEFAULT_API).trim(); }
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

    JSONObject post(JSONObject body) throws Exception { return postTo(api(), body); }

    JSONObject postTo(String endpoint, JSONObject body) throws Exception {
        if (endpoint == null || endpoint.trim().isEmpty()) throw new Exception("Endpoint vacío.");
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(16000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        byte[] payload = body.toString().getBytes(Charset.forName("UTF-8"));
        conn.setFixedLengthStreamingMode(payload.length);
        try (OutputStream os = conn.getOutputStream()) { os.write(payload); }
        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(stream);
        conn.disconnect();
        if (text == null || text.trim().isEmpty()) throw new Exception("Respuesta vacía del servidor (HTTP " + status + ").");
        try { return new JSONObject(text); }
        catch (Exception e) { throw new Exception("Respuesta no válida: " + text.substring(0, Math.min(text.length(), 180))); }
    }

    void ensureOk(JSONObject json) throws Exception {
        if (!json.optBoolean("ok", false)) throw new Exception(json.optString("error", "Operación rechazada"));
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

    void printTicket(String title, String subtitle, String body, String qrData, String imageUri, int imagePosition) throws Exception {
        Bitmap bitmap = null;
        if (imagePosition != 0 && imageUri != null && !imageUri.trim().isEmpty()) bitmap = loadBitmap(imageUri);
        Bitmap finalBitmap = bitmap;
        withPrinter(out -> {
            escInit(out);
            if (finalBitmap != null && imagePosition == 1) {
                align(out, 1);
                writeBitmap(out, finalBitmap);
                writeText(out, "\n");
            }
            if (!safe(title).isEmpty()) {
                align(out,1); bold(out,true); doubleSize(out,true);
                writeText(out, safe(title).toUpperCase() + "\n");
                doubleSize(out,false); bold(out,false);
            }
            if (!safe(subtitle).isEmpty()) {
                align(out,1); bold(out,true);
                writeText(out, safe(subtitle).toUpperCase() + "\n");
                bold(out,false);
            }
            if (!safe(title).isEmpty() || !safe(subtitle).isEmpty()) {
                align(out,1); writeText(out,"------------------------------\n");
            }
            if (!safe(body).isEmpty()) {
                align(out,0); writeText(out,"\n" + body.trim() + "\n");
            }
            if (!safe(qrData).isEmpty()) {
                align(out,1); writeText(out,"\n"); writeQr(out,qrData,7); writeText(out,"\n");
            }
            if (finalBitmap != null && imagePosition == 2) {
                align(out,1); writeText(out,"\n"); writeBitmap(out,finalBitmap); writeText(out,"\n");
            }
            writeText(out,"\n\n"); cut(out); out.flush();
        });
    }

    void printQrTicket(String title, String text, String qrData) throws Exception {
        printTicket(title,"",text,qrData,"",0);
    }

    void printFreeText(String title, String text, String qrData) throws Exception {
        printTicket(title,"",text,qrData,"",0);
    }

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
        withPrinter(ip,port,out -> {
            escInit(out); align(out,1); bold(out,true); writeText(out,"MESON O FARO\n"); bold(out,false);
            writeText(out,"Prueba impresora ESC/POS\n"+ip+":"+port+"\n\n"); writeQr(out,"OFARO:PRUEBA",6);
            writeText(out,"\nConexion correcta\n\n\n"); cut(out); out.flush();
        });
    }

    private interface PrinterJob { void run(OutputStream out) throws Exception; }
    private void withPrinter(PrinterJob job) throws Exception { withPrinter(printerIp(),printerPort(),job); }
    private void withPrinter(String ip, int port, PrinterJob job) throws Exception {
        if (ip == null || ip.trim().isEmpty()) throw new Exception("IP de impresora no configurada.");
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip.trim(),port),4000); socket.setSoTimeout(7000);
            try (OutputStream out = socket.getOutputStream()) { job.run(out); }
        }
    }

    private void writeBitmap(OutputStream out, Bitmap original) throws Exception {
        if (original == null) return;
        int sourceW = Math.max(1, original.getWidth());
        int sourceH = Math.max(1, original.getHeight());
        int targetW = Math.min(MAX_IMAGE_DOTS, sourceW);
        targetW -= targetW % 8;
        if (targetW < 8) targetW = 8;
        int targetH = Math.max(1, Math.round(sourceH * (targetW / (float) sourceW)));
        Bitmap bitmap = sourceW == targetW ? original : Bitmap.createScaledBitmap(original,targetW,targetH,true);
        int widthBytes = (targetW + 7) / 8;
        byte[] data = new byte[widthBytes * targetH];
        for (int y=0; y<targetH; y++) {
            for (int x=0; x<targetW; x++) {
                int pixel = bitmap.getPixel(x,y);
                int alpha = Color.alpha(pixel);
                int gray = (Color.red(pixel)*30 + Color.green(pixel)*59 + Color.blue(pixel)*11) / 100;
                boolean black = alpha > 80 && gray < 170;
                if (black) data[y*widthBytes + (x/8)] |= (byte)(0x80 >> (x%8));
            }
        }
        int xL = widthBytes & 0xFF, xH = (widthBytes >> 8) & 0xFF;
        int yL = targetH & 0xFF, yH = (targetH >> 8) & 0xFF;
        out.write(new byte[]{0x1D,0x76,0x30,0x00,(byte)xL,(byte)xH,(byte)yL,(byte)yH});
        out.write(data);
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
