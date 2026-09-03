package com.ofaro.participaciones;

import android.app.Activity;
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

public class HomeActivityV2 extends Activity {
    private AppCore core;
    private TextView connection;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        core = new AppCore(this);
        setContentView(build());
        core.startPrinterWatchdog();
    }

    @Override protected void onResume() {
        super.onResume();
        if (core != null) {
            core.startPrinterWatchdog();
            refreshStatus();
        }
    }

    private View build() {
        LinearLayout root = col();
        root.setBackgroundColor(Color.rgb(247,246,243));

        LinearLayout header = col();
        header.setPadding(dp(20),dp(20),dp(20),dp(18));
        header.setBackgroundColor(Color.rgb(15,15,15));
        header.addView(text("O FARO",29,Color.WHITE,true));
        TextView sub = text("Gestión del mesón",14,Color.LTGRAY,false);
        sub.setPadding(0,dp(2),0,0);
        header.addView(sub);
        root.addView(header);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = col();
        page.setPadding(dp(15),dp(14),dp(15),dp(30));
        scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        connection = text("Preparando…",12,Color.DKGRAY,true);
        connection.setPadding(dp(12),dp(9),dp(12),dp(9));
        connection.setBackground(box(Color.WHITE,11,Color.rgb(218,218,218),1));
        page.addView(connection);

        page.addView(section("TRABAJO DIARIO"));
        Button reservations = action("RESERVAS","Ver las de hoy, crear una, confirmar, editar y sacar ticket");
        reservations.setOnClickListener(v->startActivity(new Intent(this,ReservationsActivity.class)));
        page.addView(reservations);

        Button promotions = action("PROMOCIONES","Crear una promoción, sacar QR y canjear premios");
        promotions.setOnClickListener(v->startActivity(new Intent(this,PromotionsSimpleActivity.class)));
        page.addView(promotions,top(dp(9)));

        Button print = action("IMPRIMIR","Crear un ticket, un QR o imprimir una imagen");
        print.setOnClickListener(v->startActivity(new Intent(this,TicketPreviewActivity.class).putExtra("type","Libre")));
        page.addView(print,top(dp(9)));

        page.addView(section("GESTIÓN"));
        LinearLayout row = row();
        Button web = tile("WEB\nCarta y avisos");
        web.setOnClickListener(v->startActivity(new Intent(this,WebManagementActivity.class)));
        Button tools = tile("HERRAMIENTAS\nQR e historial");
        tools.setOnClickListener(v->startActivity(new Intent(this,ToolsActivity.class)));
        row.addView(web,weight());
        row.addView(tools,weight());
        page.addView(row);

        Button settings = secondary("AJUSTES · IMPRESORA Y CONEXIÓN");
        settings.setOnClickListener(v->startActivity(new Intent(this,PrinterSettingsActivity.class)));
        page.addView(settings,top(dp(9)));

        Button advanced = ghost("ABRIR GESTIÓN AVANZADA DE PROMOCIONES");
        advanced.setOnClickListener(v->startActivity(new Intent(this,PromotionsActivity.class)));
        page.addView(advanced,top(dp(9)));

        TextView note = text("La pantalla de inicio no espera a Internet. Los datos se cargan solo al entrar en cada apartado.",12,Color.GRAY,false);
        note.setPadding(dp(3),dp(16),dp(3),0);
        page.addView(note);
        return root;
    }

    private void refreshStatus() {
        if (connection == null) return;
        String api = core.configured() ? "Servidor configurado" : "Falta configurar Apps Script";
        String printer = core.printerIp().isEmpty() ? "Impresora sin configurar" : (core.printerConnected() ? "Impresora conectada" : "Impresora reconectando");
        connection.setText(api + "   ·   " + printer);
        connection.setTextColor(core.configured() ? Color.rgb(35,95,60) : Color.rgb(155,65,40));
    }

    private TextView section(String s) {
        TextView t=text(s,12,Color.GRAY,true);
        t.setPadding(dp(2),dp(20),0,dp(8));
        return t;
    }
    private Button action(String title,String subtitle) {
        Button b=new Button(this);
        b.setAllCaps(false);
        b.setText(title+"\n"+subtitle);
        b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setPadding(dp(17),dp(8),dp(17),dp(8));
        b.setBackground(box(Color.rgb(15,15,15),15,Color.TRANSPARENT,0));
        b.setMinHeight(dp(82));
        return b;
    }
    private Button tile(String s) {
        Button b=new Button(this);
        b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.rgb(20,20,20));b.setBackground(box(Color.WHITE,13,Color.rgb(205,205,205),1));b.setMinHeight(dp(70));
        return b;
    }
    private Button secondary(String s) {
        Button b=tile(s);b.setMinHeight(dp(54));return b;
    }
    private Button ghost(String s) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(s);b.setTextSize(11);b.setTextColor(Color.DKGRAY);b.setBackgroundColor(Color.TRANSPARENT);return b;
    }
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private LinearLayout.LayoutParams weight(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);p.setMargins(dp(3),0,dp(3),0);return p;}
    private LinearLayout.LayoutParams top(int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=m;return p;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private GradientDrawable box(int fill,int radius,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(sw>0)d.setStroke(dp(sw),stroke);return d;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
