package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ParticipationActivityV3 extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private AppCore core;
    private LinearLayout page;
    private TextView subtitle;
    private EditText manualCode;
    private TextView validationTitle;
    private TextView validationDetail;
    private Button redeem;
    private String validatedCode="";

    @Override protected void onCreate(Bundle b){super.onCreate(b);core=new AppCore(this);setContentView(buildRoot());showGenerate();}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View buildRoot(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(246,246,246));
        LinearLayout head=new LinearLayout(this);head.setOrientation(LinearLayout.VERTICAL);head.setPadding(dp(20),dp(18),dp(20),dp(16));head.setBackgroundColor(Color.rgb(17,17,17));head.addView(text("MESÓN O FARO",23,Color.WHITE,true));subtitle=text("Participaciones",14,Color.rgb(210,210,210),false);head.addView(subtitle);root.addView(head);
        LinearLayout nav=row();nav.setPadding(dp(10),dp(10),dp(10),dp(10));nav.setBackgroundColor(Color.WHITE);Button back=navButton("‹ INICIO");back.setOnClickListener(v->finish());Button gen=navButton("GENERAR");gen.setOnClickListener(v->showGenerate());Button scan=navButton("ESCANEAR");scan.setOnClickListener(v->showScan());nav.addView(back,weight());nav.addView(gen,weight());nav.addView(scan,weight());root.addView(nav);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(18),dp(20),dp(18),dp(30));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
    }

    private void showGenerate(){
        clear("Generar participaciones");page.addView(sectionTitle("Generar participación"));page.addView(paragraph("Genera el código primero. Después podrás previsualizar el ticket y decidir si imprimirlo, con o sin imagen."));
        TextView l=text("Cantidad",14,Color.DKGRAY,true);l.setPadding(0,0,0,dp(6));page.addView(l);EditText count=input("1");count.setInputType(InputType.TYPE_CLASS_NUMBER);count.setGravity(Gravity.CENTER);page.addView(count,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
        Button generate=primaryButton("GENERAR CÓDIGOS");page.addView(generate,marginTop(dp(12)));TextView status=text("Listo.",14,Color.DKGRAY,false);status.setPadding(0,dp(14),0,dp(8));page.addView(status);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);page.addView(list);
        generate.setOnClickListener(v->{if(!requireApi())return;int n=safeInt(count.getText().toString(),1,1,20);generate.setEnabled(false);status.setText("Generando…");list.removeAllViews();io.execute(()->{int done=0;try{for(int i=0;i<n;i++){JSONObject res=core.post(core.action("participationCreate").put("origin","APK-v2.2-preview"));core.ensureOk(res);done++;JSONObject item=res;runOnUiThread(()->addGeneratedCard(list,item));}int total=done;runOnUiThread(()->{status.setText("Generados "+total+(total==1?" código":" códigos")+". Ninguno se marca como impreso hasta que confirmes una impresión.");generate.setEnabled(true);});}catch(Exception e){int total=done;runOnUiThread(()->{status.setText("Generados "+total+". Error: "+cleanError(e));generate.setEnabled(true);});}});});
    }

    private void addGeneratedCard(LinearLayout list,JSONObject item){
        String code=item.optString("code","");String created=item.optString("createdAt","");String qr=item.optString("qrPayload","OFARO:"+code);LinearLayout card=cardBox();card.addView(text(code,19,Color.rgb(20,20,20),true));card.addView(text(created,13,Color.DKGRAY,false));Button preview=primaryButton("PREVISUALIZAR / IMPRIMIR");preview.setOnClickListener(v->openPreview(code,created,qr));card.addView(preview,marginTop(dp(8)));list.addView(card,marginBottomWrap(dp(10)));
    }

    private void openPreview(String code,String created,String qr){Intent i=new Intent(this,TicketPreviewActivity.class);i.putExtra("title","MESON O FARO");i.putExtra("subtitle","PARTICIPACION");i.putExtra("body",code+"\n"+created+"\n\nConserva este ticket.");i.putExtra("qr",qr);i.putExtra("type","Participación");i.putExtra("reference",code);i.putExtra("detail","Participación generada");i.putExtra("participationCode",code);startActivity(i);}

    private void showScan(){
        clear("Validar y canjear");page.addView(sectionTitle("Validar y canjear"));page.addView(paragraph("Escanea un QR. El lector está incluido dentro de la APK y la validación se hace contra Google Sheets."));Button scan=primaryButton("ESCANEAR QR");scan.setOnClickListener(v->startScanner());page.addView(scan);TextView alt=text("O introduce el código manualmente",14,Color.DKGRAY,false);alt.setPadding(0,dp(16),0,dp(7));page.addView(alt);manualCode=input("");manualCode.setHint("OF-XXXXX-XXXXX");manualCode.setSingleLine(true);page.addView(manualCode,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));Button validate=secondaryButton("VALIDAR CÓDIGO");validate.setOnClickListener(v->validateCode(manualCode.getText().toString()));page.addView(validate,marginTop(dp(9)));
        LinearLayout result=cardBox();validationTitle=text("Sin código validado",20,Color.rgb(20,20,20),true);validationDetail=text("Escanea un ticket para ver su premio y estado.",15,Color.DKGRAY,false);validationDetail.setPadding(0,dp(8),0,dp(12));redeem=primaryButton("CANJEAR PREMIO");redeem.setVisibility(View.GONE);redeem.setOnClickListener(v->confirmRedeem());result.addView(validationTitle);result.addView(validationDetail);result.addView(redeem);page.addView(result,marginTopWrap(dp(16)));
    }

    private void startScanner(){if(!requireApi())return;IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(java.util.Collections.singletonList("QR_CODE"));i.setPrompt("Apunta al código QR del ticket");i.setBeepEnabled(false);i.setOrientationLocked(true);i.initiateScan();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){IntentResult result=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(result!=null){String raw=result.getContents();if(raw==null){toast("Escaneo cancelado");return;}raw=raw.trim();if(manualCode!=null)manualCode.setText(raw);validateCode(raw);return;}super.onActivityResult(requestCode,resultCode,data);}

    private void validateCode(String raw){if(!requireApi())return;String code=raw==null?"":raw.trim();if(code.isEmpty()){alert("Código vacío","Introduce o escanea un código.");return;}setValidation("Validando…","Consultando Google Sheets.",false);io.execute(()->{try{JSONObject r=core.post(core.action("participationValidate").put("code",code));core.ensureOk(r);String out=r.optString("code",code),state=r.optString("state",""),prize=r.optString("prize","Sin premio");boolean has=r.optBoolean("hasPrize",false),can=r.optBoolean("canRedeem",false);validatedCode=out;String title,detail;if("Canjeada".equalsIgnoreCase(state)){title="YA CANJEADO";detail=out+"\nPremio: "+prize+"\nCanjeado: "+dash(r.optString("redeemedAt",""))+"\nTerminal: "+dash(r.optString("redeemedBy",""));}else if(!has){title="SIN PREMIO";detail=out+"\nEstado: "+state+"\nCódigo válido sin premio canjeable.";}else{title=prize.toUpperCase(Locale.ROOT);detail=out+"\nEstado: "+state+"\nPremio pendiente de canje.";}runOnUiThread(()->setValidation(title,detail,can));}catch(Exception e){validatedCode="";runOnUiThread(()->setValidation("CÓDIGO NO VÁLIDO",cleanError(e),false));}});}
    private void confirmRedeem(){if(validatedCode.isEmpty())return;new AlertDialog.Builder(this).setTitle("Confirmar canje").setMessage("El código quedará inutilizado para siempre. ¿Entregar el premio ahora?").setNegativeButton("Cancelar",null).setPositiveButton("CANJEAR",(d,w)->redeem()).show();}
    private void redeem(){String code=validatedCode;redeem.setEnabled(false);setValidation("Canjeando…","Bloqueando el código en Google Sheets.",false);io.execute(()->{try{JSONObject r=core.post(core.action("participationRedeem").put("code",code));core.ensureOk(r);String prize=r.optString("prize","Premio"),at=r.optString("redeemedAt","");runOnUiThread(()->{setValidation("PREMIO CANJEADO",prize+"\n"+code+"\n"+at,false);redeem.setEnabled(true);});}catch(Exception e){runOnUiThread(()->{setValidation("NO SE PUDO CANJEAR",cleanError(e),false);redeem.setEnabled(true);});}});}
    private void setValidation(String t,String d,boolean can){if(validationTitle==null)return;validationTitle.setText(t);validationDetail.setText(d);redeem.setVisibility(can?View.VISIBLE:View.GONE);redeem.setEnabled(true);}

    private boolean requireApi(){if(core.configured())return true;alert("Falta configurar la API","Vuelve a Inicio → Ajustes y configura la conexión.");return false;}
    private void clear(String s){page.removeAllViews();subtitle.setText(s);}private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}private Button navButton(String s){Button b=secondaryButton(s);b.setTextSize(11);return b;}private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(48),1);p.setMargins(dp(3),0,dp(3),0);return p;}private TextView sectionTitle(String s){TextView t=text(s,26,Color.rgb(20,20,20),true);t.setPadding(0,0,0,dp(8));return t;}private TextView paragraph(String s){TextView t=text(s,15,Color.DKGRAY,false);t.setLineSpacing(0,1.15f);t.setPadding(0,0,0,dp(18));return t;}private LinearLayout cardBox(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(15));c.setBackground(roundRect(Color.WHITE,14,Color.rgb(225,225,225),1));return c;}private EditText input(String s){EditText e=new EditText(this);e.setText(s);e.setTextSize(16);e.setTextColor(Color.rgb(20,20,20));e.setHintTextColor(Color.GRAY);e.setPadding(dp(14),0,dp(14),0);e.setBackground(roundRect(Color.WHITE,12,Color.rgb(205,205,205),1));return e;}private Button primaryButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(roundRect(Color.rgb(17,17,17),12,Color.TRANSPARENT,0));b.setMinHeight(dp(54));return b;}private Button secondaryButton(String s){Button b=new Button(this);b.setText(s);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.rgb(20,20,20));b.setAllCaps(false);b.setBackground(roundRect(Color.WHITE,12,Color.rgb(190,190,190),1));b.setMinHeight(dp(52));return b;}private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}private GradientDrawable roundRect(int fill,int radius,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(sw>0)d.setStroke(dp(sw),stroke);return d;}private LinearLayout.LayoutParams marginTop(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54));p.topMargin=top;return p;}private LinearLayout.LayoutParams marginTopWrap(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=top;return p;}private LinearLayout.LayoutParams marginBottomWrap(int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=bottom;return p;}private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}private int safeInt(String v,int f,int min,int max){try{int n=Integer.parseInt(v.trim());return Math.max(min,Math.min(max,n));}catch(Exception e){return f;}}private String dash(String s){return s==null||s.trim().isEmpty()?"—":s.trim();}private String cleanError(Throwable t){String m=t==null?"Error desconocido":t.getMessage();if(m==null||m.trim().isEmpty())m=String.valueOf(t);return m.length()>420?m.substring(0,420):m;}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}private void alert(String t,String m){if(!isFinishing())new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Aceptar",null).show();}
}
