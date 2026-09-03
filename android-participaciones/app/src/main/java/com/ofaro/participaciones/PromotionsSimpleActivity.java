package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PromotionsSimpleActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppCore core;
    private LinearLayout page;
    private TextView subtitle;
    private JSONArray campaigns = new JSONArray();
    private JSONArray prizes = new JSONArray();
    private EditText redeemInput;
    private TextView redeemResult;
    private Button redeemButton;
    private String validatedCode = "";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        core = new AppCore(this);
        readCache();
        setContentView(buildRoot());
        showHome();
        refreshInBackground();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private View buildRoot() {
        LinearLayout root=col();root.setBackgroundColor(Color.rgb(247,246,243));
        LinearLayout head=col();head.setPadding(dp(18),dp(17),dp(18),dp(14));head.setBackgroundColor(Color.rgb(15,15,15));
        head.addView(text("O FARO",24,Color.WHITE,true));subtitle=text("Promociones",13,Color.LTGRAY,false);head.addView(subtitle);root.addView(head);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=col();page.setPadding(dp(14),dp(15),dp(14),dp(30));scroll.addView(page);root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        return root;
    }

    private void showHome() {
        clear("Promociones");
        back("Promociones",this::finish);
        page.addView(title("¿Qué quieres hacer?"));
        page.addView(paragraph("Las tareas habituales están separadas de la configuración técnica. Entras, eliges una acción y listo."));

        Button qr=action("SACAR UN QR","Elige una promoción activa y prepara el ticket para el cliente");
        qr.setOnClickListener(v->showQrList());page.addView(qr,top(dp(10)));
        Button create=action("CREAR PROMOCIÓN","Crea un borrador con nombre, tipo y probabilidad en menos de un minuto");
        create.setOnClickListener(v->createPromotion());page.addView(create,top(dp(9)));
        Button redeem=action("CANJEAR UN PREMIO","Escanea o escribe el código y confirma el canje");
        redeem.setOnClickListener(v->showRedeem());page.addView(redeem,top(dp(9)));
        Button prize=action("VER PREMIOS","Consulta rápidamente los premios configurados");
        prize.setOnClickListener(v->showPrizes());page.addView(prize,top(dp(9)));

        int active=0;
        for(int i=0;i<campaigns.length();i++) if(active(campaigns.optJSONObject(i))) active++;
        TextView summary=text(active+" promociones activas   ·   "+countRealPrizes()+" premios",13,Color.DKGRAY,true);
        summary.setPadding(dp(12),dp(11),dp(12),dp(11));summary.setBackground(box(Color.WHITE,11,Color.rgb(218,218,218),1));page.addView(summary,top(dp(15)));

        Button advanced=secondary("CONFIGURACIÓN AVANZADA");advanced.setOnClickListener(v->startActivity(new Intent(this,PromotionsActivity.class)));page.addView(advanced,top(dp(10)));
    }

    private void showQrList() {
        clear("Sacar QR");back("Sacar QR",this::showHome);
        page.addView(title("Elige una promoción"));
        page.addView(paragraph("Solo las promociones activas pueden entregar un QR al cliente."));
        TextView status=text(campaigns.length()>0?"Datos guardados · actualizando en segundo plano…":"Buscando promociones…",13,Color.GRAY,false);page.addView(status);
        LinearLayout list=col();page.addView(list,top(dp(8)));
        paintCampaigns(list);
        refreshCampaigns(status,list);
    }

    private void paintCampaigns(LinearLayout list) {
        list.removeAllViews();int shown=0;
        for(int i=0;i<campaigns.length();i++) {
            JSONObject c=campaigns.optJSONObject(i);if(c==null)continue;shown++;
            String name=str(c,"name","NOMBRE","Promoción");String type=str(c,"type","TIPO_PROMOCION","Promoción");boolean on=active(c);
            LinearLayout card=card();
            card.addView(text(type.toUpperCase(Locale.ROOT),11,Color.GRAY,true));
            card.addView(text(name,20,Color.rgb(20,20,20),true));
            TextView state=text(on?"ACTIVA · lista para entregar":"PAUSADA · necesita configuración",13,on?Color.rgb(30,105,58):Color.rgb(145,90,25),true);state.setPadding(0,dp(5),0,dp(9));card.addView(state);
            Button main=on?primary("SACAR QR"):secondary("CONFIGURAR");
            if(on) main.setOnClickListener(v->issueQr(c,main)); else main.setOnClickListener(v->startActivity(new Intent(this,PromotionsActivity.class)));
            card.addView(main);list.addView(card,bottom(dp(9)));
        }
        if(shown==0) list.addView(empty("Todavía no hay promociones. Pulsa atrás y crea una."));
    }

    private void refreshCampaigns(TextView status,LinearLayout list) {
        if(!core.configured()){status.setText("Sin conexión al servidor · mostrando datos guardados");return;}
        io.execute(()->{try{
            JSONObject r=QuickApi.post(core,core.action("promotionList"));
            campaigns=items(r);saveCache("promo_campaigns",campaigns);
            runOnUiThread(()->{status.setText("Actualizado ahora");paintCampaigns(list);});
        }catch(Exception e){runOnUiThread(()->status.setText(campaigns.length()>0?"No se pudo actualizar · puedes usar los datos guardados":"No se pudo cargar · "+msg(e)));}});
    }

    private void issueQr(JSONObject c,Button b) {
        if(!core.configured()){alert("Falta configurar la conexión","Ve a Ajustes desde Inicio.");return;}
        b.setEnabled(false);b.setText("CREANDO QR…");
        io.execute(()->{try{
            JSONObject r=QuickApi.post(core,core.action("promoPublicIssue").put("id",id(c)).put("expiresHours",48));
            String url=r.optString("url","");if(url.isEmpty())throw new Exception("El servidor no devolvió el QR.");
            String name=str(c,"name","NOMBRE","Promoción"),type=str(c,"type","TIPO_PROMOCION","Promoción");
            runOnUiThread(()->{
                b.setEnabled(true);b.setText("SACAR QR");
                Intent i=new Intent(this,TicketPreviewActivity.class);
                i.putExtra("title",name);i.putExtra("subtitle",type.toUpperCase(Locale.ROOT));
                i.putExtra("body","ESCANEA PARA JUGAR\nQR único de un solo uso.");i.putExtra("qr",url);
                i.putExtra("type","QR");i.putExtra("reference",r.optString("token",""));i.putExtra("detail","QR cliente · "+name);
                startActivity(i);
            });
        }catch(Exception e){runOnUiThread(()->{b.setEnabled(true);b.setText("SACAR QR");alert("No se pudo crear el QR",msg(e));});}});
    }

    private void createPromotion() {
        LinearLayout box=col();box.setPadding(dp(16),dp(4),dp(16),dp(6));
        EditText name=field(box,"Nombre que verá el cliente","");
        Spinner type=spinner(box,"Tipo",new String[]{"Ruleta","Rasca","Premio directo","Código premiado"},"Ruleta");
        EditText probability=field(box,"Probabilidad de premio (%)","20");probability.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        TextView note=paragraph("Se guardará como BORRADOR para que puedas elegir los premios antes de activarla. Los horarios y límites avanzados quedan con valores seguros por defecto.");box.addView(note,top(dp(7)));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Nueva promoción · paso 1 de 2").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("CREAR BORRADOR",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=val(name);if(n.isEmpty()){toast("Escribe un nombre");return;}double pct=number(val(probability),20);if(pct<0||pct>100){toast("La probabilidad debe estar entre 0 y 100");return;}
            Button save=d.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);save.setText("GUARDANDO…");String t=selected(type);
            JSONObject p=new JSONObject();try{
                p.put("ID_PROMOCION","");p.put("NOMBRE",n);p.put("DESCRIPCION","");p.put("TIPO_PROMOCION",t);p.put("ESTADO","Pausada");p.put("ACTIVA","No");
                p.put("FECHA_INICIO","");p.put("FECHA_FIN","");p.put("HORA_INICIO","00:00");p.put("HORA_FIN","23:59");p.put("DIAS_ACTIVOS","Lun,Mar,Mié,Jue,Vie,Sáb,Dom");
                p.put("PROB_GANAR",pct);p.put("MENSAJE_GANA","¡Enhorabuena! Te ha tocado {{premio}}.");p.put("MENSAJE_NO_GANA","Esta vez no ha habido premio.");
                p.put("PLANTILLA_TICKET",t.equals("Ruleta")?"Ruleta QR":t.equals("Rasca")?"Rasca QR":"Promoción Premium");p.put("LIMITE_POR_CLIENTE","1");p.put("LIMITE_POR_DIA","500");p.put("REQUIERE_CODIGO",t.equals("Código premiado")?"Sí":"No");
            }catch(Exception ignored){}
            io.execute(()->{try{
                QuickApi.post(core,core.action("promotionSave").put("promotion",p));
                runOnUiThread(()->{d.dismiss();new AlertDialog.Builder(this).setTitle("Promoción creada").setMessage("Paso 2: elige los premios y, cuando esté lista, actívala. He dejado la promoción pausada para que ningún cliente pueda jugar antes de terminarla.").setNegativeButton("MÁS TARDE",(dd,w)->showHome()).setPositiveButton("CONFIGURAR AHORA",(dd,w)->startActivity(new Intent(this,PromotionsActivity.class))).show();});
            }catch(Exception e){runOnUiThread(()->{save.setEnabled(true);save.setText("CREAR BORRADOR");alert("No se pudo crear",msg(e));});}});
        }));d.show();
    }

    private void showPrizes() {
        clear("Premios");back("Premios",this::showHome);page.addView(title("Premios configurados"));
        TextView status=text(prizes.length()>0?"Datos guardados · actualizando…":"Buscando premios…",13,Color.GRAY,false);page.addView(status);LinearLayout list=col();page.addView(list,top(dp(8)));paintPrizes(list);
        Button advanced=secondary("AÑADIR O EDITAR PREMIOS");advanced.setOnClickListener(v->startActivity(new Intent(this,PromotionsActivity.class)));page.addView(advanced,top(dp(10)));
        if(core.configured())io.execute(()->{try{JSONObject r=QuickApi.post(core,core.action("prizeList").put("includeNoPrize",true));prizes=items(r);saveCache("promo_prizes",prizes);runOnUiThread(()->{status.setText("Actualizado ahora");paintPrizes(list);});}catch(Exception e){runOnUiThread(()->status.setText(prizes.length()>0?"No se pudo actualizar · mostrando copia guardada":"No se pudo cargar · "+msg(e)));}});
    }

    private void paintPrizes(LinearLayout list) {
        list.removeAllViews();int n=0;for(int i=0;i<prizes.length();i++){JSONObject p=prizes.optJSONObject(i);if(p==null||"P000".equals(id(p)))continue;n++;LinearLayout c=card();c.addView(text(str(p,"name","NOMBRE","Premio"),19,Color.rgb(20,20,20),true));c.addView(text(str(p,"type","TIPO","Premio")+" · Stock: "+str(p,"stockRemaining","STOCK_RESTANTE","—"),13,Color.DKGRAY,false));list.addView(c,bottom(dp(8)));}if(n==0)list.addView(empty("No hay premios configurados."));
    }

    private void showRedeem() {
        clear("Canjear");back("Canjear premio",this::showHome);page.addView(title("Canjear premio"));page.addView(paragraph("Escanea el QR del ganador. También puedes escribir el código manualmente."));
        Button scan=primary("ESCANEAR QR");scan.setOnClickListener(v->scan());page.addView(scan,top(dp(8)));
        redeemInput=input("");redeemInput.setHint("PR-XXXXX-XXXXX");page.addView(redeemInput,top(dp(10)));
        Button validate=secondary("VALIDAR CÓDIGO");validate.setOnClickListener(v->validateCode(val(redeemInput)));page.addView(validate,top(dp(8)));
        redeemResult=text("Todavía no hay ningún código validado.",14,Color.DKGRAY,false);redeemResult.setPadding(dp(12),dp(12),dp(12),dp(12));redeemResult.setBackground(box(Color.WHITE,11,Color.rgb(215,215,215),1));page.addView(redeemResult,top(dp(10)));
        redeemButton=primary("CONFIRMAR CANJE");redeemButton.setVisibility(View.GONE);redeemButton.setOnClickListener(v->confirmRedeem());page.addView(redeemButton,top(dp(9)));
    }

    private void scan() {
        IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(java.util.Collections.singletonList("QR_CODE"));i.setPrompt("Escanea el premio");i.setBeepEnabled(false);i.setOrientationLocked(true);i.initiateScan();
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if(r!=null){if(r.getContents()!=null){String code=cleanCode(r.getContents());if(redeemInput==null)showRedeem();redeemInput.setText(code);validateCode(code);}return;}super.onActivityResult(requestCode,resultCode,data);
    }

    private void validateCode(String raw) {
        String code=cleanCode(raw);validatedCode="";if(code.isEmpty()){toast("Código vacío");return;}redeemResult.setText("Validando…");redeemButton.setVisibility(View.GONE);
        io.execute(()->{try{JSONObject r=QuickApi.post(core,core.action("promotionValidate").put("code",code));boolean can=r.optBoolean("canRedeem",false);String finalCode=r.optString("code",code);runOnUiThread(()->{validatedCode=can?finalCode:"";redeemResult.setText("Premio: "+r.optString("prize","Sin premio")+"\nEstado: "+r.optString("state","—")+"\nPromoción: "+r.optString("promotionName","—")+"\nCódigo: "+finalCode);redeemButton.setVisibility(can?View.VISIBLE:View.GONE);});}catch(Exception e){runOnUiThread(()->redeemResult.setText("Código no válido · "+msg(e)));}});
    }

    private void confirmRedeem() {
        if(validatedCode.isEmpty())return;String code=validatedCode;new AlertDialog.Builder(this).setTitle("Confirmar canje").setMessage("El código quedará inutilizado. Confirma solo cuando hayas entregado el premio.").setNegativeButton("Cancelar",null).setPositiveButton("CANJEAR",(d,w)->{redeemButton.setEnabled(false);io.execute(()->{try{JSONObject r=QuickApi.post(core,core.action("promotionRedeem").put("code",code));runOnUiThread(()->{validatedCode="";redeemButton.setVisibility(View.GONE);redeemResult.setText("CANJEADO\n"+r.optString("prize","Premio"));toast("Premio canjeado");});}catch(Exception e){runOnUiThread(()->{redeemButton.setEnabled(true);alert("No se pudo canjear",msg(e));});}});}).show();
    }

    private void refreshInBackground() {
        if(!core.configured())return;io.execute(()->{try{JSONObject c=QuickApi.post(core,core.action("promotionList"));campaigns=items(c);saveCache("promo_campaigns",campaigns);}catch(Exception ignored){}try{JSONObject p=QuickApi.post(core,core.action("prizeList").put("includeNoPrize",true));prizes=items(p);saveCache("promo_prizes",prizes);}catch(Exception ignored){}});
    }

    private void readCache(){campaigns=cache("promo_campaigns");prizes=cache("promo_prizes");}
    private void saveCache(String key,JSONArray a){core.prefs().edit().putString(key,a==null?"[]":a.toString()).apply();}
    private JSONArray cache(String key){try{return new JSONArray(core.prefs().getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    private JSONArray items(JSONObject r){JSONArray a=r.optJSONArray("items");if(a==null)a=r.optJSONArray("data");if(a==null)a=r.optJSONArray("promotions");return a==null?new JSONArray():a;}
    private boolean active(JSONObject o){if(o==null)return false;if(o.has("active"))return o.optBoolean("active",false);String s=str(o,"ACTIVA","ESTADO","");return "sí".equalsIgnoreCase(s)||"si".equalsIgnoreCase(s)||"true".equalsIgnoreCase(s)||"activa".equalsIgnoreCase(s);}
    private int countRealPrizes(){int n=0;for(int i=0;i<prizes.length();i++){JSONObject p=prizes.optJSONObject(i);if(p!=null&&!"P000".equals(id(p)))n++;}return n;}
    private String id(JSONObject o){if(o==null)return"";String s=o.optString("id","");if(s.isEmpty())s=o.optString("ID_PROMOCION",o.optString("ID_PREMIO",""));return s;}
    private String str(JSONObject o,String a,String b,String fallback){if(o==null)return fallback;String s=o.optString(a,"");if(s.isEmpty())s=o.optString(b,"");return s.isEmpty()?fallback:s;}
    private String cleanCode(String raw){if(raw==null)return"";String s=raw.trim();try{Uri u=Uri.parse(s);String q=u.getQueryParameter("code");if(q!=null&&!q.trim().isEmpty())s=q.trim();}catch(Exception ignored){}Matcher m=Pattern.compile("PR-[A-Za-z0-9-]+",Pattern.CASE_INSENSITIVE).matcher(s);if(m.find())return m.group().toUpperCase(Locale.ROOT);return s;}

    private void clear(String s){page.removeAllViews();subtitle.setText(s);}
    private void back(String s,Runnable r){LinearLayout x=row();Button b=secondary("‹ ATRÁS");b.setOnClickListener(v->r.run());x.addView(b,new LinearLayout.LayoutParams(dp(105),dp(48)));TextView t=text(s,20,Color.rgb(20,20,20),true);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(10),0,0,0);x.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(x);}
    private TextView title(String s){TextView t=text(s,25,Color.rgb(20,20,20),true);t.setPadding(0,dp(14),0,dp(5));return t;}
    private TextView paragraph(String s){TextView t=text(s,14,Color.DKGRAY,false);t.setLineSpacing(0,1.15f);return t;}
    private TextView empty(String s){TextView t=paragraph(s);t.setPadding(dp(12),dp(14),dp(12),dp(14));t.setBackground(box(Color.WHITE,11,Color.rgb(220,220,220),1));return t;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(box(Color.WHITE,14,Color.rgb(218,216,212),1));return c;}
    private Button action(String title,String sub){Button b=new Button(this);b.setAllCaps(false);b.setText(title+"\n"+sub);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setPadding(dp(16),dp(7),dp(16),dp(7));b.setTextColor(Color.WHITE);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(box(Color.rgb(15,15,15),14,Color.TRANSPARENT,0));b.setMinHeight(dp(80));return b;}
    private Button primary(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.WHITE);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(box(Color.rgb(15,15,15),12,Color.TRANSPARENT,0));b.setMinHeight(dp(52));return b;}
    private Button secondary(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextColor(Color.rgb(20,20,20));b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setBackground(box(Color.WHITE,12,Color.rgb(195,195,195),1));b.setMinHeight(dp(50));return b;}
    private EditText field(LinearLayout host,String label,String value){TextView l=text(label,13,Color.DKGRAY,true);l.setPadding(0,dp(9),0,dp(4));host.addView(l);EditText e=input(value);host.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));return e;}
    private EditText input(String value){EditText e=new EditText(this);e.setText(value);e.setTextSize(16);e.setTextColor(Color.rgb(20,20,20));e.setPadding(dp(12),0,dp(12),0);e.setBackground(box(Color.WHITE,11,Color.rgb(205,205,205),1));return e;}
    private Spinner spinner(LinearLayout host,String label,String[] values,String selected){TextView l=text(label,13,Color.DKGRAY,true);l.setPadding(0,dp(9),0,dp(4));host.addView(l);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));host.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));for(int i=0;i<s.getCount();i++)if(String.valueOf(s.getItemAtPosition(i)).equals(selected)){s.setSelection(i);break;}return s;}
    private String selected(Spinner s){return s==null||s.getSelectedItem()==null?"":String.valueOf(s.getSelectedItem());}
    private String val(EditText e){return e==null?"":e.getText().toString().trim();}
    private double number(String s,double f){try{return Double.parseDouble(s.replace(',','.'));}catch(Exception e){return f;}}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=m;return p;}
    private LinearLayout.LayoutParams bottom(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=m;return p;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable box(int fill,int radius,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(sw>0)d.setStroke(dp(sw),stroke);return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void alert(String t,String m){if(!isFinishing())new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Aceptar",null).show();}
    private String msg(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}
}
