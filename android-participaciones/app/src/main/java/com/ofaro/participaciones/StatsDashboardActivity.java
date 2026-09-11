package com.ofaro.participaciones;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dashboard completo de reservas y promociones con periodos y comparación previa. */
public class StatsDashboardActivity extends Activity {
    private AppCore core;
    private LinearLayout content;
    private Spinner period;
    private TextView customDates,status;
    private LocalDate customStart=LocalDate.now().minusDays(6),customEnd=LocalDate.now();
    private JSONArray reservations=new JSONArray(),promoHistory=new JSONArray();
    private final ExecutorService io=Executors.newFixedThreadPool(2);

    @Override protected void onCreate(Bundle state){super.onCreate(state);V5Ui.applySystemBars(this);core=new AppCore(this);reservations=core.cachedArray("reservations_all_v5");promoHistory=core.cachedArray("promo_history_v5");setContentView(build());render();refresh(false);}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}

    private View build(){
        LinearLayout root=V5Ui.column(this);root.setBackgroundColor(V5Ui.BG);
        LinearLayout header=V5Ui.row(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(V5Ui.dp(this,18),V5Ui.dp(this,13),V5Ui.dp(this,18),V5Ui.dp(this,8));TextView back=V5Ui.text(this,"‹",31,V5Ui.GREEN,false);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(V5Ui.dp(this,38),V5Ui.dp(this,44)));LinearLayout t=V5Ui.column(this);t.addView(V5Ui.kicker(this,"ESTADÍSTICAS"));t.addView(V5Ui.text(this,"Rendimiento",26,V5Ui.INK,true));header.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));TextView refresh=V5Ui.text(this,"ACTUALIZAR",9.5f,V5Ui.GREEN,true);refresh.setGravity(Gravity.CENTER);refresh.setOnClickListener(v->refresh(true));header.addView(refresh,new LinearLayout.LayoutParams(V5Ui.dp(this,88),V5Ui.dp(this,40)));root.addView(header);

        LinearLayout filters=V5Ui.row(this);filters.setPadding(V5Ui.dp(this,18),0,V5Ui.dp(this,18),V5Ui.dp(this,8));String[] names={"Hoy","7 días","30 días","Mes","Personalizado"};period=new Spinner(this);ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,names);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);period.setAdapter(a);period.setSelection(1);period.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));filters.addView(period,new LinearLayout.LayoutParams(0,V5Ui.dp(this,44),1));customDates=V5Ui.text(this,"Elegir fechas",10,V5Ui.GREEN,true);customDates.setGravity(Gravity.CENTER);customDates.setBackground(V5Ui.bg(this,V5Ui.SURFACE_ALT,14));customDates.setOnClickListener(v->pickCustom());LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(V5Ui.dp(this,112),V5Ui.dp(this,44));cp.leftMargin=V5Ui.dp(this,8);filters.addView(customDates,cp);root.addView(filters);
        period.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){@Override public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){customDates.setVisibility(pos==4?View.VISIBLE:View.GONE);render();}@Override public void onNothingSelected(android.widget.AdapterView<?> p){}});

        status=V5Ui.text(this,"",9.5f,V5Ui.FAINT,false);status.setPadding(V5Ui.dp(this,18),0,V5Ui.dp(this,18),V5Ui.dp(this,5));root.addView(status);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);content=V5Ui.column(this);content.setPadding(V5Ui.dp(this,18),0,V5Ui.dp(this,18),V5Ui.dp(this,28));sc.addView(content);root.addView(sc,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));return root;
    }

    private void render(){
        if(content==null)return;content.removeAllViews();V5StatsEngine.Range range=currentRange();V5StatsEngine.ReservationStats r=V5StatsEngine.reservations(reservations,range);V5StatsEngine.PromotionStats p=V5StatsEngine.promotions(promoHistory,range);
        status.setText("Periodo · "+range.start+" → "+range.end+(core.internetAvailable()?"":" · sin conexión, datos guardados"));
        content.addView(sectionTitle("RESERVAS"));LinearLayout row=V5Ui.row(this);row.addView(metric("RESERVAS",r.reservations,V5StatsEngine.percent(r.reservationChangePct)+" vs. periodo anterior"),weight());LinearLayout.LayoutParams gp=weight();gp.leftMargin=V5Ui.dp(this,8);row.addView(metric("PERSONAS",r.people,V5StatsEngine.percent(r.peopleChangePct)+" vs. periodo anterior"),gp);content.addView(row);
        LinearLayout row2=V5Ui.row(this);row2.addView(metric("CONFIRMADAS",r.confirmed,"Pendientes · "+r.pending),weight());LinearLayout.LayoutParams gp2=weight();gp2.leftMargin=V5Ui.dp(this,8);row2.addView(metric("NO-SHOW",r.noShow,"Canceladas · "+r.cancelled),gp2);content.addView(row2,top(V5Ui.dp(this,8)));
        LinearLayout detail=V5Ui.softCard(this);detail.addView(V5Ui.text(this,"Media por reserva · "+String.format(new Locale("es","ES"),"%.1f",r.avgPartySize)+" personas",11,V5Ui.INK,true));detail.addView(V5Ui.text(this,"Hora punta · "+r.peakHour+"   ·   Interior "+r.interior+"   ·   Terraza "+r.terrace,10.5f,V5Ui.MUTED,false));content.addView(detail,top(V5Ui.dp(this,9)));
        StatsBarChartView chart=new StatsBarChartView(this);chart.setData(r.byDay);LinearLayout chartCard=V5Ui.card(this);chartCard.addView(V5Ui.kicker(this,"RESERVAS POR DÍA"));chartCard.addView(chart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,170)));content.addView(chartCard,top(V5Ui.dp(this,9)));

        content.addView(sectionTitle("PROMOCIONES"));LinearLayout pr=V5Ui.row(this);pr.addView(metric("JUGADAS",p.plays,V5StatsEngine.percent(p.playsChangePct)+" vs. periodo anterior"),weight());LinearLayout.LayoutParams pp=weight();pp.leftMargin=V5Ui.dp(this,8);pr.addView(metric("GANADORES",p.winners,String.format(new Locale("es","ES"),"%.0f %% de jugadas",p.winRate)),pp);content.addView(pr);
        LinearLayout pr2=V5Ui.row(this);pr2.addView(metric("CANJES",p.redeemed,String.format(new Locale("es","ES"),"%.0f %% de ganadores",p.redeemRate)),weight());LinearLayout.LayoutParams pp2=weight();pp2.leftMargin=V5Ui.dp(this,8);pr2.addView(metric("SIN PREMIO",p.withoutPrize,"jugadas sin premio"),pp2);content.addView(pr2,top(V5Ui.dp(this,8)));
        StatsBarChartView promoChart=new StatsBarChartView(this);promoChart.setData(p.byDay);LinearLayout pc=V5Ui.card(this);pc.addView(V5Ui.kicker(this,"JUGADAS POR DÍA"));pc.addView(promoChart,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,V5Ui.dp(this,170)));content.addView(pc,top(V5Ui.dp(this,9)));
    }

    private void refresh(boolean user){
        if(!core.configured()){if(user)Toast.makeText(this,"Falta configurar el servidor",Toast.LENGTH_SHORT).show();return;}status.setText("Actualizando…");io.execute(()->{
            JSONArray newReservations=null,newHistory=null;String error="";
            try{JSONObject rr=core.post(core.action("reservationList"));newReservations=array(rr,"items","reservations");}catch(Exception e){error=e.getMessage()==null?String.valueOf(e):e.getMessage();}
            try{JSONObject ph=core.post(core.action("promotionHistory"));newHistory=array(ph,"items","history","results");}catch(Exception e){if(error.isEmpty())error=e.getMessage()==null?String.valueOf(e):e.getMessage();}
            JSONArray fr=newReservations,fh=newHistory;String err=error;runOnUiThread(()->{if(fr!=null){reservations=fr;core.saveArray("reservations_all_v5",fr);}if(fh!=null){promoHistory=fh;core.saveArray("promo_history_v5",fh);}render();if(user&&!err.isEmpty())Toast.makeText(this,err,Toast.LENGTH_LONG).show();});
        });
    }

    private void pickCustom(){pickDate(customStart,date->{customStart=date;pickDate(customEnd,end->{customEnd=end;period.setSelection(4);customDates.setText(customStart+"\n"+customEnd);render();});});}
    private interface DateCb{void onDate(LocalDate date);}private void pickDate(LocalDate initial,DateCb cb){new DatePickerDialog(this,(v,y,m,d)->cb.onDate(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();}
    private V5StatsEngine.Range currentRange(){int p=period==null?1:period.getSelectedItemPosition();if(p==0)return V5StatsEngine.range(V5StatsEngine.Period.TODAY,null,null);if(p==2)return V5StatsEngine.range(V5StatsEngine.Period.LAST_30_DAYS,null,null);if(p==3)return V5StatsEngine.range(V5StatsEngine.Period.MONTH,null,null);if(p==4)return V5StatsEngine.range(V5StatsEngine.Period.CUSTOM,customStart,customEnd);return V5StatsEngine.range(V5StatsEngine.Period.LAST_7_DAYS,null,null);}
    private JSONArray array(JSONObject o,String...keys){for(String k:keys){JSONArray a=o.optJSONArray(k);if(a!=null)return a;}return new JSONArray();}
    private TextView sectionTitle(String s){TextView t=V5Ui.kicker(this,s);t.setPadding(0,V5Ui.dp(this,16),0,V5Ui.dp(this,7));return t;}
    private LinearLayout metric(String label,int value,String note){return V5Ui.metric(this,label,String.valueOf(value),note);}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1);}
    private LinearLayout.LayoutParams top(int px){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=px;return p;}
}
