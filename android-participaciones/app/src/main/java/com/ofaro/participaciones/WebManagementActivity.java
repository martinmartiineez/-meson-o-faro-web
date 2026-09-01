package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebManagementActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppCore core;
    private LinearLayout page;
    private TextView subtitle;
    private String currentSection = "";
    private String currentTitle = "";
    private JSONObject currentMeta;

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
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(246,246,246));
        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL); header.setPadding(dp(20),dp(18),dp(20),dp(16)); header.setBackgroundColor(Color.rgb(17,17,17));
        header.addView(text("MESÓN O FARO",23,Color.WHITE,true)); subtitle=text("Gestión de la web",14,Color.rgb(210,210,210),false); header.addView(subtitle); root.addView(header);
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); page=new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(18),dp(20),dp(18),dp(30)); scroll.addView(page); root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        return root;
    }

    private void showHome() {
        clear("Gestión de la web");
        addBackTo("Contenido web",this::finish);
        page.addView(paragraph("Todo lo que cambies aquí se guarda en la misma hoja de cálculo que alimenta la web. Las claves privadas no se muestran en este editor."));
        TextView status=text("Cargando apartados…",14,Color.DKGRAY,false); page.addView(status);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list,marginTopWrap(dp(10)));
        if(!core.configured()) { status.setText("Configura primero la API interna desde Ajustes."); return; }
        io.execute(() -> {
            try {
                JSONObject res=core.post(core.action("webSections")); core.ensureOk(res); JSONArray items=res.optJSONArray("items");
                runOnUiThread(() -> {
                    list.removeAllViews(); int n=items==null?0:items.length(); status.setText(n+" apartados editables");
                    for(int i=0;i<n;i++) { JSONObject s=items.optJSONObject(i); if(s==null)continue; Button b=bigButton(s.optString("title",s.optString("key","")),s.optString("description","Editar datos de la web")); String key=s.optString("key",""); String title=s.optString("title",key); b.setOnClickListener(v->showSection(key,title)); list.addView(b,marginBottomWrap(dp(10))); }
                });
            } catch(Exception e) { runOnUiThread(() -> status.setText("Error: "+cleanError(e)+"\n\nSi acabas de actualizar la APK, falta añadir el módulo GestionWeb en Apps Script.")); }
        });
    }

    private void showSection(String key,String title) {
        currentSection=key; currentTitle=title; clear(title); addBackTo(title,this::showHome);
        TextView status=text("Cargando…",14,Color.DKGRAY,false); page.addView(status);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); page.addView(list,marginTopWrap(dp(10)));
        io.execute(() -> {
            try {
                JSONObject res=core.post(core.action("webSectionRows").put("section",key)); core.ensureOk(res); currentMeta=res;
                JSONArray rows=res.optJSONArray("rows"); boolean allowAdd=res.optBoolean("allowAdd",false);
                runOnUiThread(() -> {
                    page.removeView(status); page.removeView(list);
                    if(allowAdd) { Button add=primaryButton("+ AÑADIR"); add.setOnClickListener(v->editRow(new JSONObject(),true)); page.addView(add); }
                    TextView count=text((rows==null?0:rows.length())+" registros",13,Color.DKGRAY,false); count.setPadding(0,dp(12),0,dp(8)); page.addView(count);
                    list.removeAllViews(); page.addView(list);
                    int n=rows==null?0:rows.length(); if(n==0)list.addView(emptyText("No hay registros."));
                    for(int i=0;i<n;i++) { JSONObject row=rows.optJSONObject(i); if(row!=null)addRowCard(list,row); }
                });
            } catch(Exception e) { runOnUiThread(() -> status.setText("Error: "+cleanError(e))); }
        });
    }

    private void addRowCard(LinearLayout list,JSONObject row) {
        LinearLayout card=cardBox();
        JSONArray fields=currentMeta==null?null:currentMeta.optJSONArray("fields");
        String head="Registro"; String secondary="";
        if(fields!=null) {
            for(int i=0;i<fields.length();i++) {
                JSONObject f=fields.optJSONObject(i); if(f==null||f.optBoolean("hidden",false))continue; String key=f.optString("key",""); String val=row.optString(key,"").trim(); if(val.isEmpty())continue;
                if(head.equals("Registro") && !f.optBoolean("readOnly",false)) head=val; else if(secondary.isEmpty()) secondary=f.optString("label",key)+": "+val;
            }
        }
        card.addView(text(head,18,Color.rgb(20,20,20),true));
        if(!secondary.isEmpty()) { TextView s=text(secondary,13,Color.DKGRAY,false); s.setPadding(0,dp(5),0,dp(10)); card.addView(s); }
        Button edit=primaryButton("EDITAR"); edit.setOnClickListener(v->editRow(row,false)); card.addView(edit); list.addView(card,marginBottomWrap(dp(10)));
    }

    private void editRow(JSONObject row,boolean isNew) {
        clear("Editar · "+currentTitle); addBackTo("Editar",()->showSection(currentSection,currentTitle));
        JSONArray fields=currentMeta==null?null:currentMeta.optJSONArray("fields"); if(fields==null){ alert("Error","No se recibió la definición de campos."); return; }
        LinkedHashMap<String,EditText> editors=new LinkedHashMap<>();
        for(int i=0;i<fields.length();i++) {
            JSONObject f=fields.optJSONObject(i); if(f==null||f.optBoolean("hidden",false))continue;
            String key=f.optString("key",""); String label=f.optString("label",key); String value=row.optString(key,""); boolean readOnly=f.optBoolean("readOnly",false); String type=f.optString("type","text"); boolean multi=f.optBoolean("multiline",false);
            TextView l=text(label,14,Color.DKGRAY,true); l.setPadding(dp(2),dp(12),dp(2),dp(6)); page.addView(l);
            if(readOnly && !isNew) { TextView ro=text(value.isEmpty()?"—":value,15,Color.rgb(35,35,35),false); ro.setPadding(dp(12),dp(12),dp(12),dp(12)); ro.setBackground(roundRect(Color.rgb(238,238,238),10,Color.rgb(210,210,210),1)); page.addView(ro); continue; }
            EditText e=input(value); if("number".equals(type))e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL); else e.setInputType(InputType.TYPE_CLASS_TEXT);
            if(multi){e.setSingleLine(false);e.setMinLines(4);e.setGravity(Gravity.TOP);e.setPadding(dp(14),dp(12),dp(14),dp(12));page.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));}
            else {e.setSingleLine(true);page.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));}
            editors.put(key,e);
        }
        Button save=primaryButton("GUARDAR CAMBIOS"); page.addView(save,marginTop(dp(18)));
        save.setOnClickListener(v -> {
            JSONObject values=new JSONObject();
            try {
                for(Map.Entry<String,EditText> en:editors.entrySet()) values.put(en.getKey(),en.getValue().getText().toString().trim());
                JSONArray fs=currentMeta.optJSONArray("fields"); if(fs!=null)for(int i=0;i<fs.length();i++){JSONObject f=fs.optJSONObject(i);if(f!=null&&f.optBoolean("readOnly",false)){String k=f.optString("key","");if(row.has(k))values.put(k,row.opt(k));}}
            } catch(Exception ignored) {}
            save.setEnabled(false); io.execute(() -> {
                try { JSONObject res=core.post(core.action("webSectionSave").put("section",currentSection).put("values",values)); core.ensureOk(res); runOnUiThread(() -> {toast("Cambios guardados");showSection(currentSection,currentTitle);}); }
                catch(Exception e){runOnUiThread(()->{save.setEnabled(true);alert("No se pudo guardar",cleanError(e));});}
            });
        });
        if(!isNew && currentMeta.optBoolean("allowDelete",false)) {
            Button del=secondaryButton("ELIMINAR REGISTRO"); page.addView(del,marginTop(dp(10))); del.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("Eliminar").setMessage("¿Eliminar este registro? El cambio afectará a la web.").setNegativeButton("Cancelar",null).setPositiveButton("ELIMINAR",(d,w)->deleteRow(row)).show());
        }
    }

    private void deleteRow(JSONObject row) {
        String idKey=currentMeta.optString("idKey","id"); String id=row.optString(idKey,""); if(id.isEmpty()){alert("Error","No se encuentra el identificador del registro.");return;}
        io.execute(() -> { try { JSONObject res=core.post(core.action("webSectionDelete").put("section",currentSection).put("id",id)); core.ensureOk(res); runOnUiThread(()->{toast("Registro eliminado");showSection(currentSection,currentTitle);}); } catch(Exception e){runOnUiThread(()->alert("No se pudo eliminar",cleanError(e)));} });
    }

    private void clear(String s){page.removeAllViews();subtitle.setText(s);}
    private void addBackTo(String title,Runnable action){LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);bar.setGravity(Gravity.CENTER_VERTICAL);Button back=secondaryButton("‹ ATRÁS");back.setOnClickListener(v->action.run());bar.addView(back,new LinearLayout.LayoutParams(dp(105),dp(48)));TextView t=text(title,21,Color.rgb(20,20,20),true);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(dp(12),0,0,0);bar.addView(t,new LinearLayout.LayoutParams(0,dp(48),1));page.addView(bar);page.addView(space(dp(12)));}
    private Button bigButton(String title,String sub){Button b=primaryButton(title+"\n"+sub);b.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);b.setPadding(dp(18),0,dp(18),0);b.setMinHeight(dp(76));return b;}
    private Button primaryButton(String label){Button b=new Button(this);b.setText(label);b.setTextSize(14);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.WHITE);b.setAllCaps(false);b.setBackground(roundRect(Color.rgb(17,17,17),12,Color.TRANSPARENT,0));b.setMinHeight(dp(54));return b;}
    private Button secondaryButton(String label){Button b=new Button(this);b.setText(label);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setTextColor(Color.rgb(20,20,20));b.setAllCaps(false);b.setBackground(roundRect(Color.WHITE,12,Color.rgb(190,190,190),1));b.setMinHeight(dp(52));return b;}
    private LinearLayout cardBox(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(15),dp(16),dp(15));c.setBackground(roundRect(Color.WHITE,14,Color.rgb(225,225,225),1));return c;}
    private TextView paragraph(String s){TextView t=text(s,15,Color.DKGRAY,false);t.setLineSpacing(0,1.15f);t.setPadding(0,0,0,dp(14));return t;}
    private TextView emptyText(String s){TextView t=text(s,15,Color.DKGRAY,false);t.setPadding(dp(4),dp(12),dp(4),dp(12));return t;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private EditText input(String value){EditText e=new EditText(this);e.setText(value);e.setTextSize(16);e.setTextColor(Color.rgb(20,20,20));e.setHintTextColor(Color.GRAY);e.setPadding(dp(14),0,dp(14),0);e.setBackground(roundRect(Color.WHITE,12,Color.rgb(205,205,205),1));return e;}
    private View space(int h){View v=new View(this);v.setLayoutParams(new LinearLayout.LayoutParams(1,h));return v;}
    private GradientDrawable roundRect(int fill,int radius,int stroke,int sw){GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(radius));if(sw>0)d.setStroke(dp(sw),stroke);return d;}
    private LinearLayout.LayoutParams marginTop(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54));p.topMargin=top;return p;}
    private LinearLayout.LayoutParams marginTopWrap(int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.topMargin=top;return p;}
    private LinearLayout.LayoutParams marginBottomWrap(int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.bottomMargin=bottom;return p;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private String cleanError(Throwable t){String m=t==null?"Error desconocido":t.getMessage();if(m==null||m.trim().isEmpty())m=String.valueOf(t);return m.length()>420?m.substring(0,420):m;}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void alert(String t,String m){if(!isFinishing())new AlertDialog.Builder(this).setTitle(t).setMessage(m).setPositiveButton("Aceptar",null).show();}
}
