package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shell integrada de O Faro Gestión 5. */
public class HomeActivityFinal extends Activity {
    private static final int HOME=0, RESERVATIONS=1, PROMOTIONS=2, STATS=3, MANAGEMENT=4, SETTINGS=5;
    private static final String[] NAMES={"Inicio","Reservas","Promociones","Estadísticas","Gestión","Configuración"};

    private AppCore core;
    private FrameLayout contentHost, navHost;
    private TextView headerSection, printerState;
    private View fab;
    private int section=HOME;
    private String reservationMode="HOY";
    private JSONArray reservations=new JSONArray();
    private JSONArray campaigns=new JSONArray();
    private JSONArray prizes=new JSONArray();
    private JSONArray promoHistory=new JSONArray();
    private EditText redeemInput;
    private TextView redeemResult, redeemButton;
    private String validatedCode="";
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(3);
    private final Runnable printerTick=new Runnable(){@Override public void run(){refreshPrinter();ui.postDelayed(this,2500);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        V5Ui.applySystemBars(this);
        core=new AppCore(this);
        refreshCaches();
        setContentView(buildShell());
        core.startPrinterWatchdog();
        showSection(HOME,false);
        refreshAll(false);
    }

    @Override protected void onStart(){super.onStart();ui.removeCallbacks(printerTick);ui.post(printerTick);}
    @Override protected void onResume(){super.onResume();if(core!=null){core.startPrinterWatchdog();refreshCaches();refreshPrinter();}}
    @Override protected void onStop(){ui.removeCallbacks(printerTick);super.onStop();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(section!=HOME){showSection(HOME,true);return;}super.onBackPressed();}

    private View buildShell(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(V5Ui.BG);
        LinearLayout shell=V5Ui.column(this);
        V5Ui.Header h=V5Ui.header(this,core,"Inicio");
        headerSection=h.section;printerState=h.printer;
        printerState.setOnClickListener(v->open(PrinterSettingsActivity.class));
        shell.addView(h.view);
        contentHost=new FrameLayout(this);contentHost.setClipToPadding(false);
        shell.addView(contentHost,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        navHost=new FrameLayout(this);shell.addView(navHost);
        root.addView(shell,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        fab=V5Ui.floatingPlus(this,this::fabAction);
        FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(V5Ui.dp(this,46),V5Ui.dp(this,46),Gravity.RIGHT|Gravity.BOTTOM);
        fp.rightMargin=V5Ui.dp(this,20);fp.bottomMargin=V5Ui.dp(this,65);root.addView(fab,fp);
        return root;
    }

    private void showSection(int next,boolean animate){
        section=next;validatedCode="";redeemInput=null;redeemResult=null;redeemButton=null;
        if(headerSection!=null)headerSection.setText(NAMES[next]);
        rebuildNav();updateFab();swap(build(next),animate);
    }
    private void rebuildNav(){navHost.removeAllViews();navHost.addView(V5Ui.bottomNav(this,section,i->showSection(i,true)));}
    private void updateFab(){if(fab!=null)fab.setVisibility(section==HOME||section==RESERVATIONS||section==PROMOTIONS?View.VISIBLE:View.GONE);}
    private void fabAction(){if(section==RESERVATIONS)showReservationForm(null);else if(section==PROMOTIONS)showQrGenerator();else quickActions();}
    private View build(int s){if(s==RESERVATIONS)return buildReservations();if(s==PROMOTIONS)return buildPromotions();if(s==STATS)return buildStats();if(s==MANAGEMENT)return buildManagement();if(s==SETTINGS)return buildSettings();return buildHome();}
    private void swap(View next,boolean animate){
        if(!animate||contentHost.getChildCount()==0){contentHost.removeAllViews();contentHost.addView(next);return;}
        View old=contentHost.getChildAt(0);
        old.animate().alpha(0f).setDuration(65).withEndAction(()->{contentHost.removeAllViews();next.setAlpha(0f);contentHost.addView(next);next.animate().alpha(1f).setDuration(110).start();}).start();
    }
    private LinearLayout page(){LinearLayout p=V5Ui.column(this);p.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,8),V5Ui.dp(this,18),V5Ui.dp(this,28));return p;}
    private ScrollView scroll(LinearLayout p){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.addView(p);return s;}

    // INICIO
    private View buildHome(){
        LinearLayout p=page();
        p.addView(V5Ui.kicker(this,currentDate().toUpperCase(new Locale("es","ES"))));
        TextView title=V5Ui.title(this,greeting());title.setPadding(0,V5Ui.dp(this,5),0,V5Ui.dp(this,3));p.addView(title);
        p.addView(V5Ui.subtitle(this,"Servicio, reservas, promociones e impresión en un único lugar."));

        JSONArray today=filterReservations("HOY","");int people=0;JSONObject next=null;String best="99:99";
        for(int i=0;i<today.length();i++){JSONObject r=today.optJSONObject(i);if(r==null)continue;people+=peopleOf(r);String t=timeOf(r);if(t.compareTo(best)<0){best=t;next=r;}}
        LinearLayout hero=V5Ui.darkCard(this);hero.setOnClickListener(v->showSection(RESERVATIONS,true));
        TextView k=V5Ui.kicker(this,"HOY");k.setTextColor(Color.rgb(194,205,195));hero.addView(k);
        LinearLayout nums=V5Ui.row(this);nums.addView(darkMetric(String.valueOf(today.length()),"reservas"),weight());nums.addView(darkMetric(String.valueOf(people),"personas"),weightGap());hero.addView(nums);
        String nx=next==null?"Sin próximas reservas":timeOf(next)+" · "+nameOf(next)+" · "+peopleOf(next)+" pers.";
        TextView nt=V5Ui.text(this,"Próxima · "+nx,11.5f,Color.WHITE,true);nt.setPadding(0,V5Ui.dp(this,10),0,0);hero.addView(nt);p.addView(hero,top(14));

        p.addView(sectionLabel("ACCIONES"));
        LinearLayout q1=V5Ui.row(this);q1.addView(V5Ui.quickAction(this,R.drawable.ic_plus_v5,"Reserva",()->showReservationForm(null)),weight());q1.addView(V5Ui.quickAction(this,R.drawable.ic_qr_v5,"Generar QR",this::showQrGenerator),weightGap());p.addView(q1);
        LinearLayout q2=V5Ui.row(this);q2.addView(V5Ui.quickAction(this,R.drawable.ic_check_v5,"Canjear",this::showRedeem),weight());q2.addView(V5Ui.quickAction(this,R.drawable.ic_print_v5,"Imprimir",()->open(FreePrintActivity.class)),weightGap());p.addView(q2,top(7));

        p.addView(sectionLabel("ESTADO"));
        LinearLayout status=V5Ui.card(this);status.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,16));
        status.addView(V5Ui.text(this,core.configured()?(core.internetAvailable()?"Servidor conectado":"Sin conexión · mostrando datos guardados"):"Falta configurar la conexión",10.5f,V5Ui.MUTED,false));
        status.addView(V5Ui.text(this,printerSummary(),10,V5Ui.FAINT,false));
        status.addView(V5Ui.text(this,"O Faro Gestión · "+BuildConfig.VERSION_NAME,9.5f,V5Ui.FAINT,false));p.addView(status);
        return scroll(p);
    }

    // RESERVAS
    private View buildReservations(){
        LinearLayout p=page();p.addView(V5Ui.kicker(this,"RESERVAS"));
        TextView title=V5Ui.title(this,reservationTitle());title.setPadding(0,V5Ui.dp(this,5),0,V5Ui.dp(this,9));p.addView(title);
        LinearLayout tabs=V5Ui.row(this);
        for(String m:new String[]{"HOY","PRÓXIMAS","PENDIENTES","HISTÓRICO"}){TextView c=chip(labelMode(m),m.equals(reservationMode));c.setOnClickListener(v->{reservationMode=m;showSection(RESERVATIONS,false);});tabs.addView(c,chipWeight());}
        p.addView(tabs);
        EditText search=input("");search.setHint("Buscar nombre, teléfono, mesa o zona");p.addView(search,top(9));
        LinearLayout list=V5Ui.column(this);p.addView(list);
        Runnable render=()->renderReservationList(list,search.getText().toString());render.run();search.addTextChangedListener(new SimpleTextWatcher(render));
        p.addView(action("Nueva reserva",true,()->showReservationForm(null)),top(10));
        return scroll(p);
    }

    private void renderReservationList(LinearLayout host,String query){
        host.removeAllViews();JSONArray a=filterReservations(reservationMode,query);
        TextView count=V5Ui.text(this,a.length()+" reservas",10,V5Ui.MUTED,false);count.setPadding(0,V5Ui.dp(this,10),0,V5Ui.dp(this,6));host.addView(count);
        if(a.length()==0){LinearLayout e=V5Ui.softCard(this);e.addView(V5Ui.text(this,"No hay reservas que coincidan.",12,V5Ui.MUTED,false));host.addView(e);return;}
        for(int i=0;i<a.length();i++){JSONObject r=a.optJSONObject(i);if(r!=null)host.addView(reservationCard(r),bottom(7));}
    }

    private JSONArray filterReservations(String mode,String query){
        String q=query==null?"":query.trim().toLowerCase(Locale.ROOT);String today=LocalDate.now().toString();List<JSONObject> list=new ArrayList<>();
        for(int i=0;i<reservations.length();i++){
            JSONObject r=reservations.optJSONObject(i);if(r==null)continue;
            String date=dateOf(r),state=stateOf(r),service=r.optString("serviceState",r.optString("estadoServicio",""));
            boolean closed=state.equalsIgnoreCase("Cancelada")||state.equalsIgnoreCase("Denegada")||service.equalsIgnoreCase("Completada")||service.equalsIgnoreCase("No se presentó");
            boolean keep;
            if("HOY".equals(mode))keep=today.equals(date)&&!closed;
            else if("PRÓXIMAS".equals(mode))keep=date.compareTo(today)>0&&!closed;
            else if("PENDIENTES".equals(mode))keep=state.equalsIgnoreCase("Pendiente")&&!closed;
            else keep=date.compareTo(today)<0||closed;
            if(!keep)continue;
            String hay=(nameOf(r)+" "+phoneOf(r)+" "+tableOf(r)+" "+zoneOf(r)).toLowerCase(Locale.ROOT);
            if(!q.isEmpty()&&!hay.contains(q))continue;
            list.add(r);
        }
        list.sort(Comparator.comparing(x->dateOf(x)+" "+timeOf(x)));
        JSONArray out=new JSONArray();for(JSONObject r:list)out.put(r);return out;
    }

    private View reservationCard(JSONObject r){
        LinearLayout c=V5Ui.card(this);c.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,18));c.setOnClickListener(v->showReservationDetail(r));
        LinearLayout row=V5Ui.row(this);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(V5Ui.text(this,timeOf(r),20,V5Ui.INK,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));row.addView(statePill(stateOf(r)));c.addView(row);
        c.addView(V5Ui.text(this,nameOf(r),14.5f,V5Ui.INK,true));
        c.addView(V5Ui.text(this,peopleOf(r)+" personas · "+dash(zoneOf(r))+" · Mesa "+dash(tableOf(r)),10.5f,V5Ui.MUTED,false));return c;
    }

    private void showReservationDetail(JSONObject r){
        LinearLayout p=page();p.addView(backLink("Reservas",()->showSection(RESERVATIONS,true)));p.addView(V5Ui.kicker(this,"RESERVA"));p.addView(V5Ui.title(this,nameOf(r)));p.addView(V5Ui.subtitle(this,dateOf(r)+" · "+timeOf(r)+" · "+peopleOf(r)+" personas"));
        LinearLayout info=V5Ui.softCard(this);info.addView(V5Ui.text(this,"Mesa "+dash(tableOf(r))+" · "+dash(zoneOf(r)),13,V5Ui.INK,true));info.addView(V5Ui.text(this,"Tel. "+dash(phoneOf(r))+"\n"+dash(emailOf(r)),10.5f,V5Ui.MUTED,false));String notes=notesOf(r);if(!notes.isEmpty())info.addView(V5Ui.text(this,notes,10.5f,V5Ui.MUTED,false));p.addView(info,top(10));
        LinearLayout a=V5Ui.row(this);a.addView(action("Llamar",false,()->dial(phoneOf(r))),weight());a.addView(action("WhatsApp",false,()->whatsapp(phoneOf(r))),weightGap());p.addView(a,top(9));
        LinearLayout b=V5Ui.row(this);b.addView(action("Editar",false,()->showReservationForm(r)),weight());b.addView(action("Imprimir",true,()->printReservation(r)),weightGap());p.addView(b,top(7));
        p.addView(sectionLabel("ESTADO"));
        LinearLayout st=V5Ui.row(this);st.addView(action("Confirmar",false,()->setReservationState(r,"Confirmada",null)),weight());st.addView(action("Llegó",false,()->setReservationState(r,null,"Llegó")),weightGap());st.addView(action("Completar",false,()->setReservationState(r,null,"Completada")),weightGap());p.addView(st);
        LinearLayout st2=V5Ui.row(this);st2.addView(action("No-show",false,()->setReservationState(r,null,"No se presentó")),weight());st2.addView(action("Cancelar",false,()->setReservationState(r,"Cancelada",null)),weightGap());p.addView(st2,top(7));
        swap(scroll(p),true);
    }

    private void showReservationForm(JSONObject old){
        LinearLayout p=page();p.addView(backLink("Reservas",()->showSection(RESERVATIONS,true)));boolean edit=old!=null;
        p.addView(V5Ui.kicker(this,edit?"EDITAR RESERVA":"NUEVA RESERVA"));p.addView(V5Ui.title(this,edit?"Actualizar datos":"Añadir reserva"));
        EditText name=field(p,"Nombre",edit?nameOf(old):"",InputType.TYPE_CLASS_TEXT);
        EditText phone=field(p,"Teléfono",edit?phoneOf(old):"",InputType.TYPE_CLASS_PHONE);
        EditText email=field(p,"Correo",edit?emailOf(old):"",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText date=picker(p,"Fecha",edit?dateOf(old):LocalDate.now().toString(),true);
        EditText time=picker(p,"Hora",edit?timeOf(old):"14:00",false);
        EditText people=field(p,"Personas",edit?String.valueOf(peopleOf(old)):"2",InputType.TYPE_CLASS_NUMBER);
        EditText table=field(p,"Mesa",edit?tableOf(old):"",InputType.TYPE_CLASS_TEXT);
        Spinner zone=spinner(p,"Zona",new String[]{"Interior","Terraza"},edit&&"Terraza".equalsIgnoreCase(zoneOf(old))?1:0);
        EditText notes=multi(p,"Observaciones",edit?notesOf(old):"");
        p.addView(action(edit?"Guardar cambios":"Guardar reserva",true,()->saveReservation(old,value(date),value(time),value(name),value(phone),value(email),parse(value(people),2),value(table),String.valueOf(zone.getSelectedItem()),value(notes))),top(12));
        swap(scroll(p),true);
    }

    private void saveReservation(JSONObject old,String date,String time,String name,String phone,String email,int people,String table,String zone,String notes){
        String error=ReservationRules.validate(name,phone,date,time,people);if(!error.isEmpty()){alert("Revisa la reserva",error);return;}
        if(!core.configured()){alert("Servidor sin configurar","Configura primero la conexión.");return;}
        io.execute(()->{try{
            JSONObject q=core.action(old==null?"reservationCreate":"reservationFullUpdate");if(old!=null)q.put("id",old.optString("id",""));
            q.put("fecha",date).put("hora",time).put("nombre",name).put("telefono",phone).put("correo",email).put("personas",people).put("mesa",table).put("zona",zone).put("observaciones",notes);
            core.post(q);refreshReservations();runOnUiThread(()->{toast(old==null?"Reserva creada":"Reserva actualizada");showSection(RESERVATIONS,true);});
        }catch(Exception e){runOnUiThread(()->alert("No se pudo guardar",msg(e)));}});
    }

    private void setReservationState(JSONObject r,String state,String service){
        io.execute(()->{try{JSONObject q=core.action("reservationUpdate").put("id",r.optString("id",""));if(state!=null)q.put("state",state);if(service!=null)q.put("serviceState",service);core.post(q);refreshReservations();runOnUiThread(()->showSection(RESERVATIONS,true));}catch(Exception e){runOnUiThread(()->alert("No se pudo actualizar",msg(e)));}});
    }
    private void printReservation(JSONObject r){io.execute(()->{try{core.printReservation(r);runOnUiThread(()->toast("Reserva enviada a impresora"));}catch(Exception e){runOnUiThread(()->alert("No se pudo imprimir",msg(e)));}});}

    // PROMOCIONES
    private View buildPromotions(){
        LinearLayout p=page();p.addView(V5Ui.kicker(this,"PROMOCIONES"));p.addView(V5Ui.title(this,"Campañas y canjes"));p.addView(V5Ui.subtitle(this,"QR de un solo uso, premios y validación de canjes."));
        int plays=0,winners=0,redeemed=0;for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);JSONObject s=c==null?null:c.optJSONObject("stats");if(s!=null){plays+=s.optInt("plays",0);winners+=s.optInt("winners",0);redeemed+=s.optInt("redeemed",0);}}
        LinearLayout m=V5Ui.row(this);m.addView(V5Ui.metric(this,"ACTIVAS",String.valueOf(activeCampaigns()),"campañas"),weight());m.addView(V5Ui.metric(this,"JUGADAS",String.valueOf(plays),"registradas"),weightGap());p.addView(m,top(11));
        LinearLayout m2=V5Ui.row(this);m2.addView(V5Ui.metric(this,"GANADORES",String.valueOf(winners),"premios"),weight());m2.addView(V5Ui.metric(this,"CANJES",String.valueOf(redeemed),"realizados"),weightGap());p.addView(m2,top(7));
        LinearLayout actions=V5Ui.row(this);actions.addView(V5Ui.quickAction(this,R.drawable.ic_qr_v5,"Generar QR",this::showQrGenerator),weight());actions.addView(V5Ui.quickAction(this,R.drawable.ic_check_v5,"Canjear",this::showRedeem),weightGap());p.addView(actions,top(10));
        p.addView(sectionLabel("CAMPAÑAS ACTIVAS"));int shown=0;
        for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);if(c!=null&&c.optBoolean("active",false)){shown++;LinearLayout card=V5Ui.card(this);card.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,18));card.addView(V5Ui.text(this,c.optString("name","Promoción"),14,V5Ui.INK,true));JSONObject s=c.optJSONObject("stats");card.addView(V5Ui.text(this,c.optString("type","Promoción")+(s==null?"":" · "+s.optInt("plays",0)+" jugadas"),10.5f,V5Ui.MUTED,false));card.setOnClickListener(v->issueQr(c));p.addView(card,bottom(7));}}
        if(shown==0){LinearLayout e=V5Ui.softCard(this);e.addView(V5Ui.text(this,"No hay campañas activas.",12,V5Ui.MUTED,false));p.addView(e);}
        p.addView(V5Ui.linkCard(this,R.drawable.ic_gift_v5,"Gestor completo",prizeCount()+" premios · campañas, probabilidades y stock",()->open(PromotionsV5Activity.class)),top(10));
        return scroll(p);
    }

    private void showQrGenerator(){
        LinearLayout p=page();p.addView(backLink("Promociones",()->showSection(PROMOTIONS,true)));p.addView(V5Ui.kicker(this,"QR REAL"));p.addView(V5Ui.title(this,"Generar invitación"));p.addView(V5Ui.subtitle(this,"Cada QR se registra y solo permite una participación."));
        int n=0;for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);if(c==null||!c.optBoolean("active",false))continue;n++;LinearLayout card=V5Ui.card(this);card.addView(V5Ui.text(this,c.optString("name","Promoción"),14,V5Ui.INK,true));card.addView(action("Generar QR",true,()->issueQr(c)),top(7));p.addView(card,top(8));}
        if(n==0)p.addView(V5Ui.subtitle(this,"No hay campañas activas."),top(10));swap(scroll(p),true);
    }

    private void issueQr(JSONObject c){
        if(!core.configured()){alert("Servidor sin configurar","Configura el acceso primero.");return;}
        io.execute(()->{try{JSONObject r=core.post(core.action("promoPublicIssue").put("id",c.optString("id","")).put("expiresHours",48));String url=r.optString("url","").trim(),token=r.optString("token","").trim();if(!url.startsWith("https://")||token.isEmpty())throw new Exception("El servidor no devolvió una invitación válida.");runOnUiThread(()->showIssuedQr(c,url,token));}catch(Exception e){runOnUiThread(()->alert("No se pudo generar el QR",msg(e)));}});
    }

    private void showIssuedQr(JSONObject c,String url,String token){
        LinearLayout p=page();p.addView(backLink("QR",this::showQrGenerator));p.addView(V5Ui.kicker(this,"INVITACIÓN CREADA"));p.addView(V5Ui.title(this,c.optString("name","Promoción")));
        try{ImageView qr=new ImageView(this);qr.setImageBitmap(QrBitmapUtil.create(url,650));qr.setAdjustViewBounds(true);qr.setBackgroundColor(Color.WHITE);LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,260));qp.topMargin=V5Ui.dp(this,12);p.addView(qr,qp);}catch(Exception e){p.addView(V5Ui.subtitle(this,"No se pudo dibujar la previsualización del QR."));}
        LinearLayout info=V5Ui.darkCard(this);info.addView(V5Ui.text(this,"QR REAL · 48 H · UN SOLO USO",10,V5Ui.LIME,true));info.addView(V5Ui.text(this,"Token · "+token,9.5f,Color.WHITE,false));p.addView(info,top(9));
        p.addView(action("Imprimir QR",true,()->io.execute(()->{try{core.printQrTicket(c.optString("name","Promoción"),"ESCANEA PARA JUGAR\nQR REAL · UN SOLO USO · 48 H",url);runOnUiThread(()->toast("QR enviado a impresora"));}catch(Exception e){runOnUiThread(()->alert("No se pudo imprimir",msg(e)));}})),top(9));
        p.addView(action("Copiar enlace",false,()->copy(url)),top(7));swap(scroll(p),true);
    }

    private void showRedeem(){
        LinearLayout p=page();p.addView(backLink("Promociones",()->showSection(PROMOTIONS,true)));p.addView(V5Ui.kicker(this,"CANJES"));p.addView(V5Ui.title(this,"Validar premio"));
        p.addView(action("Escanear QR",true,this::scanQr),top(8));redeemInput=input("");redeemInput.setHint("OF-XXXXX-XXXXX");p.addView(redeemInput,top(8));p.addView(action("Validar código",false,()->validateCode(value(redeemInput))),top(7));
        redeemResult=V5Ui.text(this,"Introduce o escanea un código.",11.5f,V5Ui.MUTED,false);redeemResult.setPadding(V5Ui.dp(this,12),V5Ui.dp(this,12),V5Ui.dp(this,12),V5Ui.dp(this,12));redeemResult.setBackground(V5Ui.bg(this,V5Ui.SURFACE_SOFT,16));p.addView(redeemResult,top(8));
        redeemButton=action("Confirmar canje",true,this::confirmRedeem);redeemButton.setVisibility(View.GONE);p.addView(redeemButton,top(8));swap(scroll(p),true);
    }
    private void scanQr(){IntentIntegrator i=new IntentIntegrator(this);i.setDesiredBarcodeFormats(java.util.Collections.singletonList("QR_CODE"));i.setPrompt("Escanea el premio");i.setBeepEnabled(false);i.initiateScan();}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){IntentResult r=IntentIntegrator.parseActivityResult(requestCode,resultCode,data);if(r!=null){if(r.getContents()!=null){showRedeem();String code=PromoRules.cleanCode(r.getContents());if(redeemInput!=null)redeemInput.setText(code);validateCode(code);}return;}super.onActivityResult(requestCode,resultCode,data);}
    private void validateCode(String raw){String code=PromoRules.cleanCode(raw);validatedCode="";if(code.isEmpty()){toast("Código vacío");return;}io.execute(()->{try{JSONObject r=core.post(core.action("promotionValidate").put("code",code));boolean can=r.optBoolean("canRedeem",false);runOnUiThread(()->{validatedCode=can?r.optString("code",code):"";if(redeemResult!=null){redeemResult.setText("Premio: "+dash(r.optString("prize",""))+"\nEstado: "+dash(r.optString("state",""))+"\nPromoción: "+dash(r.optString("promotionName",""))+(can?"\n\nLISTO PARA CANJEAR":"\n\nNo se puede canjear"));redeemResult.setTextColor(can?V5Ui.GREEN:V5Ui.ERROR);}if(redeemButton!=null)redeemButton.setVisibility(can?View.VISIBLE:View.GONE);});}catch(Exception e){runOnUiThread(()->alert("No se pudo validar",msg(e)));}});}
    private void confirmRedeem(){if(validatedCode.isEmpty())return;String code=validatedCode;new AlertDialog.Builder(this).setTitle("Confirmar canje").setMessage("Este código quedará inutilizado.").setNegativeButton("Cancelar",null).setPositiveButton("Canjear",(d,w)->io.execute(()->{try{JSONObject r=core.post(core.action("promotionRedeem").put("code",code));runOnUiThread(()->{validatedCode="";if(redeemResult!=null)redeemResult.setText("CANJEADO\n"+r.optString("prize","Premio"));if(redeemButton!=null)redeemButton.setVisibility(View.GONE);toast("Canje registrado");});}catch(Exception e){runOnUiThread(()->alert("No se pudo canjear",msg(e)));}})).show();}

    // ESTADÍSTICAS
    private View buildStats(){
        LinearLayout p=page();p.addView(V5Ui.kicker(this,"ESTADÍSTICAS"));p.addView(V5Ui.title(this,"Datos del negocio"));
        V5StatsEngine.Range range=V5StatsEngine.range(V5StatsEngine.Period.LAST_7_DAYS,null,null);V5StatsEngine.ReservationStats rs=V5StatsEngine.reservations(reservations,range);V5StatsEngine.PromotionStats ps=V5StatsEngine.promotions(promoHistory,range);
        LinearLayout a=V5Ui.row(this);a.addView(V5Ui.metric(this,"RESERVAS",String.valueOf(rs.reservations),V5StatsEngine.percent(rs.reservationChangePct)+" vs. anterior"),weight());a.addView(V5Ui.metric(this,"PERSONAS",String.valueOf(rs.people),V5StatsEngine.percent(rs.peopleChangePct)+" vs. anterior"),weightGap());p.addView(a,top(10));
        LinearLayout b=V5Ui.row(this);b.addView(V5Ui.metric(this,"JUGADAS",String.valueOf(ps.plays),V5StatsEngine.percent(ps.playsChangePct)+" vs. anterior"),weight());b.addView(V5Ui.metric(this,"CANJES",String.valueOf(ps.redeemed),String.format(new Locale("es","ES"),"%.0f %% de ganadores",ps.redeemRate)),weightGap());p.addView(b,top(7));
        LinearLayout detail=V5Ui.softCard(this);detail.addView(V5Ui.text(this,"Hora punta · "+rs.peakHour+"   ·   Interior "+rs.interior+"   ·   Terraza "+rs.terrace,10.5f,V5Ui.MUTED,false));detail.addView(V5Ui.text(this,"Confirmadas "+rs.confirmed+" · Pendientes "+rs.pending+" · No-show "+rs.noShow,10.5f,V5Ui.MUTED,false));p.addView(detail,top(8));
        StatsBarChartView chart=new StatsBarChartView(this);chart.setData(rs.byDay);LinearLayout cc=V5Ui.card(this);cc.addView(V5Ui.kicker(this,"RESERVAS · 7 DÍAS"));cc.addView(chart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,170)));p.addView(cc,top(8));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_chart_v5,"Dashboard completo","Hoy, 7 días, 30 días, mes y periodo personalizado",()->open(StatsDashboardActivity.class)),top(8));return scroll(p);
    }

    // GESTIÓN
    private View buildManagement(){
        LinearLayout p=page();p.addView(V5Ui.kicker(this,"GESTIÓN"));p.addView(V5Ui.title(this,"Contenido y recursos"));p.addView(V5Ui.subtitle(this,"Herramientas operativas y contenido del mesón."));
        p.addView(sectionLabel("IMPRESIÓN"));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_print_v5,"Crear ticket","Texto, QR, imagen y varias copias",()->open(FreePrintActivity.class)));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_grid_v5,"Plantillas","Editar, duplicar, importar, exportar y predeterminadas",()->open(V5TemplateManagerActivity.class)),top(7));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_calendar_v5,"Cola de impresión","Enviados, errores y reintentos manuales",()->open(PrintQueueActivity.class)),top(7));
        p.addView(sectionLabel("CONTENIDO"));p.addView(V5Ui.linkCard(this,R.drawable.ic_grid_v5,"Web","Carta, menú, avisos, popup y horarios",()->open(WebManagementActivity.class)));
        p.addView(sectionLabel("SEGURIDAD"));p.addView(V5Ui.linkCard(this,R.drawable.ic_settings_v5,"Copia de seguridad","Exportar o restaurar configuración y plantillas",()->open(BackupRestoreActivity.class)));
        return scroll(p);
    }

    // CONFIGURACIÓN
    private View buildSettings(){
        LinearLayout p=page();p.addView(V5Ui.kicker(this,"CONFIGURACIÓN"));p.addView(V5Ui.title(this,"Sistema y dispositivo"));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_print_v5,"Impresora",printerSummary(),()->open(PrinterSettingsActivity.class)),top(10));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_settings_v5,"Diagnóstico","Backend, caché e impresión",()->open(DiagnosticsActivity.class)),top(7));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_settings_v5,"Terminal",core.terminal(),this::editTerminal),top(7));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_chart_v5,"Conexión",core.configured()?"Servidor configurado":"Falta clave",this::editConnection),top(7));
        p.addView(V5Ui.linkCard(this,R.drawable.ic_settings_v5,"Backup","Copia y restauración",()->open(BackupRestoreActivity.class)),top(7));
        p.addView(sectionLabel("APLICACIÓN"));p.addView(V5Ui.text(this,"O Faro Gestión · "+BuildConfig.VERSION_NAME,10,V5Ui.FAINT,false));return scroll(p);
    }
    private void editTerminal(){LinearLayout b=V5Ui.column(this);b.setPadding(V5Ui.dp(this,16),4,V5Ui.dp(this,16),4);EditText name=input(core.terminal());b.addView(V5Ui.text(this,"Nombre del terminal",10,V5Ui.MUTED,true));b.addView(name);new AlertDialog.Builder(this).setTitle("Terminal").setView(b).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{core.prefs().edit().putString("terminal",value(name)).apply();showSection(SETTINGS,false);}).show();}
    private void editConnection(){LinearLayout b=V5Ui.column(this);b.setPadding(V5Ui.dp(this,16),4,V5Ui.dp(this,16),4);EditText key=input(core.key());key.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);b.addView(V5Ui.text(this,"Clave de gestión",10,V5Ui.MUTED,true));b.addView(key);new AlertDialog.Builder(this).setTitle("Conexión").setView(b).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{core.prefs().edit().putString("key",value(key)).apply();showSection(SETTINGS,false);}).show();}

    // SINCRONIZACIÓN
    private void refreshAll(boolean user){if(!core.configured()){if(user)toast("Configura primero la conexión");return;}io.execute(()->{refreshReservations();refreshPromotions();try{JSONObject h=core.post(core.action("promotionHistory"));JSONArray a=array(h,"items","history","results");promoHistory=a;core.saveArray("promo_history_v5",a);}catch(Exception ignored){}runOnUiThread(()->{if(user)toast("Datos actualizados");showSection(section,false);});});}
    private void refreshReservations(){try{JSONObject r=core.post(core.action("reservationList"));JSONArray a=array(r,"items","reservations");reservations=a;core.saveArray("reservations_all_v5",a);}catch(Exception ignored){}}
    private void refreshPromotions(){try{JSONObject r=core.post(core.action("promotionList"));JSONArray a=array(r,"items","campaigns");campaigns=a;core.saveArray("promo_campaigns",a);}catch(Exception ignored){}try{JSONObject r=core.post(core.action("promotionPrizeList"));JSONArray a=array(r,"items","prizes");prizes=a;core.saveArray("promo_prizes",a);}catch(Exception ignored){}}
    private void refreshCaches(){reservations=core.cachedArray("reservations_all_v5");campaigns=core.cachedArray("promo_campaigns");prizes=core.cachedArray("promo_prizes");promoHistory=core.cachedArray("promo_history_v5");}
    private JSONArray array(JSONObject o,String...keys){for(String k:keys){JSONArray a=o.optJSONArray(k);if(a!=null)return a;}return new JSONArray();}
    private void refreshPrinter(){if(printerState==null)return;String ip=core.printerIp();printerState.setText(ip.isEmpty()?"• Sin impresora":"• "+ip);printerState.setTextColor(ip.isEmpty()?V5Ui.MUTED:V5Ui.GREEN);}

    // HELPERS DE DATOS
    private String nameOf(JSONObject r){return r.optString("name",r.optString("nombre","Reserva"));}
    private String phoneOf(JSONObject r){return r.optString("phone",r.optString("telefono",""));}
    private String emailOf(JSONObject r){return r.optString("email",r.optString("correo",""));}
    private String dateOf(JSONObject r){return r.optString("date",r.optString("fecha",""));}
    private String timeOf(JSONObject r){return r.optString("time",r.optString("hora","--:--"));}
    private String tableOf(JSONObject r){return r.optString("table",r.optString("mesa",""));}
    private String zoneOf(JSONObject r){return r.optString("zone",r.optString("zona",""));}
    private String notesOf(JSONObject r){return r.optString("notes",r.optString("observaciones",""));}
    private String stateOf(JSONObject r){return r.optString("state",r.optString("estado","Pendiente"));}
    private int peopleOf(JSONObject r){return r.optInt("people",r.optInt("personas",0));}

    // UI HELPERS
    private LinearLayout darkMetric(String value,String note){LinearLayout c=V5Ui.column(this);c.addView(V5Ui.text(this,value,30,Color.WHITE,true));c.addView(V5Ui.text(this,note,10.5f,Color.rgb(197,207,198),false));return c;}
    private TextView statePill(String state){int fill=V5Ui.LIME_SOFT,color=V5Ui.GREEN;if(state.equalsIgnoreCase("Pendiente")){fill=Color.rgb(235,236,232);color=V5Ui.MUTED;}if(state.equalsIgnoreCase("Cancelada")||state.equalsIgnoreCase("Denegada")){fill=Color.rgb(248,229,225);color=V5Ui.ERROR;}return V5Ui.pill(this,state,fill,color);}
    private TextView sectionLabel(String s){TextView t=V5Ui.kicker(this,s);t.setPadding(0,V5Ui.dp(this,16),0,V5Ui.dp(this,7));return t;}
    private TextView chip(String s,boolean on){TextView t=V5Ui.text(this,s,9.2f,on?Color.WHITE:V5Ui.MUTED,true);t.setGravity(Gravity.CENTER);t.setBackground(V5Ui.bg(this,on?V5Ui.GREEN:V5Ui.SURFACE_ALT,14));return t;}
    private LinearLayout.LayoutParams chipWeight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,V5Ui.dp(this,38),1);p.setMargins(V5Ui.dp(this,2),0,V5Ui.dp(this,2),0);return p;}
    private TextView action(String s,boolean primary,Runnable r){TextView t=V5Ui.text(this,s,10.5f,primary?Color.WHITE:V5Ui.GREEN,true);t.setGravity(Gravity.CENTER);t.setMinHeight(V5Ui.dp(this,46));t.setBackground(V5Ui.bg(this,primary?V5Ui.GREEN:V5Ui.SURFACE_ALT,15));t.setOnClickListener(v->r.run());return t;}
    private TextView backLink(String s,Runnable r){TextView t=V5Ui.text(this,"‹  "+s,10.5f,V5Ui.GREEN,true);t.setGravity(Gravity.CENTER_VERTICAL);t.setOnClickListener(v->r.run());t.setPadding(0,0,0,V5Ui.dp(this,9));return t;}
    private EditText input(String initial){EditText e=new EditText(this);e.setText(initial);e.setTextSize(12.5f);e.setTextColor(V5Ui.INK);e.setSingleLine(true);e.setPadding(V5Ui.dp(this,12),0,V5Ui.dp(this,12),0);e.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));return e;}
    private EditText field(LinearLayout p,String label,String initial,int type){TextView l=V5Ui.text(this,label,10,V5Ui.MUTED,true);l.setPadding(0,V5Ui.dp(this,9),0,V5Ui.dp(this,4));p.addView(l);EditText e=input(initial);e.setInputType(type);p.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,46)));return e;}
    private EditText multi(LinearLayout p,String label,String initial){TextView l=V5Ui.text(this,label,10,V5Ui.MUTED,true);l.setPadding(0,V5Ui.dp(this,9),0,V5Ui.dp(this,4));p.addView(l);EditText e=new EditText(this);e.setText(initial);e.setTextSize(12.5f);e.setTextColor(V5Ui.INK);e.setGravity(Gravity.TOP);e.setMinLines(4);e.setPadding(V5Ui.dp(this,12),V5Ui.dp(this,8),V5Ui.dp(this,12),V5Ui.dp(this,8));e.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));p.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,116)));return e;}
    private EditText picker(LinearLayout p,String label,String initial,boolean date){EditText e=field(p,label,initial,InputType.TYPE_CLASS_TEXT);e.setFocusable(false);e.setOnClickListener(v->{if(date){LocalDate d;try{d=LocalDate.parse(value(e));}catch(Exception x){d=LocalDate.now();}LocalDate f=d;new DatePickerDialog(this,(x,y,m,day)->e.setText(LocalDate.of(y,m+1,day).toString()),f.getYear(),f.getMonthValue()-1,f.getDayOfMonth()).show();}else{LocalTime t;try{t=LocalTime.parse(value(e));}catch(Exception x){t=LocalTime.of(14,0);}LocalTime f=t;new TimePickerDialog(this,(x,h,min)->e.setText(String.format(Locale.ROOT,"%02d:%02d",h,min)),f.getHour(),f.getMinute(),true).show();}});return e;}
    private Spinner spinner(LinearLayout p,String label,String[] values,int selected){TextView l=V5Ui.text(this,label,10,V5Ui.MUTED,true);l.setPadding(0,V5Ui.dp(this,9),0,V5Ui.dp(this,4));p.addView(l);Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setSelection(Math.max(0,Math.min(values.length-1,selected)));s.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));p.addView(s,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,46)));return s;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);}
    private LinearLayout.LayoutParams weightGap(){LinearLayout.LayoutParams p=weight();p.leftMargin=V5Ui.dp(this,7);return p;}
    private LinearLayout.LayoutParams top(int dp){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=V5Ui.dp(this,dp);return p;}
    private LinearLayout.LayoutParams bottom(int dp){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=V5Ui.dp(this,dp);return p;}
    private String reservationTitle(){if("PRÓXIMAS".equals(reservationMode))return"Próximas reservas";if("PENDIENTES".equals(reservationMode))return"Por confirmar";if("HISTÓRICO".equals(reservationMode))return"Histórico";return"Reservas de hoy";}
    private String labelMode(String s){return s.substring(0,1)+s.substring(1).toLowerCase(new Locale("es","ES"));}
    private int activeCampaigns(){int n=0;for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);if(c!=null&&c.optBoolean("active",false))n++;}return n;}
    private int prizeCount(){int n=0;for(int i=0;i<prizes.length();i++){JSONObject p=prizes.optJSONObject(i);if(p!=null&&!"P000".equals(p.optString("id")))n++;}return n;}
    private String printerSummary(){return core.printerIp().isEmpty()?"Sin configurar":core.printerIp()+":"+core.printerPort()+" · "+core.printerPaper()+" mm";}
    private String currentDate(){return LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM",new Locale("es","ES")));}
    private String greeting(){int h=LocalTime.now().getHour();return h<12?"Buenos días":h<20?"Buenas tardes":"Buenas noches";}
    private String dash(String s){return s==null||s.trim().isEmpty()?"—":s.trim();}
    private String value(EditText e){return e==null?"":e.getText().toString().trim();}
    private int parse(String s,int fallback){try{return Integer.parseInt(s.trim());}catch(Exception e){return fallback;}}
    private String msg(Exception e){String m=e.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(e):m;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void alert(String t,String m){new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Cerrar",null).show();}
    private void open(Class<?> c){startActivity(new Intent(this,c));overridePendingTransition(0,0);}
    private void dial(String phone){if(phone==null||phone.trim().isEmpty()){toast("Sin teléfono");return;}startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+phone.replace(" ",""))));}
    private void whatsapp(String phone){if(phone==null||phone.trim().isEmpty()){toast("Sin teléfono");return;}String clean=phone.replaceAll("[^0-9]","");if(!clean.startsWith("34"))clean="34"+clean;startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://wa.me/"+clean)));}
    private void copy(String s){ClipboardManager c=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);c.setPrimaryClip(ClipData.newPlainText("O Faro",s));toast("Copiado");}
    private void quickActions(){new AlertDialog.Builder(this).setTitle("Acción rápida").setItems(new String[]{"Nueva reserva","Generar QR","Canjear premio","Crear ticket"},(d,w)->{if(w==0)showReservationForm(null);else if(w==1)showQrGenerator();else if(w==2)showRedeem();else open(FreePrintActivity.class);}).show();}

    private static final class SimpleTextWatcher implements android.text.TextWatcher{
        private final Runnable action;SimpleTextWatcher(Runnable action){this.action=action;}
        @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
        @Override public void onTextChanged(CharSequence s,int start,int before,int count){action.run();}
        @Override public void afterTextChanged(android.text.Editable s){}
    }
}
