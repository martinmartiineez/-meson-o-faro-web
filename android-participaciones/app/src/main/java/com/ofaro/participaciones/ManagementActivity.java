package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ManagementActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppCore core;
    private LinearLayout page;
    private TextView headerSubtitle;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        core = new AppCore(this);
        setContentView(buildRoot());
        showHome();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246,246,246));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20),dp(18),dp(20),dp(16));
        header.setBackgroundColor(Color.rgb(17,17,17));
        TextView title = text("MESÓN O FARO",23,Color.WHITE,true);
        headerSubtitle = text("Gestión e impresión",14,Color.rgb(210,210,210),false);
        header.addView(title); header.addView(headerSubtitle);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(20),dp(18),dp(30));
        scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        return root;
    }

    private void showHome() {
        clear("Gestión e impresión");
        page.addView(sectionTitle("¿Qué quieres hacer?"));
        page.addView(paragraph("Herramientas internas de Mesón O Faro conectadas con Google Sheets y la impresora térmica."));

        Button reservations = bigButton("RESERVAS", "Consultar, crear e imprimir");
        reservations.setOnClickListener(v -> showReservations()); page.addView(reservations);

        LinearLayout row1 = row();
        Button participation = tileButton("PARTICIPACIONES"); participation.setOnClickListener(v -> startActivity(new Intent(this,MainActivity.class)));
        Button qr = tileButton("QR RÁPIDOS"); qr.setOnClickListener(v -> showQr());
        row1.addView(participation,weight()); row1.addView(qr,weight()); page.addView(row1,marginTopWrap(dp(10)));

        LinearLayout row2 = row();
        Button templates = tileButton("PLANTILLAS"); templates.setOnClickListener(v -> showTemplates());
        Button free = tileButton("IMPRESIÓN LIBRE"); free.setOnClickListener(v -> showFreePrint());
        row2.addView(templates,weight()); row2.addView(free,weight()); page.addView(row2,marginTopWrap(dp(10)));

        LinearLayout row3 = row();
        Button history = tileButton("HISTORIAL"); history.setOnClickListener(v -> showHistory());
        Button settings = tileButton("AJUSTES"); settings.setOnClickListener(v -> showSettings());
        row3.addView(history,weight()); row3.addView(settings,weight()); page.addView(row3,marginTopWrap(dp(10)));

        TextView status = text(core.configured() ? "API configurada · " + core.terminal() : "Falta configurar la API interna",13,Color.DKGRAY,false);
        status.setPadding(dp(4),dp(22),dp(4),0); page.addView(status);
    }

    private void showReservations() {
        clear("Reservas"); addBack("Reservas");
        TextView summary = text("Cargando reservas de hoy…",15,Color.DKGRAY,false); page.addView(summary);
        Button create = primaryButton("NUEVA RESERVA"); create.setOnClickListener(v -> showNewReservation()); page.addView(create,marginTop(dp(14)));
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list,marginTopWrap(dp(16)));
        if (!requireApi()) return;
        String today = LocalDate.now().toString();
        io.execute(() -> {
            try {
                JSONObject res = core.post(core.action("reservationList").put("date",today).put("limit",100)); core.ensureOk(res);
                JSONArray items = res.optJSONArray("items"); int people = res.optInt("totalPeople",0); int count = items == null ? 0 : items.length();
                runOnUiThread(() -> {
                    summary.setText("HOY · " + count + (count==1?" reserva":" reservas") + " · " + people + " personas");
                    list.removeAllViews();
                    if (count == 0) list.addView(emptyText("No hay reservas para hoy."));
                    else for (int i=0;i<count;i++) addReservationCard(list,items.optJSONObject(i));
                });
            } catch (Exception e) { runOnUiThread(() -> summary.setText("Error: "+cleanError(e))); }
        });
    }

    private void addReservationCard(LinearLayout list, JSONObject r) {
        if (r == null) return;
        LinearLayout card = cardBox();
        TextView top = text(r.optString("time","--:--") + " · " + r.optString("name",""),19,Color.rgb(20,20,20),true);
        card.addView(top);
        String detail = r.optInt("people",0)+" personas";
        if (!r.optString("table","").isEmpty()) detail += " · Mesa "+r.optString("table");
        if (!r.optString("zone","").isEmpty() && !"Sin asignar".equalsIgnoreCase(r.optString("zone"))) detail += " · "+r.optString("zone");
        card.addView(text(detail,14,Color.DKGRAY,false));
        String notes = r.optString("notes",""); if (!notes.isEmpty()) { TextView n=text(notes,14,Color.rgb(50,50,50),false); n.setPadding(0,dp(8),0,0); card.addView(n); }
        TextView state = text("Reserva: "+r.optString("state","—")+" · Servicio: "+value(r.optString("serviceState","Pendiente")),13,Color.DKGRAY,false); state.setPadding(0,dp(8),0,dp(8)); card.addView(state);

        LinearLayout actions = row();
        Button print = miniButton("IMPRIMIR"); print.setOnClickListener(v -> printReservation(r,print));
        Button arrived = miniButton("LLEGÓ"); arrived.setOnClickListener(v -> updateReservationState(r,"Llegó",arrived));
        Button done = miniButton("COMPLETADA"); done.setOnClickListener(v -> updateReservationState(r,"Completada",done));
        actions.addView(print,weight()); actions.addView(arrived,weight()); actions.addView(done,weight()); card.addView(actions);
        list.addView(card,marginBottomWrap(dp(10)));
    }

    private void showNewReservation() {
        clear("Nueva reserva"); addBackTo("Nueva reserva",this::showReservations);
        EditText name = field("Nombre","",InputType.TYPE_CLASS_TEXT);
        EditText phone = field("Teléfono","",InputType.TYPE_CLASS_PHONE);
        EditText email = field("Correo (opcional)","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        EditText date = field("Fecha",LocalDate.now().toString(),InputType.TYPE_CLASS_DATETIME);
        EditText time = field("Hora","14:00",InputType.TYPE_CLASS_DATETIME);
        EditText people = field("Personas","2",InputType.TYPE_CLASS_NUMBER);
        EditText table = field("Mesa (opcional)","",InputType.TYPE_CLASS_TEXT);
        EditText zone = field("Zona: Interior / Terraza / Sin asignar","Sin asignar",InputType.TYPE_CLASS_TEXT);
        EditText notes = fieldMulti("Observaciones","",4);
        Button savePrint = primaryButton("GUARDAR E IMPRIMIR");
        Button save = secondaryButton("GUARDAR SIN IMPRIMIR");
        page.addView(savePrint,marginTop(dp(16))); page.addView(save,marginTop(dp(10)));
        View.OnClickListener submit = v -> createReservation(name,phone,email,date,time,people,table,zone,notes,v==savePrint,(Button)v);
        savePrint.setOnClickListener(submit); save.setOnClickListener(submit);
    }

    private void createReservation(EditText name,EditText phone,EditText email,EditText date,EditText time,EditText people,EditText table,EditText zone,EditText notes,boolean print,Button button) {
        if (!requireApi()) return;
        if (name.getText().toString().trim().isEmpty() || phone.getText().toString().trim().isEmpty()) { alert("Faltan datos","Nombre y teléfono son obligatorios."); return; }
        button.setEnabled(false);
        io.execute(() -> {
            try {
                JSONObject body = core.action("reservationCreate")
                        .put("nombre",name.getText().toString().trim()).put("telefono",phone.getText().toString().trim())
                        .put("correo",email.getText().toString().trim()).put("fecha",date.getText().toString().trim())
                        .put("hora",time.getText().toString().trim()).put("personas",safeInt(people.getText().toString(),2,1,30))
                        .put("mesa",table.getText().toString().trim()).put("zona",zone.getText().toString().trim())
                        .put("observaciones",notes.getText().toString().trim());
                JSONObject res = core.post(body); core.ensureOk(res);
                String id = res.optString("id","");
                if (print) {
                    JSONObject r = new JSONObject().put("id",id).put("date",date.getText().toString().trim()).put("time",time.getText().toString().trim())
                            .put("name",name.getText().toString().trim()).put("phone",phone.getText().toString().trim()).put("people",safeInt(people.getText().toString(),2,1,30))
                            .put("table",table.getText().toString().trim()).put("zone",zone.getText().toString().trim()).put("notes",notes.getText().toString().trim());
                    core.printReservation(r);
                    try { core.post(core.action("reservationMarkPrinted").put("id",id)); } catch (Exception ignored) {}
                }
                runOnUiThread(() -> { toast(print?"Reserva guardada e impresa":"Reserva guardada"); showReservations(); });
            } catch (Exception e) { runOnUiThread(() -> { button.setEnabled(true); alert("No se pudo guardar",cleanError(e)); }); }
        });
    }

    private void printReservation(JSONObject r, Button button) {
        if (core.printerIp().isEmpty()) { alert("Falta impresora","Configura la IP en Ajustes."); return; }
        button.setEnabled(false);
        io.execute(() -> {
            try {
                core.printReservation(r);
                if (core.configured()) try { core.post(core.action("reservationMarkPrinted").put("id",r.optString("id",""))); } catch(Exception ignored) {}
                runOnUiThread(() -> { button.setEnabled(true); toast("Reserva impresa"); });
            } catch(Exception e) { runOnUiThread(() -> { button.setEnabled(true); alert("Error de impresión",cleanError(e)); }); }
        });
    }

    private void updateReservationState(JSONObject r,String state,Button button) {
        if (!requireApi()) return; button.setEnabled(false);
        io.execute(() -> {
            try { JSONObject res=core.post(core.action("reservationUpdate").put("id",r.optString("id","")).put("serviceState",state)); core.ensureOk(res);
                runOnUiThread(() -> { toast("Estado: "+state); showReservations(); });
            } catch(Exception e) { runOnUiThread(() -> { button.setEnabled(true); alert("Error",cleanError(e)); }); }
        });
    }

    private void showQr() {
        clear("QR rápidos"); addBack("QR rápidos");
        TextView status = text("Cargando QR…",15,Color.DKGRAY,false); page.addView(status);
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list,marginTopWrap(dp(12)));
        if (!requireApi()) return;
        io.execute(() -> loadItems("qrList",(items) -> {
            status.setText(items.length()==0?"No hay QR activos.":items.length()+" accesos disponibles"); list.removeAllViews();
            for(int i=0;i<items.length();i++) { JSONObject q=items.optJSONObject(i); if(q!=null) addQrCard(list,q); }
        },status));
    }

    private void addQrCard(LinearLayout list,JSONObject q) {
        LinearLayout card=cardBox();
        card.addView(text(q.optString("name","QR"),18,Color.rgb(20,20,20),true));
        TextView d=text(q.optString("ticketText",q.optString("content","")),13,Color.DKGRAY,false); d.setPadding(0,dp(5),0,dp(8)); card.addView(d);
        Button print=primaryButton("IMPRIMIR QR"); print.setOnClickListener(v -> printQr(q,print)); card.addView(print);
        list.addView(card,marginBottomWrap(dp(10)));
    }

    private void printQr(JSONObject q,Button button) {
        if (core.printerIp().isEmpty()) { alert("Falta impresora","Configura la IP en Ajustes."); return; }
        button.setEnabled(false); io.execute(() -> {
            try { core.printQrTicket("MESON O FARO",q.optString("ticketText",q.optString("name","")),q.optString("content",""));
                logPrint("QR",q.optString("id",""),q.optString("name",""));
                runOnUiThread(() -> { button.setEnabled(true); toast("QR impreso"); });
            } catch(Exception e) { runOnUiThread(() -> { button.setEnabled(true); alert("Error de impresión",cleanError(e)); }); }
        });
    }

    private void showTemplates() {
        clear("Plantillas"); addBack("Plantillas");
        TextView status=text("Cargando plantillas…",15,Color.DKGRAY,false); page.addView(status);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list,marginTopWrap(dp(12)));
        if(!requireApi()) return;
        io.execute(() -> loadItems("templateList",(items) -> {
            status.setText(items.length()+" plantillas activas"); list.removeAllViews();
            for(int i=0;i<items.length();i++) { JSONObject t=items.optJSONObject(i); if(t!=null) addTemplateCard(list,t); }
        },status));
    }

    private void addTemplateCard(LinearLayout list,JSONObject t) {
        LinearLayout card=cardBox(); card.addView(text(t.optString("name","Plantilla"),18,Color.rgb(20,20,20),true));
        TextView type=text(t.optString("type",""),13,Color.DKGRAY,false); type.setPadding(0,dp(4),0,dp(8)); card.addView(type);
        Button print=primaryButton("IMPRIMIR");
        if ("QR".equalsIgnoreCase(t.optString("type","")) && t.optJSONObject("qr")!=null) print.setOnClickListener(v -> printTemplate(t,print));
        else { print.setEnabled(false); print.setText("PLANTILLA OPERATIVA EN SU MÓDULO"); }
        card.addView(print); list.addView(card,marginBottomWrap(dp(10)));
    }

    private void printTemplate(JSONObject t,Button button) {
        JSONObject q=t.optJSONObject("qr"); if(q==null) return;
        if(core.printerIp().isEmpty()) { alert("Falta impresora","Configura la IP en Ajustes."); return; }
        button.setEnabled(false); io.execute(() -> {
            try { core.printQrTicket(t.optString("title","MESON O FARO"),t.optString("text",q.optString("ticketText","")),q.optString("content",""));
                logPrint("Plantilla",t.optString("id",""),t.optString("name",""));
                runOnUiThread(() -> { button.setEnabled(true); toast("Plantilla impresa"); });
            } catch(Exception e) { runOnUiThread(() -> { button.setEnabled(true); alert("Error",cleanError(e)); }); }
        });
    }

    private void showFreePrint() {
        clear("Impresión libre"); addBack("Impresión libre");
        page.addView(paragraph("Escribe cualquier aviso o texto. Puedes añadir un QR opcional con una URL o contenido."));
        EditText title=field("Título","",InputType.TYPE_CLASS_TEXT);
        EditText body=fieldMulti("Texto","",7);
        EditText qr=field("QR opcional (URL o texto)","",InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        Button print=primaryButton("IMPRIMIR"); page.addView(print,marginTop(dp(16)));
        print.setOnClickListener(v -> {
            if(core.printerIp().isEmpty()) { alert("Falta impresora","Configura la IP en Ajustes."); return; }
            if(title.getText().toString().trim().isEmpty() && body.getText().toString().trim().isEmpty()) { alert("Sin contenido","Escribe un título o un texto."); return; }
            print.setEnabled(false); io.execute(() -> {
                try { core.printFreeText(title.getText().toString(),body.getText().toString(),qr.getText().toString());
                    logPrint("Libre","",title.getText().toString()); runOnUiThread(() -> { print.setEnabled(true); toast("Impresión enviada"); });
                } catch(Exception e) { runOnUiThread(() -> { print.setEnabled(true); alert("Error de impresión",cleanError(e)); }); }
            });
        });
    }

    private void showHistory() {
        clear("Historial"); addBack("Historial");
        TextView status=text("Cargando historial…",15,Color.DKGRAY,false); page.addView(status);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list,marginTopWrap(dp(12)));
        if(!requireApi()) return;
        io.execute(() -> {
            try { JSONObject res=core.post(core.action("historyList").put("limit",60)); core.ensureOk(res); JSONArray items=res.optJSONArray("items");
                runOnUiThread(() -> { int n=items==null?0:items.length(); status.setText(n+" movimientos recientes"); list.removeAllViews();
                    if(n==0) list.addView(emptyText("Todavía no hay movimientos."));
                    else for(int i=0;i<n;i++) { JSONObject h=items.optJSONObject(i); if(h!=null) addHistoryRow(list,h); }
                });
            } catch(Exception e) { runOnUiThread(() -> status.setText("Error: "+cleanError(e))); }
        });
    }

    private void addHistoryRow(LinearLayout list,JSONObject h) {
        LinearLayout card=cardBox(); card.addView(text(h.optString("type","Acción")+" · "+h.optString("action",""),16,Color.rgb(20,20,20),true));
        card.addView(text(h.optString("date","")+" · "+h.optString("reference",""),13,Color.DKGRAY,false));
        String detail=h.optString("detail",""); if(!detail.isEmpty()) { TextView d=text(detail,13,Color.DKGRAY,false); d.setPadding(0,dp(5),0,0); card.addView(d); }
        list.addView(card,marginBottomWrap(dp(8)));
    }

    private void showSettings() {
        clear("Ajustes"); addBack("Ajustes");
        SharedPreferences p=core.prefs();
        EditText api=field("Endpoint Apps Script",p.getString("api",AppCore.DEFAULT_API),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText key=field("Clave API",p.getString("key",""),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText ip=field("IP impresora ESC/POS",p.getString("printerIp",""),InputType.TYPE_CLASS_PHONE);
        EditText port=field("Puerto",String.valueOf(p.getInt("printerPort",9100)),InputType.TYPE_CLASS_NUMBER);
        EditText terminal=field("Nombre del terminal",p.getString("terminal","Caja O Faro"),InputType.TYPE_CLASS_TEXT);
        Button save=primaryButton("GUARDAR AJUSTES"); page.addView(save,marginTop(dp(16)));
        Button testApi=secondaryButton("PROBAR CONEXIÓN CON GOOGLE"); page.addView(testApi,marginTop(dp(10)));
        Button testPrinter=secondaryButton("IMPRIMIR TICKET DE PRUEBA"); page.addView(testPrinter,marginTop(dp(10)));
        save.setOnClickListener(v -> {
            p.edit().putString("api",api.getText().toString().trim()).putString("key",key.getText().toString().trim())
                    .putString("printerIp",ip.getText().toString().trim()).putInt("printerPort",safeInt(port.getText().toString(),9100,1,65535))
                    .putString("terminal",terminal.getText().toString().trim()).apply(); toast("Ajustes guardados");
        });
        testApi.setOnClickListener(v -> {
            p.edit().putString("api",api.getText().toString().trim()).putString("key",key.getText().toString().trim()).putString("printerIp",ip.getText().toString().trim())
                    .putInt("printerPort",safeInt(port.getText().toString(),9100,1,65535)).putString("terminal",terminal.getText().toString().trim()).apply();
            testApi.setEnabled(false); io.execute(() -> {
                try { JSONObject res=core.post(core.action("appPing")); core.ensureOk(res); runOnUiThread(() -> { testApi.setEnabled(true); toast("Conexión correcta · API v"+res.optInt("version",2)); }); }
                catch(Exception e) { runOnUiThread(() -> { testApi.setEnabled(true); alert("Error de conexión",cleanError(e)); }); }
            });
        });
        testPrinter.setOnClickListener(v -> {
            String printerIp=ip.getText().toString().trim(); int printerPort=safeInt(port.getText().toString(),9100,1,65535);
            testPrinter.setEnabled(false); io.execute(() -> {
                try { core.printTest(printerIp,printerPort); runOnUiThread(() -> { testPrinter.setEnabled(true); toast("Ticket de prueba enviado"); }); }
                catch(Exception e) { runOnUiThread(() -> { testPrinter.setEnabled(true); alert("Error de impresora",cleanError(e)); }); }
            });
        });
    }

    private interface ItemsUi { void accept(JSONArray items); }
    private void loadItems(String action,ItemsUi ui,TextView status) {
        try { JSONObject res=core.post(core.action(action)); core.ensureOk(res); JSONArray items=res.optJSONArray("items"); if(items==null) items=new JSONArray(); JSONArray finalItems=items; runOnUiThread(() -> ui.accept(finalItems)); }
        catch(Exception e) { runOnUiThread(() -> status.setText("Error: "+cleanError(e))); }
    }

    private void logPrint(String type,String reference,String detail) {
        if(!core.configured()) return;
        try { core.post(core.action("historyAdd").put("type",type).put("reference",reference).put("event","Impreso").put("printer","IMP001").put("detail",detail).put("state","OK")); } catch(Exception ignored) {}
    }

    private boolean requireApi() { if(core.configured()) return true; alert("Falta configurar la API","Entra en Ajustes e introduce el endpoint y la clave de la app."); return false; }
    private void clear(String subtitle) { page.removeAllViews(); headerSubtitle.setText(subtitle); }
    private void addBack(String title) { addBackTo(title,this::showHome); }
    private void addBackTo(String title,Runnable action) {
        LinearLayout bar=row(); Button back=secondaryButton("‹ INICIO"); back.setOnClickListener(v -> action.run()); bar.addView(back,new LinearLayout.LayoutParams(dp(105),dp(48)));
        TextView t=text(title,22,Color.rgb(20,20,20),true); t.setGravity(Gravity.CENTER_VERTICAL); t.setPadding(dp(12),0,0,0); bar.addView(t,new LinearLayout.LayoutParams(0,dp(48),1)); page.addView(bar); page.addView(space(dp(14)));
    }

    private Button bigButton(String title,String subtitle) {
        Button b=primaryButton(title+"\n"+subtitle); b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL); b.setPadding(dp(18),0,dp(18),0); b.setMinHeight(dp(76)); return b;
    }
    private Button tileButton(String label) { Button b=secondaryButton(label); b.setMinHeight(dp(68)); return b; }
    private Button miniButton(String label) { Button b=secondaryButton(label); b.setTextSize(11); b.setMinHeight(dp(44)); return b; }
    private Button primaryButton(String label) { Button b=new Button(this); b.setText(label); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackground(roundRect(Color.rgb(17,17,17),12,Color.TRANSPARENT,0)); b.setMinHeight(dp(54)); return b; }
    private Button secondaryButton(String label) { Button b=new Button(this); b.setText(label); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setTextColor(Color.rgb(20,20,20)); b.setAllCaps(false); b.setBackground(roundRect(Color.WHITE,12,Color.rgb(190,190,190),1)); b.setMinHeight(dp(52)); return b; }
    private LinearLayout cardBox() { LinearLayout c=new LinearLayout(this); c.setOrientation(LinearLayout.VERTICAL); c.setPadding(dp(16),dp(15),dp(16),dp(15)); c.setBackground(roundRect(Color.WHITE,14,Color.rgb(225,225,225),1)); return c; }
    private LinearLayout row() { LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.HORIZONTAL); r.setGravity(Gravity.CENTER_VERTICAL); return r; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1); p.setMargins(dp(3),0,dp(3),0); return p; }
    private TextView sectionTitle(String s) { TextView t=text(s,26,Color.rgb(20,20,20),true); t.setPadding(0,0,0,dp(8)); return t; }
    private TextView paragraph(String s) { TextView t=text(s,15,Color.DKGRAY,false); t.setLineSpacing(0,1.15f); t.setPadding(0,0,0,dp(18)); return t; }
    private TextView emptyText(String s) { TextView t=text(s,15,Color.DKGRAY,false); t.setPadding(dp(4),dp(12),dp(4),dp(12)); return t; }
    private TextView text(String s,int sp,int color,boolean bold) { TextView t=new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private EditText field(String label,String value,int type) { TextView l=text(label,14,Color.DKGRAY,true); l.setPadding(dp(2),dp(12),dp(2),dp(6)); page.addView(l); EditText e=input(value); e.setInputType(type); e.setSingleLine(true); page.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54))); return e; }
    private EditText fieldMulti(String label,String value,int lines) { TextView l=text(label,14,Color.DKGRAY,true); l.setPadding(dp(2),dp(12),dp(2),dp(6)); page.addView(l); EditText e=input(value); e.setSingleLine(false); e.setMinLines(lines); e.setGravity(Gravity.TOP); e.setPadding(dp(14),dp(12),dp(14),dp(12)); page.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); return e; }
    private EditText input(String value) { EditText e=new EditText(this); e.setText(value); e.setTextSize(16); e.setTextColor(Color.rgb(20,20,20)); e.setHintTextColor(Color.GRAY); e.setPadding(dp(14),0,dp(14),0); e.setBackground(roundRect(Color.WHITE,12,Color.rgb(205,205,205),1)); return e; }
    private View space(int h) { View v=new View(this); v.setLayoutParams(new LinearLayout.LayoutParams(1,h)); return v; }
    private GradientDrawable roundRect(int fill,int radius,int stroke,int strokeWidth) { GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); if(strokeWidth>0)d.setStroke(dp(strokeWidth),stroke); return d; }
    private LinearLayout.LayoutParams marginTop(int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)); p.topMargin=top; return p; }
    private LinearLayout.LayoutParams marginTopWrap(int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin=top; return p; }
    private LinearLayout.LayoutParams marginBottomWrap(int bottom) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.bottomMargin=bottom; return p; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private int safeInt(String value,int fallback,int min,int max) { try { int n=Integer.parseInt(value.trim()); return Math.max(min,Math.min(max,n)); } catch(Exception e){ return fallback; } }
    private String value(String s) { return s==null||s.trim().isEmpty()?"Pendiente":s.trim(); }
    private String cleanError(Throwable t) { String m=t==null?"Error desconocido":t.getMessage(); if(m==null||m.trim().isEmpty())m=String.valueOf(t); return m.length()>420?m.substring(0,420):m; }
    private void toast(String s) { Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }
    private void alert(String title,String message) { if(!isFinishing()) new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Aceptar",null).show(); }
}
