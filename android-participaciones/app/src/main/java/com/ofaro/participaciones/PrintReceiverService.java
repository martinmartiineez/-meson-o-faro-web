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
 * Mantiene una conexión TCP preparada y reconecta si la impresora/router la cierra.
 */
public class PrintReceiverService extends Service {
    private static final String CHANNEL = "ofaro_printer_connection";
    private static final int NOTIFICATION_ID = 3010;
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
                if (ip.isEmpty()) {
                    PrinterConnectionManager.get().close();
                    String text = "Configura la IP de la impresora en Ajustes";
                    if (!text.equals(previous)) { updateNotification(text); previous = text; }
                    sleep(8000); continue;
                }
                boolean ok = PrinterConnectionManager.get().ensureConnected(ip,port);
                String text = ok ? "Impresora conectada · " + ip + ":" + port : "Reconectando · " + ip + ":" + port;
                if (!text.equals(previous)) { updateNotification(text); previous = text; }
                sleep(ok ? 12000 : 3500);
            } catch (Exception e) {
                updateNotification("Reconectando con la impresora…");
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
