package com.ofaro.participaciones;

import android.content.Context;
import android.content.SharedPreferences;

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
    static final String APP_VERSION = "2.0.0";

    private final SharedPreferences prefs;

    AppCore(Context context) {
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

    void printQrTicket(String title, String text, String qrData) throws Exception {
        withPrinter(out -> {
            escInit(out); align(out,1); bold(out,true); doubleSize(out,true);
            writeText(out, safe(title).isEmpty() ? "MESON O FARO\n" : safe(title).toUpperCase() + "\n");
            doubleSize(out,false); bold(out,false);
            if (!safe(text).isEmpty()) writeText(out, "\n" + safe(text) + "\n");
            writeText(out, "------------------------------\n\n");
            writeQr(out, qrData, 7);
            writeText(out, "\n\nMESON O FARO\n\n\n"); cut(out); out.flush();
        });
    }

    void printFreeText(String title, String text, String qrData) throws Exception {
        withPrinter(out -> {
            escInit(out); align(out,1);
            if (!safe(title).isEmpty()) { bold(out,true); doubleSize(out,true); writeText(out, safe(title).toUpperCase()+"\n"); doubleSize(out,false); bold(out,false); writeText(out,"\n"); }
            align(out,0); writeText(out, safe(text) + "\n");
            if (!safe(qrData).isEmpty()) { align(out,1); writeText(out,"\n"); writeQr(out,qrData,6); writeText(out,"\n"); }
            writeText(out,"\n\n"); cut(out); out.flush();
        });
    }

    void printReservation(JSONObject r) throws Exception {
        withPrinter(out -> {
            escInit(out); align(out,1); bold(out,true); doubleSize(out,true); writeText(out,"MESON O FARO\n");
            doubleSize(out,false); writeText(out,"RESERVA\n"); bold(out,false); writeText(out,"------------------------------\n");
            bold(out,true); writeText(out, r.optString("date", "") + "  " + r.optString("time", "") + "\n\n");
            doubleSize(out,true); writeText(out, r.optString("name", "").toUpperCase() + "\n"); doubleSize(out,false);
            writeText(out, r.optInt("people",0) + " PERSONAS\n"); bold(out,false);
            String table = r.optString("table",""); String zone = r.optString("zone","");
            if (!table.isEmpty()) writeText(out,"Mesa: " + table + "\n");
            if (!zone.isEmpty() && !"Sin asignar".equalsIgnoreCase(zone)) writeText(out,"Zona: " + zone + "\n");
            String phone = r.optString("phone",""); if (!phone.isEmpty()) writeText(out,"Tel: " + phone + "\n");
            String notes = r.optString("notes",""); if (!notes.isEmpty()) { writeText(out,"\nOBSERVACIONES\n"); bold(out,true); writeText(out,notes+"\n"); bold(out,false); }
            writeText(out,"\n" + r.optString("id","") + "\n\n\n"); cut(out); out.flush();
        });
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
            socket.connect(new InetSocketAddress(ip.trim(),port),4000); socket.setSoTimeout(5000);
            try (OutputStream out = socket.getOutputStream()) { job.run(out); }
        }
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
    private void doubleSize(OutputStream out,boolean on) throws Exception { out.write(new byte[]{0x1D,0x21,(byte)(on?0x11:0)}); }
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
