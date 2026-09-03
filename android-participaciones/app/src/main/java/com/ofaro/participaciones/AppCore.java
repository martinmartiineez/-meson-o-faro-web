package com.ofaro.participaciones;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Núcleo único de la APK. Red, caché, configuración e impresión local. */
final class AppCore {
    static final String PREFS = "ofaro_participaciones";
    static final String DEFAULT_API = ApiEndpoint.URL;
    static final String APP_VERSION = BuildConfig.VERSION_NAME;

    private static final Set<String> READ_ACTIONS = new HashSet<>(Arrays.asList(
            "appPing","participationPing","appBootstrap","reservationList",
            "qrList","templateList","historyList","webSections","webSectionRows",
            "promotionPing","promotionList","promotionGet","promotionHistory",
            "promotionStats","promotionPrizeList","promotionValidate","promoPublicGet"
    ));

    private static final Set<String> INTERACTIVE_ACTIONS = new HashSet<>(Arrays.asList(
            "promoPublicIssue","promoPublicPlay","promotionPlay","promotionRedeem",
            "reservationCreate","reservationUpdate","reservationFullUpdate","reservationMarkPrinted",
            "historyAdd","promotionSave","promotionSetState","promotionDelete",
            "promotionPrizeSave","promotionReplaceSegments","promotionReplacePrizes",
            "webSectionSave","webSectionDelete"
    ));

    private final SharedPreferences prefs;
    private final Context context;

    AppCore(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        migrateDefaults();
    }

    private void migrateDefaults() {
        String stored = prefs.getString("api", "").trim();
        if (stored.isEmpty() || stored.contains("AKfycbwyICNMM0CHeSFQqOaO4d6g_d84vougY6OivfrMi6G5DIIVy7Y1qK_v2tBsZKmnQ2njkQ")) {
            prefs.edit().putString("api", DEFAULT_API).apply();
        }
        if (!prefs.contains("printerPort")) prefs.edit().putInt("printerPort", 9100).apply();
        if (!prefs.contains("printerPaper")) prefs.edit().putInt("printerPaper", 80).apply();
        if (!prefs.contains("printerCut")) prefs.edit().putString("printerCut", "full").apply();
        if (!prefs.contains("printerFeed")) prefs.edit().putInt("printerFeed", 3).apply();
        if (!prefs.contains("printerDarkness")) prefs.edit().putInt("printerDarkness", 180).apply();
    }

    Context context() { return context; }
    SharedPreferences prefs() { return prefs; }
    String api() {
        String value = prefs.getString("api", DEFAULT_API).trim();
        return value.isEmpty() ? DEFAULT_API : value;
    }
    String key() { return prefs.getString("key", "").trim(); }
    String terminal() {
        String value = prefs.getString("terminal", "Caja O Faro").trim();
        return value.isEmpty() ? "Caja O Faro" : value;
    }
    String printerIp() { return prefs.getString("printerIp", "").trim(); }
    int printerPort() { return clamp(prefs.getInt("printerPort", 9100), 1, 65535); }
    int printerPaper() { return prefs.getInt("printerPaper",80) <= 58 ? 58 : 80; }
    String printerCut() { return prefs.getString("printerCut","full"); }
    int printerFeed() { return clamp(prefs.getInt("printerFeed",3),0,8); }
    int printerDarkness() { return clamp(prefs.getInt("printerDarkness",180),120,230); }
    boolean configured() { return !api().isEmpty() && !key().isEmpty(); }

    JSONObject action(String action) throws Exception {
        return new JSONObject()
                .put("action", action)
                .put("key", key())
                .put("terminal", terminal())
                .put("appVersion", APP_VERSION)
                .put("printerIp", printerIp());
    }

    /**
     * Política única de red:
     * - lecturas: rápida, un único reintento si falla transporte;
     * - acciones interactivas/escrituras: nunca se reintentan automáticamente;
     * - ninguna petición puede dejar la interfaz esperando 25-50 s.
     */
    JSONObject post(JSONObject body) throws Exception {
        String action = body == null ? "" : body.optString("action", "");
        boolean read = READ_ACTIONS.contains(action);
        boolean interactive = INTERACTIVE_ACTIONS.contains(action);
        int connectMs = read ? 3500 : 4000;
        int readMs = interactive ? 12000 : (read ? 7500 : 12000);
        int attempts = read ? 2 : 1;
        Exception last = null;
        for (int i=0;i<attempts;i++) {
            try {
                JSONObject result = postTo(api(),body,connectMs,readMs);
                ensureOk(result);
                return result;
            } catch (Exception e) {
                last = e;
                if (i+1<attempts) {
                    try { Thread.sleep(250L); }
                    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
                }
            }
        }
        throw last == null ? new Exception("No se pudo conectar con el servidor") : last;
    }

    /** Compatibilidad para pruebas de endpoint desde Ajustes. */
    JSONObject postTo(String endpoint, JSONObject body) throws Exception {
        return postTo(endpoint,body,4000,9000);
    }

    private JSONObject postTo(String endpoint, JSONObject body, int connectMs, int readMs) throws Exception {
        if (endpoint == null || endpoint.trim().isEmpty()) throw new Exception("Endpoint vacío.");
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(endpoint.trim()).openConnection();
            conn.setConnectTimeout(connectMs);
            conn.setReadTimeout(readMs);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
            conn.setRequestProperty("User-Agent", "OFaroAndroid/" + APP_VERSION);
            byte[] payload = (body == null ? "{}" : body.toString()).getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream os = conn.getOutputStream()) { os.write(payload); os.flush(); }
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
            String text = readAll(stream).trim();
            if (text.isEmpty()) throw new Exception("El servidor no respondió (HTTP " + status + ").");
            if (text.startsWith("<")) throw new Exception("Apps Script devolvió HTML en lugar de datos.");
            try { return new JSONObject(text); }
            catch (Exception parse) { throw new Exception("Respuesta del servidor no válida."); }
        } catch (SocketTimeoutException timeout) {
            throw new Exception("El servidor está tardando demasiado. Vuelve a intentarlo.");
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    void ensureOk(JSONObject json) throws Exception {
        if (json == null || !json.optBoolean("ok",false)) {
            throw new Exception(json == null ? "Respuesta vacía" : json.optString("error","Operación rechazada"));
        }
    }

    boolean internetAvailable() {
        try {
            ConnectivityManager cm=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if(cm==null)return false;
            Network network=cm.getActiveNetwork();
            if(network==null)return false;
            NetworkCapabilities c=cm.getNetworkCapabilities(network);
            return c!=null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch(Exception e){ return true; }
    }

    void saveArray(String name, JSONArray value) {
        prefs.edit().putString("cache_"+name,value==null?"[]":value.toString())
                .putLong("cache_"+name+"_at",System.currentTimeMillis()).apply();
    }
    JSONArray cachedArray(String name) {
        try { return new JSONArray(prefs.getString("cache_"+name,"[]")); }
        catch(Exception e){ return new JSONArray(); }
    }
    long cacheAgeMs(String name) {
        long at=prefs.getLong("cache_"+name+"_at",0L);
        return at<=0?Long.MAX_VALUE:Math.max(0L,System.currentTimeMillis()-at);
    }

    Bitmap loadBitmap(String uriText) throws Exception {
        if (uriText == null || uriText.trim().isEmpty()) return null;
        Uri uri=Uri.parse(uriText.trim());
        try(InputStream in=context.getContentResolver().openInputStream(uri)) {
            if(in==null)throw new Exception("No se pudo abrir la imagen seleccionada.");
            Bitmap bitmap=BitmapFactory.decodeStream(in);
            if(bitmap==null)throw new Exception("El archivo seleccionado no es una imagen compatible.");
            return bitmap;
        }
    }

    void startPrinterWatchdog() {
        if(printerIp().isEmpty())return;
        try {
            Intent service=new Intent(context,PrintReceiverService.class);
            if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);else context.startService(service);
        } catch(Exception ignored){}
    }
    String printerStatus(){return PrinterConnectionManager.get().statusText();}
    boolean printerConnected(){return PrinterConnectionManager.get().isConnected();}
    void reconnectPrinter(){PrinterConnectionManager.get().reconnect(printerIp(),printerPort());}

    private JSONObject basePrintJob() throws Exception {
        return new JSONObject()
                .put("paperWidth",printerPaper())
                .put("cutMode",printerCut())
                .put("feedLines",printerFeed())
                .put("darkness",printerDarkness())
                .put("typography","O Faro")
                .put("separator","line")
                .put("copies",1);
    }

    void printTicket(String title,String subtitle,String body,String qrData,String imageUri,int imagePosition) throws Exception {
        JSONObject job=basePrintJob()
                .put("templateId","Minimal Premium")
                .put("title",safe(title)).put("subtitle",safe(subtitle)).put("text",safe(body))
                .put("qr",safe(qrData)).put("qrSize","L")
                .put("imagePosition",imagePosition==1?"top":imagePosition==2?"bottom":"none");
        if(imagePosition!=0 && imageUri!=null && !imageUri.trim().isEmpty()) {
            job.put("imageData",ImageUtil.toDataUri(context,imageUri));
        }
        RemotePrinter.print(this,job);
    }
    void printQrTicket(String title,String text,String qrData)throws Exception{printTicket(title,"",text,qrData,"",0);}
    void printFreeText(String title,String text,String qrData)throws Exception{printTicket(title,"",text,qrData,"",0);}

    void printReservation(JSONObject r) throws Exception {
        StringBuilder body=new StringBuilder();
        body.append(r.optString("date","")).append("   ").append(r.optString("time","")).append("\n\n");
        body.append(r.optString("name","").toUpperCase()).append("\n");
        body.append(r.optInt("people",0)).append(" PERSONAS\n");
        String table=r.optString("table","");String zone=r.optString("zone","");
        if(!table.isEmpty())body.append("Mesa: ").append(table).append("\n");
        if(!zone.isEmpty()&&!"Sin asignar".equalsIgnoreCase(zone))body.append("Zona: ").append(zone).append("\n");
        String phone=r.optString("phone","");if(!phone.isEmpty())body.append("Tel: ").append(phone).append("\n");
        String notes=r.optString("notes","");if(!notes.isEmpty())body.append("\nOBSERVACIONES\n").append(notes).append("\n");
        body.append("\n").append(r.optString("id",""));
        JSONObject job=basePrintJob().put("templateId","Reserva Express")
                .put("title","MESÓN O FARO").put("subtitle","RESERVA")
                .put("text",body.toString()).put("qr","").put("imagePosition","none");
        RemotePrinter.print(this,job);
    }

    void printTest(String ip,int port)throws Exception{
        JSONObject job=basePrintJob().put("templateId","Minimal Premium")
                .put("title","MESÓN O FARO").put("subtitle","PRUEBA ESC/POS")
                .put("text","Conexión directa correcta\n"+ip+":"+port)
                .put("qr","OFARO:PRUEBA").put("qrSize","M").put("imagePosition","none");
        RemotePrinter.print(this,job);
    }

    private String readAll(InputStream stream)throws Exception{
        if(stream==null)return"";
        StringBuilder sb=new StringBuilder();
        try(BufferedReader br=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8))){
            String line;while((line=br.readLine())!=null)sb.append(line);
        }
        return sb.toString();
    }
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static String safe(String s){return s==null?"":s.trim();}
}
