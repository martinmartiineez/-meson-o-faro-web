package com.ofaro.participaciones;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivityV2 extends Activity {
    private AppCore core;private TextView printerState,serverState;private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();private long lastServerProbe=0L;
    private final Runnable tick=new Runnable(){@Override public void run(){refreshPrinter();ui.postDelayed(this,2500);}};

    @Override protected void onCreate(Bundle b){super.onCreate(b);core=new AppCore(this);setContentView(build());core.startPrinterWatchdog();requestNotificationOnce();ui.post(tick);probeServer(false);}
    @Override protected void onResume(){super.onResume();if(core!=null){core.startPrinterWatchdog();refreshPrinter();if(System.currentTimeMillis()-lastServerProbe>45_000L)probeServer(false);}}
    @Override protected void onDestroy(){ui.removeCallbacks(tick);io.shutdownNow();super.onDestroy();}

    private android.view.View build(){
        LinearLayout root=col();root.setBackgroundColor(Color.rgb(247,246,243));
        LinearLayout header=col();header.setPadding(dp(20),dp(20),dp(20),dp(18));header.setBackgroundColor(Color.rgb(14,14,14));
        header.addView(text("MESÓN O FARO",27,Color.WHITE,true));header.addView(text("Gestión · "+core.terminal(),13,Color.LTGRAY,false));root.addView(header);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=col();page.setPadding(dp(15),dp(14),dp(15),dp(32));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout status=col();status.setPadding(dp(14),dp(12),dp(14),dp(12));status.setBackground(box(Color.WHITE,13,Color.rgb(216,216,216),1));
        printerState=text("Impresora · comprobando…",14,Color.DKGRAY,true);serverState=text("Servidor · comprobando…",13,Color.GRAY,false);serverState.setPadding(0,dp(5),0,0);status.addView(printerState);status.addView(serverState);page.addView(status);

        page.addView(section("TRABAJO DIARIO"));
        Button reservations=action("RESERVAS","Hoy, nuevas reservas, mesas, servicio y tickets");reservations.setOnClickListener(v->startActivity(new Intent(this,ReservationsActivity.class)));page.addView(reservations);
        Button promos=action("PROMOCIONES","Sacar QR, crear campañas, premios y canjes");promos.setOnClickListener(v->startActivity(new Intent(this,PromotionsSimpleActivity.class)));page.addView(promos,top(dp(9)));
        Button print=action("IMPRIMIR","Ticket libre, QR, imagen y 28 plantillas térmicas");print.setOnClickListener(v->startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre")));page.addView(print,top(dp(9)));

        page.addView(section("GESTIÓN Y HERRAMIENTAS"));
        LinearLayout row=row();Button web=tile("WEB\nCarta · menú · avisos");web.setOnClickListener(v->startActivity(new Intent(this,WebManagementActivity.class)));Button tools=tile("QR Y ARCHIVO\nPlantillas · historial");tools.setOnClickListener(v->startActivity(new Intent(this,ToolsActivity.class)));row.addView(web,weight());row.addView(tools,weight());page.addView(row);
        Button settings=secondary("IMPRESORA Y AJUSTES");settings.setOnClickListener(v->startActivity(new Intent(this,PrinterSettingsActivity.class)));page.addView(settings,top(dp(9)));
        Button diagnostics=secondary("DIAGNÓSTICO DEL SISTEMA");diagnostics.setOnClickListener(v->startActivity(new Intent(this,DiagnosticsActivity.class)));page.addView(diagnostics,top(dp(8)));
        Button complement=ghost("WEBAPP · complemento de emergencia");complement.setOnClickListener(v->startActivity(new Intent(this,WebAppActivity.class)));page.addView(complement,top(dp(8)));

        TextView note=text("La impresión de esta APK es local y directa a la térmica. No existe el flujo “Enviar a Android”.",12,Color.GRAY,false);note.setPadding(dp(2),dp(18),dp(2),0);page.addView(note);
        TextView version=text("O Faro Gestión · v"+BuildConfig.VERSION_NAME,11,Color.rgb(145,145,145),false);version.setGravity(Gravity.CENTER);version.setPadding(0,dp(18),0,0);page.addView(version);
        return root;
    }

    private void refreshPrinter(){if(printerState==null)return;if(core.printerIp().isEmpty()){printerState.setText("● Impresora · falta configurar IP");printerState.setTextColor(Color.rgb(155,75,35));return;}boolean ok=core.printerConnected();printerState.setText((ok?"● ":"○ ")+"Impresora · "+(ok?"lista":"reconectando")+" · "+core.printerIp()+":"+core.printerPort());printerState.setTextColor(ok?Color.rgb(28,105,58):Color.rgb(155,75,35));}
    private void probeServer(boolean forced){if(serverState==null)return;lastServerProbe=System.currentTimeMillis();if(!core.configured()){serverState.setText("Servidor · falta configurar la clave de gestión");serverState.setTextColor(Color.rgb(150,70,40));return;}if(!core.internetAvailable()){serverState.setText("Servidor · sin Internet · se usarán datos guardados");serverState.setTextColor(Color.rgb(150,95,30));return;}serverState.setText("Servidor · comprobando en segundo plano…");io.execute(()->{try{core.post(core.action("appPing"));runOnUiThread(()->{if(serverState!=null){serverState.setText("Servidor · conectado");serverState.setTextColor(Color.rgb(35,95,60));}});}catch(Exception e){runOnUiThread(()->{if(serverState!=null){serverState.setText("Servidor · no responde · datos locales disponibles");serverState.setTextColor(Color.rgb(150,75,35));}});}});}
    private void requestNotificationOnce(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!core.prefs().getBoolean("askedNotifications",false)){core.prefs().edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},501);}}

    private TextView section(String s){TextView t=text(s,12,Color.GRAY,true);t.setPadding(dp(2),dp(21),0,dp(8));return t;}
    private Button action(String title,String subtitle){Button b=new Button(this);b.setAllCaps(false);b.setText(title+"\n"+subtitle);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setPadding(dp(17),dp(8),dp(17),dp(8));b.setBackground(box(Color.rgb(15,15,15),15,Color.TRANSPARENT,0));b.setMinHeight(dp(82));return b;}
    private Button tile(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.rgb(20,20,20));b.setBackground(box(Color.WHITE,13,Color.rgb(205,205,205),1));b.setMinHeight(dp(72));return b;}
    private Button secondary(String s){Button b=tile(s);b.setMinHeight(dp(54));return b;}
    private Button ghost(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(11);b.setTextColor(Color.DKGRAY);b.setBackgroundColor(Color.TRANSPARENT);return b;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=m;return p;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable box(int fill,int radius,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(sw>0)d.setStroke(dp(sw),stroke);return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
