package com.ofaro.participaciones;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PrinterSettingsActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private AppCore core;private TextView connection,darkLabel;private EditText ip,port,key,terminal,api;private Spinner paper,cut,feed;private SeekBar darkness;
    @Override protected void onCreate(Bundle b){super.onCreate(b);core=new AppCore(this);setContentView(build());refresh();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private android.view.View build(){LinearLayout root=col();root.setBackgroundColor(Color.rgb(247,246,243));LinearLayout head=col();head.setPadding(dp(18),dp(17),dp(18),dp(14));head.setBackgroundColor(Color.rgb(15,15,15));head.addView(txt("O FARO",24,Color.WHITE,true));head.addView(txt("Impresora y conexión",13,Color.LTGRAY,false));root.addView(head);ScrollView scroll=new ScrollView(this);LinearLayout page=col();page.setPadding(dp(16),dp(16),dp(16),dp(30));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        connection=cardText("");page.addView(connection);
        page.addView(section("IMPRESORA TÉRMICA"));ip=field(page,"IP de la impresora",core.printerIp(),InputType.TYPE_CLASS_PHONE);port=field(page,"Puerto ESC/POS",String.valueOf(core.printerPort()),InputType.TYPE_CLASS_NUMBER);
        LinearLayout row=row();LinearLayout a=col(),b=col();paper=spinner(a,"Papel",new String[]{"80 mm","58 mm"},core.printerPaper()==58?"58 mm":"80 mm");cut=spinner(b,"Corte",new String[]{"Completo","Parcial","Sin corte"},cutLabel(core.printerCut()));row.addView(a,weight());row.addView(b,weight());page.addView(row);
        feed=spinner(page,"Avance después del ticket",new String[]{"0 líneas","1 línea","2 líneas","3 líneas","4 líneas","5 líneas","6 líneas"},core.printerFeed()+" "+(core.printerFeed()==1?"línea":"líneas"));darkLabel=txt("Oscuridad térmica · "+core.printerDarkness(),13,Color.DKGRAY,true);darkLabel.setPadding(0,dp(12),0,0);page.addView(darkLabel);darkness=new SeekBar(this);darkness.setMax(110);darkness.setProgress(core.printerDarkness()-120);darkness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){darkLabel.setText("Oscuridad térmica · "+(120+p));}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});page.addView(darkness);
        Button savePrinter=primary("GUARDAR Y CONECTAR");savePrinter.setOnClickListener(v->saveAndConnect(savePrinter));page.addView(savePrinter,top(dp(12)));Button reconnect=secondary("RECONECTAR AHORA");reconnect.setOnClickListener(v->reconnect(reconnect));page.addView(reconnect,top(dp(8)));Button test=secondary("IMPRIMIR TICKET DE PRUEBA");test.setOnClickListener(v->test(test));page.addView(test,top(dp(8)));
        page.addView(section("SERVIDOR Y TERMINAL"));terminal=field(page,"Nombre de este terminal",core.terminal(),InputType.TYPE_CLASS_TEXT);key=field(page,"Clave de gestión",core.key(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);api=field(page,"Endpoint Apps Script",core.api(),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);Button testApi=secondary("PROBAR APPS SCRIPT");testApi.setOnClickListener(v->testApi(testApi));page.addView(testApi,top(dp(9)));Button diag=secondary("ABRIR DIAGNÓSTICO");diag.setOnClickListener(v->{save();startActivity(new Intent(this,DiagnosticsActivity.class));});page.addView(diag,top(dp(8)));Button close=secondary("VOLVER");close.setOnClickListener(v->{save();finish();});page.addView(close,top(dp(14)));
        TextView note=txt("La APK mantiene una conexión TCP local con la térmica y renueva el socket antes de imprimir si lleva demasiado tiempo abierto. Ninguna impresión usa la cola “Enviar a Android”.",12,Color.GRAY,false);note.setPadding(0,dp(16),0,0);page.addView(note);return root;}

    private void save(){int p=parse(text(port),9100);int f=selected(feed).startsWith("0")?0:selected(feed).startsWith("1")?1:selected(feed).startsWith("2")?2:selected(feed).startsWith("3")?3:selected(feed).startsWith("4")?4:selected(feed).startsWith("5")?5:6;core.prefs().edit().putString("printerIp",text(ip)).putInt("printerPort",Math.max(1,Math.min(65535,p))).putInt("printerPaper",selected(paper).startsWith("58")?58:80).putString("printerCut",cutValue(selected(cut))).putInt("printerFeed",f).putInt("printerDarkness",120+darkness.getProgress()).putString("terminal",text(terminal)).putString("key",text(key)).putString("api",text(api)).apply();}
    private void saveAndConnect(Button b){save();b.setEnabled(false);core.startPrinterWatchdog();io.execute(()->{core.reconnectPrinter();runOnUiThread(()->{b.setEnabled(true);refresh();toast(core.printerStatus());});});}
    private void reconnect(Button b){save();b.setEnabled(false);io.execute(()->{core.reconnectPrinter();runOnUiThread(()->{b.setEnabled(true);refresh();});});}
    private void test(Button b){save();b.setEnabled(false);io.execute(()->{try{core.printTest(core.printerIp(),core.printerPort());runOnUiThread(()->{b.setEnabled(true);refresh();toast("Prueba impresa");});}catch(Exception e){runOnUiThread(()->{b.setEnabled(true);refresh();toast("Error · "+msg(e));});}});}
    private void testApi(Button b){save();b.setEnabled(false);io.execute(()->{try{JSONObject r=core.post(core.action("appPing"));runOnUiThread(()->{b.setEnabled(true);toast("Apps Script conectado");});}catch(Exception e){runOnUiThread(()->{b.setEnabled(true);toast("Servidor · "+msg(e));});}});}
    private void refresh(){if(connection==null)return;connection.setText("IMPRESORA · "+core.printerStatus()+"\nPerfil · "+core.printerPaper()+" mm · "+cutLabel(core.printerCut())+" · oscuridad "+core.printerDarkness());connection.setTextColor(core.printerConnected()?Color.rgb(25,105,55):Color.rgb(145,65,40));}

    private String cutValue(String s){if(s.startsWith("Parcial"))return"partial";if(s.startsWith("Sin"))return"none";return"full";}private String cutLabel(String s){if("partial".equalsIgnoreCase(s))return"Parcial";if("none".equalsIgnoreCase(s))return"Sin corte";return"Completo";}
    private TextView section(String s){TextView t=txt(s,12,Color.GRAY,true);t.setPadding(0,dp(20),0,dp(5));return t;}private TextView cardText(String s){TextView t=txt(s,13,Color.DKGRAY,true);t.setPadding(dp(12),dp(12),dp(12),dp(12));t.setBackground(box(Color.WHITE,12,Color.rgb(205,205,205),1));return t;}
    private EditText field(LinearLayout p,String label,String value,int type){TextView l=txt(label,13,Color.DKGRAY,true);l.setPadding(0,dp(10),0,dp(4));p.addView(l);EditText e=new EditText(this);e.setText(value);e.setTextSize(16);e.setTextColor(Color.rgb(20,20,20));e.setInputType(type);e.setSingleLine(true);e.setPadding(dp(12),0,dp(12),0);e.setBackground(box(Color.WHITE,11,Color.rgb(205,205,205),1));p.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));return e;}
    private Spinner spinner(LinearLayout p,String label,String[] values,String selected){TextView l=txt(label,13,Color.DKGRAY,true);l.setPadding(0,dp(10),0,dp(4));p.addView(l);Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);for(int i=0;i<values.length;i++)if(values[i].equalsIgnoreCase(selected)){s.setSelection(i);break;}s.setBackground(box(Color.WHITE,11,Color.rgb(205,205,205),1));p.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));return s;}
    private Button primary(String s){Button b=button(s);b.setTextColor(Color.WHITE);b.setBackground(box(Color.rgb(15,15,15),12,Color.TRANSPARENT,0));return b;}private Button secondary(String s){Button b=button(s);b.setTextColor(Color.rgb(20,20,20));b.setBackground(box(Color.WHITE,12,Color.rgb(190,190,190),1));return b;}private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(54));return b;}private TextView txt(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private GradientDrawable box(int fill,int r,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(r));if(sw>0)d.setStroke(dp(sw),stroke);return d;}private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(dp(3),0,dp(3),0);return p;}private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=v;return p;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private int parse(String s,int f){try{return Integer.parseInt(s.trim());}catch(Exception e){return f;}}private String selected(Spinner s){Object x=s.getSelectedItem();return x==null?"":String.valueOf(x);}private String text(EditText e){return e==null?"":e.getText().toString().trim();}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}private String msg(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}
}
