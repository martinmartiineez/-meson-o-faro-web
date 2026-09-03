package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TicketPreviewActivity extends Activity {
    private static final int PICK_IMAGE=4401;
    private static final long PREVIEW_DEBOUNCE_MS=120L;
    private static final String[] TEMPLATES={
            "Reserva Express","Reserva Elegante","Reserva Completa","Reserva Cliente",
            "Promoción Premium","Promoción Clásica","Entrada QR","Ruleta QR","Rasca QR",
            "Premio Canjeable","Vale Regalo","Cupón Descuento","Próxima Visita",
            "QR Carta","QR Menú","QR WiFi","QR Reseñas","QR Instagram","QR Personalizado",
            "Promo del Día","Oferta Flash","Evento Especial","Novedad O Faro",
            "Minimal Premium","Ticket Editorial","Ticket Retro","Texto Libre","Solo Imagen"
    };
    private static final String[] TYPO={"O Faro","Editorial","Elegante restaurante","Promocional fuerte","Minimal nórdico","Retro ticket"};
    private static final String[] PAPERS={"80 mm","58 mm"};
    private static final String[] QR_SIZES={"S","M","L","XL"};
    private static final String[] SEPARATORS={"Línea","Puntos","Guiones","Doble","Ninguno"};
    private static final String[] IMAGE_POS={"No imprimir","Arriba","Abajo"};

    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler previewHandler=new Handler(Looper.getMainLooper());
    private final Runnable previewRunnable=this::renderPreviewNow;
    private AppCore core;
    private EditText title,subtitle,body,qr,copies;
    private Spinner template,typography,paper,qrSize,separator,imagePosition;
    private SeekBar imageSize;
    private TextView imageSizeLabel,printerStatus,status;
    private ImageView preview;
    private Bitmap previewBitmap;
    private String imageData="";
    private String reference="",detail="",reservationId="",participationCode="",type="Ticket";
    private volatile boolean rendering;
    private volatile boolean renderPending;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);core=new AppCore(this);readIntent();setContentView(build());core.startPrinterWatchdog();refreshPrinterStatus();renderPreview();
    }
    @Override protected void onDestroy(){
        previewHandler.removeCallbacksAndMessages(null);
        io.shutdownNow();
        Bitmap old=previewBitmap;previewBitmap=null;
        if(old!=null&&!old.isRecycled())old.recycle();
        super.onDestroy();
    }

    private void readIntent(){Intent i=getIntent();reference=v(i,"reference");detail=v(i,"detail");reservationId=v(i,"reservationId");participationCode=v(i,"participationCode");String t=v(i,"type");if(!t.isEmpty())type=t;}
    private String v(Intent i,String k){String s=i.getStringExtra(k);return s==null?"":s;}

    private View build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(244,243,239));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(18),dp(16),dp(18),dp(14));head.setBackgroundColor(Color.rgb(15,15,15));head.addView(txt("O FARO",23,Color.WHITE,true));head.addView(txt("Estudio de impresión · directo ESC/POS",13,Color.LTGRAY,false));root.addView(head);
        ScrollView scroll=new ScrollView(this);LinearLayout page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(14),dp(14),dp(14),dp(30));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        printerStatus=txt("Impresora…",13,Color.DKGRAY,true);printerStatus.setPadding(dp(12),dp(10),dp(12),dp(10));printerStatus.setBackground(box(Color.WHITE,12,Color.rgb(210,210,210),1));page.addView(printerStatus);
        preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);preview.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(390));pp.topMargin=dp(12);page.addView(preview,pp);

        template=spin(page,"Plantilla",TEMPLATES,defaultTemplate());
        typography=spin(page,"Tipografía",TYPO,"O Faro");
        LinearLayout row1=row();LinearLayout a=column();LinearLayout b=column();paper=spinInto(a,"Papel",PAPERS,"80 mm");qrSize=spinInto(b,"Tamaño QR",QR_SIZES,"L");row1.addView(a,weight());row1.addView(b,weight());page.addView(row1);
        LinearLayout row2=row();LinearLayout c=column();LinearLayout d=column();separator=spinInto(c,"Separador",SEPARATORS,"Línea");imagePosition=spinInto(d,"Imagen",IMAGE_POS,"No imprimir");row2.addView(c,weight());row2.addView(d,weight());page.addView(row2);

        title=field(page,"Título",v(getIntent(),"title"));subtitle=field(page,"Subtítulo",v(getIntent(),"subtitle"));body=multi(page,"Texto",v(getIntent(),"body"),5);qr=field(page,"Contenido QR",v(getIntent(),"qr"));
        copies=field(page,"Copias","1");copies.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);

        imageSizeLabel=txt("Tamaño imagen · 75%",13,Color.DKGRAY,true);imageSizeLabel.setPadding(0,dp(12),0,0);page.addView(imageSizeLabel);imageSize=new SeekBar(this);imageSize.setMax(75);imageSize.setProgress(50);page.addView(imageSize);
        Button choose=secondary("ELEGIR / CAMBIAR IMAGEN");choose.setOnClickListener(x->pickImage());page.addView(choose,top(dp(10)));
        Button remove=secondary("QUITAR IMAGEN");remove.setOnClickListener(x->{imageData="";setSpinner(imagePosition,"No imprimir");renderPreview();});page.addView(remove,top(dp(8)));

        Button print=primary("IMPRIMIR AHORA");print.setOnClickListener(x->confirmPrint(print));page.addView(print,top(dp(16)));
        Button reconnect=secondary("RECONECTAR IMPRESORA");reconnect.setOnClickListener(x->reconnect(reconnect));page.addView(reconnect,top(dp(9)));
        Button close=secondary("CERRAR");close.setOnClickListener(x->finish());page.addView(close,top(dp(9)));
        status=txt("",13,Color.DKGRAY,false);status.setPadding(0,dp(10),0,0);page.addView(status);

        template.setOnItemSelectedListener(listener(this::renderPreview));typography.setOnItemSelectedListener(listener(this::renderPreview));paper.setOnItemSelectedListener(listener(this::renderPreview));qrSize.setOnItemSelectedListener(listener(this::renderPreview));separator.setOnItemSelectedListener(listener(this::renderPreview));imagePosition.setOnItemSelectedListener(listener(this::renderPreview));
        android.text.TextWatcher watcher=new SimpleWatcher(this::renderPreview);title.addTextChangedListener(watcher);subtitle.addTextChangedListener(watcher);body.addTextChangedListener(watcher);qr.addTextChangedListener(watcher);
        imageSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean f){imageSizeLabel.setText("Tamaño imagen · "+(25+p)+"%");renderPreview();}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        return root;
    }

    private String defaultTemplate(){String t=type.toLowerCase();if(t.contains("reserva"))return"Reserva Express";if(t.contains("promoc"))return"Promoción Premium";if(t.contains("premio"))return"Premio Canjeable";if(t.contains("qr"))return"QR Personalizado";if(t.contains("imagen"))return"Solo Imagen";return"Minimal Premium";}

    private void renderPreview(){
        if(preview==null||isFinishing())return;
        previewHandler.removeCallbacks(previewRunnable);
        previewHandler.postDelayed(previewRunnable,PREVIEW_DEBOUNCE_MS);
    }

    private void renderPreviewNow(){
        if(preview==null||isFinishing())return;
        if(rendering){renderPending=true;return;}
        rendering=true;renderPending=false;
        final JSONObject j=currentJob();
        io.execute(()->{
            Bitmap bm=null;
            try{bm=TicketRenderer.render(j);}catch(Exception ignored){}
            Bitmap out=bm;
            runOnUiThread(()->{
                if(isFinishing()){
                    if(out!=null&&!out.isRecycled())out.recycle();
                    rendering=false;
                    return;
                }
                Bitmap old=previewBitmap;
                if(out!=null){previewBitmap=out;preview.setImageBitmap(out);}
                if(old!=null&&old!=out&&!old.isRecycled())old.recycle();
                rendering=false;
                if(renderPending){renderPending=false;renderPreview();}
            });
        });
    }

    private JSONObject currentJob(){
        JSONObject j=new JSONObject();try{
            j.put("type",type);j.put("templateId",selected(template));j.put("typography",selected(typography));j.put("paperWidth",selected(paper).startsWith("58")?58:80);j.put("title",text(title));j.put("subtitle",text(subtitle));j.put("text",text(body));j.put("qr",text(qr));j.put("qrSize",selected(qrSize));j.put("separator",sepValue());j.put("imageData",imageData);j.put("imagePosition",posValue());j.put("imageWidthPercent",25+imageSize.getProgress());j.put("copies",clamp(parse(text(copies),1),1,5));j.put("origin","APK nativa");
        }catch(Exception ignored){}return j;
    }
    private String sepValue(){String s=selected(separator);if(s.startsWith("Puntos"))return"dots";if(s.startsWith("Guiones"))return"dashes";if(s.startsWith("Doble"))return"double";if(s.startsWith("Ninguno"))return"none";return"line";}
    private String posValue(){String s=selected(imagePosition);if("Arriba".equals(s))return"top";if("Abajo".equals(s))return"bottom";return"none";}

    private void confirmPrint(Button b){
        if(core.printerIp().isEmpty()){alert("Impresora sin configurar","Configura la IP de la impresora en Ajustes.");return;}
        new AlertDialog.Builder(this).setTitle("Imprimir ticket").setMessage("Se enviará directamente a "+core.printerIp()+":"+core.printerPort()+".\n\nPlantilla: "+selected(template)+"\nCopias: "+clamp(parse(text(copies),1),1,5)).setNegativeButton("Cancelar",null).setPositiveButton("IMPRIMIR",(d,w)->printNow(b)).show();
    }
    private void printNow(Button b){b.setEnabled(false);status.setText("Imprimiendo…");JSONObject job=currentJob();io.execute(()->{try{RemotePrinter.print(core,job);afterPrint();runOnUiThread(()->{b.setEnabled(true);status.setText("Impresión completada · "+core.printerStatus());Toast.makeText(this,"Ticket impreso",Toast.LENGTH_SHORT).show();refreshPrinterStatus();});}catch(Exception e){runOnUiThread(()->{b.setEnabled(true);status.setText("Error: "+msg(e));refreshPrinterStatus();alert("Error de impresión",msg(e));});}});}
    private void afterPrint(){if(!core.configured())return;try{core.post(core.action("historyAdd").put("type",type).put("reference",reference).put("event","Impreso desde APK").put("printer","IMP001").put("detail",detail).put("state","OK"));}catch(Exception ignored){}if(!reservationId.isEmpty())try{core.post(core.action("reservationMarkPrinted").put("id",reservationId));}catch(Exception ignored){}if(!participationCode.isEmpty())try{core.post(core.action("participationMarkPrinted").put("code",participationCode));}catch(Exception ignored){} }

    private void reconnect(Button b){b.setEnabled(false);status.setText("Reconectando…");io.execute(()->{core.reconnectPrinter();runOnUiThread(()->{b.setEnabled(true);refreshPrinterStatus();status.setText(core.printerStatus());});});}
    private void refreshPrinterStatus(){if(printerStatus==null)return;String s=core.printerStatus();printerStatus.setText("IMPRESORA · "+s);printerStatus.setTextColor(core.printerConnected()?Color.rgb(25,110,55):Color.rgb(150,55,40));}

    private void pickImage(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_IMAGE);}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==PICK_IMAGE&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){Uri u=data.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}try{imageData=encodeImage(u);setSpinner(imagePosition,"Arriba");renderPreview();}catch(Exception e){alert("Imagen",msg(e));}}}
    private String encodeImage(Uri uri)throws Exception{
        Bitmap source=null,scaled=null;
        try(InputStream in=getContentResolver().openInputStream(uri)){
            source=BitmapFactory.decodeStream(in);if(source==null)throw new Exception("Imagen no compatible");
            int max=900;float sc=Math.min(1f,max/(float)Math.max(1,source.getWidth()));
            scaled=sc<1?Bitmap.createScaledBitmap(source,Math.max(1,Math.round(source.getWidth()*sc)),Math.max(1,Math.round(source.getHeight()*sc)),true):source;
            ByteArrayOutputStream os=new ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.JPEG,82,os);
            return"data:image/jpeg;base64,"+Base64.encodeToString(os.toByteArray(),Base64.NO_WRAP);
        } finally {
            if(scaled!=null&&scaled!=source&&!scaled.isRecycled())scaled.recycle();
            if(source!=null&&!source.isRecycled())source.recycle();
        }
    }

    private Spinner spin(LinearLayout page,String label,String[] values,String selected){TextView l=txt(label,13,Color.DKGRAY,true);l.setPadding(0,dp(10),0,dp(4));page.addView(l);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));page.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));setSpinner(s,selected);return s;}
    private Spinner spinInto(LinearLayout box,String label,String[] values,String selected){return spin(box,label,values,selected);}
    private EditText field(LinearLayout page,String label,String value){TextView l=txt(label,13,Color.DKGRAY,true);l.setPadding(0,dp(10),0,dp(4));page.addView(l);EditText e=input(value);e.setSingleLine(true);page.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));return e;}
    private EditText multi(LinearLayout page,String label,String value,int lines){TextView l=txt(label,13,Color.DKGRAY,true);l.setPadding(0,dp(10),0,dp(4));page.addView(l);EditText e=input(value);e.setSingleLine(false);e.setMinLines(lines);e.setGravity(Gravity.TOP);e.setPadding(dp(12),dp(10),dp(12),dp(10));page.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}
    private EditText input(String v){EditText e=new EditText(this);e.setText(v);e.setTextSize(16);e.setTextColor(Color.rgb(20,20,20));e.setPadding(dp(12),0,dp(12),0);e.setBackground(box(Color.WHITE,11,Color.rgb(205,205,205),1));return e;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);return r;}private LinearLayout column(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);return c;}private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private Button primary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(box(Color.rgb(15,15,15),12,Color.TRANSPARENT,0));b.setMinHeight(dp(54));return b;}private Button secondary(String s){Button b=new Button(this);b.setText(s);b.setTextColor(Color.rgb(20,20,20));b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(box(Color.WHITE,12,Color.rgb(190,190,190),1));b.setMinHeight(dp(52));return b;}
    private TextView txt(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private GradientDrawable box(int fill,int rad,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(rad));if(sw>0)d.setStroke(dp(sw),stroke);return d;}private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54));p.topMargin=v;return p;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private String text(EditText e){return e==null?"":e.getText().toString().trim();}private String selected(Spinner s){return s==null||s.getSelectedItem()==null?"":String.valueOf(s.getSelectedItem());}private void setSpinner(Spinner s,String val){if(s==null)return;for(int i=0;i<s.getCount();i++)if(String.valueOf(s.getItemAtPosition(i)).equals(val)){s.setSelection(i);return;}}private int parse(String s,int f){try{return Integer.parseInt(s.trim());}catch(Exception e){return f;}}private int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}private String msg(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}private void alert(String t,String m){if(!isFinishing())new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Aceptar",null).show();}

    private android.widget.AdapterView.OnItemSelectedListener listener(Runnable r){return new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){r.run();}public void onNothingSelected(android.widget.AdapterView<?> p){}};}
    private static class SimpleWatcher implements android.text.TextWatcher{private final Runnable r;SimpleWatcher(Runnable r){this.r=r;}public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){r.run();}public void afterTextChanged(android.text.Editable e){}}
}
