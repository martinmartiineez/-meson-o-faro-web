package com.ofaro.participaciones;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiagnosticsActivity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private AppCore core;private LinearLayout page;private TextView live;
    private String backendStatus="No ejecutado",rendererStatus="No ejecutado";

    @Override protected void onCreate(Bundle b){super.onCreate(b);core=new AppCore(this);setContentView(build());paint();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private android.view.View build(){
        LinearLayout root=col();root.setBackgroundColor(Color.rgb(247,246,243));
        LinearLayout head=col();head.setPadding(dp(18),dp(17),dp(18),dp(14));head.setBackgroundColor(Color.rgb(15,15,15));
        head.addView(text("O FARO",24,Color.WHITE,true));head.addView(text("Diagnóstico · v"+BuildConfig.VERSION_NAME,13,Color.LTGRAY,false));root.addView(head);
        ScrollView scroll=new ScrollView(this);page=col();page.setPadding(dp(15),dp(15),dp(15),dp(30));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        live=cardText("");page.addView(live);
        Button backend=primary("PROBAR CONTRATOS DEL BACKEND");backend.setOnClickListener(v->testBackend(backend));page.addView(backend,top(dp(12)));
        Button server=secondary("PROBAR SERVIDOR RÁPIDO");server.setOnClickListener(v->probe(server,"appPing","Servidor"));page.addView(server,top(dp(8)));
        Button promos=secondary("PROBAR PROMOCIONES RÁPIDO");promos.setOnClickListener(v->probe(promos,"promotionPing","Promociones"));page.addView(promos,top(dp(8)));
        Button renderer=secondary("PROBAR LAS 28 PLANTILLAS · 58/80 MM");renderer.setOnClickListener(v->testRenderer(renderer));page.addView(renderer,top(dp(8)));
        Button printer=secondary("RECONECTAR IMPRESORA");printer.setOnClickListener(v->reconnect(printer));page.addView(printer,top(dp(8)));
        Button print=secondary("IMPRIMIR TICKET DE DIAGNÓSTICO");print.setOnClickListener(v->printTest(print));page.addView(print,top(dp(8)));
        Button copy=secondary("COPIAR DIAGNÓSTICO");copy.setOnClickListener(v->copy());page.addView(copy,top(dp(8)));
        Button back=secondary("VOLVER");back.setOnClickListener(v->finish());page.addView(back,top(dp(14)));
        TextView note=text("Las pruebas de backend solo consultan datos. No crean, editan, canjean ni eliminan registros. La clave de gestión no aparece ni se copia.",12,Color.GRAY,false);note.setPadding(0,dp(14),0,0);page.addView(note);
        return root;
    }

    private String report(){
        StringBuilder s=new StringBuilder();
        s.append("O FARO · DIAGNÓSTICO\n");
        s.append("Hora: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.getDefault()).format(new Date())).append("\n");
        s.append("APK: ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
        s.append("Terminal: ").append(core.terminal()).append("\n");
        s.append("Internet: ").append(core.internetAvailable()?"disponible":"sin red").append("\n");
        s.append("Servidor: ").append(core.configured()?"configurado":"falta configuración").append("\n");
        s.append("Impresora: ").append(core.printerStatus()).append("\n");
        s.append("Perfil: ").append(core.printerPaper()).append("mm · corte ").append(core.printerCut()).append(" · avance ").append(core.printerFeed()).append(" · oscuridad ").append(core.printerDarkness()).append("\n");
        s.append("Contratos backend: ").append(backendStatus).append("\n");
        s.append("Renderer: ").append(rendererStatus).append("\n");
        s.append("Caché reservas hoy: ").append(age("reservations_HOY")).append("\n");
        s.append("Caché promociones: ").append(age("promo_campaigns")).append("\n");
        s.append("Caché premios: ").append(age("promo_prizes")).append("\n");
        return s.toString();
    }
    private String age(String name){long a=core.cacheAgeMs(name);if(a==Long.MAX_VALUE)return"sin datos";long m=a/60000;return m<1?"ahora":m+" min";}
    private void paint(){live.setText(report());live.setTextColor(core.printerIp().isEmpty()?Color.rgb(135,65,40):Color.rgb(35,80,55));}

    private void testBackend(Button b){b.setEnabled(false);b.setText("COMPROBANDO CONTRATOS…");io.execute(()->{BackendContractSelfTest.Result result=BackendContractSelfTest.run(core);runOnUiThread(()->{backendStatus=result.details();b.setEnabled(true);b.setText("PROBAR CONTRATOS DEL BACKEND");paint();toast(result.summary());});});}
    private void probe(Button b,String action,String label){b.setEnabled(false);io.execute(()->{try{core.post(core.action(action));runOnUiThread(()->{b.setEnabled(true);paint();toast(label+" · correcto");});}catch(Exception e){runOnUiThread(()->{b.setEnabled(true);paint();toast(label+" · "+msg(e));});}});}
    private void testRenderer(Button b){b.setEnabled(false);b.setText("PROBANDO 56 RENDERIZADOS…");io.execute(()->{RendererSelfTest.Result result=RendererSelfTest.run();runOnUiThread(()->{rendererStatus=result.details();b.setEnabled(true);b.setText("PROBAR LAS 28 PLANTILLAS · 58/80 MM");paint();toast(result.summary());});});}
    private void reconnect(Button b){b.setEnabled(false);io.execute(()->{core.reconnectPrinter();runOnUiThread(()->{b.setEnabled(true);paint();toast(core.printerStatus());});});}
    private void printTest(Button b){b.setEnabled(false);io.execute(()->{try{core.printTest(core.printerIp(),core.printerPort());runOnUiThread(()->{b.setEnabled(true);paint();toast("Ticket enviado a la impresora");});}catch(Exception e){runOnUiThread(()->{b.setEnabled(true);paint();toast("Impresora · "+msg(e));});}});}
    private void copy(){ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);cm.setPrimaryClip(ClipData.newPlainText("Diagnóstico O Faro",report()));toast("Diagnóstico copiado");}

    private TextView cardText(String s){TextView t=text(s,13,Color.DKGRAY,false);t.setPadding(dp(14),dp(14),dp(14),dp(14));t.setBackground(box(Color.WHITE,12,Color.rgb(210,210,210),1));return t;}
    private Button primary(String s){Button b=button(s);b.setTextColor(Color.WHITE);b.setBackground(box(Color.rgb(15,15,15),12,Color.TRANSPARENT,0));return b;}
    private Button secondary(String s){Button b=button(s);b.setTextColor(Color.rgb(20,20,20));b.setBackground(box(Color.WHITE,12,Color.rgb(195,195,195),1));return b;}
    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(54));return b;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable box(int fill,int radius,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(sw>0)d.setStroke(dp(sw),stroke);return d;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=m;return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private String msg(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}
}
