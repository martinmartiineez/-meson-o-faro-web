package com.ofaro.participaciones;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Cliente corto para acciones interactivas. No reintenta silenciosamente:
 * si Apps Script no responde pronto, la UI recupera el control y muestra error.
 */
final class QuickApi {
    private QuickApi() {}

    static JSONObject post(AppCore core, JSONObject body) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(core.api()).openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
            byte[] payload = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
                out.flush();
            }
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
            String text = read(stream).trim();
            if (text.isEmpty()) throw new Exception("El servidor no respondió.");
            if (text.startsWith("<")) throw new Exception("Apps Script devolvió una página HTML en lugar de datos.");
            JSONObject json;
            try { json = new JSONObject(text); }
            catch (Exception e) { throw new Exception("Respuesta del servidor no válida."); }
            core.ensureOk(json);
            return json;
        } catch (java.net.SocketTimeoutException e) {
            throw new Exception("El servidor está tardando demasiado. Inténtalo de nuevo.");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String read(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }
}
