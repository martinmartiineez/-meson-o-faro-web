package com.ofaro.participaciones;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class NativeMainActivity extends Activity {
    private AppCore core;private TextView printerStatus,apiStatus;private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable tick=new Runnable(){public void run(){refreshStatus();handler.postDelayed(this,2000);}};
    @Override protected void onCreate(Bundle b){super.onCreate(b);core=new AppCore(this);setContentView(build());core.startPrinterWatchdog();handler.post(tick);}
    @Override protected void onResume(){super.onResume();if(core!=null)core.startPrinterWatchdog();refreshStatus();}
    @Override protected void onDestroy(){handler.removeCallbacks(tick);super.onDestroy();}

    private android.view.View build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(246,245,242));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(20),dp(18),dp(20),dp(16));head.setBackgroundColor(Color.rgb(15,15,15));head.addView(txt("O FARO",28,Color.WHITE,true));head.addView(txt("Gestión nativa · APK principal",14,Color.LTGRAY,false));root.addView(head);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(16),dp(16),dp(16),dp(32));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        printerStatus=statusCard("IMPRESORA · comprobando…");page.addView(printerStatus);apiStatus=statusCard(core.configured()?"APPS SCRIPT · configurado":"APPS SCRIPT · falta clave");page.addView(apiStatus,topWrap(dp(8)));
        TextView h=txt("Operación diaria",25,Color.rgb(20,20,20),true);h.setPadding(0,dp(18),0,dp(8));page.addView(h);
        Button promos=big("PROMOCIONES","Ruleta, rasca, premios, QR de cliente y canjes");promos.setOnClickListener(v->startActivity(new Intent(this,PromotionsActivity.class)));page.addView(promos);
        Button reservations=big("RESERVAS Y OPERACIÓN","Reservas, estados, QR, plantillas, historial y utilidades");reservations.setOnClickListener(v->startActivity(new Intent(this,ManagementActivityV3.class)));page.addView(reservations,top(dp(10)));
        TextView p=txt("Impresión",25,Color.rgb(20,20,20),true);p.setPadding(0,dp(20),0,dp(8));page.addView(p);
        Button studio=big("ESTUDIO DE IMPRESIÓN","28 plantillas · 58/80 mm · QR · imagen · tipografías · copias");studio.setOnClickListener(v->startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre")));page.addView(studio);
        LinearLayout r=row();Button image=tile("SOLO IMAGEN");image.setOnClickListener(v->startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Imagen")));Button settings=tile("IMPRESORA / AJUSTES");settings.setOnClickListener(v->startActivity(new Intent(this,PrinterSettingsActivity.class)));r.addView(image,weight());r.addView(settings,weight());page.addView(r,topWrap(dp(9)));
        TextView w=txt("Contenido",25,Color.rgb(20,20,20),true);w.setPadding(0,dp(20),0,dp(8));page.addView(w);
        Button web=big("GESTIÓN DE LA WEB","Carta, menú, avisos, imágenes y otros datos desde la APK");web.setOnClickListener(v->startActivity(new Intent(this,WebManagementActivity.class)));page.addView(web);
        Button webapp=secondary("ABRIR WEBAPP · COMPLEMENTO");webapp.setOnClickListener(v->startActivity(new Intent(this,WebAppActivity.class)));page.addView(webapp,top(dp(10)));
        TextView note=txt("La impresión de la APK es siempre LOCAL y DIRECTA a la impresora configurada. La cola 'Enviar a Android' no forma parte de este flujo.",13,Color.DKGRAY,false);note.setPadding(dp(2),dp(18),dp(2),0);page.addView(note);
        return root;
    }
    private void refreshStatus(){if(core==null||printerStatus==null)return;printerStatus.setText("IMPRESORA · "+core.printerStatus());printerStatus.setTextColor(core.printerConnected()?Color.rgb(25,110,55):Color.rgb(145,55,40));apiStatus.setText(core.configured()?"APPS SCRIPT · listo · "+core.terminal():"APPS SCRIPT · configura la clave en Ajustes");apiStatus.setTextColor(core.configured()?Color.rgb(25,90,55):Color.rgb(145,75,35));}
    private TextView statusCard(String s){TextView t=txt(s,14,Color.DKGRAY,true);t.setPadding(dp(13),dp(12),dp(13),dp(12));t.setBackground(box(Color.WHITE,12,Color.rgb(210,210,210),1));return t;}private Button big(String title,String sub){Button b=primary(title+"\n"+sub);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setPadding(dp(16),0,dp(16),0);b.setMinHeight(dp(78));return b;}private Button tile(String s){Button b=secondary(s);b.setMinHeight(dp(64));return b;}private Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(box(Color.rgb(15,15,15),14,Color.TRANSPARENT,0));return b;}private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.rgb(20,20,20));b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setBackground(box(Color.WHITE,12,Color.rgb(190,190,190),1));return b;}private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(dp(3),0,dp(3),0);return p;}private TextView txt(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private GradientDrawable box(int fill,int r,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(r));if(sw>0)d.setStroke(dp(sw),stroke);return d;}private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(78));p.topMargin=v;return p;}private LinearLayout.LayoutParams topWrap(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=v;return p;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
