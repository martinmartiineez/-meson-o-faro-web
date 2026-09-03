package com.ofaro.participaciones;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrinterSettingsActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private AppCore core;private TextView connection;private EditText ip,port,key,terminal,api;
    @Override protected void onCreate(Bundle b){super.onCreate(b);core=new AppCore(this);setContentView(build());refresh();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
    private android.view.View build(){LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(246,245,242));LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(18),dp(15),dp(18),dp(13));head.setBackgroundColor(Color.rgb(15,15,15));head.addView(txt("O FARO",23,Color.WHITE,true));head.addView(txt("Ajustes · conexión permanente",13,Color.LTGRAY,false));root.addView(head);ScrollView scroll=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(16),dp(16),dp(16),dp(30));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));connection=txt("",14,Color.DKGRAY,true);connection.setPadding(dp(12),dp(12),dp(12),dp(12));connection.setBackground(box(Color.WHITE,12,Color.rgb(205,205,205),1));page.addView(connection);ip=field(page,"IP impresora ESC/POS",core.printerIp(),InputType.TYPE_CLASS_PHONE);port=field(page,"Puerto",String.valueOf(core.printerPort()),InputType.TYPE_CLASS_NUMBER);terminal=field(page,"Nombre de este terminal",core.terminal(),InputType.TYPE_CLASS_TEXT);key=field(page,"Clave de gestión",core.key(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);api=field(page,"Endpoint Apps Script",core.api(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);Button save=primary("GUARDAR Y CONECTAR");save.setOnClickListener(v->saveAndConnect(save));page.addView(save,top(dp(15)));Button reconnect=secondary("RECONECTAR AHORA");reconnect.setOnClickListener(v->reconnect(reconnect));page.addView(reconnect,top(dp(8)));Button test=secondary("IMPRIMIR TICKET DE PRUEBA");test.setOnClickListener(v->test(test));page.addView(test,top(dp(8)));Button testApi=secondary("PROBAR APPS SCRIPT");testApi.setOnClickListener(v->testApi(testApi));page.addView(testApi,top(dp(8)));Button close=secondary("VOLVER");close.setOnClickListener(v->finish());page.addView(close,top(dp(12)));TextView note=txt("La APK intenta mantener la conexión TCP con la térmica en segundo plano y reconecta automáticamente si el router o la impresora la cierran. No existe ningún modo 'Enviar a Android': todas las impresiones de esta APK son locales y directas.",13,Color.DKGRAY,false);note.setPadding(0,dp(16),0,0);page.addView(note);return root;}
    private void save(){int p=parse(port.getText().toString(),9100);core.prefs().edit().putString("printerIp",ip.getText().toString().trim()).putInt("printerPort",Math.max(1,Math.min(65535,p))).putString("terminal",terminal.getText().toString().trim()).putString("key",key.getText().toString().trim()).putString("api",api.getText().toString().trim()).apply();}
    private void saveAndConnect(Button b){save();b.setEnabled(false);core.startPrinterWatchdog();io.execute(()->{core.reconnectPrinter();runOnUiThread(()->{b.setEnabled(true);refresh();Toast.makeText(this,core.printerStatus(),Toast.LENGTH_SHORT).show();});});}
    private void reconnect(Button b){save();b.setEnabled(false);io.execute(()->{core.reconnectPrinter();runOnUiThread(()->{b.setEnabled(true);refresh();});});}
    private void test(Button b){save();b.setEnabled(false);io.execute(()->{try{core.printTest(core.printerIp(),core.printerPort());runOnUiThread(()->{b.setEnabled(true);refresh();Toast.makeText(this,"Prueba impresa",Toast.LENGTH_SHORT).show();});}catch(Exception e){runOnUiThread(()->{b.setEnabled(true);refresh();Toast.makeText(this,"Error: "+msg(e),Toast.LENGTH_LONG).show();});}});}
    private void testApi(Button b){save();b.setEnabled(false);io.execute(()->{try{JSONObject r=core.post(core.action("appPing"));core.ensureOk(r);runOnUiThread(()->{b.setEnabled(true);Toast.makeText(this,"Apps Script conectado",Toast.LENGTH_SHORT).show();});}catch(Exception e){runOnUiThread(()->{b.setEnabled(true);Toast.makeText(this,"API: "+msg(e),Toast.LENGTH_LONG).show();});}});}
    private void refresh(){if(connection==null)return;connection.setText("IMPRESORA · "+core.printerStatus());connection.setTextColor(core.printerConnected()?Color.rgb(25,110,55):Color.rgb(145,55,40));}
    private EditText field(LinearLayout p,String label,String value,int type){TextView l=txt(label,13,Color.DKGRAY,true);l.setPadding(0,dp(10),0,dp(4));p.addView(l);EditText e=new EditText(this);e.setText(value);e.setTextSize(16);e.setTextColor(Color.rgb(20,20,20));e.setInputType(type);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackground(box(Color.WHITE,11,Color.rgb(205,205,205),1));p.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));return e;}private Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(box(Color.rgb(15,15,15),12,Color.TRANSPARENT,0));return b;}private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.rgb(20,20,20));b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(box(Color.WHITE,12,Color.rgb(190,190,190),1));return b;}private TextView txt(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private GradientDrawable box(int fill,int r,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(r));if(sw>0)d.setStroke(dp(sw),stroke);return d;}private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54));p.topMargin=v;return p;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private int parse(String s,int f){try{return Integer.parseInt(s.trim());}catch(Exception e){return f;}}private String msg(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}
}
