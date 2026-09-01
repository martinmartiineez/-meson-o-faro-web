package com.ofaro.participaciones;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import org.json.JSONObject;

public class PrintReceiverService extends Service {
    private static final String CHANNEL = "ofaro_print_receiver";
    private static final int NOTIFICATION_ID = 3010;
    private volatile boolean running;
    private Thread worker;
    private AppCore core;

    @Override
    public void onCreate() {
        super.onCreate();
        core = new AppCore(this);
        createChannel();
        startForeground(NOTIFICATION_ID,notification("Esperando trabajos de impresión"));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(this::loop,"OFaroPrintReceiver");
            worker.start();
        }
        return START_STICKY;
    }

    private void loop() {
        long heartbeatAt = 0;
        while (running) {
            try {
                if (!core.prefs().getBoolean("printReceiverEnabled",false)) { stopSelf(); break; }
                if (!core.configured() || core.printerIp().isEmpty()) { sleep(7000); continue; }
                long now = System.currentTimeMillis();
                if (now - heartbeatAt > 30000) {
                    JSONObject hb=core.action("printQueueHeartbeat").put("appVersion","3.0.0").put("printerIp",core.printerIp());
                    try{core.ensureOk(core.post(hb));}catch(Exception ignored){}
                    heartbeatAt=now;
                }
                JSONObject req=core.action("printQueueNext").put("appVersion","3.0.0").put("printerIp",core.printerIp());
                JSONObject res=core.post(req);core.ensureOk(res);
                JSONObject job=res.optJSONObject("job");
                if(job==null){sleep(3500);continue;}
                String id=job.optString("id","");
                updateNotification("Imprimiendo " + id);
                boolean ok=true;String error="";
                try{RemotePrinter.print(core,job);}catch(Exception e){ok=false;error=e.getMessage()==null?e.toString():e.getMessage();}
                JSONObject done=core.action("printQueueComplete").put("id",id).put("success",ok).put("error",error).put("appVersion","3.0.0");
                try{core.post(done);}catch(Exception ignored){}
                updateNotification(ok?"Impreso · "+id:"Error · "+id);
                sleep(1200);
            } catch (Exception e) {
                updateNotification("Receptor activo · sin conexión");
                sleep(6000);
            }
        }
    }

    private void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}

    private void createChannel(){
        if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel(CHANNEL,"Impresión remota O Faro",NotificationManager.IMPORTANCE_LOW);c.setDescription("Mantiene activo el receptor de tickets de O Faro");getSystemService(NotificationManager.class).createNotificationChannel(c);}
    }
    private Notification notification(String text){
        Intent open=new Intent(this,WebAppActivity.class);PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setContentTitle("O Faro · Impresión remota").setContentText(text).setSmallIcon(android.R.drawable.stat_sys_upload_done).setOngoing(true).setContentIntent(pi).build();
    }
    private void updateNotification(String text){getSystemService(NotificationManager.class).notify(NOTIFICATION_ID,notification(text));}

    @Override
    public void onDestroy(){running=false;if(worker!=null)worker.interrupt();super.onDestroy();}
    @Override
    public IBinder onBind(Intent intent){return null;}
}
