package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cola e historial de trabajos ESC/POS, con reintento manual de errores. */
public class PrintQueueActivity extends Activity {
    private AppCore core;
    private LinearLayout host;
    private final ExecutorService io=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state){super.onCreate(state);V5Ui.applySystemBars(this);core=new AppCore(this);setContentView(build());refresh();}
    @Override protected void onResume(){super.onResume();if(host!=null)refresh();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View build(){
        LinearLayout root=V5Ui.column(this);root.setBackgroundColor(V5Ui.BG);
        LinearLayout header=V5Ui.row(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,12),V5Ui.dp(this,18),V5Ui.dp(this,8));
        TextView back=V5Ui.text(this,"‹",31,V5Ui.GREEN,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(V5Ui.dp(this,38),V5Ui.dp(this,44)));
        LinearLayout titles=V5Ui.column(this);titles.addView(V5Ui.kicker(this,"IMPRESIÓN"));titles.addView(V5Ui.text(this,"Cola e historial",26,V5Ui.INK,true));header.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(header);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);host=V5Ui.column(this);host.setPadding(V5Ui.dp(this,18),0,V5Ui.dp(this,18),V5Ui.dp(this,24));sc.addView(host);root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
    }

    private void refresh(){
        host.removeAllViews();List<JSONObject> all=PrintJobStore.list(core);int pending=PrintJobStore.pendingCount(core);
        host.addView(V5Ui.subtitle(this,pending==0?"Todos los trabajos están resueltos.":pending+" trabajos requieren atención."));
        LinearLayout summary=V5Ui.darkCard(this);summary.setPadding(V5Ui.dp(this,16),V5Ui.dp(this,14),V5Ui.dp(this,16),V5Ui.dp(this,14));summary.addView(V5Ui.kicker(this,"REGISTRO LOCAL"));summary.addView(V5Ui.text(this,String.valueOf(all.size()),30,android.graphics.Color.WHITE,true));summary.addView(V5Ui.text(this,"trabajos conservados · "+pending+" pendientes/error",10.5f,android.graphics.Color.rgb(198,207,199),false));host.addView(summary,top(V5Ui.dp(this,12)));
        if(all.isEmpty()){LinearLayout e=V5Ui.softCard(this);e.addView(V5Ui.text(this,"Todavía no hay impresiones registradas.",12,V5Ui.MUTED,false));host.addView(e,top(V5Ui.dp(this,10)));return;}
        for(JSONObject x:all)host.addView(jobCard(x),top(V5Ui.dp(this,8)));
    }

    private View jobCard(JSONObject x){
        LinearLayout c=V5Ui.card(this);c.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,18));String status=x.optString("status","");
        LinearLayout top=V5Ui.row(this);top.setGravity(Gravity.CENTER_VERTICAL);String title=x.optString("title","").trim();if(title.isEmpty())title=x.optString("templateId","Ticket");top.addView(V5Ui.text(this,title,13.5f,V5Ui.INK,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));top.addView(statusPill(status));c.addView(top);
        String meta=date(x.optLong("createdAt",0))+" · "+x.optInt("copies",1)+" copia"+(x.optInt("copies",1)==1?"":"s")+" · intento "+x.optInt("attempts",0);c.addView(V5Ui.text(this,meta,9.8f,V5Ui.MUTED,false));
        String err=x.optString("error","").trim();if(!err.isEmpty()){TextView e=V5Ui.text(this,err,10,V5Ui.ERROR,false);e.setPadding(0,V5Ui.dp(this,6),0,0);c.addView(e);}
        if(PrintJobStore.STATUS_ERROR.equals(status)||PrintJobStore.STATUS_QUEUED.equals(status)||PrintJobStore.STATUS_SENDING.equals(status)){
            LinearLayout buttons=V5Ui.row(this);TextView retry=button("Reintentar");retry.setOnClickListener(v->retry(x.optString("id","")));buttons.addView(retry,new LinearLayout.LayoutParams(0,V5Ui.dp(this,40),1));TextView remove=button("Eliminar");remove.setOnClickListener(v->confirmDelete(x.optString("id","")));LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,V5Ui.dp(this,40),1);rp.leftMargin=V5Ui.dp(this,7);buttons.addView(remove,rp);c.addView(buttons,top(V5Ui.dp(this,9)));
        }
        return c;
    }

    private TextView statusPill(String s){int fill=V5Ui.LIME_SOFT,color=V5Ui.GREEN;String label="Enviado";if(PrintJobStore.STATUS_ERROR.equals(s)){fill=android.graphics.Color.rgb(248,229,225);color=V5Ui.ERROR;label="Error";}else if(PrintJobStore.STATUS_SENDING.equals(s)){fill=android.graphics.Color.rgb(242,236,218);color=V5Ui.WARNING;label="Enviando";}else if(PrintJobStore.STATUS_QUEUED.equals(s)){fill=android.graphics.Color.rgb(235,236,232);color=V5Ui.MUTED;label="En cola";}return V5Ui.pill(this,label,fill,color);}
    private TextView button(String label){TextView t=V5Ui.text(this,label,10.5f,V5Ui.GREEN,true);t.setGravity(Gravity.CENTER);t.setBackground(V5Ui.bg(this,V5Ui.SURFACE,13));return t;}

    private void retry(String id){
        if(id.isEmpty())return;io.execute(()->{try{RemotePrinter.retry(core,id);runOnUiThread(()->{Toast.makeText(this,"Trabajo enviado",Toast.LENGTH_SHORT).show();refresh();});}catch(Exception e){String m=e.getMessage()==null?String.valueOf(e):e.getMessage();runOnUiThread(()->{new AlertDialog.Builder(this).setTitle("No se pudo imprimir").setMessage(m).setPositiveButton("Cerrar",null).show();refresh();});}});
    }
    private void confirmDelete(String id){new AlertDialog.Builder(this).setTitle("Eliminar del historial").setMessage("Se eliminará este trabajo guardado. No afecta a tickets ya impresos.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(d,w)->{PrintJobStore.delete(core,id);refresh();}).show();}
    private String date(long ms){if(ms<=0)return"—";return new SimpleDateFormat("dd/MM HH:mm",new Locale("es","ES")).format(new Date(ms));}
    private LinearLayout.LayoutParams top(int px){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=px;return p;}
}
