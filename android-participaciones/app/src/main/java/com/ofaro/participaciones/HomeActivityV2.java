package com.ofaro.participaciones;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivityV2 extends Activity {
    private static final int BG=Color.rgb(244,245,239);
    private static final int INK=Color.rgb(17,19,17);
    private static final int MUTED=Color.rgb(113,117,111);
    private static final int GREEN=Color.rgb(24,48,32);
    private static final int LIME=Color.rgb(185,232,91);
    private static final int BORDER=Color.rgb(225,228,220);

    private AppCore core;
    private TextView printerState,serverState,reservationCount,peopleCount,promoCount,nextReservation;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private long lastServerProbe=0L;
    private final Runnable tick=new Runnable(){@Override public void run(){refreshPrinter();ui.postDelayed(this,2500);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        core=new AppCore(this);
        setContentView(build());
        core.startPrinterWatchdog();
        requestNotificationOnce();
        paintCachedDashboard();
        probeServer(false);
    }

    @Override protected void onStart(){super.onStart();ui.removeCallbacks(tick);ui.post(tick);}
    @Override protected void onResume(){super.onResume();if(core!=null){core.startPrinterWatchdog();refreshPrinter();paintCachedDashboard();if(System.currentTimeMillis()-lastServerProbe>45_000L)probeServer(false);}}
    @Override protected void onStop(){ui.removeCallbacks(tick);super.onStop();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}

    private View build(){
        LinearLayout root=col();root.setBackgroundColor(BG);
        root.addView(header());

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        LinearLayout page=col();page.setPadding(dp(18),dp(13),dp(18),dp(28));

        TextView date=text(currentDate(),12,MUTED,true);date.setLetterSpacing(.08f);page.addView(date);
        TextView greeting=text(greeting(),29,INK,true);greeting.setPadding(0,dp(5),0,dp(18));page.addView(greeting);

        page.addView(todayCard());

        TextView ops=sectionLabel("ACTIVIDAD");page.addView(ops);
        LinearLayout metrics=row();
        metrics.addView(metricCard("PROMOCIONES","—","activas",true),weightGap());
        metrics.addView(metricCard("WEB","—","analítica pendiente",false),weightGap());
        page.addView(metrics);

        page.addView(nextCard(),topWrap(dp(10)));

        page.addView(sectionLabel("ACCIONES RÁPIDAS"));
        LinearLayout q1=row();q1.addView(quickCard("+","Reserva",()->openNewReservation()),weightGap());q1.addView(quickCard("QR","Generar QR",()->openPromoQr()),weightGap());page.addView(q1);
        LinearLayout q2=row();q2.addView(quickCard("✓","Canjear",()->openRedeem()),weightGap());q2.addView(quickCard("▤","Imprimir",()->openPrint()),weightGap());page.addView(q2,topWrap(dp(8)));

        LinearLayout connection=col();connection.setPadding(dp(14),dp(12),dp(14),dp(12));connection.setBackground(box(Color.WHITE,15,BORDER,1));
        serverState=text("Servidor · comprobando…",12,MUTED,false);connection.addView(serverState);
        TextView version=text("O Faro Gestión · v"+BuildConfig.VERSION_NAME,11,Color.rgb(145,148,142),false);version.setPadding(0,dp(4),0,0);connection.addView(version);page.addView(connection,topWrap(dp(18)));

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

    private View todayCard(){
        LinearLayout card=col();card.setPadding(dp(17),dp(16),dp(17),dp(16));card.setBackground(box(Color.WHITE,18,BORDER,1));card.setOnClickListener(v->startActivity(new Intent(this,ReservationsActivity.class)));
        LinearLayout top=row();TextView label=text("HOY",10,GREEN,true);label.setLetterSpacing(.12f);top.addView(label,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView arrow=text("›",27,GREEN,false);top.addView(arrow);card.addView(top);
        LinearLayout numbers=row();
        LinearLayout a=col();reservationCount=text("0",34,INK,true);a.addView(reservationCount);a.addView(text("reservas",12,MUTED,false));numbers.addView(a,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout b=col();peopleCount=text("0",34,INK,true);b.addView(peopleCount);b.addView(text("personas",12,MUTED,false));numbers.addView(b,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        card.addView(numbers,topWrap(dp(12)));
        View accent=new View(this);accent.setBackgroundColor(LIME);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(56),dp(4));ap.topMargin=dp(15);card.addView(accent,ap);
        return card;
    }

    private View nextCard(){
        LinearLayout card=row();card.setPadding(dp(15),dp(13),dp(15),dp(13));card.setGravity(Gravity.CENTER_VERTICAL);card.setBackground(box(Color.WHITE,16,BORDER,1));
        LinearLayout copy=col();copy.addView(text("PRÓXIMA RESERVA",10,MUTED,true));nextReservation=text("Sin datos guardados",14,INK,true);nextReservation.setPadding(0,dp(5),0,0);copy.addView(nextReservation);card.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView arrow=text("›",28,GREEN,false);card.addView(arrow);card.setOnClickListener(v->startActivity(new Intent(this,ReservationsActivity.class)));return card;
    }

    private LinearLayout metricCard(String label,String value,String note,boolean promos){
        LinearLayout card=col();card.setPadding(dp(15),dp(14),dp(15),dp(14));card.setBackground(box(Color.WHITE,16,BORDER,1));
        card.addView(text(label,10,MUTED,true));TextView number=text(value,29,INK,true);number.setPadding(0,dp(7),0,0);if(promos)promoCount=number;card.addView(number);card.addView(text(note,11,MUTED,false));
        card.setOnClickListener(v->{if(promos)startActivity(new Intent(this,PromotionsSimpleActivity.class));else openSection(V5SectionActivity.STATS);});return card;
    }

    private LinearLayout quickCard(String icon,String label,Runnable action){
        LinearLayout card=row();card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(13),dp(14),dp(13));card.setBackground(box(Color.WHITE,16,BORDER,1));
        TextView badge=text(icon,icon.length()>1?12:19,Color.WHITE,true);badge.setGravity(Gravity.CENTER);badge.setBackground(box(GREEN,18,Color.TRANSPARENT,0));card.addView(badge,new LinearLayout.LayoutParams(dp(38),dp(38)));
        TextView name=text(label,14,INK,true);name.setPadding(dp(10),0,0,0);card.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));card.setOnClickListener(v->action.run());return card;
    }

    private View bottomNav(){
        LinearLayout wrap=col();wrap.setPadding(dp(12),dp(6),dp(12),dp(12));wrap.setBackgroundColor(BG);
        LinearLayout bar=row();bar.setPadding(dp(5),dp(5),dp(5),dp(5));bar.setGravity(Gravity.CENTER);bar.setBackground(box(Color.WHITE,25,BORDER,1));
        addNav(bar,"⌂","Inicio",true,()->{});
        addNav(bar,"▣","Reservas",false,()->startActivity(new Intent(this,ReservationsActivity.class)));
        addNav(bar,"◆","Promos",false,()->startActivity(new Intent(this,PromotionsSimpleActivity.class)));
        addNav(bar,"▥","Stats",false,()->openSection(V5SectionActivity.STATS));
        addNav(bar,"☰","Gestión",false,()->openSection(V5SectionActivity.MANAGEMENT));
        addNav(bar,"⚙","Config",false,()->openSection(V5SectionActivity.SETTINGS));
        wrap.addView(bar);return wrap;
    }

    private void addNav(LinearLayout bar,String icon,String label,boolean selected,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(selected?icon+"\n"+label:icon);b.setTextSize(selected?10:18);b.setTextColor(selected?Color.WHITE:MUTED);b.setGravity(Gravity.CENTER);b.setPadding(0,0,0,0);b.setMinWidth(0);b.setMinHeight(0);b.setBackground(box(selected?GREEN:Color.TRANSPARENT,20,Color.TRANSPARENT,0));b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(52),1);p.setMargins(dp(2),0,dp(2),0);bar.addView(b,p);
    }

    private void paintCachedDashboard(){
        JSONArray reservations=core.cachedArray("reservations_HOY");int people=0;for(int i=0;i<reservations.length();i++){JSONObject r=reservations.optJSONObject(i);if(r!=null)people+=Math.max(0,r.optInt("people",0));}
        if(reservationCount!=null)reservationCount.setText(String.valueOf(reservations.length()));if(peopleCount!=null)peopleCount.setText(String.valueOf(people));
        if(nextReservation!=null){if(reservations.length()>0){JSONObject r=reservations.optJSONObject(0);if(r!=null){String meta=r.optString("time","--:--")+" · "+r.optString("name","Reserva")+" · "+r.optInt("people",0)+" pers.";nextReservation.setText(meta);}else nextReservation.setText("Sin datos guardados");}else nextReservation.setText("No hay reservas guardadas para hoy");}
        JSONArray campaigns=core.cachedArray("promo_campaigns");int active=0;for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);if(c!=null&&c.optBoolean("active",false))active++;}if(promoCount!=null)promoCount.setText(String.valueOf(active));
    }

    private void refreshPrinter(){
        if(printerState==null)return;if(core.printerIp().isEmpty()){printerState.setText("● Sin impresora");printerState.setTextColor(Color.rgb(155,75,35));return;}boolean ok=core.printerConnected();printerState.setText(ok?"● Conectada":"● Reconectando");printerState.setTextColor(ok?GREEN:Color.rgb(145,95,35));
    }

    private void probeServer(boolean forced){
        if(serverState==null)return;lastServerProbe=System.currentTimeMillis();if(!core.configured()){serverState.setText("Servidor · falta configurar la clave de gestión");serverState.setTextColor(Color.rgb(150,70,40));return;}if(!core.internetAvailable()){serverState.setText("Servidor · sin Internet · usando datos guardados");serverState.setTextColor(Color.rgb(150,95,30));return;}serverState.setText("Servidor · comprobando en segundo plano…");io.execute(()->{try{core.post(core.action("appPing"));runOnUiThread(()->{if(serverState!=null){serverState.setText("Servidor · online");serverState.setTextColor(GREEN);}});}catch(Exception e){runOnUiThread(()->{if(serverState!=null){serverState.setText("Servidor · no responde · datos locales disponibles");serverState.setTextColor(Color.rgb(150,75,35));}});}});
    }

    private void quickActions(){
        String[] items={"Nueva reserva","Generar QR promocional","Canjear premio","Imprimir ticket"};
        new AlertDialog.Builder(this).setTitle("Acción rápida").setItems(items,(d,which)->{if(which==0)openNewReservation();else if(which==1)openPromoQr();else if(which==2)openRedeem();else openPrint();}).show();
    }

    private void openNewReservation(){startActivity(new Intent(this,ReservationsActivity.class).putExtra("openNew",true));}
    private void openPromoQr(){startActivity(new Intent(this,PromotionsSimpleActivity.class).putExtra("mode","qr"));}
    private void openRedeem(){startActivity(new Intent(this,PromotionsSimpleActivity.class).putExtra("mode","redeem"));}
    private void openPrint(){startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre"));}
    private void openSection(String section){startActivity(new Intent(this,V5SectionActivity.class).putExtra(V5SectionActivity.EXTRA_SECTION,section));}

    private void requestNotificationOnce(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!core.prefs().getBoolean("askedNotifications",false)){core.prefs().edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},501);}}

    private String currentDate(){String raw=LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM",new Locale("es","ES")));return raw.isEmpty()?raw:Character.toUpperCase(raw.charAt(0))+raw.substring(1);}
    private String greeting(){int h=LocalTime.now().getHour();if(h<13)return"Buenos días";if(h<20)return"Buenas tardes";return"Buenas noches";}
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
