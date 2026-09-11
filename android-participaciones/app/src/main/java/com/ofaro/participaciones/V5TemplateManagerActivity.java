package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Gestor de plantillas v5 accesible sin iniciar una impresión. */
public class V5TemplateManagerActivity extends Activity {
    private static final int REQ_IMPORT=7121;
    private AppCore core;
    private LinearLayout listHost;
    private final ExecutorService io=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        V5Ui.applySystemBars(this);
        core=new AppCore(this);
        setContentView(build());
        refresh();
    }

    @Override protected void onResume(){super.onResume();if(listHost!=null)refresh();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View build(){
        LinearLayout root=V5Ui.column(this);root.setBackgroundColor(V5Ui.BG);
        LinearLayout header=V5Ui.row(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,14),V5Ui.dp(this,18),V5Ui.dp(this,10));
        TextView back=V5Ui.text(this,"‹",31,V5Ui.GREEN,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(V5Ui.dp(this,38),V5Ui.dp(this,44)));
        LinearLayout titleBox=V5Ui.column(this);titleBox.addView(V5Ui.kicker(this,"RECURSOS"));titleBox.addView(V5Ui.text(this,"Plantillas",26,V5Ui.INK,true));header.addView(titleBox,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(header);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout page=V5Ui.column(this);page.setPadding(V5Ui.dp(this,18),0,V5Ui.dp(this,18),V5Ui.dp(this,26));
        page.addView(V5Ui.subtitle(this,"Crea, importa, previsualiza y administra diseños de impresión sin generar un ticket real."));

        LinearLayout actions=V5Ui.row(this);actions.setPadding(0,V5Ui.dp(this,13),0,V5Ui.dp(this,13));
        actions.addView(action("GESTIONAR",()->V5PrintPreview.manage(this,core)),weight());
        LinearLayout.LayoutParams ip=weight();ip.leftMargin=V5Ui.dp(this,8);actions.addView(action("IMPORTAR",this::pickImport),ip);page.addView(actions);

        listHost=V5Ui.column(this);page.addView(listHost);scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
    }

    private void refresh(){
        if(listHost==null)return;listHost.removeAllViews();List<JSONObject> all=V5TemplateStore.list(core);
        TextView count=V5Ui.text(this,all.size()+" plantillas personalizadas",10.5f,V5Ui.MUTED,false);count.setPadding(0,0,0,V5Ui.dp(this,8));listHost.addView(count);
        if(all.isEmpty()){
            LinearLayout empty=V5Ui.softCard(this);empty.addView(V5Ui.text(this,"Todavía no hay plantillas propias.",13,V5Ui.INK,true));empty.addView(V5Ui.text(this,"Pulsa GESTIONAR para crear una o IMPORTAR para recuperar una exportada.",10.5f,V5Ui.MUTED,false));listHost.addView(empty);return;
        }
        for(JSONObject t:all)listHost.addView(card(t),bottom(V5Ui.dp(this,8)));
    }

    private View card(JSONObject t){
        LinearLayout c=V5Ui.card(this);c.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,18));c.setOnClickListener(v->preview(t));
        LinearLayout row=V5Ui.row(this);row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info=V5Ui.column(this);info.addView(V5Ui.text(this,t.optString("name","Mi plantilla"),14.5f,V5Ui.INK,true));
        String meta=t.optString("baseTemplate","Minimal Premium")+" · "+t.optInt("paperWidth",80)+" mm";
        info.addView(V5Ui.text(this,meta,10,V5Ui.MUTED,false));row.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        int images=(t.optString("image1Data","").isEmpty()?0:1)+(t.optString("image2Data","").isEmpty()?0:1);
        row.addView(V5Ui.pill(this,images+" img",V5Ui.LIME_SOFT,V5Ui.GREEN));c.addView(row);
        TextView hint=V5Ui.text(this,"Toca para previsualizar",9.5f,V5Ui.FAINT,false);hint.setPadding(0,V5Ui.dp(this,7),0,0);c.addView(hint);return c;
    }

    private void preview(JSONObject template){
        io.execute(()->{
            Bitmap bm=null;String error="";
            try{
                JSONObject job=new JSONObject().put("paperWidth",template.optInt("paperWidth",80)).put("templateId",template.optString("baseTemplate","Minimal Premium"))
                        .put("title","MESÓN O FARO").put("subtitle","VISTA PREVIA").put("text","Ejemplo de plantilla\n2 PERSONAS · 21:30\nMesa 6 · Interior")
                        .put("qr","https://menudeldiaofaro.netlify.app").put("qrSize",template.optString("qrSize","L")).put("separator",template.optString("separator","line"));
                V5TemplateStore.apply(job,template);bm=TicketRenderer.render(job);
            }catch(Exception e){error=e.getMessage()==null?String.valueOf(e):e.getMessage();}
            Bitmap result=bm;String err=error;runOnUiThread(()->{if(result==null){new AlertDialog.Builder(this).setTitle("No se pudo previsualizar").setMessage(err).setPositiveButton("Cerrar",null).show();return;}ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);image.setBackgroundColor(Color.WHITE);image.setImageBitmap(result);new AlertDialog.Builder(this).setTitle(template.optString("name","Plantilla")).setView(image).setNegativeButton("Cerrar",(d,w)->{if(!result.isRecycled())result.recycle();}).setOnCancelListener(d->{if(!result.isRecycled())result.recycle();}).show();});
        });
    }

    private void pickImport(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});startActivityForResult(i,REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(requestCode!=REQ_IMPORT||resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();io.execute(()->{try{String raw=read(uri);JSONObject imported=V5TemplateStore.importJson(core,raw);runOnUiThread(()->{Toast.makeText(this,"Plantilla importada · "+imported.optString("name","Mi plantilla"),Toast.LENGTH_SHORT).show();refresh();});}catch(Exception e){String msg=e.getMessage()==null?String.valueOf(e):e.getMessage();runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo importar").setMessage(msg).setPositiveButton("Cerrar",null).show());}});
    }

    private String read(Uri uri)throws Exception{
        StringBuilder out=new StringBuilder();try(InputStream in=getContentResolver().openInputStream(uri);BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null){out.append(line).append('\n');if(out.length()>4_000_000)throw new Exception("El archivo es demasiado grande.");}}return out.toString();
    }

    private TextView action(String label,Runnable r){TextView t=V5Ui.text(this,label,10,V5Ui.GREEN,true);t.setGravity(Gravity.CENTER);t.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));t.setOnClickListener(v->r.run());return t;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,V5Ui.dp(this,44),1);}
    private LinearLayout.LayoutParams bottom(int px){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=px;return p;}
}
