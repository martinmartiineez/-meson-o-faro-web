package com.ofaro.participaciones;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Gráfico ligero sin dependencias externas, pensado para pocas categorías. */
final class StatsBarChartView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<String> labels=new ArrayList<>();
    private final List<Integer> values=new ArrayList<>();

    StatsBarChartView(Context context){super(context);setMinimumHeight(dp(150));}

    void setData(Map<String,Integer> data){labels.clear();values.clear();if(data!=null)for(Map.Entry<String,Integer> e:data.entrySet()){labels.add(shortLabel(e.getKey()));values.add(Math.max(0,e.getValue()));}invalidate();}

    @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;int left=dp(8),right=dp(8),top=dp(12),bottom=dp(30);int chartW=Math.max(1,w-left-right),chartH=Math.max(1,h-top-bottom);
        paint.setColor(Color.rgb(226,230,222));paint.setStrokeWidth(dp(1));c.drawLine(left,top+chartH,left+chartW,top+chartH,paint);
        if(values.isEmpty()){paint.setColor(V5Ui.MUTED);paint.setTextSize(sp(11));c.drawText("Sin datos para este periodo",left,top+chartH/2f,paint);return;}
        int max=1;for(int v:values)max=Math.max(max,v);float slot=chartW/(float)values.size();float barW=Math.min(dp(34),Math.max(dp(8),slot*.58f));
        paint.setTextAlign(Paint.Align.CENTER);for(int i=0;i<values.size();i++){int value=values.get(i);float x=left+slot*i+slot/2f;float bh=chartH*(value/(float)max);paint.setColor(V5Ui.GREEN);c.drawRoundRect(x-barW/2f,top+chartH-bh,x+barW/2f,top+chartH,dp(5),dp(5),paint);paint.setTextSize(sp(9));paint.setColor(V5Ui.INK);c.drawText(String.valueOf(value),x,Math.max(top+sp(9),top+chartH-bh-dp(4)),paint);paint.setTextSize(sp(8));paint.setColor(V5Ui.MUTED);c.drawText(labels.get(i),x,top+chartH+dp(16),paint);}paint.setTextAlign(Paint.Align.LEFT);
    }

    private String shortLabel(String s){if(s==null)return"";String v=s.trim();if(v.matches("\\d{4}-\\d{2}-\\d{2}"))return v.substring(8,10)+"/"+v.substring(5,7);if(v.length()>7)return v.substring(0,7);return v;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private float sp(int v){return v*getResources().getDisplayMetrics().scaledDensity;}
}
