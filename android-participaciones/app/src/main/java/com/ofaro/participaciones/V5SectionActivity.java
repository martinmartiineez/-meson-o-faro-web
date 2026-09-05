package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class V5SectionActivity extends Activity {
    public static final String EXTRA_SECTION = "section";
    public static final String STATS = "ESTADÍSTICAS";
    public static final String MANAGEMENT = "GESTIÓN";
    public static final String SETTINGS = "CONFIGURACIÓN";

    private static final int BG = Color.rgb(244,245,239);
    private static final int INK = Color.rgb(17,19,17);
    private static final int MUTED = Color.rgb(113,117,111);
    private static final int GREEN = Color.rgb(24,48,32);
    private static final int LIME = Color.rgb(185,232,91);
    private static final int BORDER = Color.rgb(225,228,220);

    private AppCore core;
    private String section;
    private TextView printerState;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        core=new AppCore(this);
        section=getIntent().getStringExtra(EXTRA_SECTION);
        if(section==null||section.trim().isEmpty()) section=MANAGEMENT;
        core.startPrinterWatchdog();
        setContentView(build());
        refreshPrinter();
    }

    @Override protected void onResume(){super.onResume();if(core!=null){core.startPrinterWatchdog();refreshPrinter();}}

    private View build(){
        LinearLayout root=col();root.setBackgroundColor(BG);
        root.addView(header());

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout page=col();page.setPadding(dp(18),dp(22),dp(18),dp(28));
        page.addView(kicker(section));
        page.addView(title(titleForSection()));
        TextView intro=text(introForSection(),14,MUTED,false);intro.setPadding(0,dp(5),0,dp(18));page.addView(intro);

        if(STATS.equals(section)) buildStats(page);
        else if(SETTINGS.equals(section)) buildSettings(page);
        else buildManagement(page);

        scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        root.addView(bottomNav());
        return root;
    }

    private View header(){
        LinearLayout row=row();row.setPadding(dp(18),dp(15),dp(14),dp(13));row.setGravity(Gravity.CENTER_VERTICAL);row.setBackgroundColor(BG);
        LinearLayout left=col();left.addView(text("O FARO",24,INK,true));left.addView(text("Gestión · "+core.terminal(),12,MUTED,false));row.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout right=row();right.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        printerState=text("● Impresora",12,MUTED,true);printerState.setPadding(dp(9),dp(8),dp(9),dp(8));printerState.setBackground(box(Color.WHITE,18,BORDER,1));printerState.setOnClickListener(v->startActivity(new Intent(this,PrinterSettingsActivity.class)));right.addView(printerState);
        Button quick=smallRound("+");quick.setOnClickListener(v->quickActions());LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(dp(44),dp(44));qp.leftMargin=dp(8);right.addView(quick,qp);row.addView(right);
        return row;
    }

    private void buildStats(LinearLayout page){
        page.addView(periods());
        LinearLayout r=row();r.addView(metric("RESERVAS","—","En preparación"),weightGap());r.addView(metric("PERSONAS","—","En preparación"),weightGap());page.addView(r,topWrap(dp(16)));
        LinearLayout r2=row();r2.addView(metric("PROMOCIONES","—","En preparación"),weightGap());r2.addView(metric("WEB","—","Analítica pendiente"),weightGap());page.addView(r2,topWrap(dp(8)));
        page.addView(sectionLabel("MÓDULOS"));
        page.addView(linkCard("Reservas","Personas, zonas, estados, horas punta y evolución",null));
        page.addView(linkCard("Promociones","QR, jugadas, ganadores, canjes y conversión",null),topWrap(dp(8)));
        page.addView(linkCard("Web","Visitas, páginas, acciones, embudos, origen y dispositivos",null),topWrap(dp(8)));
        page.addView(linkCard("Impresión","Tickets, errores y actividad por terminal",null),topWrap(dp(8)));
    }

    private void buildManagement(LinearLayout page){
        page.addView(sectionLabel("CONTENIDO Y OPERACIÓN"));
        page.addView(linkCard("Web","Carta, menú del día, avisos, popup, horarios y textos",()->startActivity(new Intent(this,WebManagementActivity.class))));
        page.addView(linkCard("Impresión","Crear ticket y abrir el estudio ESC/POS",()->startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre"))),topWrap(dp(8)));
        page.addView(linkCard("Recursos","QR, plantillas guardadas e historial",()->startActivity(new Intent(this,ToolsActivity.class))),topWrap(dp(8)));
        TextView note=text("Plantillas v5 incluirá crear, editar, duplicar, eliminar, exportar y establecer plantillas personalizadas como predeterminadas.",12,MUTED,false);note.setPadding(dp(3),dp(18),dp(3),0);page.addView(note);
    }

    private void buildSettings(LinearLayout page){
        page.addView(sectionLabel("SISTEMA"));
        page.addView(linkCard("Impresora",printerSummary(),()->startActivity(new Intent(this,PrinterSettingsActivity.class))));
        page.addView(linkCard("Terminal",core.terminal()+" · configuración del dispositivo",null),topWrap(dp(8)));
        page.addView(linkCard("Conexión","Servidor, sincronización y endpoint de producción",()->startActivity(new Intent(this,DiagnosticsActivity.class))),topWrap(dp(8)));
        page.addView(linkCard("Diagnóstico","Pruebas de backend, plantillas, impresora y caché",()->startActivity(new Intent(this,DiagnosticsActivity.class))),topWrap(dp(8)));
        page.addView(sectionLabel("APLICACIÓN"));
        page.addView(linkCard("Preferencias","Tema, sonidos, vibración y confirmaciones · próxima fase",null));
        page.addView(linkCard("Información","O Faro Gestión · v"+BuildConfig.VERSION_NAME,null),topWrap(dp(8)));
    }

    private String printerSummary(){
        if(core.printerIp().isEmpty()) return "Sin IP configurada";
        return (core.printerConnected()?"Conectada · ":"Reconectando · ")+core.printerIp()+":"+core.printerPort();
    }

    private void refreshPrinter(){
        if(printerState==null)return;
        if(core.printerIp().isEmpty()){printerState.setText("● Sin impresora");printerState.setTextColor(Color.rgb(155,75,35));return;}
        boolean ok=core.printerConnected();printerState.setText(ok?"● Conectada":"● Reconectando");printerState.setTextColor(ok?GREEN:Color.rgb(145,95,35));
    }

    private View periods(){
        LinearLayout r=row();String[] p={"HOY","7 DÍAS","30 DÍAS","MES"};
        for(int i=0;i<p.length;i++){Button b=new Button(this);b.setAllCaps(false);b.setText(p[i]);b.setTextSize(11);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(i==0?Color.WHITE:MUTED);b.setBackground(box(i==0?GREEN:Color.WHITE,18,BORDER,1));r.addView(b,weightGap());}
        return r;
    }

    private LinearLayout metric(String label,String value,String note){
        LinearLayout c=col();c.setPadding(dp(15),dp(14),dp(15),dp(14));c.setBackground(box(Color.WHITE,16,BORDER,1));
        c.addView(text(label,10,MUTED,true));TextView v=text(value,30,INK,true);v.setPadding(0,dp(6),0,0);c.addView(v);c.addView(text(note,11,MUTED,false));return c;
    }

    private LinearLayout linkCard(String name,String note,Runnable action){
        LinearLayout c=row();c.setPadding(dp(16),dp(14),dp(13),dp(14));c.setGravity(Gravity.CENTER_VERTICAL);c.setBackground(box(Color.WHITE,16,BORDER,1));
        LinearLayout copy=col();copy.addView(text(name,17,INK,true));TextView n=text(note,12,MUTED,false);n.setPadding(0,dp(3),0,0);copy.addView(n);c.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView arrow=text("›",28,action==null?Color.rgb(190,193,187):GREEN,false);c.addView(arrow);if(action!=null)c.setOnClickListener(v->action.run());return c;
    }

    private View bottomNav(){
        LinearLayout wrap=col();wrap.setPadding(dp(12),dp(6),dp(12),dp(12));wrap.setBackgroundColor(BG);
        LinearLayout bar=row();bar.setPadding(dp(5),dp(5),dp(5),dp(5));bar.setGravity(Gravity.CENTER);bar.setBackground(box(Color.WHITE,25,BORDER,1));
        addNav(bar,"⌂","Inicio",false,()->{startActivity(new Intent(this,HomeActivityV2.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));finish();});
        addNav(bar,"▣","Reservas",false,()->startActivity(new Intent(this,ReservationsActivity.class)));
        addNav(bar,"◆","Promos",false,()->startActivity(new Intent(this,PromotionsSimpleActivity.class)));
        addNav(bar,"▥","Stats",STATS.equals(section),()->switchSection(STATS));
        addNav(bar,"☰","Gestión",MANAGEMENT.equals(section),()->switchSection(MANAGEMENT));
        addNav(bar,"⚙","Config",SETTINGS.equals(section),()->switchSection(SETTINGS));
        wrap.addView(bar);return wrap;
    }

    private void addNav(LinearLayout bar,String icon,String label,boolean selected,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(selected?icon+"\n"+label:icon);b.setTextSize(selected?10:18);b.setTextColor(selected?Color.WHITE:MUTED);b.setGravity(Gravity.CENTER);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinHeight(0);b.setBackground(box(selected?GREEN:Color.TRANSPARENT,20,Color.TRANSPARENT,0));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(2),0,dp(2),0);bar.addView(b,p);
    }

    private void switchSection(String target){if(target.equals(section))return;startActivity(new Intent(this,V5SectionActivity.class).putExtra(EXTRA_SECTION,target));finish();}

    private void quickActions(){
        String[] items={"Nueva reserva","Generar QR promocional","Canjear premio","Imprimir ticket"};
        new AlertDialog.Builder(this).setTitle("Acción rápida").setItems(items,(d,which)->{
            if(which==0)startActivity(new Intent(this,ReservationsActivity.class).putExtra("openNew",true));
            else if(which==1)startActivity(new Intent(this,PromotionsSimpleActivity.class).putExtra("mode","qr"));
            else if(which==2)startActivity(new Intent(this,PromotionsSimpleActivity.class).putExtra("mode","redeem"));
            else startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre"));
        }).show();
    }

    private String titleForSection(){if(STATS.equals(section))return"Lo importante, sin ruido";if(SETTINGS.equals(section))return"Sistema y dispositivo";return"Contenido y recursos";}
    private String introForSection(){if(STATS.equals(section))return"Reservas, promociones, web e impresión en un único lugar.";if(SETTINGS.equals(section))return"Impresora, terminal, conexión, diagnóstico y preferencias.";return"Web, impresión, recursos y herramientas del negocio.";}

    private TextView kicker(String s){TextView t=text(s,10,GREEN,true);t.setLetterSpacing(.12f);return t;}
    private TextView title(String s){return text(s,28,INK,true);}
    private TextView sectionLabel(String s){TextView t=text(s,10,MUTED,true);t.setLetterSpacing(.1f);t.setPadding(dp(2),dp(22),0,dp(9));return t;}
    private Button smallRound(String s){Button b=new Button(this);b.setText(s);b.setTextSize(22);b.setTextColor(Color.WHITE);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinHeight(0);b.setBackground(box(GREEN,22,Color.TRANSPARENT,0));return b;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable box(int fill,int radius,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(sw>0)d.setStroke(dp(sw),stroke);return d;}
    private LinearLayout.LayoutParams weightGap(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(dp(4),0,dp(4),0);return p;}
    private LinearLayout.LayoutParams topWrap(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=top;return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
