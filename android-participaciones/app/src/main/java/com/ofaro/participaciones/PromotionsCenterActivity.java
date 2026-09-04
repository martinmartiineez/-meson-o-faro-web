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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Centro de promociones de producción. */
public class PromotionsCenterActivity extends Activity {
    private static final String CACHE_CAMPAIGNS="promo_campaigns";
    private static final String CACHE_PRIZES="promo_prizes";
    private static final DateTimeFormatter DATE_FMT=DateTimeFormatter.ofPattern("yyyy-MM-dd",Locale.ROOT);
    private static final DateTimeFormatter TIME_FMT=DateTimeFormatter.ofPattern("HH:mm",Locale.ROOT);
    private static final String[] DAY_CODES={"Lun","Mar","Mié","Jue","Vie","Sáb","Dom"};
    private static final String[] MONTHS={"Ene","Feb","Mar","Abr","May","Jun","Jul","Ago","Sep","Oct","Nov","Dic"};

    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private AppCore core;
    private LinearLayout page;
    private TextView subtitle;
    private JSONArray campaigns=new JSONArray(),prizes=new JSONArray();
    private EditText redeemInput;
    private TextView redeemResult;
    private Button redeemButton;
    private String validatedCode="";

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        core=new AppCore(this);
        campaigns=core.cachedArray(CACHE_CAMPAIGNS);
        prizes=core.cachedArray(CACHE_PRIZES);
        setContentView(buildRoot());
        String mode=getIntent().getStringExtra("mode");
        if("redeem".equals(mode))showRedeem();else showHome();
        refreshReferenceData();
    }

    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View buildRoot(){
        LinearLayout root=col();root.setBackgroundColor(Color.rgb(247,246,243));
        LinearLayout head=col();head.setPadding(dp(18),dp(17),dp(18),dp(14));head.setBackgroundColor(Color.rgb(15,15,15));
        head.addView(text("O FARO",24,Color.WHITE,true));subtitle=text("Promociones",13,Color.LTGRAY,false);head.addView(subtitle);root.addView(head);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);page=col();page.setPadding(dp(14),dp(15),dp(14),dp(34));scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
    }

    private void showHome(){
        clear("Promociones");back("Centro de promociones",this::finish);
        page.addView(title("Promociones"));
        page.addView(paragraph("QR reales, campañas, horarios, premios y canjes desde una sola pantalla."));
        LinearLayout quick=row();
        Button qr=tile("SACAR QR\nREAL");qr.setOnClickListener(v->showQrList());
        Button redeem=tile("CANJEAR\nPREMIO");redeem.setOnClickListener(v->showRedeem());
        quick.addView(qr,weight());quick.addView(redeem,weight());page.addView(quick,topWrap(dp(8)));
        Button c=action("CAMPAÑAS","Crear, programar, configurar y activar");c.setOnClickListener(v->showCampaigns());page.addView(c,top(dp(10)));
        Button p=action("PREMIOS","Stock, condiciones y texto del ticket");p.setOnClickListener(v->showPrizes());page.addView(p,top(dp(9)));
        Button h=secondary("HISTORIAL Y RESULTADOS");h.setOnClickListener(v->showHistory());page.addView(h,top(dp(9)));
        int active=0;for(int i=0;i<campaigns.length();i++){JSONObject x=campaigns.optJSONObject(i);if(x!=null&&x.optBoolean("active",false))active++;}
        page.addView(infoCard(active+" campañas activas · "+availablePrizeCount()+" premios disponibles"),top(dp(14)));
    }

    private void showCampaigns(){
        clear("Campañas");back("Campañas",this::showHome);
        LinearLayout top=row();top.addView(title("Campañas"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        Button add=primary("+ NUEVA");add.setOnClickListener(v->createCampaign());top.addView(add,new LinearLayout.LayoutParams(dp(110),dp(50)));page.addView(top);
        TextView status=text(campaigns.length()>0?"Mostrando copia local · actualizando…":"Actualizando…",13,Color.GRAY,false);page.addView(status);
        LinearLayout list=col();page.addView(list,topWrap(dp(8)));paintCampaigns(list);refreshCampaigns(status,list);
    }

    private void paintCampaigns(LinearLayout list){
        list.removeAllViews();if(campaigns.length()==0){list.addView(empty("No hay campañas. Pulsa + Nueva para crear la primera."));return;}
        for(int i=0;i<campaigns.length();i++){
            JSONObject c=campaigns.optJSONObject(i);if(c==null)continue;
            LinearLayout card=card();String name=c.optString("name","Promoción"),type=c.optString("type","Promoción");
            boolean active=c.optBoolean("active",false),playable=c.optBoolean("isPlayable",false);
            card.addView(text(type.toUpperCase(Locale.ROOT),11,Color.GRAY,true));card.addView(text(name,20,Color.rgb(20,20,20),true));
            String state=active?(playable?"● ACTIVA AHORA":"● ACTIVA · fuera de horario"):"○ PAUSADA";
            card.addView(text(state,13,active?Color.rgb(25,105,55):Color.rgb(145,80,30),true));
            card.addView(text(scheduleSummary(c),12,Color.DKGRAY,false));
            JSONObject stats=c.optJSONObject("stats");if(stats!=null)card.addView(text(stats.optInt("plays",0)+" jugadas · "+stats.optInt("winners",0)+" premios",12,Color.DKGRAY,false));
            Button config=secondary("ABRIR Y CONFIGURAR");config.setOnClickListener(v->openCampaign(c));card.addView(config,top(dp(8)));
            if(active){Button q=secondary("GENERAR QR REAL");q.setOnClickListener(v->issueRealQr(c,q));card.addView(q,top(dp(6)));}
            list.addView(card,bottom(dp(9)));
        }
    }

    private void refreshCampaigns(TextView status,LinearLayout list){
        if(!core.configured()){status.setText("Servidor sin configurar · datos locales");return;}
        io.execute(()->{try{JSONObject r=core.post(core.action("promotionList"));campaigns=items(r);core.saveArray(CACHE_CAMPAIGNS,campaigns);runOnUiThread(()->{status.setText("Actualizado ahora");paintCampaigns(list);});}
        catch(Exception e){runOnUiThread(()->status.setText(campaigns.length()>0?"Sin conexión · usando copia local":"No se pudo cargar · "+msg(e)));}});
    }

    private void createCampaign(){
        LinearLayout box=col();box.setPadding(dp(15),dp(2),dp(15),dp(8));
        EditText name=field(box,"Nombre de la promoción","");Spinner type=spinner(box,"Tipo",new String[]{"Ruleta","Rasca","Premio directo","Código premiado"},"Ruleta");
        EditText prob=field(box,"Probabilidad general de premio (%)","20");prob.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(paragraph("Se crea pausada. Después podrás elegir horario, días y premios antes de activarla."));
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Nueva promoción").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("CREAR",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String n=val(name);double pct=dbl(val(prob),20);if(n.length()<2){toast("Escribe un nombre");return;}if(pct<0||pct>100){toast("Probabilidad entre 0 y 100");return;}
            Button save=d.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);JSONObject c=new JSONObject();
            try{String t=selected(type);c.put("name",n).put("description","").put("type",t).put("state","Pausada").put("active",false)
                    .put("startDate","").put("endDate","").put("startTime","00:00").put("endTime","23:59")
                    .put("activeDays","Lun,Mar,Mié,Jue,Vie,Sáb,Dom").put("winProbability",pct)
                    .put("winMessage","¡Enhorabuena! Te ha tocado {{premio}}.").put("loseMessage","Esta vez no ha habido premio.")
                    .put("ticketTemplate",defaultTemplate(t)).put("totalLimit","").put("clientLimit",1).put("dailyLimit",500)
                    .put("requiresCode","Código premiado".equals(t)).put("order",999);}catch(Exception ignored){}
            io.execute(()->{try{JSONObject r=core.post(core.action("promotionSave").put("campaign",c));JSONObject saved=r.optJSONObject("campaign");String id=saved==null?r.optString("id",""):saved.optString("id","");if(id.isEmpty())throw new Exception("El servidor no devolvió el ID de la promoción.");JSONObject fresh=core.post(core.action("promotionGet").put("id",id));saveDetail(id,fresh);JSONObject fc=fresh.optJSONObject("campaign");runOnUiThread(()->{d.dismiss();toast("Promoción creada");openCampaign(fc!=null?fc:saved);});}
            catch(Exception e){runOnUiThread(()->{save.setEnabled(true);alert("No se pudo crear",msg(e));});}});
        }));d.show();
    }

    private void openCampaign(JSONObject c){
        if(c==null){showCampaigns();return;}clear("Configurar");back(c.optString("name","Promoción"),this::showCampaigns);
        TextView status=text("Abriendo configuración…",13,Color.GRAY,false);page.addView(status);String id=c.optString("id","");JSONObject cached=detailCache(id);if(cached!=null)paintCampaignDetail(cached,status);
        if(!core.configured()){status.setText(cached!=null?"Datos guardados · sin servidor":"Servidor sin configurar");return;}
        io.execute(()->{try{JSONObject detail=core.post(core.action("promotionGet").put("id",id));saveDetail(id,detail);runOnUiThread(()->paintCampaignDetail(detail,status));}
        catch(Exception e){runOnUiThread(()->status.setText(cached!=null?"Sin conexión · mostrando copia local":"No se pudo abrir · "+msg(e)));}});
    }

    private void paintCampaignDetail(JSONObject detail,TextView status){
        JSONObject c=detail.optJSONObject("campaign");if(c==null)return;status.setText("Configuración sincronizada");while(page.getChildCount()>2)page.removeViewAt(2);
        boolean basics=PromoRules.basicsReady(c),distribution=PromoRules.distributionReady(detail),active=c.optBoolean("active",false);
        LinearLayout summary=card();summary.addView(text(c.optString("type","Promoción").toUpperCase(Locale.ROOT),11,Color.GRAY,true));summary.addView(text(c.optString("name","Promoción"),22,Color.rgb(18,18,18),true));
        summary.addView(text(active?"● ACTIVA":"○ PAUSADA",13,active?Color.rgb(25,105,55):Color.rgb(145,80,30),true));summary.addView(text(scheduleSummary(c),13,Color.DKGRAY,false));
        summary.addView(text((basics?"✓ Datos básicos":"✕ Datos básicos")+" · "+(distribution?"✓ Premios configurados":"✕ Falta reparto de premios"),12,distribution?Color.rgb(30,95,55):Color.rgb(145,80,30),false));page.addView(summary,topWrap(dp(8)));

        Button data=action("DATOS, FECHAS Y HORARIOS","Selectores de rueda · guardado verificado en servidor");data.setOnClickListener(v->editCampaign(c));page.addView(data,top(dp(10)));
        Button dist=action("PREMIOS Y REPARTO",distribution?"Configuración válida · revisar":"Obligatorio antes de activar");dist.setOnClickListener(v->configureDistribution(detail));page.addView(dist,top(dp(9)));
        Button qr=secondary("QR REAL · PREVISUALIZAR E IMPRIMIR");qr.setEnabled(active);qr.setOnClickListener(v->issueRealQr(c,qr));page.addView(qr,top(dp(9)));
        if(!active){TextView note=text("Activa la promoción para generar una invitación QR real. Así el QR de la previsualización nunca será ficticio.",12,Color.GRAY,false);page.addView(note,top(dp(5)));}
        Button state=primary(active?"PAUSAR PROMOCIÓN":"ACTIVAR PROMOCIÓN");state.setEnabled(active||(basics&&distribution));state.setOnClickListener(v->changeState(c,!active,state));page.addView(state,top(dp(12)));
        if(!active){Button del=ghost("ELIMINAR PROMOCIÓN");del.setOnClickListener(v->deleteCampaign(c));page.addView(del,top(dp(8)));}
    }

    private void editCampaign(JSONObject c){
        LinearLayout box=col();box.setPadding(dp(15),dp(3),dp(15),dp(10));
        EditText name=field(box,"Nombre",c.optString("name",""));EditText desc=multi(box,"Descripción",c.optString("description",""),2);
        Spinner type=spinner(box,"Tipo",new String[]{"Ruleta","Rasca","Premio directo","Código premiado"},c.optString("type","Ruleta"));
        EditText prob=field(box,"Probabilidad general de ganar (%)",fmt(c.optDouble("winProbability",0)));prob.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(section("VIGENCIA"));
        EditText start=dateField(box,"Fecha de inicio · opcional",c.optString("startDate",""));
        EditText end=dateField(box,"Fecha de fin · opcional",c.optString("endDate",""));
        EditText startTime=timeField(box,"Hora de inicio",c.optString("startTime","00:00"));
        EditText endTime=timeField(box,"Hora de fin",c.optString("endTime","23:59"));
        box.addView(section("DÍAS ACTIVOS"));
        LinearLayout daysWrap=col();List<CheckBox> dayChecks=new ArrayList<>();
        LinearLayout r1=row(),r2=row();for(int i=0;i<DAY_CODES.length;i++){CheckBox cb=new CheckBox(this);cb.setText(DAY_CODES[i]);cb.setChecked(dayEnabled(c.optString("activeDays",""),DAY_CODES[i]));dayChecks.add(cb);(i<4?r1:r2).addView(cb,new LinearLayout.LayoutParams(0,dp(48),1));}daysWrap.addView(r1);daysWrap.addView(r2);box.addView(daysWrap);
        box.addView(section("LÍMITES"));EditText client=field(box,"Máximo por cliente",String.valueOf(c.optInt("clientLimit",1)));client.setInputType(InputType.TYPE_CLASS_NUMBER);EditText daily=field(box,"Máximo al día",String.valueOf(c.optInt("dailyLimit",500)));daily.setInputType(InputType.TYPE_CLASS_NUMBER);
        box.addView(section("MENSAJES Y TICKET"));EditText win=multi(box,"Mensaje ganador",c.optString("winMessage","¡Enhorabuena! Te ha tocado {{premio}}."),2);EditText lose=multi(box,"Mensaje sin premio",c.optString("loseMessage","Esta vez no ha habido premio."),2);Spinner template=spinner(box,"Plantilla del ticket",ticketTemplates(),c.optString("ticketTemplate",defaultTemplate(c.optString("type","Ruleta"))));
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("Datos y horario").setView(scroll).setNegativeButton("Cancelar",null).setPositiveButton("GUARDAR Y VERIFICAR",null).create();
        d.setOnShowListener(x->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String sd=val(start),ed=val(end),st=val(startTime),et=val(endTime);double pct=dbl(val(prob),-1);
            if(val(name).length()<2){toast("Escribe un nombre");return;}if(pct<0||pct>100){toast("Probabilidad entre 0 y 100");return;}
            if(!validDateRange(sd,ed)){alert("Revisa las fechas","La fecha de fin no puede ser anterior a la fecha de inicio.");return;}
            if(!validTimeRange(st,et)){alert("Revisa el horario","La hora de fin debe ser igual o posterior a la hora de inicio.");return;}
            String days=daysValue(dayChecks);if(days.isEmpty()){alert("Días activos","Selecciona al menos un día de la semana.");return;}
            Button save=d.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);save.setText("GUARDANDO…");JSONObject n=new JSONObject();
            try{String t=selected(type);n.put("id",c.optString("id","")).put("name",val(name)).put("description",val(desc)).put("type",t).put("state",c.optString("state","Pausada")).put("active",c.optBoolean("active",false))
                    .put("startDate",sd).put("endDate",ed).put("startTime",st).put("endTime",et).put("activeDays",days).put("winProbability",pct)
                    .put("winMessage",val(win)).put("loseMessage",val(lose)).put("ticketTemplate",selected(template)).put("totalLimit",c.opt("totalLimit"))
                    .put("clientLimit",Math.max(1,integer(val(client),1))).put("dailyLimit",Math.max(1,integer(val(daily),500))).put("requiresCode","Código premiado".equals(t))
                    .put("allowedTerminals",c.optString("allowedTerminals","")).put("order",c.optInt("order",999));}catch(Exception ignored){}
            io.execute(()->{try{
                JSONObject saved=core.post(core.action("promotionSave").put("campaign",n));String id=c.optString("id",saved.optString("id",""));if(id.isEmpty())id=saved.optString("id","");
                JSONObject fresh=core.post(core.action("promotionGet").put("id",id));JSONObject fc=fresh.optJSONObject("campaign");if(fc==null)throw new Exception("El servidor no devolvió la promoción después de guardarla.");
                verifySchedule(fc,sd,ed,st,et,days);saveDetail(id,fresh);
                JSONObject finalFc=fc;runOnUiThread(()->{d.dismiss();toast("Guardado y verificado en servidor");openCampaign(finalFc);});
            }catch(Exception e){runOnUiThread(()->{save.setEnabled(true);save.setText("GUARDAR Y VERIFICAR");alert("No se pudo confirmar el guardado",msg(e));});}});
        }));d.show();
    }

    private EditText dateField(LinearLayout parent,String label,String value){
        TextView l=text(label,13,Color.DKGRAY,true);l.setPadding(0,dp(9),0,dp(4));parent.addView(l);LinearLayout row=row();
        EditText e=input(value);e.setFocusable(false);e.setClickable(true);e.setHint("Sin fecha");e.setOnClickListener(v->showDateWheel(e));row.addView(e,new LinearLayout.LayoutParams(0,dp(52),1));
        Button clear=secondary("BORRAR");clear.setTextSize(11);clear.setOnClickListener(v->e.setText(""));LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(92),dp(52));bp.leftMargin=dp(6);row.addView(clear,bp);parent.addView(row);return e;
    }

    private EditText timeField(LinearLayout parent,String label,String value){
        TextView l=text(label,13,Color.DKGRAY,true);l.setPadding(0,dp(9),0,dp(4));parent.addView(l);EditText e=input(normalizeTime(value,"00:00"));e.setFocusable(false);e.setClickable(true);e.setOnClickListener(v->showTimeWheel(e));parent.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(52)));return e;
    }

    private void showDateWheel(EditText target){
        LocalDate initial=parseDate(val(target),LocalDate.now());LinearLayout body=row();body.setPadding(dp(10),dp(12),dp(10),dp(8));body.setGravity(Gravity.CENTER);
        NumberPicker day=picker(1,31,initial.getDayOfMonth());NumberPicker month=picker(1,12,initial.getMonthValue());month.setDisplayedValues(MONTHS);NumberPicker year=picker(2024,LocalDate.now().getYear()+12,initial.getYear());
        body.addView(wheel("DÍA",day),weight());body.addView(wheel("MES",month),weight());body.addView(wheel("AÑO",year),weight());
        Runnable update=()->{int max=YearMonth.of(year.getValue(),month.getValue()).lengthOfMonth();day.setMaxValue(max);if(day.getValue()>max)day.setValue(max);};month.setOnValueChangedListener((p,o,n)->update.run());year.setOnValueChangedListener((p,o,n)->update.run());update.run();
        new AlertDialog.Builder(this).setTitle("Seleccionar fecha").setView(body).setNegativeButton("Cancelar",null).setPositiveButton("ACEPTAR",(d,w)->target.setText(LocalDate.of(year.getValue(),month.getValue(),day.getValue()).format(DATE_FMT))).show();
    }

    private void showTimeWheel(EditText target){
        LocalTime initial=parseTime(val(target),LocalTime.of(0,0));LinearLayout body=row();body.setPadding(dp(28),dp(12),dp(28),dp(8));body.setGravity(Gravity.CENTER);
        NumberPicker hour=picker(0,23,initial.getHour()),minute=picker(0,59,initial.getMinute());hour.setFormatter(v->String.format(Locale.ROOT,"%02d",v));minute.setFormatter(v->String.format(Locale.ROOT,"%02d",v));
        body.addView(wheel("HORA",hour),weight());body.addView(wheel("MIN",minute),weight());
        new AlertDialog.Builder(this).setTitle("Seleccionar hora").setView(body).setNegativeButton("Cancelar",null).setPositiveButton("ACEPTAR",(d,w)->target.setText(LocalTime.of(hour.getValue(),minute.getValue()).format(TIME_FMT))).show();
    }

    private NumberPicker picker(int min,int max,int value){NumberPicker p=new NumberPicker(this);p.setMinValue(min);p.setMaxValue(max);p.setValue(Math.max(min,Math.min(max,value)));p.setWrapSelectorWheel(true);return p;}
    private LinearLayout wheel(String label,NumberPicker p){LinearLayout c=col();TextView l=text(label,11,Color.GRAY,true);l.setGravity(Gravity.CENTER);c.addView(l);c.addView(p,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(150)));return c;}

    private void verifySchedule(JSONObject c,String sd,String ed,String st,String et,String days)throws Exception{
        List<String> wrong=new ArrayList<>();if(!sd.equals(c.optString("startDate","")))wrong.add("fecha inicio");if(!ed.equals(c.optString("endDate","")))wrong.add("fecha fin");if(!st.equals(c.optString("startTime","")))wrong.add("hora inicio");if(!et.equals(c.optString("endTime","")))wrong.add("hora fin");if(!normDays(days).equals(normDays(c.optString("activeDays",""))))wrong.add("días activos");
        if(!wrong.isEmpty())throw new Exception("El servidor no confirmó: "+join(wrong)+". Los cambios no se darán por guardados.");
    }

    private void configureDistribution(JSONObject detail){JSONObject c=detail.optJSONObject("campaign");if(c==null)return;if("Ruleta".equalsIgnoreCase(c.optString("type")))wheelEditor(detail);else directPrizeEditor(detail);}

    private static final class SegmentRow{LinearLayout root;EditText label,pct;Spinner prize;}
    private void wheelEditor(JSONObject detail){
        JSONObject c=detail.optJSONObject("campaign");JSONArray segments=detail.optJSONArray("segments"),available=detail.optJSONArray("prizes");if(available!=null){prizes=available;core.saveArray(CACHE_PRIZES,prizes);}clear("Ruleta");back("Sectores · "+c.optString("name",""),()->openCampaign(c));
        page.addView(paragraph("Cada sector tiene un texto, un resultado y un porcentaje. La suma debe ser exactamente 100 %."));LinearLayout rows=col();page.addView(rows);List<SegmentRow> list=new ArrayList<>();if(segments!=null)for(int i=0;i<segments.length();i++)addSegment(rows,list,segments.optJSONObject(i));if(list.isEmpty())addSegment(rows,list,new JSONObject());
        Button add=secondary("+ AÑADIR SECTOR");add.setOnClickListener(v->addSegment(rows,list,new JSONObject()));page.addView(add,top(dp(8)));Button quick=secondary("CREAR REPARTO RÁPIDO");quick.setOnClickListener(v->quickWheel(c,rows,list));page.addView(quick,top(dp(7)));Button save=primary("GUARDAR RULETA");save.setOnClickListener(v->saveWheel(c,list,save));page.addView(save,top(dp(10)));
    }

    private void addSegment(LinearLayout host,List<SegmentRow> all,JSONObject old){
        SegmentRow r=new SegmentRow();r.root=card();r.label=field(r.root,"Texto del sector",old==null?"":old.optString("label",""));String pid=old==null?"P000":old.optString("prizeId","P000");r.prize=spinner(r.root,"Resultado",prizeChoices(true),prizeChoiceFor(pid));r.pct=field(r.root,"Porcentaje",old==null?"":fmt(old.optDouble("percentage",0)));r.pct.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);Button del=ghost("ELIMINAR SECTOR");del.setOnClickListener(v->{host.removeView(r.root);all.remove(r);});r.root.addView(del,top(dp(5)));host.addView(r.root,bottom(dp(8)));all.add(r);
    }

    private void quickWheel(JSONObject c,LinearLayout host,List<SegmentRow> rows){
        if(availablePrizeCount()==0){alert("Falta un premio","Crea al menos un premio activo y con stock antes de generar la ruleta.");return;}Spinner choose=new Spinner(this);String[] choices=prizeChoices(false);choose.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));
        new AlertDialog.Builder(this).setTitle("Premio principal").setMessage("Se crearán dos sectores: premio y sin premio, usando la probabilidad general.").setView(choose).setNegativeButton("Cancelar",null).setPositiveButton("CREAR",(d,w)->{host.removeAllViews();rows.clear();double win=Math.max(0,Math.min(100,c.optDouble("winProbability",20)));JSONObject a=new JSONObject(),b=new JSONObject();try{a.put("label",selected(choose)).put("prizeId",prizeIdFromChoice(selected(choose))).put("percentage",win);b.put("label","Sin premio").put("prizeId","P000").put("percentage",100-win);}catch(Exception ignored){}addSegment(host,rows,a);addSegment(host,rows,b);}).show();
    }

    private void saveWheel(JSONObject c,List<SegmentRow> rows,Button save){
        JSONArray out=new JSONArray();double total=0;int order=1;for(SegmentRow r:rows){double pct=dbl(val(r.pct),0);if(pct<=0)continue;String pid=prizeIdFromChoice(selected(r.prize));total+=pct;JSONObject x=new JSONObject();try{x.put("label",val(r.label).isEmpty()?("P000".equals(pid)?"Sin premio":prizeName(pid)):val(r.label)).put("prizeId",pid).put("percentage",pct).put("active",true).put("order",order++).put("resultType","P000".equals(pid)?"SIN_PREMIO":"PREMIO").put("style","premium");}catch(Exception ignored){}out.put(x);}
        if(out.length()==0||Math.abs(total-100)>0.009){alert("Revisa los porcentajes","La suma es "+fmt(total)+" %. Debe ser exactamente 100 %.");return;}save.setEnabled(false);io.execute(()->{try{core.post(core.action("promotionReplaceSegments").put("id",c.optString("id","")).put("segments",out));runOnUiThread(()->{toast("Ruleta guardada");openCampaign(c);});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);alert("No se pudo guardar",msg(e));});}});
    }

    private static final class PrizeRow{String id;CheckBox check;EditText pct;}
    private void directPrizeEditor(JSONObject detail){
        JSONObject c=detail.optJSONObject("campaign");JSONArray links=detail.optJSONArray("prizeLinks"),available=detail.optJSONArray("prizes");if(available!=null){prizes=available;core.saveArray(CACHE_PRIZES,prizes);}clear("Reparto");back("Premios · "+c.optString("name",""),()->openCampaign(c));page.addView(paragraph("Selecciona premios disponibles. Entre los seleccionados, los porcentajes deben sumar 100 %."));LinearLayout rows=col();page.addView(rows);List<PrizeRow> list=new ArrayList<>();
        for(int i=0;i<prizes.length();i++){JSONObject p=prizes.optJSONObject(i);if(!prizeAvailable(p))continue;JSONObject old=findLink(links,p.optString("id"));PrizeRow r=new PrizeRow();r.id=p.optString("id");LinearLayout line=card();r.check=new CheckBox(this);r.check.setText(p.optString("name","Premio")+remainingSuffix(p));r.check.setChecked(old!=null);line.addView(r.check);r.pct=field(line,"Porcentaje entre premios",old==null?"":fmt(old.optDouble("percentage",0)));r.pct.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);rows.addView(line,bottom(dp(8)));list.add(r);}
        if(list.isEmpty())page.addView(empty("No hay premios activos con stock disponible."));Button equal=secondary("REPARTIR IGUAL ENTRE SELECCIONADOS");equal.setOnClickListener(v->splitExactly(list));page.addView(equal,top(dp(8)));Button save=primary("GUARDAR REPARTO");save.setOnClickListener(v->savePrizeLinks(c,list,save));page.addView(save,top(dp(9)));
    }

    private void splitExactly(List<PrizeRow> list){List<PrizeRow> selected=new ArrayList<>();for(PrizeRow r:list)if(r.check.isChecked())selected.add(r);if(selected.isEmpty()){toast("Selecciona al menos un premio");return;}double used=0;for(int i=0;i<selected.size();i++){double value=i==selected.size()-1?100d-used:Math.floor((10000d/selected.size()))/100d;selected.get(i).pct.setText(fmt(value));used+=value;}}

    private void savePrizeLinks(JSONObject c,List<PrizeRow> list,Button save){
        JSONArray out=new JSONArray();double total=0;int order=1;for(PrizeRow r:list)if(r.check.isChecked()){double pct=dbl(val(r.pct),0);if(pct<=0){alert("Revisa el reparto","Los premios seleccionados necesitan un porcentaje mayor que 0.");return;}total+=pct;JSONObject x=new JSONObject();try{x.put("prizeId",r.id).put("percentage",pct).put("weight",pct).put("active",true).put("order",order++);}catch(Exception ignored){}out.put(x);}if(out.length()==0||Math.abs(total-100)>0.009){alert("Revisa el reparto","Los premios seleccionados deben sumar 100 %. Ahora suman "+fmt(total)+" %.");return;}save.setEnabled(false);io.execute(()->{try{core.post(core.action("promotionReplacePrizes").put("id",c.optString("id","")).put("prizes",out));runOnUiThread(()->{toast("Reparto guardado");openCampaign(c);});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);alert("No se pudo guardar",msg(e));});}});
    }

    private void changeState(JSONObject c,boolean activate,Button button){
        if(activate){button.setEnabled(false);io.execute(()->{try{JSONObject detail=core.post(core.action("promotionGet").put("id",c.optString("id","")));if(!PromoRules.distributionReady(detail))throw new Exception("Configura un reparto válido y al menos un premio disponible antes de activar.");core.post(core.action("promotionSetState").put("id",c.optString("id","")).put("state","Activa"));runOnUiThread(()->{toast("Promoción activada");showCampaigns();});}catch(Exception e){runOnUiThread(()->{button.setEnabled(true);alert("No se puede activar",msg(e));});}});return;}
        new AlertDialog.Builder(this).setTitle("Pausar promoción").setMessage("Los QR existentes dejarán de aceptar jugadas mientras esté pausada.").setNegativeButton("Cancelar",null).setPositiveButton("PAUSAR",(d,w)->{button.setEnabled(false);io.execute(()->{try{core.post(core.action("promotionSetState").put("id",c.optString("id","")).put("state","Pausada"));runOnUiThread(()->{toast("Promoción pausada");showCampaigns();});}catch(Exception e){runOnUiThread(()->{button.setEnabled(true);alert("No se pudo pausar",msg(e));});}});}).show();
    }

    private void deleteCampaign(JSONObject c){
        new AlertDialog.Builder(this).setTitle("Eliminar promoción").setMessage("Solo se eliminará si el servidor permite hacerlo. Esta acción no se puede deshacer.").setNegativeButton("Cancelar",null).setPositiveButton("ELIMINAR",(d,w)->io.execute(()->{try{core.post(core.action("promotionDelete").put("id",c.optString("id","")));runOnUiThread(()->{toast("Promoción eliminada");showCampaigns();});}catch(Exception e){runOnUiThread(()->alert("No se pudo eliminar",msg(e)));}})).show();
    }

    private void showQrList(){
        clear("QR reales");back("QR para clientes",this::showHome);page.addView(title("Generar invitación real"));page.addView(paragraph("Cada QR se registra en el servidor, dura 48 h y permite una sola jugada. Abrir el QR no lo consume; jugar sí."));int n=0;
        for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);if(c==null||!c.optBoolean("active",false))continue;n++;LinearLayout card=card();card.addView(text(c.optString("name","Promoción"),18,Color.rgb(20,20,20),true));card.addView(text(scheduleSummary(c),12,Color.DKGRAY,false));Button b=primary("GENERAR QR REAL");b.setOnClickListener(v->issueRealQr(c,b));card.addView(b,top(dp(8)));page.addView(card,bottom(dp(8)));}
        if(n==0)page.addView(empty("No hay promociones activas. Activa una campaña antes de emitir QR."));
    }

    private void issueRealQr(JSONObject c,Button b){
        if(!core.configured()){alert("Falta conexión","Configura Apps Script en Ajustes.");return;}if(!c.optBoolean("active",false)){alert("Promoción pausada","Actívala antes de generar una invitación real.");return;}String old=b.getText().toString();b.setEnabled(false);b.setText("GENERANDO INVITACIÓN…");
        io.execute(()->{try{JSONObject r=core.post(core.action("promoPublicIssue").put("id",c.optString("id","")).put("expiresHours",48));String url=r.optString("url","").trim(),token=r.optString("token","").trim();if(!url.startsWith("https://"))throw new Exception("El servidor no devolvió una URL segura para la invitación.");if(token.isEmpty())throw new Exception("El servidor no devolvió el token de la invitación.");
            runOnUiThread(()->{b.setEnabled(true);b.setText(old);Intent i=new Intent(this,TicketPreviewActivity.class);i.putExtra("title",c.optString("name","Promoción"));i.putExtra("subtitle",c.optString("type","").toUpperCase(Locale.ROOT));i.putExtra("body","ESCANEA PARA JUGAR\nQR REAL · UN SOLO USO · 48 H");i.putExtra("qr",url);i.putExtra("type","Promoción QR");i.putExtra("reference",token);i.putExtra("detail","Invitación real · "+c.optString("name",""));i.putExtra("template",c.optString("ticketTemplate",defaultTemplate(c.optString("type",""))));startActivity(i);});
        }catch(Exception e){runOnUiThread(()->{b.setEnabled(true);b.setText(old);alert("No se pudo generar el QR real",msg(e));});}});
    }

    private void showPrizes(){
        clear("Premios");back("Premios",this::showHome);LinearLayout top=row();top.addView(title("Premios"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));Button add=primary("+ NUEVO");add.setOnClickListener(v->editPrize(null));top.addView(add,new LinearLayout.LayoutParams(dp(110),dp(50)));page.addView(top);TextView status=text(prizes.length()>0?"Datos guardados · actualizando…":"Actualizando…",13,Color.GRAY,false);page.addView(status);LinearLayout list=col();page.addView(list,topWrap(dp(8)));paintPrizes(list);refreshPrizes(status,list);
    }

    private void paintPrizes(LinearLayout list){
        list.removeAllViews();int n=0;for(int i=0;i<prizes.length();i++){JSONObject p=prizes.optJSONObject(i);if(p==null||"P000".equals(p.optString("id")))continue;n++;LinearLayout c=card();c.addView(text(p.optString("name","Premio"),19,Color.rgb(20,20,20),true));String remaining=unlimited(p)?"sin límite":String.valueOf(p.optInt("remaining",0));c.addView(text((p.optBoolean("active",true)?"Activo":"Pausado")+" · restante: "+remaining+" · canjeados: "+p.optInt("redeemed",0),12,Color.DKGRAY,false));Button edit=secondary("EDITAR PREMIO");edit.setOnClickListener(v->editPrize(p));c.addView(edit,top(dp(7)));list.addView(c,bottom(dp(8)));}if(n==0)list.addView(empty("No hay premios. Crea uno con + Nuevo."));
    }

    private void refreshPrizes(TextView status,LinearLayout list){if(!core.configured()){status.setText("Servidor sin configurar · datos locales");return;}io.execute(()->{try{JSONObject r=core.post(core.action("promotionPrizeList"));prizes=items(r);core.saveArray(CACHE_PRIZES,prizes);runOnUiThread(()->{status.setText("Actualizado ahora");paintPrizes(list);});}catch(Exception e){runOnUiThread(()->status.setText(prizes.length()>0?"Sin conexión · copia local":"No se pudo cargar · "+msg(e)));}});}

    private void editPrize(JSONObject p){
        boolean isNew=p==null;JSONObject x=isNew?new JSONObject():p;LinearLayout box=col();box.setPadding(dp(15),dp(3),dp(15),dp(10));EditText name=field(box,"Nombre del premio",x.optString("name",""));EditText ticket=multi(box,"Texto del ticket",x.optString("ticketText",""),2);Spinner active=spinner(box,"Estado",new String[]{"Activo","Pausado"},x.optBoolean("active",true)?"Activo":"Pausado");EditText stock=field(box,"Stock · vacío = ilimitado",x.has("stock")&&!x.isNull("stock")?String.valueOf(x.opt("stock")):"");stock.setInputType(InputType.TYPE_CLASS_NUMBER);EditText type=field(box,"Tipo / categoría",x.optString("type","Consumición"));EditText value=field(box,"Valor orientativo",x.optString("value",""));EditText conditions=multi(box,"Condiciones de canje",x.optString("conditions",""),2);
        AlertDialog d=new AlertDialog.Builder(this).setTitle(isNew?"Nuevo premio":"Editar premio").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("GUARDAR",null).create();d.setOnShowListener(xx->d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(val(name).isEmpty()){toast("Escribe el nombre");return;}Button save=d.getButton(AlertDialog.BUTTON_POSITIVE);save.setEnabled(false);JSONObject prize=new JSONObject();try{prize.put("id",isNew?"":x.optString("id","")).put("name",val(name)).put("ticketText",val(ticket)).put("active","Activo".equals(selected(active))).put("weight",x.optDouble("weight",1)).put("stock",val(stock).isEmpty()?"":integer(val(stock),0)).put("startDate",x.optString("startDate","")).put("endDate",x.optString("endDate","")).put("order",x.optInt("order",999)).put("type",val(type)).put("conditions",val(conditions)).put("imageUrl",x.optString("imageUrl","")).put("value",val(value));}catch(Exception ignored){}io.execute(()->{try{core.post(core.action("promotionPrizeSave").put("prize",prize));runOnUiThread(()->{d.dismiss();toast("Premio guardado");showPrizes();});}catch(Exception e){runOnUiThread(()->{save.setEnabled(true);alert("No se pudo guardar",msg(e));});}});}));d.show();
    }

    private void showRedeem(){
        clear("Canjear");back("Canjear premio",this::showHome);page.addView(title("Validar y canjear"));page.addView(paragraph("Escanea el QR o introduce el código del ganador. Solo se habilita Canjear después de validarlo en el servidor."));Button scan=primary("ESCANEAR QR");scan.setOnClickListener(v->scan());page.addView(scan,top(dp(8)));redeemInput=input("");redeemInput.setHint("OF-XXXXX-XXXXX");page.addView(redeemInput,top(dp(10)));Button validate=secondary("VALIDAR CÓDIGO");validate.setOnClickListener(v->validateCode(val(redeemInput)));page.addView(validate,top(dp(8)));redeemResult=infoCard("Todavía no hay ningún código validado.");page.addView(redeemResult,top(dp(10)));redeemButton=primary("CONFIRMAR CANJE");redeemButton.setVisibility(View.GONE);redeemButton.setOnClickListener(v->confirmRedeem());page.addView(redeemButton,top(dp(9)));
    }

    private void scan(){IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(Collections.singletonList("QR_CODE"));i.setPrompt("Escanea el premio");i.setBeepEnabled(false);i.setOrientationLocked(true);i.initiateScan();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()!=null){String code=PromoRules.cleanCode(r.getContents());if(redeemInput==null)showRedeem();redeemInput.setText(code);validateCode(code);}return;}super.onActivityResult(requestCode,resultCode,data);}

    private void validateCode(String raw){String code=PromoRules.cleanCode(raw);validatedCode="";if(code.isEmpty()){toast("Código vacío");return;}redeemResult.setText("Validando…");redeemButton.setVisibility(View.GONE);io.execute(()->{try{JSONObject r=core.post(core.action("promotionValidate").put("code",code));boolean can=r.optBoolean("canRedeem",false);String finalCode=r.optString("code",code);runOnUiThread(()->{validatedCode=can?finalCode:"";redeemResult.setText("Premio: "+dash(r.optString("prize",""))+"\nEstado: "+dash(r.optString("state",""))+"\nPromoción: "+dash(r.optString("promotionName",""))+"\nCódigo: "+finalCode+(can?"\n\n✓ LISTO PARA CANJEAR":"\n\nEste código no se puede canjear."));redeemResult.setTextColor(can?Color.rgb(25,105,55):Color.rgb(145,65,40));redeemButton.setVisibility(can?View.VISIBLE:View.GONE);});}catch(Exception e){runOnUiThread(()->{redeemResult.setText("No válido · "+msg(e));redeemResult.setTextColor(Color.rgb(145,65,40));});}});}

    private void confirmRedeem(){if(validatedCode.isEmpty())return;String code=validatedCode;new AlertDialog.Builder(this).setTitle("Confirmar canje").setMessage("Confirma solo cuando vayas a entregar el premio. El código quedará inutilizado.").setNegativeButton("Cancelar",null).setPositiveButton("CANJEAR",(d,w)->{redeemButton.setEnabled(false);io.execute(()->{try{JSONObject r=core.post(core.action("promotionRedeem").put("code",code));runOnUiThread(()->{validatedCode="";redeemResult.setText("✓ CANJEADO\n"+r.optString("prize","Premio")+"\nCódigo: "+r.optString("code",code));redeemResult.setTextColor(Color.rgb(25,105,55));redeemButton.setVisibility(View.GONE);toast("Canje registrado");});}catch(Exception e){runOnUiThread(()->{redeemButton.setEnabled(true);alert("No se pudo canjear",msg(e));});}});}).show();}

    private void showHistory(){
        clear("Historial");back("Historial",this::showHome);page.addView(title("Últimos resultados"));TextView status=text("Actualizando…",13,Color.GRAY,false);page.addView(status);LinearLayout list=col();page.addView(list,topWrap(dp(8)));JSONArray cached=core.cachedArray("promo_history");paintHistory(list,cached);if(!core.configured()){status.setText("Servidor sin configurar");return;}io.execute(()->{try{JSONObject r=core.post(core.action("promotionHistory").put("limit",100));JSONArray a=items(r);core.saveArray("promo_history",a);runOnUiThread(()->{status.setText("Últimos "+a.length()+" resultados");paintHistory(list,a);});}catch(Exception e){runOnUiThread(()->status.setText(cached.length()>0?"Sin conexión · historial guardado":"No se pudo cargar · "+msg(e)));}});
    }

    private void paintHistory(LinearLayout list,JSONArray a){list.removeAllViews();if(a==null||a.length()==0){list.addView(empty("Todavía no hay resultados."));return;}for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r==null)continue;LinearLayout c=card();String prize=r.optString("prize","");c.addView(text(r.optString("promotionName","Promoción"),16,Color.rgb(20,20,20),true));c.addView(text((prize.isEmpty()?"Sin premio":prize)+" · "+r.optString("state","")+"\n"+r.optString("createdAt","")+" · "+r.optString("code",""),12,Color.DKGRAY,false));list.addView(c,bottom(dp(7)));}}

    private void refreshReferenceData(){if(!core.configured())return;io.execute(()->{try{JSONObject r=core.post(core.action("promotionList"));campaigns=items(r);core.saveArray(CACHE_CAMPAIGNS,campaigns);}catch(Exception ignored){}});io.execute(()->{try{JSONObject r=core.post(core.action("promotionPrizeList"));prizes=items(r);core.saveArray(CACHE_PRIZES,prizes);}catch(Exception ignored){}});}

    private JSONObject detailCache(String id){if(id==null||id.isEmpty())return null;try{return new JSONObject(core.prefs().getString("promo_detail_"+id,""));}catch(Exception e){return null;}}
    private void saveDetail(String id,JSONObject detail){if(id!=null&&!id.isEmpty()&&detail!=null)core.prefs().edit().putString("promo_detail_"+id,detail.toString()).apply();}
    private JSONObject findLink(JSONArray links,String prizeId){if(links==null)return null;for(int i=0;i<links.length();i++){JSONObject x=links.optJSONObject(i);if(x!=null&&prizeId.equals(x.optString("prizeId")))return x;}return null;}

    private String[] prizeChoices(boolean includeNoPrize){List<String> out=new ArrayList<>();if(includeNoPrize)out.add("P000 · Sin premio");for(int i=0;i<prizes.length();i++){JSONObject p=prizes.optJSONObject(i);if(!prizeAvailable(p))continue;out.add(p.optString("id")+" · "+p.optString("name","Premio"));}return out.toArray(new String[0]);}
    private String prizeChoiceFor(String id){if(id==null||id.isEmpty()||"P000".equals(id))return"P000 · Sin premio";for(String s:prizeChoices(true))if(s.startsWith(id+" ·"))return s;return"P000 · Sin premio";}
    private String prizeIdFromChoice(String choice){if(choice==null)return"P000";int i=choice.indexOf(" ·");return(i>0?choice.substring(0,i):choice).trim();}
    private String prizeName(String id){if("P000".equals(id))return"Sin premio";for(int i=0;i<prizes.length();i++){JSONObject p=prizes.optJSONObject(i);if(p!=null&&id.equals(p.optString("id")))return p.optString("name","Premio");}return"Premio";}
    private boolean prizeAvailable(JSONObject p){return p!=null&&!"P000".equals(p.optString("id"))&&p.optBoolean("active",true)&&(unlimited(p)||p.optInt("remaining",0)>0);}
    private boolean unlimited(JSONObject p){Object v=p==null?null:p.opt("remaining");return v==null||v==JSONObject.NULL||String.valueOf(v).trim().isEmpty();}
    private int availablePrizeCount(){int n=0;for(int i=0;i<prizes.length();i++)if(prizeAvailable(prizes.optJSONObject(i)))n++;return n;}
    private String remainingSuffix(JSONObject p){return unlimited(p)?" · sin límite":" · quedan "+p.optInt("remaining",0);}

    private String defaultTemplate(String type){if("Ruleta".equals(type))return"Ruleta QR";if("Rasca".equals(type))return"Rasca QR";if("Código premiado".equals(type))return"Entrada QR";return"Promoción Premium";}
    private String[] ticketTemplates(){return new String[]{"Promoción Premium","Promoción Clásica","Entrada QR","Ruleta QR","Rasca QR","Premio Canjeable","Cupón Descuento","Minimal Premium"};}

    private String scheduleSummary(JSONObject c){String sd=c.optString("startDate","").trim(),ed=c.optString("endDate","").trim(),st=c.optString("startTime","00:00"),et=c.optString("endTime","23:59"),days=c.optString("activeDays","");String date=sd.isEmpty()&&ed.isEmpty()?"Sin límite de fechas":(sd.isEmpty()?"Hasta "+ed:ed.isEmpty()?"Desde "+sd:sd+" → "+ed);return date+"\n"+st+"–"+et+(days.isEmpty()?"":" · "+days);}
    private boolean validDateRange(String start,String end){try{return start.isEmpty()||end.isEmpty()||!LocalDate.parse(end,DATE_FMT).isBefore(LocalDate.parse(start,DATE_FMT));}catch(Exception e){return false;}}
    private boolean validTimeRange(String start,String end){try{return !LocalTime.parse(end,TIME_FMT).isBefore(LocalTime.parse(start,TIME_FMT));}catch(Exception e){return false;}}
    private LocalDate parseDate(String s,LocalDate fallback){try{return LocalDate.parse(s,DATE_FMT);}catch(Exception e){return fallback;}}
    private LocalTime parseTime(String s,LocalTime fallback){try{return LocalTime.parse(s,TIME_FMT);}catch(Exception e){return fallback;}}
    private String normalizeTime(String s,String fallback){return parseTime(s,parseTime(fallback,LocalTime.MIDNIGHT)).format(TIME_FMT);}
    private boolean dayEnabled(String value,String day){String n=norm(value),d=norm(day);for(String x:n.split(","))if(x.trim().startsWith(d.substring(0,Math.min(3,d.length()))))return true;return false;}
    private String daysValue(List<CheckBox> checks){List<String> out=new ArrayList<>();for(int i=0;i<checks.size()&&i<DAY_CODES.length;i++)if(checks.get(i).isChecked())out.add(DAY_CODES[i]);return join(out);}
    private String normDays(String s){List<String> out=new ArrayList<>();for(String x:s.split(",")){String n=norm(x).trim();if(!n.isEmpty())out.add(n.substring(0,Math.min(3,n.length())));}Collections.sort(out);return join(out);}
    private String norm(String s){return Normalizer.normalize(s==null?"":s,Normalizer.Form.NFD).replaceAll("\\p{M}+","").toLowerCase(Locale.ROOT);}
    private String join(List<String> a){StringBuilder b=new StringBuilder();for(String s:a){if(b.length()>0)b.append(",");b.append(s);}return b.toString();}

    private void clear(String s){page.removeAllViews();subtitle.setText(s);}
    private void back(String s,Runnable r){LinearLayout x=row();Button b=secondary("‹ ATRÁS");b.setOnClickListener(v->r.run());x.addView(b,new LinearLayout.LayoutParams(dp(105),dp(48)));TextView t=text(s,20,Color.rgb(20,20,20),true);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(10),0,0,0);x.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(x);}
    private TextView title(String s){TextView t=text(s,25,Color.rgb(18,18,18),true);t.setPadding(0,dp(7),0,dp(8));return t;}
    private TextView paragraph(String s){TextView t=text(s,14,Color.DKGRAY,false);t.setLineSpacing(0,1.14f);t.setPadding(0,0,0,dp(8));return t;}
    private TextView section(String s){TextView t=text(s,12,Color.GRAY,true);t.setPadding(0,dp(17),0,dp(4));return t;}
    private TextView empty(String s){TextView t=infoCard(s);t.setTextColor(Color.GRAY);return t;}
    private TextView infoCard(String s){TextView t=text(s,13,Color.DKGRAY,false);t.setPadding(dp(13),dp(12),dp(13),dp(12));t.setBackground(box(Color.WHITE,12,Color.rgb(215,215,215),1));return t;}
    private LinearLayout card(){LinearLayout c=col();c.setPadding(dp(14),dp(13),dp(14),dp(13));c.setBackground(box(Color.WHITE,14,Color.rgb(218,216,212),1));return c;}
    private Button action(String title,String sub){Button b=primary(title+"\n"+sub);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setPadding(dp(16),dp(7),dp(16),dp(7));b.setMinHeight(dp(76));return b;}
    private Button tile(String s){Button b=secondary(s);b.setMinHeight(dp(66));return b;}
    private Button primary(String s){Button b=button(s);b.setTextColor(Color.WHITE);b.setBackground(box(Color.rgb(15,15,15),12,Color.TRANSPARENT,0));return b;}
    private Button secondary(String s){Button b=button(s);b.setTextColor(Color.rgb(20,20,20));b.setBackground(box(Color.WHITE,12,Color.rgb(190,190,190),1));return b;}
    private Button ghost(String s){Button b=button(s);b.setTextColor(Color.rgb(130,55,45));b.setBackgroundColor(Color.TRANSPARENT);return b;}
    private Button button(String s){Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setMinHeight(dp(50));return b;}
    private EditText input(String v){EditText e=new EditText(this);e.setText(v);e.setTextSize(16);e.setTextColor(Color.rgb(20,20,20));e.setPadding(dp(12),0,dp(12),0);e.setBackground(box(Color.WHITE,11,Color.rgb(205,205,205),1));return e;}
    private EditText field(LinearLayout p,String l,String v){TextView t=text(l,13,Color.DKGRAY,true);t.setPadding(0,dp(9),0,dp(4));p.addView(t);EditText e=input(v);e.setSingleLine(true);p.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));return e;}
    private EditText multi(LinearLayout p,String l,String v,int lines){TextView t=text(l,13,Color.DKGRAY,true);t.setPadding(0,dp(9),0,dp(4));p.addView(t);EditText e=input(v);e.setSingleLine(false);e.setMinLines(lines);e.setGravity(Gravity.TOP);e.setPadding(dp(12),dp(9),dp(12),dp(9));p.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));return e;}
    private Spinner spinner(LinearLayout p,String label,String[] values,String selected){TextView l=text(label,13,Color.DKGRAY,true);l.setPadding(0,dp(9),0,dp(4));p.addView(l);Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);for(int i=0;i<values.length;i++)if(values[i].equalsIgnoreCase(selected)){s.setSelection(i);break;}s.setBackground(box(Color.WHITE,11,Color.rgb(205,205,205),1));p.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(50)));return s;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable box(int fill,int r,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(r));if(sw>0)d.setStroke(dp(sw),stroke);return d;}
    private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=v;return p;}
    private LinearLayout.LayoutParams topWrap(int v){return top(v);}
    private LinearLayout.LayoutParams bottom(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=v;return p;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private JSONArray items(JSONObject r){JSONArray a=r==null?null:r.optJSONArray("items");return a==null?new JSONArray():a;}
    private String selected(Spinner s){Object x=s.getSelectedItem();return x==null?"":String.valueOf(x);}
    private String val(EditText e){return e==null?"":e.getText().toString().trim();}
    private double dbl(String s,double f){try{return Double.parseDouble(s.replace(',','.'));}catch(Exception e){return f;}}
    private int integer(String s,int f){try{return Integer.parseInt(s.trim());}catch(Exception e){return f;}}
    private String fmt(double n){if(Math.abs(n-Math.rint(n))<0.0001)return String.valueOf((int)Math.rint(n));return String.format(Locale.US,"%.2f",n).replaceAll("0+$","").replaceAll("\\.$","");}
    private String dash(String s){return s==null||s.trim().isEmpty()?"—":s.trim();}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void alert(String t,String m){if(!isFinishing())new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Aceptar",null).show();}
    private String msg(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}
}
