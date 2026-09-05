package com.ofaro.participaciones;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class V5Ui {
    static final int BG = Color.rgb(246,247,242);
    static final int SURFACE = Color.rgb(253,253,249);
    static final int SURFACE_SOFT = Color.rgb(238,243,232);
    static final int SURFACE_ALT = Color.rgb(242,244,238);
    static final int INK = Color.rgb(17,20,17);
    static final int MUTED = Color.rgb(103,109,102);
    static final int FAINT = Color.rgb(150,156,149);
    static final int GREEN = Color.rgb(20,45,29);
    static final int GREEN_2 = Color.rgb(40,78,49);
    static final int LIME = Color.rgb(188,230,93);
    static final int LIME_SOFT = Color.rgb(231,244,200);
    static final int BORDER = Color.rgb(228,231,223);
    static final int ERROR = Color.rgb(168,67,56);
    static final int WARNING = Color.rgb(157,107,33);

    interface NavHandler { void onNavigate(int index); }

    private V5Ui(){}

    static void applySystemBars(Activity a){
        a.getWindow().setStatusBarColor(BG);
        a.getWindow().setNavigationBarColor(BG);
        int flags=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if(Build.VERSION.SDK_INT>=26) flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}

    static TextView text(Activity a,String value,float sp,int color,boolean bold){
        TextView t=new TextView(a);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if(Build.VERSION.SDK_INT>=21)t.setLetterSpacing(0f);
        t.setLineSpacing(0f,1.03f);
        t.setTypeface(Typeface.create("sans",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }

    static TextView kicker(Activity a,String value){
        TextView t=text(a,value,9.5f,MUTED,true);
        if(Build.VERSION.SDK_INT>=21)t.setLetterSpacing(.14f);
        return t;
    }

    static TextView title(Activity a,String value){return text(a,value,26,INK,true);}
    static TextView subtitle(Activity a,String value){return text(a,value,12.5f,MUTED,false);}

    static GradientDrawable bg(Activity a,int fill,float radius){
        GradientDrawable d=new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(a,(int)radius));
        return d;
    }

    static GradientDrawable outlined(Activity a,int fill,float radius,int stroke){
        GradientDrawable d=bg(a,fill,radius);
        d.setStroke(dp(a,1),stroke);
        return d;
    }

    static Drawable icon(Activity a,int res,int color){
        Drawable d=a.getDrawable(res);
        if(d!=null){d=d.mutate();d.setTintList(ColorStateList.valueOf(color));}
        return d;
    }

    static LinearLayout column(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.VERTICAL);return l;}
    static LinearLayout row(Activity a){LinearLayout l=new LinearLayout(a);l.setOrientation(LinearLayout.HORIZONTAL);return l;}

    static LinearLayout card(Activity a){
        LinearLayout c=column(a);
        c.setPadding(dp(a,15),dp(a,13),dp(a,15),dp(a,13));
        c.setBackground(bg(a,SURFACE,18));
        return c;
    }

    static LinearLayout softCard(Activity a){
        LinearLayout c=column(a);
        c.setPadding(dp(a,16),dp(a,15),dp(a,16),dp(a,15));
        c.setBackground(bg(a,SURFACE_SOFT,20));
        return c;
    }

    static LinearLayout darkCard(Activity a){
        LinearLayout c=column(a);
        c.setPadding(dp(a,17),dp(a,16),dp(a,17),dp(a,16));
        c.setBackground(bg(a,GREEN,22));
        return c;
    }

    static TextView pill(Activity a,String value,int fill,int textColor){
        TextView t=text(a,value,9.5f,textColor,true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(a,9),dp(a,5),dp(a,9),dp(a,5));
        t.setBackground(bg(a,fill,14));
        return t;
    }

    static LinearLayout linkCard(Activity a,int iconRes,String name,String note,Runnable action){
        LinearLayout c=row(a);c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(a,13),dp(a,11),dp(a,12),dp(a,11));
        c.setBackground(bg(a,SURFACE_ALT,18));
        ImageView iv=new ImageView(a);iv.setImageDrawable(icon(a,iconRes,GREEN));iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(a,25),dp(a,25));ip.rightMargin=dp(a,12);c.addView(iv,ip);
        LinearLayout copy=column(a);copy.addView(text(a,name,15,INK,true));if(note!=null&&!note.isEmpty()){TextView n=text(a,note,11,MUTED,false);n.setPadding(0,dp(a,3),0,0);copy.addView(n);}c.addView(copy,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        ImageView arrow=new ImageView(a);arrow.setImageDrawable(icon(a,R.drawable.ic_chevron_v5,action==null?FAINT:GREEN));c.addView(arrow,new LinearLayout.LayoutParams(dp(a,18),dp(a,18)));
        if(action!=null){c.setClickable(true);c.setOnClickListener(v->action.run());}
        return c;
    }

    static Header header(Activity a,AppCore core,String section){
        LinearLayout root=row(a);root.setGravity(Gravity.CENTER_VERTICAL);
        root.setPadding(dp(a,18),dp(a,8),dp(a,16),dp(a,8));root.setBackgroundColor(BG);
        LinearLayout left=column(a);left.addView(text(a,"O FARO",18,INK,true));TextView s=text(a,section,10.5f,MUTED,false);s.setPadding(0,dp(a,2),0,0);left.addView(s);root.addView(left,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));
        TextView state=pill(a,"● Impresora",SURFACE_ALT,GREEN);state.setMinWidth(dp(a,88));root.addView(state,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(a,31)));
        return new Header(root,state,s);
    }

    static final class Header {
        final View view;final TextView printer;final TextView section;
        Header(View v,TextView p,TextView s){view=v;printer=p;section=s;}
    }

    static View bottomNav(Activity a,int selected){
        return bottomNav(a,selected,index->navigate(a,index,selected));
    }

    static View bottomNav(Activity a,int selected,NavHandler handler){
        LinearLayout outer=column(a);outer.setPadding(dp(a,16),dp(a,3),dp(a,16),dp(a,7));outer.setBackgroundColor(BG);
        LinearLayout bar=row(a);bar.setGravity(Gravity.CENTER);bar.setPadding(dp(a,3),dp(a,3),dp(a,3),dp(a,3));bar.setBackground(bg(a,SURFACE,23));bar.setElevation(dp(a,2));
        int[] icons={R.drawable.ic_home_v5,R.drawable.ic_calendar_v5,R.drawable.ic_gift_v5,R.drawable.ic_chart_v5,R.drawable.ic_grid_v5,R.drawable.ic_settings_v5};
        String[] labels={"Inicio","Reservas","Promos","Stats","Gestión","Config"};
        for(int i=0;i<6;i++){
            final int index=i;LinearLayout item=column(a);item.setGravity(Gravity.CENTER);item.setPadding(0,0,0,0);
            FrameLayout badge=new FrameLayout(a);if(i==selected)badge.setBackground(bg(a,GREEN,15));
            ImageView iv=new ImageView(a);iv.setImageDrawable(icon(a,icons[i],i==selected?Color.WHITE:MUTED));iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);badge.addView(iv,new FrameLayout.LayoutParams(dp(a,19),dp(a,19),Gravity.CENTER));
            item.addView(badge,new LinearLayout.LayoutParams(dp(a,29),dp(a,29)));
            if(i==selected){TextView label=text(a,labels[i],7.5f,GREEN,true);label.setGravity(Gravity.CENTER);label.setPadding(0,dp(a,2),0,0);item.addView(label);}
            item.setOnClickListener(v->{if(handler!=null)handler.onNavigate(index);});
            bar.addView(item,new LinearLayout.LayoutParams(0,dp(a,44),1));
        }
        outer.addView(bar,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(a,50)));
        return outer;
    }

    static View floatingPlus(Activity a,Runnable action){
        FrameLayout circle=new FrameLayout(a);circle.setBackground(bg(a,GREEN,24));circle.setElevation(dp(a,5));
        ImageView iv=new ImageView(a);iv.setImageDrawable(icon(a,R.drawable.ic_plus_v5,Color.WHITE));iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);circle.addView(iv,new FrameLayout.LayoutParams(dp(a,21),dp(a,21),Gravity.CENTER));circle.setOnClickListener(v->action.run());return circle;
    }

    static LinearLayout quickAction(Activity a,int iconRes,String label,Runnable action){
        LinearLayout c=row(a);c.setGravity(Gravity.CENTER_VERTICAL);c.setPadding(dp(a,11),dp(a,9),dp(a,11),dp(a,9));c.setBackground(bg(a,SURFACE_ALT,17));
        FrameLayout badge=new FrameLayout(a);badge.setBackground(bg(a,GREEN,17));ImageView iv=new ImageView(a);iv.setImageDrawable(icon(a,iconRes,Color.WHITE));badge.addView(iv,new FrameLayout.LayoutParams(dp(a,17),dp(a,17),Gravity.CENTER));c.addView(badge,new LinearLayout.LayoutParams(dp(a,34),dp(a,34)));
        TextView t=text(a,label,12.5f,INK,true);t.setPadding(dp(a,9),0,0,0);c.addView(t,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));if(action!=null)c.setOnClickListener(v->action.run());return c;
    }

    static LinearLayout metric(Activity a,String label,String value,String note){
        LinearLayout c=column(a);c.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,12));c.setBackground(bg(a,SURFACE_ALT,18));c.addView(kicker(a,label));TextView v=text(a,value,27,INK,true);v.setPadding(0,dp(a,6),0,0);c.addView(v);if(note!=null)c.addView(text(a,note,10.5f,MUTED,false));return c;
    }

    static void navigate(Activity a,int target,int current){
        if(target==current)return;Intent i;
        if(target==0){i=new Intent(a,HomeActivityV2.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);}
        else if(target==1)i=new Intent(a,ReservationsV5Activity.class);
        else if(target==2)i=new Intent(a,PromotionsV5Activity.class);
        else {String section=target==3?V5SectionActivity.STATS:target==4?V5SectionActivity.MANAGEMENT:V5SectionActivity.SETTINGS;i=new Intent(a,V5SectionActivity.class).putExtra(V5SectionActivity.EXTRA_SECTION,section);}
        a.startActivity(i);a.overridePendingTransition(0,0);
    }

    static void updatePrinter(TextView target,AppCore core){
        if(target==null)return;
        if(core.printerIp().isEmpty()){target.setText("● Sin impresora");target.setTextColor(WARNING);return;}
        boolean ok=core.printerConnected();target.setText(ok?"● Conectada":"● Reconectando");target.setTextColor(ok?GREEN:WARNING);
    }
}
