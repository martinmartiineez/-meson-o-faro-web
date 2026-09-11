package com.ofaro.participaciones;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Centro local de alertas de O Faro.
 * Mantiene historial, evita duplicados y genera notificaciones Android cuando procede.
 */
final class InternalAlertCenter {
    static final String PREFS = "ofaro_internal_alerts_v1";
    private static final String KEY_ALERTS = "alerts_json";
    private static final String KEY_RES_BASELINE = "reservation_baseline";
    private static final String KEY_PRINTER_SEEN = "printer_seen_connected";
    private static final String KEY_PRINTER_OUTAGE = "printer_outage_active";
    private static final String CHANNEL = "ofaro_internal_alerts";
    private static final int MAX_ALERTS = 120;

    private final Context app;
    private final SharedPreferences prefs;

    InternalAlertCenter(Context context){
        app=context.getApplicationContext();
        prefs=app.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        ensureDefaults();
        createChannel();
    }

    SharedPreferences prefs(){return prefs;}

    JSONArray alerts(){
        try{return new JSONArray(prefs.getString(KEY_ALERTS,"[]"));}
        catch(Exception e){return new JSONArray();}
    }

    int unreadCount(){
        int n=0;JSONArray a=alerts();
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&!o.optBoolean("read",false))n++;}
        return n;
    }

    void markAllRead(){
        JSONArray a=alerts();
        for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null){try{o.put("read",true);}catch(Exception ignored){}}}
        save(a);
    }

    void clear(){prefs.edit().putString(KEY_ALERTS,"[]").apply();}

    void scan(JSONArray reservations,JSONArray prizes,boolean printerConfigured,boolean printerConnected,String terminal){
        if(reservations==null)reservations=new JSONArray();if(prizes==null)prizes=new JSONArray();
        scanNewReservations(reservations,terminal==null?"":terminal.trim());
        scanUpcomingReservations(reservations);
        scanPrizeStock(prizes);
        scanPrinter(printerConfigured,printerConnected);
    }

    private void scanNewReservations(JSONArray reservations,String terminal){
        Set<String> current=new HashSet<>();
        for(int i=0;i<reservations.length();i++){JSONObject r=reservations.optJSONObject(i);if(r!=null)current.add(reservationKey(r));}
        Set<String> previous=prefs.getStringSet(KEY_RES_BASELINE,null);
        if(previous==null){prefs.edit().putStringSet(KEY_RES_BASELINE,new HashSet<>(current)).apply();return;}

        if(prefs.getBoolean("alert_new_reservations",true)){
            for(int i=0;i<reservations.length();i++){
                JSONObject r=reservations.optJSONObject(i);if(r==null)continue;String key=reservationKey(r);if(previous.contains(key))continue;
                String origin=r.optString("origin",r.optString("origen",""));String sourceTerminal=r.optString("terminal","");
                if("APK".equalsIgnoreCase(origin)&&!terminal.isEmpty()&&terminal.equalsIgnoreCase(sourceTerminal))continue;
                if(isClosedReservation(r))continue;
                String state=stateOf(r);String title="Pendiente".equalsIgnoreCase(state)?"Nueva reserva pendiente":"Nueva reserva";
                String message=nameOf(r)+" · "+dateOf(r)+" · "+timeOf(r)+" · "+peopleOf(r)+" personas"+(zoneOf(r).isEmpty()?"":" · "+zoneOf(r));
                addOnce("reservation:"+key,"reservation",title,message,true);
            }
        }
        prefs.edit().putStringSet(KEY_RES_BASELINE,new HashSet<>(current)).apply();
    }

    private void scanUpcomingReservations(JSONArray reservations){
        if(!prefs.getBoolean("alert_upcoming_reservations",true))return;
        int lead=Math.max(10,Math.min(120,prefs.getInt("alert_lead_minutes",30)));
        LocalDate today=LocalDate.now();LocalDateTime now=LocalDateTime.now();
        for(int i=0;i<reservations.length();i++){
            JSONObject r=reservations.optJSONObject(i);if(r==null||isClosedReservation(r))continue;
            try{
                LocalDate d=LocalDate.parse(dateOf(r));if(!today.equals(d))continue;
                LocalTime t=LocalTime.parse(normalizeTime(timeOf(r)));LocalDateTime when=LocalDateTime.of(d,t);
                long mins=Duration.between(now,when).toMinutes();if(mins<0||mins>lead)continue;
                String key=reservationKey(r);String message=timeOf(r)+" · "+nameOf(r)+" · "+peopleOf(r)+" personas"+(tableOf(r).isEmpty()?"":" · Mesa "+tableOf(r));
                addOnce("upcoming:"+key+":"+d,"upcoming","Reserva próxima",message,true);
            }catch(Exception ignored){}
        }
    }

    private void scanPrizeStock(JSONArray prizes){
        if(!prefs.getBoolean("alert_low_stock",true))return;
        int threshold=Math.max(1,Math.min(20,prefs.getInt("alert_stock_threshold",3)));
        for(int i=0;i<prizes.length();i++){
            JSONObject p=prizes.optJSONObject(i);if(p==null||"P000".equals(p.optString("id",""))||!p.optBoolean("active",true))continue;
            Object raw=p.opt("stock");if(raw==null||raw==JSONObject.NULL)continue;String rawText=String.valueOf(raw).trim();if(rawText.isEmpty()||"null".equalsIgnoreCase(rawText))continue;
            int stock;try{stock=raw instanceof Number?((Number)raw).intValue():Integer.parseInt(rawText);}catch(Exception e){continue;}
            int remaining=p.has("remaining")?p.optInt("remaining",stock):stock-p.optInt("redeemed",0);
            if(remaining>threshold)continue;
            String id=p.optString("id",p.optString("name","premio"));String name=p.optString("name","Premio");
            String message=remaining<=0?name+" · sin unidades disponibles":name+" · quedan "+remaining+" unidades";
            addOnce("stock:"+id+":"+remaining,"stock",remaining<=0?"Premio agotado":"Stock bajo",message,remaining<=0);
        }
    }

    private void scanPrinter(boolean configured,boolean connected){
        boolean seen=prefs.getBoolean(KEY_PRINTER_SEEN,false);boolean outage=prefs.getBoolean(KEY_PRINTER_OUTAGE,false);
        if(connected){prefs.edit().putBoolean(KEY_PRINTER_SEEN,true).putBoolean(KEY_PRINTER_OUTAGE,false).apply();return;}
        if(!configured||!seen||outage||!prefs.getBoolean("alert_printer",true))return;
        add("printer:"+System.currentTimeMillis(),"printer","Impresora desconectada","No se puede comunicar con la impresora térmica. La app seguirá intentando reconectar.",true);
        prefs.edit().putBoolean(KEY_PRINTER_OUTAGE,true).apply();
    }

    private boolean isClosedReservation(JSONObject r){
        String state=stateOf(r),service=r.optString("serviceState",r.optString("estadoServicio",""));
        return state.equalsIgnoreCase("Cancelada")||state.equalsIgnoreCase("Denegada")||service.equalsIgnoreCase("Completada")||service.equalsIgnoreCase("No se presentó");
    }

    private void addOnce(String id,String type,String title,String message,boolean important){if(!contains(id))add(id,type,title,message,important);}

    private void add(String id,String type,String title,String message,boolean important){
        try{
            JSONArray old=alerts(),next=new JSONArray();
            JSONObject a=new JSONObject();a.put("id",id).put("type",type).put("title",title).put("message",message).put("ts",System.currentTimeMillis()).put("read",false);
            next.put(a);for(int i=0;i<old.length()&&next.length()<MAX_ALERTS;i++){JSONObject o=old.optJSONObject(i);if(o!=null)next.put(o);}save(next);notifyAlert(a,important);
        }catch(Exception ignored){}
    }

    private boolean contains(String id){JSONArray a=alerts();for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&id.equals(o.optString("id","")))return true;}return false;}
    private void save(JSONArray a){prefs.edit().putString(KEY_ALERTS,a.toString()).apply();}

    private void ensureDefaults(){
        if(prefs.contains("defaults_initialized"))return;
        prefs.edit().putBoolean("defaults_initialized",true)
                .putBoolean("notifications_enabled",true)
                .putBoolean("alert_new_reservations",true)
                .putBoolean("alert_upcoming_reservations",true)
                .putBoolean("alert_printer",true)
                .putBoolean("alert_low_stock",true)
                .putInt("alert_lead_minutes",30)
                .putInt("alert_stock_threshold",3).apply();
    }

    private void createChannel(){
        if(Build.VERSION.SDK_INT<26)return;
        NotificationChannel c=new NotificationChannel(CHANNEL,"Alertas internas O Faro",NotificationManager.IMPORTANCE_HIGH);
        c.setDescription("Reservas, impresora y stock de premios");c.setShowBadge(true);
        app.getSystemService(NotificationManager.class).createNotificationChannel(c);
    }

    private void notifyAlert(JSONObject a,boolean important){
        if(!prefs.getBoolean("notifications_enabled",true))return;
        if(Build.VERSION.SDK_INT>=33&&app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
        Intent open=new Intent(app,InternalAlertsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi=PendingIntent.getActivity(app,Math.abs(a.optString("id","").hashCode()),open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(app,CHANNEL):new Notification.Builder(app);
        b.setContentTitle(a.optString("title","O Faro"))
                .setContentText(a.optString("message",""))
                .setSmallIcon(important?android.R.drawable.stat_notify_error:android.R.drawable.stat_notify_more)
                .setAutoCancel(true).setContentIntent(pi).setWhen(a.optLong("ts",System.currentTimeMillis())).setShowWhen(true);
        if(Build.VERSION.SDK_INT<26)b.setPriority(important?Notification.PRIORITY_HIGH:Notification.PRIORITY_DEFAULT);
        app.getSystemService(NotificationManager.class).notify(Math.abs(a.optString("id",String.valueOf(System.nanoTime())).hashCode()),b.build());
    }

    private String reservationKey(JSONObject r){String id=r.optString("id","").trim();if(!id.isEmpty())return id;return dateOf(r)+"|"+timeOf(r)+"|"+nameOf(r).toLowerCase(Locale.ROOT)+"|"+phoneOf(r)+"|"+peopleOf(r);}
    private String nameOf(JSONObject r){return r.optString("name",r.optString("nombre","Reserva")).trim();}
    private String phoneOf(JSONObject r){return r.optString("phone",r.optString("telefono","")).trim();}
    private String dateOf(JSONObject r){return r.optString("date",r.optString("fecha","")).trim();}
    private String timeOf(JSONObject r){return r.optString("time",r.optString("hora","--:--")).trim();}
    private String tableOf(JSONObject r){return r.optString("table",r.optString("mesa","")).trim();}
    private String zoneOf(JSONObject r){return r.optString("zone",r.optString("zona","")).trim();}
    private String stateOf(JSONObject r){return r.optString("state",r.optString("estado","Pendiente")).trim();}
    private int peopleOf(JSONObject r){return r.optInt("people",r.optInt("personas",0));}
    private String normalizeTime(String t){String s=t==null?"":t.trim();return s.length()==5?s:s.length()==4?"0"+s:s;}
}
