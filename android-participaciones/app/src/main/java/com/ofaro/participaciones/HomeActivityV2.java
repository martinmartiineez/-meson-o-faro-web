package com.ofaro.participaciones;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
    private AppCore core;
    private TextView printerState,serverState,reservationCount,peopleCount,promoCount,nextReservation;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private long lastServerProbe=0L;
    private final Runnable tick=new Runnable(){@Override public void run(){refreshPrinter();ui.postDelayed(this,2500);}};

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);V5Ui.applySystemBars(this);core=new AppCore(this);setContentView(build());core.startPrinterWatchdog();requestNotificationOnce();paintCachedDashboard();probeServer();
    }
    @Override protected void onStart(){super.onStart();ui.removeCallbacks(tick);ui.post(tick);}
    @Override protected void onResume(){super.onResume();if(core!=null){core.startPrinterWatchdog();refreshPrinter();paintCachedDashboard();if(System.currentTimeMillis()-lastServerProbe>45_000L)probeServer();}}
    @Override protected void onStop(){ui.removeCallbacks(tick);super.onStop();}
    @Override protected void onDestroy(){ui.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}

    private View build(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(V5Ui.BG);
        LinearLayout shell=V5Ui.column(this);
        V5Ui.Header header=V5Ui.header(this,core,"Gestión · "+core.terminal());printerState=header.printer;shell.addView(header.view);

        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        LinearLayout page=V5Ui.column(this);page.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,8),V5Ui.dp(this,18),V5Ui.dp(this,20));
        TextView date=V5Ui.kicker(this,currentDate().toUpperCase(new Locale("es","ES")));page.addView(date);
        TextView greeting=V5Ui.title(this,greeting());greeting.setPadding(0,V5Ui.dp(this,5),0,0);page.addView(greeting);
        TextView intro=V5Ui.subtitle(this,"Todo listo para el servicio.");intro.setPadding(0,V5Ui.dp(this,4),0,V5Ui.dp(this,14));page.addView(intro);

        page.addView(todayCard());
        page.addView(section("ACTIVIDAD"));
        LinearLayout metrics=V5Ui.row(this);metrics.addView(promoMetric(),weightGap());metrics.addView(webMetric(),weightGap());page.addView(metrics);
        page.addView(section("ACCIONES RÁPIDAS"));
        LinearLayout q1=V5Ui.row(this);q1.addView(V5Ui.quickAction(this,R.drawable.ic_plus_v5,"Reserva",this::openNewReservation),weightGap());q1.addView(V5Ui.quickAction(this,R.drawable.ic_qr_v5,"Generar QR",this::openPromoQr),weightGap());page.addView(q1);
        LinearLayout q2=V5Ui.row(this);q2.addView(V5Ui.quickAction(this,R.drawable.ic_check_v5,"Canjear",this::openRedeem),weightGap());q2.addView(V5Ui.quickAction(this,R.drawable.ic_print_v5,"Imprimir",this::openPrint),weightGap());page.addView(q2,top(V5Ui.dp(this,8)));

        LinearLayout server=V5Ui.row(this);server.setGravity(Gravity.CENTER_VERTICAL);server.setPadding(V5Ui.dp(this,12),V5Ui.dp(this,10),V5Ui.dp(this,12),V5Ui.dp(this,10));server.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,15));
        serverState=V5Ui.text(this,"Servidor · comprobando…",11,V5Ui.MUTED,false);server.addView(serverState,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView version=V5Ui.text(this,"v"+BuildConfig.VERSION_NAME,10,V5Ui.FAINT,false);server.addView(version);page.addView(server,top(V5Ui.dp(this,15)));

        scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));shell.addView(V5Ui.bottomNav(this,0));root.addView(shell,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
        View plus=V5Ui.floatingPlus(this,this::quickActions);FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(V5Ui.dp(this,50),V5Ui.dp(this,50),Gravity.RIGHT|Gravity.BOTTOM);fp.rightMargin=V5Ui.dp(this,22);fp.bottomMargin=V5Ui.dp(this,73);root.addView(plus,fp);return root;
    }

    private View todayCard(){
        LinearLayout card=V5Ui.softCard(this);card.setOnClickListener(v->startActivity(new Intent(this,ReservationsV5Activity.class)));
        LinearLayout head=V5Ui.row(this);head.setGravity(Gravity.CENTER_VERTICAL);head.addView(V5Ui.kicker(this,"HOY"),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView arrow=V5Ui.text(this,"›",24,V5Ui.GREEN,false);head.addView(arrow);card.addView(head);
        LinearLayout numbers=V5Ui.row(this);numbers.setPadding(0,V5Ui.dp(this,10),0,0);
        LinearLayout a=V5Ui.column(this);reservationCount=V5Ui.text(this,"0",32,V5Ui.INK,true);a.addView(reservationCount);a.addView(V5Ui.text(this,"reservas",11,V5Ui.MUTED,false));numbers.addView(a,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        LinearLayout b=V5Ui.column(this);peopleCount=V5Ui.text(this,"0",32,V5Ui.INK,true);b.addView(peopleCount);b.addView(V5Ui.text(this,"personas",11,V5Ui.MUTED,false));numbers.addView(b,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));card.addView(numbers);
        View divider=new View(this);divider.setBackgroundColor(V5Ui.BORDER);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,1));dp.topMargin=V5Ui.dp(this,13);dp.bottomMargin=V5Ui.dp(this,11);card.addView(divider,dp);
        TextView label=V5Ui.kicker(this,"PRÓXIMA");card.addView(label);nextReservation=V5Ui.text(this,"Sin datos guardados",13,V5Ui.INK,true);nextReservation.setPadding(0,V5Ui.dp(this,4),0,0);card.addView(nextReservation);return card;
    }

    private View promoMetric(){LinearLayout c=V5Ui.metric(this,"PROMOCIONES","—","activas");promoCount=(TextView)c.getChildAt(1);c.setOnClickListener(v->startActivity(new Intent(this,PromotionsV5Activity.class)));return c;}
    private View webMetric(){LinearLayout c=V5Ui.metric(this,"WEB","—","analítica pendiente");c.setOnClickListener(v->openSection(V5SectionActivity.STATS));return c;}
    private TextView section(String value){TextView t=V5Ui.kicker(this,value);t.setPadding(V5Ui.dp(this,1),V5Ui.dp(this,18),0,V5Ui.dp(this,8));return t;}

    private void paintCachedDashboard(){
        JSONArray reservations=core.cachedArray("reservations_HOY");int people=0;JSONObject soonest=null;String best="99:99";
        for(int i=0;i<reservations.length();i++){JSONObject r=reservations.optJSONObject(i);if(r==null)continue;people+=Math.max(0,r.optInt("people",0));String time=r.optString("time","99:99");if(time.compareTo(best)<0){best=time;soonest=r;}}
        if(reservationCount!=null)reservationCount.setText(String.valueOf(reservations.length()));if(peopleCount!=null)peopleCount.setText(String.valueOf(people));
        if(nextReservation!=null){if(soonest==null)nextReservation.setText("No hay reservas guardadas para hoy");else{String zone=soonest.optString("zone","");nextReservation.setText(soonest.optString("time","--:--")+" · "+soonest.optString("name","Reserva")+" · "+soonest.optInt("people",0)+" pers."+(zone.isEmpty()?"":" · "+zone));}}
        JSONArray campaigns=core.cachedArray("promo_campaigns");int active=0;for(int i=0;i<campaigns.length();i++){JSONObject c=campaigns.optJSONObject(i);if(c!=null&&c.optBoolean("active",false))active++;}if(promoCount!=null)promoCount.setText(String.valueOf(active));
    }

    private void refreshPrinter(){V5Ui.updatePrinter(printerState,core);}
    private void probeServer(){lastServerProbe=System.currentTimeMillis();if(serverState==null)return;if(!core.configured()){serverState.setText("Servidor · falta configurar acceso");serverState.setTextColor(V5Ui.ERROR);return;}if(!core.internetAvailable()){serverState.setText("Servidor · sin Internet · caché local");serverState.setTextColor(V5Ui.WARNING);return;}serverState.setText("Servidor · comprobando…");io.execute(()->{try{core.post(core.action("appPing"));runOnUiThread(()->{if(serverState!=null){serverState.setText("Servidor · online");serverState.setTextColor(V5Ui.GREEN);}});}catch(Exception e){runOnUiThread(()->{if(serverState!=null){serverState.setText("Servidor · no responde · caché disponible");serverState.setTextColor(V5Ui.WARNING);}});}});}

    private void quickActions(){String[] items={"Nueva reserva","Generar QR promocional","Canjear premio","Imprimir ticket"};new AlertDialog.Builder(this).setTitle("Acción rápida").setItems(items,(d,w)->{if(w==0)openNewReservation();else if(w==1)openPromoQr();else if(w==2)openRedeem();else openPrint();}).show();}
    private void openNewReservation(){startActivity(new Intent(this,ReservationsV5Activity.class).putExtra("openNew",true));}
    private void openPromoQr(){startActivity(new Intent(this,PromotionsV5Activity.class).putExtra("mode","qr"));}
    private void openRedeem(){startActivity(new Intent(this,PromotionsV5Activity.class).putExtra("mode","redeem"));}
    private void openPrint(){startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre"));}
    private void openSection(String section){startActivity(new Intent(this,V5SectionActivity.class).putExtra(V5SectionActivity.EXTRA_SECTION,section));}

    private void requestNotificationOnce(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!core.prefs().getBoolean("askedNotifications",false)){core.prefs().edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},501);}}
    private String currentDate(){String raw=LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM",new Locale("es","ES")));return raw.isEmpty()?raw:Character.toUpperCase(raw.charAt(0))+raw.substring(1);}
    private String greeting(){int h=LocalTime.now().getHour();return h<13?"Buenos días":h<20?"Buenas tardes":"Buenas noches";}
    private LinearLayout.LayoutParams weightGap(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(V5Ui.dp(this,4),0,V5Ui.dp(this,4),0);return p;}
    private LinearLayout.LayoutParams top(int value){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=value;return p;}
}
