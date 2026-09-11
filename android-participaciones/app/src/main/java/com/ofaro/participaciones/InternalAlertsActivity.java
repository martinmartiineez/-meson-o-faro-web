package com.ofaro.participaciones;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Historial y preferencias de las alertas internas. */
public class InternalAlertsActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 4101;
    private InternalAlertCenter center;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        V5Ui.applySystemBars(this);
        center=new InternalAlertCenter(this);
        center.markAllRead();
        render();
        requestNotificationPermissionIfNeeded();
    }

    @Override protected void onResume(){
        super.onResume();
        if(center!=null){center.markAllRead();render();}
    }

    private void requestNotificationPermissionIfNeeded(){
        if(Build.VERSION.SDK_INT>=33
                && center.prefs().getBoolean("notifications_enabled",true)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
        }
    }

    private void render(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
        root=V5Ui.column(this);root.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,12),V5Ui.dp(this,18),V5Ui.dp(this,28));
        scroll.addView(root);setContentView(scroll);

        TextView back=V5Ui.text(this,"‹  Volver",10.5f,V5Ui.GREEN,true);back.setOnClickListener(v->finish());back.setPadding(0,0,0,V5Ui.dp(this,10));root.addView(back);
        root.addView(V5Ui.kicker(this,"ALERTAS INTERNAS"));
        TextView title=V5Ui.title(this,"Centro de avisos");title.setPadding(0,V5Ui.dp(this,5),0,V5Ui.dp(this,4));root.addView(title);
        root.addView(V5Ui.subtitle(this,"Reservas, impresora y stock de premios sin perder de vista el servicio."));

        int unread=center.unreadCount();
        LinearLayout summary=V5Ui.darkCard(this);
        summary.addView(V5Ui.text(this,String.valueOf(unread),30,android.graphics.Color.WHITE,true));
        summary.addView(V5Ui.text(this,unread==1?"alerta sin leer":"alertas sin leer",10.5f,android.graphics.Color.rgb(195,207,197),false));
        root.addView(summary,top(12));

        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            LinearLayout permission=V5Ui.softCard(this);
            permission.addView(V5Ui.text(this,"Notificaciones de Android desactivadas",12.5f,V5Ui.INK,true));
            permission.addView(V5Ui.text(this,"Las alertas seguirán guardándose aquí, pero Android no las mostrará fuera de la app.",10,V5Ui.MUTED,false));
            permission.addView(button("Activar notificaciones",true,this::openNotificationSettings),top(8));
            root.addView(permission,top(8));
        }

        root.addView(section("QUÉ QUIERES RECIBIR"));
        addToggle("Nuevas reservas","alert_new_reservations","Avisa cuando aparezca una reserva nueva de otro origen o terminal.");
        addToggle("Reservas próximas","alert_upcoming_reservations","Avisa antes de la hora de una reserva del día.");
        addToggle("Impresora desconectada","alert_printer","Avisa cuando una impresora que estaba conectada pierde la conexión.");
        addToggle("Stock bajo de premios","alert_low_stock","Avisa cuando un premio llega al umbral de stock configurado.");
        addToggle("Notificaciones del sistema","notifications_enabled","Además del centro interno, muestra una notificación Android.");

        root.addView(section("ANTELACIÓN"));
        Spinner lead=spinner(new String[]{"15 min","30 min","45 min","60 min"});
        int current=center.prefs().getInt("alert_lead_minutes",30);
        lead.setSelection(current<=15?0:current<=30?1:current<=45?2:3);
        lead.setOnItemSelectedListener(new SimpleItemListener(pos->center.prefs().edit().putInt("alert_lead_minutes",new int[]{15,30,45,60}[pos]).apply()));
        root.addView(lead,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,48)));

        root.addView(section("STOCK DE PREMIOS"));
        Spinner stock=spinner(new String[]{"1 unidad","2 unidades","3 unidades","5 unidades","10 unidades"});
        int threshold=center.prefs().getInt("alert_stock_threshold",3);
        int stockPos=threshold<=1?0:threshold<=2?1:threshold<=3?2:threshold<=5?3:4;
        stock.setSelection(stockPos);
        stock.setOnItemSelectedListener(new SimpleItemListener(pos->center.prefs().edit().putInt("alert_stock_threshold",new int[]{1,2,3,5,10}[pos]).apply()));
        root.addView(stock,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,48)));

        LinearLayout buttons=V5Ui.row(this);
        buttons.addView(button("Marcar leídas",false,()->{center.markAllRead();render();}),weight());
        buttons.addView(button("Borrar historial",false,this::confirmClear),weightGap());
        root.addView(buttons,top(12));

        root.addView(section("HISTORIAL"));
        JSONArray alerts=center.alerts();
        if(alerts.length()==0){LinearLayout empty=V5Ui.softCard(this);empty.addView(V5Ui.text(this,"Todavía no hay alertas registradas.",12,V5Ui.MUTED,false));root.addView(empty);}
        for(int i=0;i<alerts.length();i++){JSONObject a=alerts.optJSONObject(i);if(a!=null)root.addView(alertCard(a),bottom(7));}
    }

    private void addToggle(String title,String key,String note){
        LinearLayout card=V5Ui.card(this);card.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,16));
        CheckBox box=new CheckBox(this);box.setText(title);box.setTextSize(12.5f);box.setTextColor(V5Ui.INK);box.setChecked(center.prefs().getBoolean(key,true));
        box.setOnCheckedChangeListener((b,on)->{
            center.prefs().edit().putBoolean(key,on).apply();
            if("notifications_enabled".equals(key)&&on)requestNotificationPermissionIfNeeded();
        });
        card.addView(box);card.addView(V5Ui.text(this,note,10,V5Ui.MUTED,false));root.addView(card,bottom(7));
    }

    private LinearLayout alertCard(JSONObject a){
        boolean read=a.optBoolean("read",false);
        LinearLayout card=V5Ui.card(this);card.setBackground(V5Ui.bg(this,read?V5Ui.SURFACE_ALT:V5Ui.LIME_SOFT,17));
        LinearLayout top=V5Ui.row(this);top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(V5Ui.text(this,a.optString("title","Alerta"),13.5f,V5Ui.INK,true),new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        top.addView(V5Ui.pill(this,typeLabel(a.optString("type","")),read?android.graphics.Color.rgb(235,236,232):V5Ui.SURFACE,read?V5Ui.MUTED:V5Ui.GREEN));
        card.addView(top);
        card.addView(V5Ui.text(this,a.optString("message",""),10.5f,V5Ui.MUTED,false));
        card.addView(V5Ui.text(this,formatTime(a.optLong("ts",0)),9.5f,V5Ui.FAINT,false));
        return card;
    }

    private void openNotificationSettings(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
            return;
        }
        Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());
        try{startActivity(i);}catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:"+getPackageName())));}
    }

    private Spinner spinner(String[] values){Spinner s=new Spinner(this);ArrayAdapter<String>a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,values);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);s.setAdapter(a);s.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));return s;}
    private TextView section(String s){TextView t=V5Ui.kicker(this,s);t.setPadding(0,V5Ui.dp(this,16),0,V5Ui.dp(this,7));return t;}
    private TextView button(String label,boolean primary,Runnable action){TextView t=V5Ui.text(this,label,10.5f,primary?android.graphics.Color.WHITE:V5Ui.GREEN,true);t.setGravity(Gravity.CENTER);t.setMinHeight(V5Ui.dp(this,46));t.setBackground(V5Ui.bg(this,primary?V5Ui.GREEN:V5Ui.SURFACE_ALT,15));t.setOnClickListener(v->action.run());return t;}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);}
    private LinearLayout.LayoutParams weightGap(){LinearLayout.LayoutParams p=weight();p.leftMargin=V5Ui.dp(this,7);return p;}
    private LinearLayout.LayoutParams top(int dp){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=V5Ui.dp(this,dp);return p;}
    private LinearLayout.LayoutParams bottom(int dp){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=V5Ui.dp(this,dp);return p;}
    private String typeLabel(String t){if("reservation".equals(t))return"RESERVA";if("upcoming".equals(t))return"PRÓXIMA";if("printer".equals(t))return"IMPRESORA";if("stock".equals(t))return"STOCK";return"AVISO";}
    private String formatTime(long ts){if(ts<=0)return"";return new SimpleDateFormat("dd/MM · HH:mm",new Locale("es","ES")).format(new Date(ts));}
    private void confirmClear(){new AlertDialog.Builder(this).setTitle("Borrar historial").setMessage("Se eliminarán todas las alertas guardadas en este dispositivo.").setNegativeButton("Cancelar",null).setPositiveButton("Borrar",(d,w)->{center.clear();render();}).show();}

    private interface PositionConsumer{void accept(int position);}
    private static final class SimpleItemListener implements android.widget.AdapterView.OnItemSelectedListener{
        private final PositionConsumer consumer;SimpleItemListener(PositionConsumer consumer){this.consumer=consumer;}
        @Override public void onItemSelected(android.widget.AdapterView<?> parent,android.view.View view,int position,long id){consumer.accept(position);}
        @Override public void onNothingSelected(android.widget.AdapterView<?> parent){}
    }
}
