package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class V5SectionActivity extends Activity {
    public static final String EXTRA_SECTION="section";
    public static final String STATS="ESTADÍSTICAS";
    public static final String MANAGEMENT="GESTIÓN";
    public static final String SETTINGS="CONFIGURACIÓN";

    private AppCore core;private String section;private TextView printerState;

    @Override protected void onCreate(Bundle state){super.onCreate(state);V5Ui.applySystemBars(this);core=new AppCore(this);section=getIntent().getStringExtra(EXTRA_SECTION);if(section==null||section.isEmpty())section=MANAGEMENT;core.startPrinterWatchdog();setContentView(build());refreshPrinter();}
    @Override protected void onResume(){super.onResume();if(core!=null){core.startPrinterWatchdog();refreshPrinter();}}

    private View build(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(V5Ui.BG);LinearLayout shell=V5Ui.column(this);
        V5Ui.Header header=V5Ui.header(this,core,headerSubtitle());printerState=header.printer;shell.addView(header.view);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);LinearLayout page=V5Ui.column(this);page.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,10),V5Ui.dp(this,18),V5Ui.dp(this,24));
        page.addView(V5Ui.kicker(this,section));TextView title=V5Ui.title(this,titleForSection());title.setPadding(0,V5Ui.dp(this,5),0,0);page.addView(title);TextView intro=V5Ui.subtitle(this,introForSection());intro.setPadding(0,V5Ui.dp(this,5),0,V5Ui.dp(this,14));page.addView(intro);
        if(STATS.equals(section))buildStats(page);else if(SETTINGS.equals(section))buildSettings(page);else buildManagement(page);
        scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));shell.addView(V5Ui.bottomNav(this,selectedIndex()));root.addView(shell,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        View plus=V5Ui.floatingPlus(this,this::quickActions);FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(V5Ui.dp(this,50),V5Ui.dp(this,50),Gravity.RIGHT|Gravity.BOTTOM);fp.rightMargin=V5Ui.dp(this,22);fp.bottomMargin=V5Ui.dp(this,73);root.addView(plus,fp);return root;
    }

    private void buildStats(LinearLayout page){
        page.addView(periods());
        JSONArray rs=core.cachedArray("reservations_HOY");int people=0;for(int i=0;i<rs.length();i++){JSONObject r=rs.optJSONObject(i);if(r!=null)people+=Math.max(0,r.optInt("people",0));}
        JSONArray cs=core.cachedArray("promo_campaigns");int active=0;for(int i=0;i<cs.length();i++){JSONObject c=cs.optJSONObject(i);if(c!=null&&c.optBoolean("active",false))active++;}
        LinearLayout r1=V5Ui.row(this);r1.addView(V5Ui.metric(this,"RESERVAS",String.valueOf(rs.length()),"hoy"),weightGap());r1.addView(V5Ui.metric(this,"PERSONAS",String.valueOf(people),"hoy"),weightGap());page.addView(r1,top(V5Ui.dp(this,13)));
        LinearLayout r2=V5Ui.row(this);r2.addView(V5Ui.metric(this,"PROMOCIONES",String.valueOf(active),"activas"),weightGap());r2.addView(V5Ui.metric(this,"WEB","—","analítica pendiente"),weightGap());page.addView(r2,top(V5Ui.dp(this,8)));
        page.addView(sectionLabel("MÓDULOS"));page.addView(V5Ui.linkCard(this,R.drawable.ic_calendar_v5,"Reservas","Personas, zonas, estados y evolución",()->startActivity(new Intent(this,ReservationsV5Activity.class))));page.addView(V5Ui.linkCard(this,R.drawable.ic_gift_v5,"Promociones","QR, jugadas, ganadores y canjes",()->startActivity(new Intent(this,PromotionsV5Activity.class))),top(V5Ui.dp(this,8)));page.addView(V5Ui.linkCard(this,R.drawable.ic_chart_v5,"Web","Visitas, acciones, embudos y dispositivos",null),top(V5Ui.dp(this,8)));page.addView(V5Ui.linkCard(this,R.drawable.ic_print_v5,"Impresión","Tickets, errores y actividad por terminal",null),top(V5Ui.dp(this,8)));
    }

    private View periods(){LinearLayout row=V5Ui.row(this);String[] values={"Hoy","7 días","30 días","Mes"};for(int i=0;i<values.length;i++){TextView chip=V5Ui.text(this,values[i],10,i==0?android.graphics.Color.WHITE:V5Ui.MUTED,true);chip.setGravity(Gravity.CENTER);chip.setBackground(V5Ui.bg(this,i==0?V5Ui.GREEN:V5Ui.SURFACE,16));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,V5Ui.dp(this,38),1);p.setMargins(V5Ui.dp(this,3),0,V5Ui.dp(this,3),0);row.addView(chip,p);}return row;}

    private void buildManagement(LinearLayout page){page.addView(sectionLabel("CONTENIDO Y OPERACIÓN"));page.addView(V5Ui.linkCard(this,R.drawable.ic_grid_v5,"Web","Carta, menú del día, avisos, popup y horarios",()->startActivity(new Intent(this,WebManagementActivity.class))));page.addView(V5Ui.linkCard(this,R.drawable.ic_print_v5,"Impresión","Crear ticket y abrir el estudio ESC/POS",()->startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre"))),top(V5Ui.dp(this,8)));page.addView(V5Ui.linkCard(this,R.drawable.ic_qr_v5,"Recursos","QR, plantillas e historial",()->startActivity(new Intent(this,ToolsActivity.class))),top(V5Ui.dp(this,8)));TextView note=V5Ui.text(this,"Plantillas v5: crear, editar, duplicar, eliminar, exportar y elegir predeterminadas.",11,V5Ui.MUTED,false);note.setPadding(V5Ui.dp(this,2),V5Ui.dp(this,14),V5Ui.dp(this,2),0);page.addView(note);}

    private void buildSettings(LinearLayout page){page.addView(sectionLabel("SISTEMA"));page.addView(V5Ui.linkCard(this,R.drawable.ic_print_v5,"Impresora",printerSummary(),()->startActivity(new Intent(this,PrinterSettingsActivity.class))));page.addView(V5Ui.linkCard(this,R.drawable.ic_settings_v5,"Terminal",core.terminal()+" · identidad del dispositivo",null),top(V5Ui.dp(this,8)));page.addView(V5Ui.linkCard(this,R.drawable.ic_chart_v5,"Conexión","Servidor, sincronización y endpoint de producción",()->startActivity(new Intent(this,DiagnosticsActivity.class))),top(V5Ui.dp(this,8)));page.addView(V5Ui.linkCard(this,R.drawable.ic_settings_v5,"Diagnóstico","Backend, plantillas, impresora y caché",()->startActivity(new Intent(this,DiagnosticsActivity.class))),top(V5Ui.dp(this,8)));page.addView(sectionLabel("APLICACIÓN"));page.addView(V5Ui.linkCard(this,R.drawable.ic_settings_v5,"Preferencias","Tema, sonidos, vibración y confirmaciones",null));page.addView(V5Ui.linkCard(this,R.drawable.ic_grid_v5,"Información","O Faro Gestión · v"+BuildConfig.VERSION_NAME,null),top(V5Ui.dp(this,8)));}

    private String printerSummary(){if(core.printerIp().isEmpty())return"Sin IP configurada";return(core.printerConnected()?"Conectada · ":"Reconectando · ")+core.printerIp()+":"+core.printerPort();}
    private void refreshPrinter(){V5Ui.updatePrinter(printerState,core);}
    private TextView sectionLabel(String value){TextView t=V5Ui.kicker(this,value);t.setPadding(V5Ui.dp(this,1),V5Ui.dp(this,18),0,V5Ui.dp(this,8));return t;}
    private void quickActions(){String[] items={"Nueva reserva","Generar QR promocional","Canjear premio","Imprimir ticket"};new AlertDialog.Builder(this).setTitle("Acción rápida").setItems(items,(d,w)->{if(w==0)startActivity(new Intent(this,ReservationsV5Activity.class).putExtra("openNew",true));else if(w==1)startActivity(new Intent(this,PromotionsV5Activity.class).putExtra("mode","qr"));else if(w==2)startActivity(new Intent(this,PromotionsV5Activity.class).putExtra("mode","redeem"));else startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre"));}).show();}

    private int selectedIndex(){return STATS.equals(section)?3:MANAGEMENT.equals(section)?4:5;}
    private String headerSubtitle(){return STATS.equals(section)?"Estadísticas":MANAGEMENT.equals(section)?"Gestión":"Configuración";}
    private String titleForSection(){return STATS.equals(section)?"Datos del negocio":SETTINGS.equals(section)?"Sistema y dispositivo":"Contenido y recursos";}
    private String introForSection(){return STATS.equals(section)?"Lo importante, presentado de forma clara.":SETTINGS.equals(section)?"Impresora, terminal, conexión y preferencias.":"Web, impresión y recursos del mesón.";}
    private LinearLayout.LayoutParams weightGap(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(V5Ui.dp(this,4),0,V5Ui.dp(this,4),0);return p;}
    private LinearLayout.LayoutParams top(int value){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=value;return p;}
}
