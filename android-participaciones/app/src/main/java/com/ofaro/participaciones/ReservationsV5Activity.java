package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReservationsV5Activity extends Activity {
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private AppCore core;private LinearLayout page;private TextView printerState;private String currentMode="HOY";

    @Override protected void onCreate(Bundle state){super.onCreate(state);V5Ui.applySystemBars(this);core=new AppCore(this);core.startPrinterWatchdog();setContentView(build());if(getIntent().getBooleanExtra("openNew",false))newReservation();else showList("HOY");}
    @Override protected void onResume(){super.onResume();if(core!=null){core.startPrinterWatchdog();V5Ui.updatePrinter(printerState,core);}}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View build(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(V5Ui.BG);LinearLayout shell=V5Ui.column(this);V5Ui.Header h=V5Ui.header(this,core,"Reservas");printerState=h.printer;shell.addView(h.view);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);page=V5Ui.column(this);page.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,10),V5Ui.dp(this,18),V5Ui.dp(this,24));scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));shell.addView(V5Ui.bottomNav(this,1));root.addView(shell,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        View plus=V5Ui.floatingPlus(this,this::newReservation);FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(V5Ui.dp(this,50),V5Ui.dp(this,50),Gravity.RIGHT|Gravity.BOTTOM);fp.rightMargin=V5Ui.dp(this,22);fp.bottomMargin=V5Ui.dp(this,73);root.addView(plus,fp);return root;
    }

    private void showList(String mode){
        currentMode=mode;page.removeAllViews();page.addView(V5Ui.kicker(this,"RESERVAS"));TextView title=V5Ui.title(this,titleFor(mode));title.setPadding(0,V5Ui.dp(this,5),0,V5Ui.dp(this,12));page.addView(title);page.addView(tabs(mode));
        JSONArray cached=core.cachedArray("reservations_v5_"+mode);if(cached.length()==0&&"HOY".equals(mode))cached=core.cachedArray("reservations_HOY");TextView status=V5Ui.text(this,cached.length()>0?summary(cached)+" · actualizando":"Actualizando reservas…",11,V5Ui.MUTED,false);status.setPadding(V5Ui.dp(this,1),V5Ui.dp(this,12),0,V5Ui.dp(this,8));page.addView(status);LinearLayout list=V5Ui.column(this);page.addView(list);paintList(list,cached);
        if(!core.configured()){status.setText(cached.length()>0?summary(cached)+" · copia local":"Servidor sin configurar");return;}JSONArray finalCached=cached;io.execute(()->{try{JSONObject req=core.action("reservationList").put("limit",300).put("includeClosed",true);JSONObject response=core.post(req);JSONArray raw=response.optJSONArray("items");if(raw==null)raw=new JSONArray();JSONArray filtered=filter(raw,mode);core.saveArray("reservations_v5_"+mode,filtered);if("HOY".equals(mode))core.saveArray("reservations_HOY",filtered);runOnUiThread(()->{status.setText(summary(filtered));paintList(list,filtered);});}catch(Exception e){runOnUiThread(()->status.setText(finalCached.length()>0?summary(finalCached)+" · sin conexión":"No se pudo cargar · "+msg(e)));}});
    }

    private View tabs(String selected){LinearLayout row=V5Ui.row(this);String[] modes={"HOY","PRÓXIMAS","PENDIENTES","HISTÓRICO"};for(String m:modes){boolean on=m.equals(selected);TextView t=V5Ui.text(this,labelMode(m),9,on?Color.WHITE:V5Ui.MUTED,true);t.setGravity(Gravity.CENTER);t.setBackground(V5Ui.bg(this,on?V5Ui.GREEN:V5Ui.SURFACE,15));t.setOnClickListener(v->showList(m));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,V5Ui.dp(this,38),1);p.setMargins(V5Ui.dp(this,3),0,V5Ui.dp(this,3),0);row.addView(t,p);}return row;}

    private JSONArray filter(JSONArray raw,String mode){List<JSONObject> list=new ArrayList<>();String today=LocalDate.now().toString();for(int i=0;i<raw.length();i++){JSONObject r=raw.optJSONObject(i);if(r==null)continue;String date=r.optString("date","");String state=r.optString("state","");String service=r.optString("serviceState","");boolean closed="Cancelada".equalsIgnoreCase(state)||"Denegada".equalsIgnoreCase(state)||"Completada".equalsIgnoreCase(service)||"No se presentó".equalsIgnoreCase(service);boolean keep;if("HOY".equals(mode))keep=today.equals(date)&&!closed;else if("PRÓXIMAS".equals(mode))keep=date.compareTo(today)>0&&!closed;else if("PENDIENTES".equals(mode))keep="Pendiente".equalsIgnoreCase(state)&&!closed;else keep=date.compareTo(today)<0||closed;if(keep)list.add(r);}list.sort(Comparator.comparing(x->x.optString("date","")+" "+x.optString("time","")));JSONArray out=new JSONArray();for(JSONObject r:list)out.put(r);return out;}

    private void paintList(LinearLayout host,JSONArray arr){host.removeAllViews();if(arr.length()==0){LinearLayout empty=V5Ui.softCard(this);empty.addView(V5Ui.text(this,"No hay reservas en este apartado.",13,V5Ui.MUTED,false));host.addView(empty);return;}for(int i=0;i<arr.length();i++){JSONObject r=arr.optJSONObject(i);if(r!=null)host.addView(reservationCard(r),bottom(V5Ui.dp(this,8)));}}

    private View reservationCard(JSONObject r){
        LinearLayout card=V5Ui.card(this);card.setOnClickListener(v->details(r));LinearLayout top=V5Ui.row(this);top.setGravity(Gravity.CENTER_VERTICAL);TextView time=V5Ui.text(this,r.optString("time","--:--"),22,V5Ui.INK,true);top.addView(time,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));top.addView(statePill(r.optString("state","Pendiente")));card.addView(top);
        TextView name=V5Ui.text(this,r.optString("name","Reserva"),15,V5Ui.INK,true);name.setPadding(0,V5Ui.dp(this,7),0,0);card.addView(name);String meta=r.optInt("people",0)+" personas";String zone=r.optString("zone","");String table=r.optString("table","");if(!zone.isEmpty())meta+=" · "+zone;if(!table.isEmpty())meta+=" · Mesa "+table;TextView m=V5Ui.text(this,meta,11.5f,V5Ui.MUTED,false);m.setPadding(0,V5Ui.dp(this,4),0,0);card.addView(m);String service=r.optString("serviceState","Pendiente");TextView sv=V5Ui.text(this,service,10,statusColor(r.optString("state",""),service),true);sv.setPadding(0,V5Ui.dp(this,7),0,0);card.addView(sv);return card;
    }

    private TextView statePill(String state){int fill=V5Ui.LIME_SOFT,color=V5Ui.GREEN;if("Cancelada".equalsIgnoreCase(state)||"Denegada".equalsIgnoreCase(state)){fill=Color.rgb(248,230,226);color=V5Ui.ERROR;}else if("Pendiente".equalsIgnoreCase(state)){fill=Color.rgb(239,239,235);color=V5Ui.MUTED;}return V5Ui.pill(this,state,fill,color);}

    private void details(JSONObject r){
        page.removeAllViews();TextView back=V5Ui.text(this,"‹  Reservas",12,V5Ui.GREEN,true);back.setPadding(0,V5Ui.dp(this,4),0,V5Ui.dp(this,14));back.setOnClickListener(v->showList(currentMode));page.addView(back);LinearLayout head=V5Ui.row(this);head.setGravity(Gravity.CENTER_VERTICAL);LinearLayout copy=V5Ui.column(this);copy.addView(V5Ui.title(this,r.optString("name","Reserva")));copy.addView(V5Ui.subtitle(this,r.optString("phone","")));head.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));head.addView(statePill(r.optString("state","Pendiente")));page.addView(head);
        TextView meta=V5Ui.subtitle(this,r.optString("date","")+" · "+r.optString("time","")+" · "+r.optInt("people",0)+" personas");meta.setPadding(0,V5Ui.dp(this,8),0,V5Ui.dp(this,14));page.addView(meta);
        LinearLayout location=V5Ui.softCard(this);location.addView(V5Ui.kicker(this,"UBICACIÓN"));location.addView(V5Ui.text(this,"Mesa "+dash(r.optString("table",""))+" · "+dash(r.optString("zone","")),15,V5Ui.INK,true));page.addView(location);
        LinearLayout notes=V5Ui.card(this);notes.addView(V5Ui.kicker(this,"OBSERVACIONES"));TextView nt=V5Ui.text(this,dash(r.optString("notes","")),13,V5Ui.INK,false);nt.setPadding(0,V5Ui.dp(this,6),0,0);notes.addView(nt);page.addView(notes,top(V5Ui.dp(this,8)));
        LinearLayout actions=V5Ui.row(this);actions.addView(action("Llamar",false,()->dial(r.optString("phone",""))),weightGap());actions.addView(action("WhatsApp",false,()->whatsapp(r.optString("phone",""))),weightGap());page.addView(actions,top(V5Ui.dp(this,12)));
        LinearLayout actions2=V5Ui.row(this);actions2.addView(action("Editar",false,()->editReservation(r)),weightGap());actions2.addView(action("Imprimir",true,()->preview(r)),weightGap());page.addView(actions2,top(V5Ui.dp(this,8)));
        page.addView(sectionLabel("ESTADO"));LinearLayout states=V5Ui.row(this);states.addView(action("Confirmar",false,()->update(r,"Confirmada",null)),weightGap());states.addView(action("Llegó",false,()->update(r,null,"Llegó")),weightGap());states.addView(action("Completar",false,()->update(r,null,"Completada")),weightGap());page.addView(states);
    }

    private void newReservation(){showForm(null);}
    private void editReservation(JSONObject r){showForm(r);}
    private void showForm(JSONObject existing){
        boolean edit=existing!=null;page.removeAllViews();TextView back=V5Ui.text(this,"‹  "+(edit?"Reserva":"Reservas"),12,V5Ui.GREEN,true);back.setPadding(0,V5Ui.dp(this,4),0,V5Ui.dp(this,12));back.setOnClickListener(v->{if(edit)details(existing);else showList(currentMode);});page.addView(back);page.addView(V5Ui.kicker(this,edit?"EDITAR":"NUEVA RESERVA"));TextView title=V5Ui.title(this,edit?"Actualizar reserva":"Añadir reserva");title.setPadding(0,V5Ui.dp(this,5),0,V5Ui.dp(this,12));page.addView(title);
        EditText name=field("Nombre",edit?existing.optString("name",""):"",InputType.TYPE_CLASS_TEXT);EditText phone=field("Teléfono",edit?existing.optString("phone",""):"",InputType.TYPE_CLASS_PHONE);EditText email=field("Correo",edit?existing.optString("email",""):"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);EditText date=picker("Fecha",edit?existing.optString("date",LocalDate.now().toString()):LocalDate.now().toString(),true);EditText time=picker("Hora",edit?existing.optString("time","14:00"):"14:00",false);EditText people=field("Personas",edit?String.valueOf(existing.optInt("people",2)):"2",InputType.TYPE_CLASS_NUMBER);EditText table=field("Mesa",edit?existing.optString("table",""):"",InputType.TYPE_CLASS_TEXT);Spinner zone=zoneSpinner(edit?existing.optString("zone","Interior"):"Interior");EditText notes=multi("Observaciones",edit?existing.optString("notes",""):"");
        TextView save=action(edit?"Guardar cambios":"Guardar reserva",true,()->{int p=parse(people.getText().toString(),2);String error=ReservationRules.validate(value(name),value(phone),value(date),value(time),p);if(!error.isEmpty()){alert("Revisa la reserva",error);return;}saveReservation(existing,value(date),value(time),value(name),value(phone),value(email),p,value(table),String.valueOf(zone.getSelectedItem()),value(notes));});page.addView(save,top(V5Ui.dp(this,14)));
    }

    private void saveReservation(JSONObject existing,String date,String time,String name,String phone,String email,int people,String table,String zone,String notes){io.execute(()->{try{JSONObject q=core.action(existing==null?"reservationCreate":"reservationFullUpdate");if(existing!=null)q.put("id",existing.optString("id",""));q.put("fecha",date).put("hora",time).put("nombre",name).put("telefono",phone).put("correo",email).put("personas",people).put("mesa",table).put("zona",zone).put("observaciones",notes);JSONObject response=core.post(q);JSONObject item=response.optJSONObject("reservation");if(item==null)item=response.optJSONObject("item");JSONObject finalItem=item;runOnUiThread(()->{toast(existing==null?"Reserva guardada":"Cambios guardados");if(finalItem!=null)details(finalItem);else showList("HOY");});}catch(Exception e){runOnUiThread(()->alert("No se pudo guardar",msg(e)));}});}

    private void update(JSONObject r,String state,String service){io.execute(()->{try{JSONObject q=core.action("reservationUpdate").put("id",r.optString("id",""));if(state!=null)q.put("state",state);if(service!=null)q.put("serviceState",service);JSONObject x=core.post(q);JSONObject updated=x.optJSONObject("reservation");if(updated==null)updated=x.optJSONObject("item");JSONObject f=updated;runOnUiThread(()->{toast("Reserva actualizada");if(f!=null)details(f);else showList(currentMode);});}catch(Exception e){runOnUiThread(()->alert("No se pudo actualizar",msg(e)));}});}

    private EditText field(String label,String value,int type){page.addView(fieldLabel(label));EditText e=input(value);e.setInputType(type);e.setSingleLine(true);page.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,48)));return e;}
    private EditText multi(String label,String value){page.addView(fieldLabel(label));EditText e=input(value);e.setSingleLine(false);e.setMinLines(3);e.setGravity(Gravity.TOP);e.setPadding(V5Ui.dp(this,13),V5Ui.dp(this,11),V5Ui.dp(this,13),V5Ui.dp(this,11));page.addView(e);return e;}
    private EditText picker(String label,String value,boolean date){page.addView(fieldLabel(label));EditText e=input(value);e.setFocusable(false);e.setClickable(true);e.setOnClickListener(v->{if(date)showDatePicker(e);else showTimePicker(e);});page.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,48)));return e;}
    private Spinner zoneSpinner(String current){page.addView(fieldLabel("Zona"));Spinner s=new Spinner(this);String[] values={"Interior","Terraza"};ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection("Terraza".equalsIgnoreCase(current)?1:0);s.setBackground(V5Ui.bg(this,V5Ui.SURFACE,14));page.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,48)));return s;}
    private TextView fieldLabel(String value){TextView t=V5Ui.text(this,value,11,V5Ui.MUTED,true);t.setPadding(V5Ui.dp(this,1),V5Ui.dp(this,11),0,V5Ui.dp(this,5));return t;}
    private EditText input(String value){EditText e=new EditText(this);e.setText(value);e.setTextSize(14);e.setTextColor(V5Ui.INK);e.setHintTextColor(V5Ui.FAINT);e.setPadding(V5Ui.dp(this,13),0,V5Ui.dp(this,13),0);e.setBackground(V5Ui.bg(this,V5Ui.SURFACE,14));return e;}

    private void showDatePicker(EditText target){LocalDate init;try{init=LocalDate.parse(value(target));}catch(Exception e){init=LocalDate.now();}new DatePickerDialog(this,(v,y,m,d)->target.setText(String.format(Locale.ROOT,"%04d-%02d-%02d",y,m+1,d)),init.getYear(),init.getMonthValue()-1,init.getDayOfMonth()).show();}
    private void showTimePicker(EditText target){int h=14,m=0;try{String[] p=value(target).split(":");h=Integer.parseInt(p[0]);m=Integer.parseInt(p[1]);}catch(Exception ignored){}new TimePickerDialog(this,(v,hh,mm)->target.setText(String.format(Locale.ROOT,"%02d:%02d",hh,mm)),h,m,true).show();}

    private TextView action(String label,boolean primary,Runnable run){TextView t=V5Ui.text(this,label,11,primary?Color.WHITE:V5Ui.GREEN,true);t.setGravity(Gravity.CENTER);t.setBackground(V5Ui.bg(this,primary?V5Ui.GREEN:V5Ui.SURFACE,15));t.setOnClickListener(v->run.run());t.setMinHeight(V5Ui.dp(this,44));return t;}
    private TextView sectionLabel(String value){TextView t=V5Ui.kicker(this,value);t.setPadding(V5Ui.dp(this,1),V5Ui.dp(this,18),0,V5Ui.dp(this,8));return t;}
    private String titleFor(String mode){return"HOY".equals(mode)?"Reservas de hoy":"PRÓXIMAS".equals(mode)?"Próximas reservas":"PENDIENTES".equals(mode)?"Pendientes":"Histórico";}
    private String labelMode(String mode){return"PRÓXIMAS".equals(mode)?"Próximas":"HISTÓRICO".equals(mode)?"Histórico":mode.substring(0,1)+mode.substring(1).toLowerCase(Locale.ROOT);}
    private String summary(JSONArray a){int people=0;for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null)people+=Math.max(0,r.optInt("people",0));}return a.length()+" reservas"+(people>0?" · "+people+" personas":"");}
    private int statusColor(String state,String service){if("Cancelada".equalsIgnoreCase(state)||"Denegada".equalsIgnoreCase(state)||"No se presentó".equalsIgnoreCase(service))return V5Ui.ERROR;if("Confirmada".equalsIgnoreCase(state)||"Llegó".equalsIgnoreCase(service)||"Sentada".equalsIgnoreCase(service)||"En servicio".equalsIgnoreCase(service)||"Completada".equalsIgnoreCase(service))return V5Ui.GREEN;return V5Ui.WARNING;}
    private void dial(String phone){if(phone==null||phone.trim().isEmpty()){toast("No hay teléfono");return;}startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+phone.trim())));}
    private void whatsapp(String phone){if(phone==null||phone.trim().isEmpty()){toast("No hay teléfono");return;}String c=phone.replaceAll("[^0-9]","");if(c.length()==9)c="34"+c;startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+c)));}
    private void preview(JSONObject r){String body=r.optString("date","")+"   "+r.optString("time","")+"\n\n"+r.optString("name","").toUpperCase(Locale.ROOT)+"\n"+r.optInt("people",0)+" PERSONAS\nMesa: "+r.optString("table","")+"\nZona: "+r.optString("zone","")+"\n\n"+r.optString("notes","");Intent i=new Intent(this,TicketPreviewActivity.class).putExtra("title","MESÓN O FARO").putExtra("subtitle","RESERVA").putExtra("body",body).putExtra("type","Reserva").putExtra("reference",r.optString("id","")).putExtra("reservationId",r.optString("id","")).putExtra("template","Reserva Express");startActivity(i);}
    private String value(EditText e){return e.getText().toString().trim();}private int parse(String s,int fallback){try{return Integer.parseInt(s.trim());}catch(Exception e){return fallback;}}private String dash(String s){return s==null||s.trim().isEmpty()?"—":s.trim();}private String msg(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}private void alert(String t,String m){if(!isFinishing())new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Aceptar",null).show();}
    private LinearLayout.LayoutParams weightGap(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,V5Ui.dp(this,44),1);p.setMargins(V5Ui.dp(this,4),0,V5Ui.dp(this,4),0);return p;}private LinearLayout.LayoutParams top(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=v;return p;}private LinearLayout.LayoutParams bottom(int v){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=v;return p;}
}
