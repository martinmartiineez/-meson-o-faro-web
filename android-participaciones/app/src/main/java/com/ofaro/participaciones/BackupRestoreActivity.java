package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Copias portables de ajustes + plantillas. El secreto de gestión se excluye. */
public class BackupRestoreActivity extends Activity {
    private static final int REQ_EXPORT=8111,REQ_IMPORT=8112;
    private AppCore core;
    private final ExecutorService io=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state){super.onCreate(state);V5Ui.applySystemBars(this);core=new AppCore(this);setContentView(build());}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View build(){
        LinearLayout root=V5Ui.column(this);root.setBackgroundColor(V5Ui.BG);root.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,14),V5Ui.dp(this,18),V5Ui.dp(this,24));
        LinearLayout header=V5Ui.row(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView back=V5Ui.text(this,"‹",31,V5Ui.GREEN,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(V5Ui.dp(this,38),V5Ui.dp(this,44)));LinearLayout titles=V5Ui.column(this);titles.addView(V5Ui.kicker(this,"CONFIGURACIÓN"));titles.addView(V5Ui.text(this,"Copia de seguridad",26,V5Ui.INK,true));header.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(header);
        TextView expl=V5Ui.subtitle(this,"Guarda ajustes de impresora, terminal y plantillas. La clave privada de gestión no se exporta y deberá configurarse en el dispositivo de destino.");expl.setPadding(0,V5Ui.dp(this,12),0,V5Ui.dp(this,14));root.addView(expl);

        LinearLayout export=V5Ui.linkCard(this,R.drawable.ic_download_v5,"Exportar copia","Archivo JSON portable",this::exportBackup);root.addView(export);
        LinearLayout imp=V5Ui.linkCard(this,R.drawable.ic_upload_v5,"Importar copia","Restaura ajustes y plantillas",this::importBackup);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);ip.topMargin=V5Ui.dp(this,9);root.addView(imp,ip);

        LinearLayout note=V5Ui.softCard(this);note.addView(V5Ui.kicker(this,"INCLUYE"));note.addView(V5Ui.text(this,"Impresora · terminal · endpoint · predeterminadas · plantillas personalizadas",11,V5Ui.MUTED,false));note.addView(V5Ui.kicker(this,"NO INCLUYE"));note.addView(V5Ui.text(this,"Clave privada · reservas · promociones · históricos del servidor",11,V5Ui.MUTED,false));LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);np.topMargin=V5Ui.dp(this,14);root.addView(note,np);
        return root;
    }

    private void exportBackup(){
        String stamp=new SimpleDateFormat("yyyyMMdd-HHmm",Locale.ROOT).format(new Date());Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_TITLE,"O-Faro-backup-"+stamp+".json");startActivityForResult(i,REQ_EXPORT);
    }
    private void importBackup(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/json");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});startActivityForResult(i,REQ_IMPORT);}

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);if(resultCode!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(requestCode==REQ_EXPORT)io.execute(()->{try{byte[] bytes=V5BackupStore.exportJson(core).getBytes(StandardCharsets.UTF_8);try(OutputStream out=getContentResolver().openOutputStream(uri,"w")){if(out==null)throw new Exception("No se pudo abrir el archivo.");out.write(bytes);out.flush();}runOnUiThread(()->Toast.makeText(this,"Copia exportada",Toast.LENGTH_SHORT).show());}catch(Exception e){showError("No se pudo exportar",e);}});
        else if(requestCode==REQ_IMPORT)io.execute(()->{try{String raw=read(uri);JSONObject result=V5BackupStore.importJson(core,raw);runOnUiThread(()->new AlertDialog.Builder(this).setTitle("Copia restaurada").setMessage("Plantillas importadas: "+result.optInt("templates",0)+(result.optBoolean("needsManagementKey",false)?"\n\nFalta configurar la clave de gestión en este dispositivo.":"")).setPositiveButton("Aceptar",null).show());}catch(Exception e){showError("No se pudo importar",e);}});
    }

    private String read(Uri uri)throws Exception{StringBuilder b=new StringBuilder();try(InputStream in=getContentResolver().openInputStream(uri);BufferedReader br=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){if(in==null)throw new Exception("No se pudo abrir el archivo.");String line;while((line=br.readLine())!=null){b.append(line).append('\n');if(b.length()>8_000_000)throw new Exception("El archivo es demasiado grande.");}}return b.toString();}
    private void showError(String title,Throwable e){String msg=e==null?"Error desconocido":e.getMessage();runOnUiThread(()->new AlertDialog.Builder(this).setTitle(title).setMessage(msg==null?"Error desconocido":msg).setPositiveButton("Cerrar",null).show());}
}
