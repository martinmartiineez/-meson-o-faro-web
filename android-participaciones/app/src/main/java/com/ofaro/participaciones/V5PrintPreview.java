package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

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
 * Vista previa real + selector y gestor de plantillas v5.
 * Las plantillas del usuario son presets de estilo locales: nunca guardan datos de clientes.
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
    private static final String[] SEPARATOR_LABELS={"Línea","Puntos","Guiones","Doble","Sin separador"};
    private static final String[] SEPARATOR_VALUES={"line","dots","dashes","double","none"};
    private static final String[] QR_SIZES={"S","M","L"};

    interface SelectionCallback { void onSelected(String id); }
    interface SaveCallback { void onSaved(JSONObject template); }

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
            final AtomicReference<List<Option>> optionsRef=new AtomicReference<>(options(core));

            LinearLayout box=new LinearLayout(activity);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(activity,16),dp(activity,8),dp(activity,16),dp(activity,6));

            TextView helper=text(activity,"Revisa el ticket y el QR antes de imprimir.",12,V5Ui.MUTED,false);
            helper.setPadding(0,0,0,dp(activity,9));
            box.addView(helper);

            ImageView preview=new ImageView(activity);
            preview.setAdjustViewBounds(true);
            preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            preview.setBackgroundColor(Color.WHITE);
            box.addView(preview,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,292)));

            TextView status=text(activity,"Generando vista previa…",10.5f,V5Ui.MUTED,false);
            status.setGravity(Gravity.CENTER);
            status.setPadding(0,dp(activity,6),0,dp(activity,6));
            box.addView(status);

            TextView label=text(activity,"Plantilla",10.5f,V5Ui.MUTED,true);
            label.setPadding(0,dp(activity,3),0,dp(activity,4));
            box.addView(label);

            List<String> labels=new ArrayList<>();
            for(Option o:optionsRef.get())labels.add(o.label);
            Spinner spinner=new Spinner(activity);
            ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,labels);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            spinner.setBackground(V5Ui.bg(activity,V5Ui.SURFACE_ALT,14));
            box.addView(spinner,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,46)));

            String explicit=baseJob.optString("templateId","").trim();
            String preferred=core.prefs().getString(prefKey,"").trim();
            String initial=!preferred.isEmpty()?preferred:(!explicit.isEmpty()?explicit:fallback);
            spinner.setSelection(indexOf(optionsRef.get(),initial));

            LinearLayout tools=new LinearLayout(activity);
            tools.setOrientation(LinearLayout.HORIZONTAL);
            tools.setPadding(0,dp(activity,7),0,0);
            TextView saveMine=smallButton(activity,"GUARDAR COMO MÍA");
            TextView manageMine=smallButton(activity,"MIS PLANTILLAS");
            tools.addView(saveMine,new LinearLayout.LayoutParams(0,dp(activity,38),1));
            LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,dp(activity,38),1);mp.leftMargin=dp(activity,7);tools.addView(manageMine,mp);
            box.addView(tools);

            CheckBox makeDefault=new CheckBox(activity);
            makeDefault.setText("Usar como predeterminada para este tipo");
            makeDefault.setTextSize(11.5f);
            makeDefault.setTextColor(V5Ui.INK);
            makeDefault.setPadding(0,dp(activity,5),0,0);
            box.addView(makeDefault);

            TextView count=text(activity,"",9.5f,V5Ui.FAINT,false);
            count.setPadding(0,dp(activity,2),0,0);
            box.addView(count);

            Runnable[] renderPreview=new Runnable[1];
            Runnable[] reloadOptions=new Runnable[1];

            reloadOptions[0]=()->{
                List<Option> before=optionsRef.get();
                String keep="";
                int oldPos=spinner.getSelectedItemPosition();
                if(before!=null&&!before.isEmpty()&&oldPos>=0&&oldPos<before.size())keep=before.get(oldPos).id;
                List<Option> fresh=options(core);
                optionsRef.set(fresh);
                adapter.clear();
                for(Option o:fresh)adapter.add(o.label);
                adapter.notifyDataSetChanged();
                spinner.setSelection(indexOf(fresh,keep));
                int local=V5TemplateStore.count(core);
                int other=Math.max(0,fresh.size()-SYSTEM_TEMPLATES.length-local);
                count.setText(SYSTEM_TEMPLATES.length+" del sistema · "+local+" mías"+(other>0?" · "+other+" guardadas":""));
            };

            renderPreview[0]=()->{
                int g=generation.incrementAndGet();
                List<Option> current=optionsRef.get();
                if(current==null||current.isEmpty())return;
                int pos=Math.max(0,Math.min(spinner.getSelectedItemPosition(),current.size()-1));
                Option option=current.get(pos);
                JSONObject job=cloneJob(baseJob);
                applyOption(job,option);
                status.setText("Generando vista previa…");status.setTextColor(V5Ui.MUTED);
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
                @Override public void onItemSelected(AdapterView<?> parent,View view,int position,long id){renderPreview[0].run();}
                @Override public void onNothingSelected(AdapterView<?> parent){}
            });

            saveMine.setOnClickListener(v->{
                List<Option> current=optionsRef.get();
                if(current==null||current.isEmpty())return;
                int pos=Math.max(0,Math.min(spinner.getSelectedItemPosition(),current.size()-1));
                Option selected=current.get(pos);
                JSONObject styled=cloneJob(baseJob);applyOption(styled,selected);
                showEditor(activity,core,null,selected.renderTemplate,
                        styled.optInt("paperWidth",core.printerPaper()),
                        styled.optString("separator","line"),styled.optString("qrSize","L"),saved->{
                            reloadOptions[0].run();
                            spinner.setSelection(indexOf(optionsRef.get(),saved.optString("id","")));
                        });
            });

            manageMine.setOnClickListener(v->{
                List<Option> current=optionsRef.get();
                String selectedId="";
                if(current!=null&&!current.isEmpty()){
                    int pos=Math.max(0,Math.min(spinner.getSelectedItemPosition(),current.size()-1));
                    selectedId=current.get(pos).id;
                }
                showManager(activity,core,selectedId,id->{
                    reloadOptions[0].run();
                    if(id!=null&&!id.isEmpty())spinner.setSelection(indexOf(optionsRef.get(),id));
                });
            });

            AlertDialog dialog=new AlertDialog.Builder(activity)
                    .setTitle("Vista previa de impresión")
                    .setView(box)
                    .setNegativeButton("VOLVER",null)
                    .setPositiveButton("IMPRIMIR",null)
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnShowListener(d->{
                reloadOptions[0].run();
                String selectedInitial=!preferred.isEmpty()?preferred:(!explicit.isEmpty()?explicit:fallback);
                spinner.setSelection(indexOf(optionsRef.get(),selectedInitial));
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(V5Ui.GREEN);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(V5Ui.MUTED);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                    List<Option> current=optionsRef.get();
                    if(current==null||current.isEmpty())return;
                    int pos=Math.max(0,Math.min(spinner.getSelectedItemPosition(),current.size()-1));
                    Option option=current.get(pos);
                    applyOption(baseJob,option);
                    result.set(option.renderTemplate);
                    if(makeDefault.isChecked())core.prefs().edit().putString(prefKey,option.id).apply();
                    core.prefs().edit().putString("lastPrintTemplate",option.id).apply();
                    finished.set(true);dialog.dismiss();latch.countDown();
                });
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{cancelled.set(true);finished.set(true);dialog.dismiss();latch.countDown();});
                renderPreview[0].run();
            });
            dialog.setOnCancelListener(d->{if(finished.compareAndSet(false,true)){cancelled.set(true);latch.countDown();}});
            dialog.setOnDismissListener(d->{
                generation.incrementAndGet();render.shutdownNow();Bitmap old=shown.getAndSet(null);if(old!=null&&!old.isRecycled())old.recycle();
            });
            dialog.show();
        });

        latch.await();
        if(cancelled.get())throw new Exception("Impresión cancelada");
        String selected=result.get();
        return selected==null||selected.trim().isEmpty()?fallback:selected.trim();
    }

    /** Gestor independiente, preparado para abrirlo desde Gestión → Plantillas. */
    static void manage(Activity activity,AppCore core){showManager(activity,core,"",id->{});}

    private static void showManager(Activity activity,AppCore core,String currentId,SelectionCallback callback){
        List<JSONObject> local=V5TemplateStore.list(core);
        List<String> items=new ArrayList<>();
        items.add("＋ Nueva plantilla");
        for(JSONObject x:local)items.add("Mía · "+x.optString("name","Mi plantilla"));
        items.add("Restablecer plantillas predeterminadas");
        String[] arr=items.toArray(new String[0]);
        new AlertDialog.Builder(activity).setTitle("Mis plantillas · "+local.size()).setItems(arr,(d,which)->{
            if(which==0){
                String base="Minimal Premium";
                JSONObject cur=V5TemplateStore.get(core,currentId);if(cur!=null)base=cur.optString("baseTemplate",base);
                showEditor(activity,core,null,base,core.printerPaper(),"line","L",saved->{toast(activity,"Plantilla creada");if(callback!=null)callback.onSelected(saved.optString("id",""));});
            }else if(which==arr.length-1){
                new AlertDialog.Builder(activity).setTitle("Restablecer predeterminadas").setMessage("Volverán a utilizarse las plantillas originales del sistema según cada tipo de ticket.").setNegativeButton("Cancelar",null).setPositiveButton("Restablecer",(x,w)->{V5TemplateStore.resetDefaults(core);toast(activity,"Predeterminadas restablecidas");}).show();
            }else{
                JSONObject selected=local.get(which-1);showLocalActions(activity,core,selected,callback);
            }
        }).setNegativeButton("Cerrar",null).show();
    }

    private static void showLocalActions(Activity activity,AppCore core,JSONObject template,SelectionCallback callback){
        String[] actions={"Usar ahora","Editar","Duplicar","Predeterminada general","Predeterminada QR","Predeterminada reservas","Exportar","Eliminar"};
        new AlertDialog.Builder(activity).setTitle(template.optString("name","Mi plantilla")).setItems(actions,(d,w)->{
            String id=template.optString("id","");
            if(w==0){if(callback!=null)callback.onSelected(id);}
            else if(w==1){showEditor(activity,core,template,template.optString("baseTemplate","Minimal Premium"),template.optInt("paperWidth",80),template.optString("separator","line"),template.optString("qrSize","L"),saved->{toast(activity,"Plantilla actualizada");if(callback!=null)callback.onSelected(saved.optString("id",""));});}
            else if(w==2){JSONObject copy=V5TemplateStore.duplicate(template);V5TemplateStore.save(core,copy);toast(activity,"Copia creada");if(callback!=null)callback.onSelected(copy.optString("id",""));}
            else if(w==3){core.prefs().edit().putString("defaultTemplateGeneral",id).apply();toast(activity,"Predeterminada general actualizada");}
            else if(w==4){core.prefs().edit().putString("defaultTemplateQr",id).apply();toast(activity,"Predeterminada para QR actualizada");}
            else if(w==5){core.prefs().edit().putString("defaultTemplateReservation",id).apply();toast(activity,"Predeterminada para reservas actualizada");}
            else if(w==6){shareTemplate(activity,template);}
            else if(w==7){new AlertDialog.Builder(activity).setTitle("Eliminar plantilla").setMessage("¿Eliminar «"+template.optString("name","Mi plantilla")+"»? Las plantillas del sistema no se borran.").setNegativeButton("Cancelar",null).setPositiveButton("Eliminar",(x,y)->{V5TemplateStore.delete(core,id);toast(activity,"Plantilla eliminada");if(callback!=null)callback.onSelected("");}).show();}
        }).setNegativeButton("Cerrar",null).show();
    }

    private static void showEditor(Activity activity,AppCore core,JSONObject existing,String baseDefault,int paperDefault,String separatorDefault,String qrDefault,SaveCallback callback){
        LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(activity,18),dp(activity,4),dp(activity,18),dp(activity,4));
        TextView note=text(activity,"La plantilla guarda solo el diseño. El nombre, reserva, QR y demás contenido se añaden al imprimir.",10.5f,V5Ui.MUTED,false);note.setPadding(0,0,0,dp(activity,8));box.addView(note);
        box.addView(formLabel(activity,"Nombre"));
        EditText name=new EditText(activity);name.setSingleLine(true);name.setText(existing==null?"Mi plantilla":existing.optString("name","Mi plantilla"));name.setTextSize(13);name.setTextColor(V5Ui.INK);name.setInputType(InputType.TYPE_CLASS_TEXT);name.setPadding(dp(activity,12),0,dp(activity,12),0);name.setBackground(V5Ui.bg(activity,V5Ui.SURFACE_ALT,13));box.addView(name,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,44)));

        box.addView(formLabel(activity,"Diseño base"));
        Spinner base=new Spinner(activity);ArrayAdapter<String> ba=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,SYSTEM_TEMPLATES);ba.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);base.setAdapter(ba);base.setSelection(systemIndex(baseDefault));base.setBackground(V5Ui.bg(activity,V5Ui.SURFACE_ALT,13));box.addView(base,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,44)));

        box.addView(formLabel(activity,"Ancho de papel"));
        String[] papers={"58 mm","80 mm"};Spinner paper=new Spinner(activity);ArrayAdapter<String> pa=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,papers);pa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);paper.setAdapter(pa);paper.setSelection(paperDefault<=58?0:1);paper.setBackground(V5Ui.bg(activity,V5Ui.SURFACE_ALT,13));box.addView(paper,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,44)));

        box.addView(formLabel(activity,"Separador"));
        Spinner separator=new Spinner(activity);ArrayAdapter<String> sa=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,SEPARATOR_LABELS);sa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);separator.setAdapter(sa);separator.setSelection(indexOf(SEPARATOR_VALUES,separatorDefault));separator.setBackground(V5Ui.bg(activity,V5Ui.SURFACE_ALT,13));box.addView(separator,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,44)));

        box.addView(formLabel(activity,"Tamaño del QR"));
        Spinner qr=new Spinner(activity);ArrayAdapter<String> qa=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_item,QR_SIZES);qa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);qr.setAdapter(qa);qr.setSelection(indexOf(QR_SIZES,qrDefault));qr.setBackground(V5Ui.bg(activity,V5Ui.SURFACE_ALT,13));box.addView(qr,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,44)));

        new AlertDialog.Builder(activity).setTitle(existing==null?"Nueva plantilla":"Editar plantilla").setView(box).setNegativeButton("Cancelar",null).setPositiveButton("Guardar",(d,w)->{
            JSONObject out;
            if(existing==null)out=V5TemplateStore.create(name.getText().toString(),String.valueOf(base.getSelectedItem()),paper.getSelectedItemPosition()==0?58:80,SEPARATOR_VALUES[separator.getSelectedItemPosition()],QR_SIZES[qr.getSelectedItemPosition()]);
            else{
                try{out=new JSONObject(existing.toString());}catch(Exception e){out=V5TemplateStore.create(name.getText().toString(),String.valueOf(base.getSelectedItem()),paper.getSelectedItemPosition()==0?58:80,SEPARATOR_VALUES[separator.getSelectedItemPosition()],QR_SIZES[qr.getSelectedItemPosition()]);}
                try{out.put("name",name.getText().toString().trim().isEmpty()?"Mi plantilla":name.getText().toString().trim()).put("baseTemplate",String.valueOf(base.getSelectedItem())).put("paperWidth",paper.getSelectedItemPosition()==0?58:80).put("separator",SEPARATOR_VALUES[separator.getSelectedItemPosition()]).put("qrSize",QR_SIZES[qr.getSelectedItemPosition()]);}catch(Exception ignored){}
            }
            V5TemplateStore.save(core,out);if(callback!=null)callback.onSaved(out);
        }).show();
    }

    private static void shareTemplate(Activity activity,JSONObject template){
        try{
            Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/json");send.putExtra(Intent.EXTRA_SUBJECT,"Plantilla O Faro · "+template.optString("name","Mi plantilla"));send.putExtra(Intent.EXTRA_TEXT,V5TemplateStore.exportJson(template));activity.startActivity(Intent.createChooser(send,"Exportar plantilla"));
        }catch(Exception e){toast(activity,"No se pudo exportar la plantilla");}
    }

    private static List<Option> options(AppCore core){
        List<Option> out=new ArrayList<>();Set<String> ids=new HashSet<>();
        for(String s:SYSTEM_TEMPLATES){out.add(new Option(s,s,s,null));ids.add(s.toLowerCase());}
        for(JSONObject x:V5TemplateStore.list(core)){
            String id=x.optString("id","").trim();if(id.isEmpty()||ids.contains(id.toLowerCase()))continue;
            String name=x.optString("name","Mi plantilla");String base=x.optString("baseTemplate","Minimal Premium");out.add(new Option("Mía · "+name,id,base,x));ids.add(id.toLowerCase());
        }
        JSONArray custom=core.cachedArray("templates_v5");
        for(int i=0;i<custom.length();i++){
            JSONObject x=custom.optJSONObject(i);if(x==null)continue;String id=x.optString("templateId",x.optString("name","")).trim();String name=x.optString("name",id).trim();if(id.isEmpty()||ids.contains(id.toLowerCase()))continue;out.add(new Option("Guardada · "+(name.isEmpty()?id:name),id,id,null));ids.add(id.toLowerCase());
        }
        return out;
    }

    private static void applyOption(JSONObject job,Option option){
        if(job==null||option==null)return;
        if(option.local!=null)V5TemplateStore.apply(job,option.local);else try{job.put("templateId",option.renderTemplate);}catch(Exception ignored){}
    }

    private static TextView smallButton(Activity a,String label){TextView t=text(a,label,9.5f,V5Ui.GREEN,true);t.setGravity(Gravity.CENTER);t.setBackground(V5Ui.bg(a,V5Ui.SURFACE_ALT,13));return t;}
    private static TextView formLabel(Activity a,String label){TextView t=text(a,label,10,V5Ui.MUTED,true);t.setPadding(0,dp(a,8),0,dp(a,4));return t;}
    private static int indexOf(List<Option> options,String id){if(options==null||options.isEmpty())return 0;for(int i=0;i<options.size();i++)if(options.get(i).id.equalsIgnoreCase(id))return i;for(int i=0;i<options.size();i++)if(options.get(i).renderTemplate.equalsIgnoreCase(id))return i;return Math.min(23,options.size()-1);}
    private static int indexOf(String[] values,String value){if(values==null||values.length==0)return 0;for(int i=0;i<values.length;i++)if(values[i].equalsIgnoreCase(value))return i;return 0;}
    private static int systemIndex(String value){return indexOf(SYSTEM_TEMPLATES,value);}
    private static JSONObject cloneJob(JSONObject x){try{return new JSONObject(x==null?"{}":x.toString());}catch(Exception e){return new JSONObject();}}
    private static int dp(Activity a,int v){return Math.round(v*a.getResources().getDisplayMetrics().density);}
    private static TextView text(Activity a,String s,float sp,int color,boolean bold){return V5Ui.text(a,s,sp,color,bold);}
    private static String message(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m;}
    private static void toast(Activity a,String text){Toast.makeText(a,text,Toast.LENGTH_SHORT).show();}

    private static final class Option{
        final String label,id,renderTemplate;final JSONObject local;
        Option(String label,String id,String renderTemplate,JSONObject local){this.label=label;this.id=id;this.renderTemplate=renderTemplate;this.local=local;}
    }
}
