package com.ofaro.participaciones;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Watchdog local de la impresora y monitor ligero de alertas internas.
 * Mantiene una conexión TCP preparada y sincroniza reservas/premios para
 * generar avisos aunque la pantalla principal no esté abierta.
 */
public class PrintReceiverService extends Service {
    private static final String CHANNEL = "ofaro_printer_connection";
    private static final int NOTIFICATION_ID = 3010;
    private static final long REFRESH_CONNECTION_MS = 45_000L;
    private static final long ALERT_SYNC_MS = 60_000L;

    private volatile boolean running;
    private Thread worker;
    private AppCore core;
    private InternalAlertCenter alerts;
    private long lastAlertSync;
    private boolean alertDataReady;

    @Override public void onCreate() {
        super.onCreate();
        core = new AppCore(this);
        alerts = new InternalAlertCenter(this);
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Preparando impresora…"));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(this::loop,"OFaroPrinterAlertWatchdog");
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        String previous = "";
        while (running) {
            try {
                String ip = core.printerIp();
                int port = core.printerPort();
                PrinterConnectionManager manager = PrinterConnectionManager.get();
                boolean configured = !ip.isEmpty();
                boolean connected = false;

                if (!configured) {
                    manager.close();
                } else {
                    connected = manager.ensureConnected(ip,port);
                    if (connected && manager.needsRefresh(REFRESH_CONNECTION_MS)) {
                        manager.reconnect(ip,port);
                        connected = manager.isConnected();
                    }
                }

                long now = System.currentTimeMillis();
                if (lastAlertSync == 0L || now-lastAlertSync >= ALERT_SYNC_MS) {
                    alertDataReady = syncAlertData() || alertDataReady;
                    lastAlertSync = now;
                }

                JSONArray cachedReservations = core.cachedArray("reservations_all_v5");
                JSONArray cachedPrizes = core.cachedArray("promo_prizes");
                if (alertDataReady || cachedReservations.length()>0 || cachedPrizes.length()>0) {
                    alerts.scan(cachedReservations,cachedPrizes,configured,connected,core.terminal());
                }

                String text;
                if (!configured) text = "Configura la IP de la impresora en Ajustes";
                else text = connected ? "Impresora conectada · "+ip+":"+port : "Reconectando · "+ip+":"+port;
                int unread = alerts.unreadCount();
                if (unread>0) text += " · "+unread+(unread==1?" alerta":" alertas");
                if (!text.equals(previous)) { updateNotification(text); previous = text; }

                sleep(!configured ? 8000 : connected ? 10000 : 3500);
            } catch (Exception e) {
                updateNotification("Servicio activo · revisando conexión y alertas");
                previous = "";
                sleep(3500);
            }
        }
    }

    /** Devuelve true cuando la lista de reservas pudo sincronizarse, incluso si está vacía. */
    private boolean syncAlertData(){
        boolean reservationsSynced=false;
        if(!core.configured() || !core.internetAvailable()) return false;
        try{
            JSONObject r=core.post(core.action("reservationList"));
            JSONArray a=array(r,"items","reservations");
            core.saveArray("reservations_all_v5",a);
            reservationsSynced=true;
        }catch(Exception ignored){}
        try{
            JSONObject r=core.post(core.action("promotionPrizeList"));
            JSONArray a=array(r,"items","prizes");
            core.saveArray("promo_prizes",a);
        }catch(Exception ignored){}
        return reservationsSynced;
    }

    private JSONArray array(JSONObject o,String...keys){
        for(String key:keys){JSONArray a=o.optJSONArray(key);if(a!=null)return a;}
        return new JSONArray();
    }

    private void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"Servicio O Faro",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Mantiene preparada la impresora y el monitor local de alertas");
            c.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }

    private Notification notification(String text){
        Intent open=new Intent(this,HomeActivityFinal.class);
        PendingIntent home=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        Intent alertIntent=new Intent(this,InternalAlertsActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent alertPi=PendingIntent.getActivity(this,1,alertIntent,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        b.setContentTitle("O Faro · Servicio activo")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(home)
                .addAction(new Notification.Action.Builder(null,"Alertas",alertPi).build());
        return b.build();
    }

    private void updateNotification(String text){
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(text));
    }

    @Override public void onDestroy(){
        running=false;
        if(worker!=null)worker.interrupt();
        PrinterConnectionManager.get().close();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent){return null;}
}
