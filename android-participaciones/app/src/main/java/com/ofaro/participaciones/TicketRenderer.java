package com.ofaro.participaciones;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.Base64;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class TicketRenderer {
    private static final int MAX_HEIGHT = 6000;

    static Bitmap render(JSONObject job) throws Exception {
        int paper = job.optInt("paperWidth",80);
        int width = paper <= 58 ? 384 : 576;
        int margin = paper <= 58 ? 22 : 30;
        int contentW = width - margin * 2;
        String template = safe(job.optString("templateId",job.optString("template","Minimal Premium")));
        String typography = safe(job.optString("typography","O Faro"));
        String title = safe(job.optString("title",""));
        String subtitle = safe(job.optString("subtitle",""));
        String body = safe(job.optString("text",""));
        String qr = safe(job.optString("qr",""));
        String pos = safe(job.optString("imagePosition","none"));
        int imagePct = clamp(job.optInt("imageWidthPercent",75),25,100);
        String qrSize = safe(job.optString("qrSize","L"));
        String separator = safe(job.optString("separator","line"));

        Bitmap image = decodeImage(job.optString("imageData",""));
        Bitmap work = Bitmap.createBitmap(width,MAX_HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(work);
        c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.BLACK);
        p.setTypeface(typeface(typography,false));
        float y = templateTop(c,p,template,width,margin);

        if ("Solo Imagen".equalsIgnoreCase(template)) {
            if (image != null) y = drawImage(c,image,y,width,margin,imagePct,true) + 18;
            else y = drawCentered(c,p,"SIN IMAGEN",y,contentW,margin,30,true) + 20;
            return crop(work,(int)Math.min(MAX_HEIGHT,Math.max(80,y)));
        }

        if (image != null && "top".equalsIgnoreCase(pos)) y = drawImage(c,image,y,width,margin,imagePct,false) + 18;

        if (!title.isEmpty()) {
            int size = titleSize(template,paper);
            y = drawCentered(c,p,title.toUpperCase(),y,contentW,margin,size,true) + 6;
        }
        if (!subtitle.isEmpty()) {
            int size = subtitleSize(template,paper);
            if ("Premio Canjeable".equalsIgnoreCase(template) || "Cupón Moderno".equalsIgnoreCase(template)) {
                y = drawBoxText(c,p,subtitle.toUpperCase(),y,width,margin,size,typeface(typography,true)) + 10;
            } else {
                y = drawCentered(c,p,subtitle.toUpperCase(),y,contentW,margin,size,true) + 5;
            }
        }

        if (!title.isEmpty() || !subtitle.isEmpty()) y = drawSeparator(c,p,y,width,margin,separator,template) + 14;

        if ("Entrada QR".equalsIgnoreCase(template)) {
            if (!body.isEmpty()) y = drawBody(c,p,body,y,contentW,margin,bodySize(paper),typography) + 8;
            if (!qr.isEmpty()) y = drawQr(c,qr,y,width,margin,qrPx("XL",paper)) + 8;
        } else if (template.toLowerCase().startsWith("qr ") || "QR Carta / Menú".equalsIgnoreCase(template) || "QR WiFi".equalsIgnoreCase(template)) {
            if (!qr.isEmpty()) y = drawQr(c,qr,y,width,margin,qrPx("XL",paper)) + 12;
            if (!body.isEmpty()) y = drawCentered(c,p,body,y,contentW,margin,bodySize(paper),false) + 8;
        } else {
            if (!body.isEmpty()) y = drawBody(c,p,body,y,contentW,margin,bodySize(paper),typography) + 10;
            if (!qr.isEmpty()) y = drawQr(c,qr,y,width,margin,qrPx(qrSize,paper)) + 8;
        }

        if (image != null && "bottom".equalsIgnoreCase(pos)) y = drawImage(c,image,y,width,margin,imagePct,false) + 18;

        y = templateBottom(c,p,template,y,width,margin);
        y += 22;
        if (image != null && !image.isRecycled()) image.recycle();
        return crop(work,(int)Math.min(MAX_HEIGHT,Math.max(120,y)));
    }

    private static float templateTop(Canvas c, Paint p, String t, int width, int m) {
        float y = 18;
        if ("Ticket Evento".equalsIgnoreCase(t) || "Entrada QR".equalsIgnoreCase(t)) {
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(4); c.drawRoundRect(new RectF(8,8,width-8,MAX_HEIGHT-8),18,18,p); p.setStyle(Paint.Style.FILL); y=30;
        } else if ("Cupón Moderno".equalsIgnoreCase(t)) {
            p.setStrokeWidth(3); for(int x=10;x<width-10;x+=18)c.drawLine(x,12,Math.min(x+10,width-10),12,p); y=28;
        } else if ("Participación Clásica".equalsIgnoreCase(t)) {
            p.setStrokeWidth(2); c.drawLine(m,15,width-m,15,p); y=28;
        }
        return y;
    }

    private static float templateBottom(Canvas c, Paint p, String t, float y, int width, int m) {
        if ("Ticket Evento".equalsIgnoreCase(t) || "Entrada QR".equalsIgnoreCase(t)) {
            p.setStrokeWidth(2); c.drawLine(m,y,width-m,y,p); return y+8;
        }
        if ("Cupón Moderno".equalsIgnoreCase(t)) {
            p.setStrokeWidth(3); for(int x=10;x<width-10;x+=18)c.drawLine(x,y,Math.min(x+10,width-10),y,p); return y+10;
        }
        return y;
    }

    private static float drawCentered(Canvas c, Paint p, String text, float y, int maxW, int x, int size, boolean bold) {
        p.setTextSize(size); p.setTypeface(bold ? Typeface.create(p.getTypeface(),Typeface.BOLD) : Typeface.create(p.getTypeface(),Typeface.NORMAL)); p.setTextAlign(Paint.Align.CENTER);
        List<String> lines = wrap(p,text,maxW);
        float line = size*1.25f;
        for(String s:lines){ y += size; c.drawText(s,x+maxW/2f,y,p); y += line-size; }
        p.setTextAlign(Paint.Align.LEFT); return y;
    }

    private static float drawBody(Canvas c, Paint p, String text, float y, int maxW, int x, int size, String typography) {
        p.setTextSize(size); p.setTypeface(typeface(typography,false)); p.setTextAlign(Paint.Align.LEFT);
        float line=size*1.35f;
        String[] paras=text.split("\\n",-1);
        for(String para:paras){
            if(para.trim().isEmpty()){y+=line*.65f;continue;}
            List<String> lines=wrap(p,para,maxW);
            for(String s:lines){y+=size;c.drawText(s,x,y,p);y+=line-size;}
        }
        return y;
    }

    private static float drawBoxText(Canvas c, Paint p, String text, float y, int width, int m, int size, Typeface tf) {
        p.setTypeface(tf); p.setTextSize(size); List<String> lines=wrap(p,text,width-m*4); float h=lines.size()*size*1.35f+26;
        p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRoundRect(new RectF(m,y,width-m,y+h),14,14,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
        float yy=y+16;for(String s:lines){yy+=size;c.drawText(s,width/2f,yy,p);yy+=size*.35f;}p.setTextAlign(Paint.Align.LEFT);return y+h;
    }

    private static float drawSeparator(Canvas c, Paint p, float y, int width, int m, String kind, String template) {
        p.setStrokeWidth("double".equalsIgnoreCase(kind)?3:2);
        if ("none".equalsIgnoreCase(kind)) return y;
        if ("dots".equalsIgnoreCase(kind) || "Retro".equalsIgnoreCase(template)) {
            for(int x=m;x<width-m;x+=12)c.drawCircle(x,y+5,2,p);return y+10;
        }
        if ("dashes".equalsIgnoreCase(kind) || "Cupón Moderno".equalsIgnoreCase(template)) {
            for(int x=m;x<width-m;x+=20)c.drawLine(x,y+5,Math.min(x+12,width-m),y+5,p);return y+10;
        }
        c.drawLine(m,y+4,width-m,y+4,p);
        if("double".equalsIgnoreCase(kind))c.drawLine(m,y+10,width-m,y+10,p);
        return y+12;
    }

    private static float drawImage(Canvas c, Bitmap img, float y, int width, int m, int pct, boolean max) {
        int available=width-m*2;int target=max?available:Math.max(40,available*pct/100);float scale=Math.min(target/(float)img.getWidth(),(MAX_HEIGHT-y-40)/Math.max(1f,img.getHeight()));int w=Math.max(1,Math.round(img.getWidth()*scale));int h=Math.max(1,Math.round(img.getHeight()*scale));float left=(width-w)/2f;c.drawBitmap(img,null,new RectF(left,y,left+w,y+h),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return y+h;
    }

    private static float drawQr(Canvas c, String data, float y, int width, int m, int size) throws Exception {
        size=Math.min(size,width-m*2);BitMatrix matrix=new MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,size,size);Bitmap qr=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        for(int yy=0;yy<size;yy++)for(int xx=0;xx<size;xx++)qr.setPixel(xx,yy,matrix.get(xx,yy)?Color.BLACK:Color.WHITE);
        c.drawBitmap(qr,(width-size)/2f,y,null);qr.recycle();return y+size;
    }

    private static List<String> wrap(Paint p, String text, int maxW) {
        List<String> out=new ArrayList<>();String clean=text==null?"":text.trim();if(clean.isEmpty()){out.add("");return out;}
        String[] words=clean.split("\\s+");String line="";
        for(String w:words){String test=line.isEmpty()?w:line+" "+w;if(p.measureText(test)<=maxW){line=test;}else{if(!line.isEmpty())out.add(line);if(p.measureText(w)<=maxW){line=w;}else{String part="";for(int i=0;i<w.length();i++){String t=part+w.charAt(i);if(p.measureText(t)>maxW&&!part.isEmpty()){out.add(part);part="";}part+=w.charAt(i);}line=part;}}}
        if(!line.isEmpty())out.add(line);return out;
    }

    private static Typeface typeface(String preset, boolean bold) {
        String p=safe(preset).toLowerCase();String family="sans-serif";int style=bold?Typeface.BOLD:Typeface.NORMAL;
        if(p.contains("editorial")||p.contains("elegante"))family="serif";
        else if(p.contains("retro")||p.contains("clásico")||p.contains("clasico"))family="monospace";
        else if(p.contains("minimal"))family="sans-serif-light";
        else if(p.contains("o faro"))family="sans-serif-condensed";
        else if(p.contains("impacto")){family="sans-serif-black";style=Typeface.BOLD;}
        return Typeface.create(family,style);
    }

    private static int titleSize(String template,int paper){int base=paper<=58?34:42;if(template.toLowerCase().contains("minimal"))return base-4;if(template.toLowerCase().contains("evento"))return base+4;if(template.toLowerCase().contains("premio"))return base+6;return base;}
    private static int subtitleSize(String template,int paper){int base=paper<=58?23:28;if(template.toLowerCase().contains("premio"))return base+6;return base;}
    private static int bodySize(int paper){return paper<=58?20:24;}
    private static int qrPx(String s,int paper){int max=paper<=58?290:420;String q=s.toUpperCase();if("S".equals(q))return Math.min(max,paper<=58?150:180);if("M".equals(q))return Math.min(max,paper<=58?190:230);if("XL".equals(q))return Math.min(max,paper<=58?290:400);return Math.min(max,paper<=58?235:300);}
    private static Bitmap decodeImage(String data){try{if(data==null||data.isEmpty())return null;int comma=data.indexOf(',');String b64=comma>=0?data.substring(comma+1):data;byte[] bytes=Base64.decode(b64,Base64.DEFAULT);return BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Exception e){return null;}}
    private static Bitmap crop(Bitmap src,int h){h=Math.max(1,Math.min(src.getHeight(),h));Bitmap out=Bitmap.createBitmap(src,0,0,src.getWidth(),h);if(out!=src)src.recycle();return out;}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static String safe(String s){return s==null?"":s.trim();}
}
