package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Editor libre de tickets con QR, imagen, copias y previsualización real. */
public class FreePrintActivity extends Activity {
    private static final int REQ_IMAGE=8811;
    private AppCore core;
    private EditText title,subtitle,body,qr;
    private Spinner copies,imagePosition;
    private TextView imageStatus;
    private String imageUri="";
    private final ExecutorService io=Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle state){super.onCreate(state);V5Ui.applySystemBars(this);core=new AppCore(this);setContentView(build());}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View build(){
        LinearLayout root=V5Ui.column(this);root.setBackgroundColor(V5Ui.BG);
        LinearLayout header=V5Ui.row(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,12),V5Ui.dp(this,18),V5Ui.dp(this,8));TextView back=V5Ui.text(this,"‹",31,V5Ui.GREEN,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(V5Ui.dp(this,38),V5Ui.dp(this,44)));LinearLayout titles=V5Ui.column(this);titles.addView(V5Ui.kicker(this,"IMPRESIÓN"));titles.addView(V5Ui.text(this,"Crear ticket",26,V5Ui.INK,true));header.addView(titles,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));root.addView(header);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout p=V5Ui.column(this);p.setPadding(V5Ui.dp(this,18),0,V5Ui.dp(this,18),V5Ui.dp(this,28));p.addView(V5Ui.subtitle(this,"Compón un ticket libre. Antes de imprimir podrás elegir la plantilla y revisar la vista previa exacta."));
        title=field(p,"Título","MESÓN O FARO",false);subtitle=field(p,"Subtítulo","",false);body=field(p,"Texto","",true);qr=field(p,"Contenido QR (opcional)","",false);
        p.addView(label("Copias"));copies=spinner(new String[]{"1 copia","2 copias","3 copias","4 copias","5 copias"});p.addView(copies,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,46)));
        p.addView(label("Imagen opcional"));LinearLayout imgRow=V5Ui.row(this);TextView choose=button("ELEGIR IMAGEN");choose.setOnClickListener(v->pickImage());imgRow.addView(choose,new LinearLayout.LayoutParams(0,V5Ui.dp(this,42),1));TextView clear=button("QUITAR");clear.setOnClickListener(v->{imageUri="";imageStatus.setText("Sin imagen");});LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,V5Ui.dp(this,42),1);clp.leftMargin=V5Ui.dp(this,7);imgRow.addView(clear,clp);p.addView(imgRow);
        imageStatus=V5Ui.text(this,"Sin imagen",9.5f,V5Ui.FAINT,false);imageStatus.setPadding(0,V5Ui.dp(this,5),0,0);p.addView(imageStatus);
        p.addView(label("Posición de la imagen"));imagePosition=spinner(new String[]{"Cabecera","Pie"});p.addView(imagePosition,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,46)));
        TextView print=V5Ui.text(this,"PREVISUALIZAR E IMPRIMIR",11,V5Ui.SURFACE,true);print.setGravity(Gravity.CENTER);print.setBackground(V5Ui.bg(this,V5Ui.GREEN,16));print.setOnClickListener(v->print());LinearLayout.LayoutParams plp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,50));plp.topMargin=V5Ui.dp(this,16);p.addView(print,plp);sc.addView(p);root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
    }

    private EditText field(LinearLayout parent,String label,String initial,boolean multi){parent.addView(label(label));EditText e=new EditText(this);e.setText(initial);e.setTextColor(V5Ui.INK);e.setTextSize(12.5f);e.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));e.setPadding(V5Ui.dp(this,12),V5Ui.dp(this,8),V5Ui.dp(this,12),V5Ui.dp(this,8));if(multi){e.setGravity(Gravity.TOP);e.setMinLines(5);parent.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,132)));}else{e.setSingleLine(true);parent.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,46)));}return e;}
    private TextView label(String value){TextView t=V5Ui.text(this,value,10,V5Ui.MUTED,true);t.setPadding(0,V5Ui.dp(this,10),0,V5Ui.dp(this,4));return t;}
    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));return s;}
    private TextView button(String label){TextView t=V5Ui.text(this,label,9.5f,V5Ui.GREEN,true);t.setGravity(Gravity.CENTER);t.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,13));return t;}

    private void pickImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");startActivityForResult(i,REQ_IMAGE);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_IMAGE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}imageUri=u.toString();imageStatus.setText("Imagen seleccionada");}}

    private void print(){
        String t=text(title),st=text(subtitle),b=text(body),q=text(qr),img=imageUri;int copyCount=copies.getSelectedItemPosition()+1;boolean top=imagePosition.getSelectedItemPosition()==0;
        if(t.isEmpty()&&st.isEmpty()&&b.isEmpty()&&q.isEmpty()&&img.isEmpty()){Toast.makeText(this,"Añade contenido al ticket",Toast.LENGTH_SHORT).show();return;}
        io.execute(()->{try{
            JSONObject job=new JSONObject().put("paperWidth",core.printerPaper()).put("cutMode",core.printerCut()).put("feedLines",core.printerFeed()).put("darkness",core.printerDarkness()).put("typography","O Faro").put("separator","line").put("copies",copyCount)
                    .put("templateId",core.prefs().getString("defaultTemplateGeneral","Minimal Premium")).put("title",t).put("subtitle",st).put("text",b).put("qr",q).put("qrSize","L");
            if(!img.isEmpty()){job.put("imageData",ImageUtil.toDataUri(this,img)).put("imagePosition",top?"top":"bottom").put("imageWidthPercent",75);}else job.put("imagePosition","none");
            String chosen=V5PrintPreview.choose(this,core,job,"defaultTemplateGeneral","Minimal Premium");job.put("templateId",chosen);RemotePrinter.print(core,job);runOnUiThread(()->Toast.makeText(this,"Ticket enviado a impresora",Toast.LENGTH_SHORT).show());
        }catch(Exception e){String m=e.getMessage()==null?String.valueOf(e):e.getMessage();runOnUiThread(()->new AlertDialog.Builder(this).setTitle("No se pudo imprimir").setMessage(m).setPositiveButton("Cerrar",null).show());}});
    }
    private static String text(EditText e){return e==null?"":e.getText().toString().trim();}
}
