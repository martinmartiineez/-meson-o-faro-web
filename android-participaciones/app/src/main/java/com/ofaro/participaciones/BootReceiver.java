package com.ofaro.participaciones;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppCore core=new AppCore(context);
        if(!core.prefs().getBoolean("printReceiverEnabled",false))return;
        Intent service=new Intent(context,PrintReceiverService.class);
        if(Build.VERSION.SDK_INT>=26)context.startForegroundService(service);else context.startService(service);
    }
}
