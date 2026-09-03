package com.ofaro.participaciones;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

/**
 * Watchdog LOCAL de la impresora. No consulta colas remotas.
 * Mantiene una conexión TCP preparada y renueva periódicamente el socket para
 * evitar conexiones medio abiertas que Android todavía reporta como conectadas.
 */
public class PrintReceiverService extends Service {
    private static final String CHANNEL = "ofaro_printer_connection";
    private static final int NOTIFICATION_ID = 3010;
    private static final long REFRESH_CONNECTION_MS = 45_000L;
    private volatile boolean running;
    private Thread worker;
    private AppCore core;

    @Override public void onCreate() {
        super.onCreate();
        core = new AppCore(this);
        createChannel();
        startForeground(NOTIFICATION_ID, notification("Preparando impresora…"));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(this::loop,"OFaroPrinterWatchdog");
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
                if (ip.isEmpty()) {
                    manager.close();
                    String text = "Configura la IP de la impresora en Ajustes";
                    if (!text.equals(previous)) { updateNotification(text); previous = text; }
                    sleep(8000); continue;
                }

                boolean ok = manager.ensureConnected(ip,port);
                // Un socket TCP puede seguir devolviendo isConnected()==true aunque
                // la térmica o el router hayan cerrado el otro extremo. Se renueva
                // mientras está ocioso, siempre usando el mismo bloqueo que imprime.
                if (ok && manager.needsRefresh(REFRESH_CONNECTION_MS)) {
                    manager.reconnect(ip,port);
                    ok = manager.isConnected();
                }

                String text = ok ? "Impresora conectada · " + ip + ":" + port : "Reconectando · " + ip + ":" + port;
                if (!text.equals(previous)) { updateNotification(text); previous = text; }
                sleep(ok ? 10000 : 3500);
            } catch (Exception e) {
                updateNotification("Reconectando con la impresora…");
                previous = "";
                sleep(3500);
            }
        }
    }

    private void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){
            NotificationChannel c=new NotificationChannel(CHANNEL,"Conexión impresora O Faro",NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Mantiene preparada la conexión local con la impresora térmica");
            c.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(c);
        }
    }
    private Notification notification(String text){
        Intent open=new Intent(this,NativeMainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setContentTitle("O Faro · Impresora")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload_done)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pi).build();
    }
    private void updateNotification(String text){getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(text));}

    @Override public void onDestroy(){running=false;if(worker!=null)worker.interrupt();PrinterConnectionManager.get().close();super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
