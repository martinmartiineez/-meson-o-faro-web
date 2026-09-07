package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Selector v5 de plantilla + vista previa real renderizada por TicketRenderer.
 * Se invoca desde el hilo de impresión y bloquea solo ese hilo hasta confirmar/cancelar.
 */
final class V5PrintPreview {
    private static final String[] SYSTEM_TEMPLATES={
            "Reserva Express","Reserva Elegante","Reserva Completa","Reserva Cliente",
            "Promoción Premium","Promoción Clásica","Entrada QR","Ruleta QR","Rasca QR",
            "Premio Canjeable","Vale Regalo","Cupón Descuento","Próxima Visita",
            "QR Carta","QR Menú","QR WiFi","QR Reseñas","QR Instagram","QR Personalizado",
            "Promo del Día","Oferta Flash","Evento Especial","Novedad O Faro",
            "Minimal Premium","Ticket Editorial","Ticket Retro","Texto Libre","Solo Imagen"
    };

    private V5PrintPreview(){}

    static String choose(Activity activity,AppCore core,JSONObject baseJob,String prefKey,String fallback)throws Exception{
        if(activity==null||activity.isFinishing())return fallback;

        CountDownLatch latch=new CountDownLatch(1);
        AtomicReference<String> result=new AtomicReference<>("");
        AtomicBoolean cancelled=new AtomicBoolean(false);
        AtomicBoolean finished=new AtomicBoolean(false);

        activity.runOnUiThread(()->{
            final ExecutorService render=Executors.newSingleThreadExecutor();
            final AtomicInteger generation=new AtomicInteger(0);
            final AtomicReference<Bitmap> shown=new AtomicReference<>();

            LinearLayout box=new LinearLayout(activity);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(activity,16),dp(activity,8),dp(activity,16),dp(activity,6));

            TextView helper=text(activity,"Revisa el ticket y el QR antes de imprimir.",12,V5Ui.MUTED,false);
            helper.setPadding(0,0,0,dp(activity,10));
            box.addView(helper);

            ImageView preview=new ImageView(activity);
            preview.setAdjustViewBounds(true);
            preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            preview.setBackgroundColor(Color.WHITE);
            box.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,320)));

            TextView status=text(activity,"Generando vista previa…",11,V5Ui.MUTED,false);
            status.setGravity(Gravity.CENTER);
            status.setPadding(0,dp(activity,7),0,dp(activity,7));
            box.addView(status);

            TextView label=text(activity,"Plantilla",11,V5Ui.MUTED,true);
            label.setPadding(0,dp(activity,4),0,dp(activity,5));
            box.addView(label);

            List<Option> options=options(core);
            List<String> labels=new ArrayList<>();
            for(Option o:options)labels.add(o.label);
            Spinner spinner=new Spinner(activity);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setBackground(V5Ui.bg(activity,V5Ui.SURFACE_ALT,14));
            box.addView(spinner,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,48)));

            String explicit=baseJob.optString("templateId","").trim();
            String preferred=core.prefs().getString(prefKey,"").trim();
            String initial=!preferred.isEmpty()?preferred:(!explicit.isEmpty()?explicit:fallback);
            spinner.setSelection(indexOf(options,initial));

            CheckBox makeDefault=new CheckBox(activity);
            makeDefault.setText("Usar como predeterminada para este tipo");
            makeDefault.setTextSize(12);
            makeDefault.setTextColor(V5Ui.INK);
            makeDefault.setPadding(0,dp(activity,8),0,0);
            box.addView(makeDefault);

            TextView count=text(activity,SYSTEM_TEMPLATES.length+" plantillas del sistema"+(options.size()>SYSTEM_TEMPLATES.length?" · "+(options.size()-SYSTEM_TEMPLATES.length)+" personalizadas":""),10,V5Ui.FAINT,false);
            count.setPadding(0,dp(activity,3),0,0);
            box.addView(count);

            AtomicReference<AlertDialog> dialogRef=new AtomicReference<>();

            Runnable renderPreview=()->{
                int g=generation.incrementAndGet();
                int pos=spinner.getSelectedItemPosition();
                String templateId=options.get(Math.max(0,Math.min(pos,options.size()-1))).id;
                JSONObject job=cloneJob(baseJob);
                try{job.put("templateId",templateId);}catch(Exception ignored){}
                status.setText("Generando vista previa…");
                render.execute(()->{
                    Bitmap bm=null;String error="";
                    try{bm=TicketRenderer.render(job);}catch(Exception e){error=message(e);}
                    Bitmap out=bm;String err=error;
                    activity.runOnUiThread(()->{
                        if(g!=generation.get()){if(out!=null&&!out.isRecycled())out.recycle();return;}
                        Bitmap old=shown.getAndSet(out);
                        if(out!=null){preview.setImageBitmap(out);status.setText("Vista previa actualizada");status.setTextColor(V5Ui.GREEN);}else{status.setText("No se pudo generar · "+err);status.setTextColor(V5Ui.ERROR);}
                        if(old!=null&&old!=out&&!old.isRecycled())old.recycle();
                    });
                });
            };

            spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
                @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){renderPreview.run();}
                @Override public void onNothingSelected(AdapterView<?> parent){}
            });

            AlertDialog dialog=new AlertDialog.Builder(activity)
                    .setTitle("Vista previa de impresión")
                    .setView(box)
                    .setNegativeButton("VOLVER",null)
                    .setPositiveButton("IMPRIMIR",null)
                    .create();
            dialogRef.set(dialog);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnShowListener(d->{
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(V5Ui.GREEN);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(V5Ui.MUTED);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    int pos=spinner.getSelectedItemPosition();
                    Option option=options.get(Math.max(0,Math.min(pos,options.size()-1)));
                    result.set(option.id);
                    if(makeDefault.isChecked())core.prefs().edit().putString(prefKey,option.id).apply();
                    core.prefs().edit().putString("lastPrintTemplate",option.id).apply();
                    finished.set(true);
                    dialog.dismiss();
                    latch.countDown();
                });
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
                    cancelled.set(true);finished.set(true);dialog.dismiss();latch.countDown();
                });
                renderPreview.run();
            });
            dialog.setOnCancelListener(d->{if(finished.compareAndSet(false,true)){cancelled.set(true);latch.countDown();}});
            dialog.setOnDismissListener(d->{
                generation.incrementAndGet();
                render.shutdownNow();
                Bitmap old=shown.getAndSet(null);
                if(old!=null&&!old.isRecycled())old.recycle();
            });
            dialog.show();
        });

        latch.await();
        if(cancelled.get())throw new Exception("Impresión cancelada");
        String selected=result.get();
        return selected==null||selected.trim().isEmpty()?fallback:selected.trim();
    }

    private static List<Option> options(AppCore core){
        List<Option> out=new ArrayList<>();
        Set<String> ids=new HashSet<>();
        for(String s:SYSTEM_TEMPLATES){out.add(new Option(s,s));ids.add(s.toLowerCase());}
        JSONArray custom=core.cachedArray("templates_v5");
        for(int i=0;i<custom.length();i++){
            JSONObject x=custom.optJSONObject(i);if(x==null)continue;
            String id=x.optString("templateId",x.optString("name","")).trim();
            String name=x.optString("name",id).trim();
            if(id.isEmpty()||ids.contains(id.toLowerCase()))continue;
            out.add(new Option("Personalizada · "+(name.isEmpty()?id:name),id));
            ids.add(id.toLowerCase());
        }
        return out;
    }

    private static int indexOf(List<Option> options,String id){for(int i=0;i<options.size();i++)if(options.get(i).id.equalsIgnoreCase(id))return i;return Math.min(23,Math.max(0,options.size()-1));}
    private static JSONObject cloneJob(JSONObject x){try{return new JSONObject(x==null?"{}":x.toString());}catch(Exception e){return new JSONObject();}}
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    private static TextView text(Activity a,String s,float sp,int color,boolean bold){return V5Ui.text(a,s,sp,color,bold);}
    private static String message(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}

    private static final class Option{
        final String label,id;
        Option(String label,String id){this.label=label;this.id=id;}
    }
}
