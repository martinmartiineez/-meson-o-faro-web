package com.ofaro.participaciones;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Arranca el watchdog tras reinicio o actualización de la propia APK. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AppCore core=new AppCore(context);
        if(core.printerIp().isEmpty())return;
        core.startPrinterWatchdog();
    }
}
