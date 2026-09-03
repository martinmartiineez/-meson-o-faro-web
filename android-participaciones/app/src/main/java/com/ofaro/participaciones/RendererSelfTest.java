package com.ofaro.participaciones;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/** Prueba de humo del motor gráfico en el propio dispositivo Android. */
final class RendererSelfTest {
    private static final String[] TEMPLATES={
            "Reserva Express","Reserva Elegante","Reserva Completa","Reserva Cliente",
            "Promoción Premium","Promoción Clásica","Entrada QR","Ruleta QR","Rasca QR",
            "Premio Canjeable","Vale Regalo","Cupón Descuento","Próxima Visita",
            "QR Carta","QR Menú","QR WiFi","QR Reseñas","QR Instagram","QR Personalizado",
            "Promo del Día","Oferta Flash","Evento Especial","Novedad O Faro",
            "Minimal Premium","Ticket Editorial","Ticket Retro","Texto Libre","Solo Imagen"
    };
    private static final String[] QR_SIZES={"S","M","L","XL"};

    private RendererSelfTest(){}

    static Result run(){
        long start=System.currentTimeMillis();
        int passed=0;
        List<String> errors=new ArrayList<>();
        String image=sampleImage();
        for(int paper:new int[]{58,80}){
            for(int i=0;i<TEMPLATES.length;i++){
                String template=TEMPLATES[i];
                Bitmap rendered=null;
                try{
                    JSONObject job=new JSONObject()
                            .put("templateId",template)
                            .put("typography",i%2==0?"O Faro":"Editorial")
                            .put("paperWidth",paper)
                            .put("title","MESÓN O FARO · PRUEBA DE PLANTILLA")
                            .put("subtitle","VALIDACIÓN AUTOMÁTICA")
                            .put("text","Texto de prueba para comprobar saltos de línea, márgenes y composición.\nSegunda línea de validación.")
                            .put("qr","https://mesonofaro.es/sorteo/?diagnostico="+i+"&papel="+paper)
                            .put("qrSize",QR_SIZES[i%QR_SIZES.length])
                            .put("separator",i%3==0?"double":i%3==1?"dots":"line")
                            .put("imagePosition","Solo Imagen".equals(template)?"top":(i==22?"bottom":"none"))
                            .put("imageData",("Solo Imagen".equals(template)||i==22)?image:"")
                            .put("imageWidthPercent",75)
                            .put("copies",1);
                    rendered=TicketRenderer.render(job);
                    if(rendered==null||rendered.getWidth()<=0||rendered.getHeight()<=0)throw new Exception("Bitmap vacío");
                    int expected=paper<=58?384:576;
                    if(rendered.getWidth()!=expected)throw new Exception("Ancho inesperado: "+rendered.getWidth()+" px");
                    if(rendered.getHeight()>7000)throw new Exception("Altura fuera de límite: "+rendered.getHeight());
                    passed++;
                }catch(Throwable e){
                    String m=e.getMessage();if(m==null||m.trim().isEmpty())m=e.getClass().getSimpleName();
                    errors.add(template+" · "+paper+" mm · "+m);
                }finally{
                    if(rendered!=null&&!rendered.isRecycled())rendered.recycle();
                }
            }
        }
        return new Result(passed,TEMPLATES.length*2,errors,System.currentTimeMillis()-start);
    }

    private static String sampleImage(){
        Bitmap b=Bitmap.createBitmap(320,150,Bitmap.Config.ARGB_8888);
        try{
            Canvas c=new Canvas(b);c.drawColor(Color.WHITE);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(Color.BLACK);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(34);p.setFakeBoldText(true);
            c.drawText("O FARO",160,72,p);p.setTextSize(20);p.setFakeBoldText(false);c.drawText("IMAGEN DE PRUEBA",160,108,p);
            ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);
            return "data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
        }catch(Throwable e){return "";}finally{if(!b.isRecycled())b.recycle();}
    }

    static final class Result{
        final int passed,total;final List<String> errors;final long elapsedMs;
        Result(int passed,int total,List<String> errors,long elapsedMs){this.passed=passed;this.total=total;this.errors=errors;this.elapsedMs=elapsedMs;}
        boolean ok(){return passed==total&&errors.isEmpty();}
        String summary(){return (ok()?"CORRECTO":"CON FALLOS")+" · "+passed+"/"+total+" renderizados · "+elapsedMs+" ms";}
        String details(){StringBuilder s=new StringBuilder(summary());for(String e:errors)s.append("\n• ").append(e);return s.toString();}
    }
}
