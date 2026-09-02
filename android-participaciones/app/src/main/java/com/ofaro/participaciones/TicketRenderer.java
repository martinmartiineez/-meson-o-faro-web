package com.ofaro.participaciones;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
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
    private static final int MAX_HEIGHT = 7000;

    static Bitmap render(JSONObject job) throws Exception {
        int paper = job.optInt("paperWidth",80);
        int width = paper <= 58 ? 384 : 576;
        int margin = paper <= 58 ? 20 : 30;
        int contentW = width - margin*2;
        String template = safe(job.optString("templateId",job.optString("template","Minimal Premium")));
        String family = family(template);
        String typography = safe(job.optString("typography","O Faro"));
        String title = safe(job.optString("title",""));
        String subtitle = safe(job.optString("subtitle",""));
        String body = safe(job.optString("text",""));
        String qr = safe(job.optString("qr",""));
        String qrSize = safe(job.optString("qrSize","L"));
        String separator = safe(job.optString("separator","line"));
        String imagePos = safe(job.optString("imagePosition","none"));
        int imagePct = clamp(job.optInt("imageWidthPercent",75),25,100);
        Bitmap image = decodeImage(job.optString("imageData",""));

        Bitmap work = Bitmap.createBitmap(width,MAX_HEIGHT,Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(work); c.drawColor(Color.WHITE);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(Color.BLACK); p.setStyle(Paint.Style.FILL);
        p.setTypeface(typeface(typography,false));
        float y = 22;

        if ("image".equals(family)) {
            if (image != null) y = drawImage(c,image,y,width,margin,imagePct) + 18;
            else y = drawCentered(c,p,"SIN IMAGEN",y,contentW,margin,paper<=58?26:34,true)+20;
            return crop(work,(int)Math.max(100,y));
        }

        y = topDecoration(c,p,family,template,y,width,margin,paper);
        if (image != null && "top".equalsIgnoreCase(imagePos)) y = drawImage(c,image,y,width,margin,imagePct)+18;

        if ("promo".equals(family) || "wheel".equals(family) || "scratch".equals(family) || "impact".equals(family)) {
            y = drawBandHeader(c,p,title,subtitle,y,width,margin,paper,typography) + 14;
        } else if ("prize".equals(family) || "gift".equals(family) || "coupon".equals(family)) {
            if(!title.isEmpty()) y=drawCentered(c,p,title.toUpperCase(),y,contentW,margin,titleSize(paper,family),true)+4;
            if(!subtitle.isEmpty()) y=drawBox(c,p,subtitle.toUpperCase(),y,width,margin,subtitleSize(paper,family),typography)+12;
        } else {
            if(!title.isEmpty()) y=drawCentered(c,p,title.toUpperCase(),y,contentW,margin,titleSize(paper,family),true)+5;
            if(!subtitle.isEmpty()) y=drawCentered(c,p,subtitle.toUpperCase(),y,contentW,margin,subtitleSize(paper,family),"elegant".equals(family)||"editorial".equals(family))+5;
        }

        if((!title.isEmpty()||!subtitle.isEmpty()) && !"promo".equals(family) && !"wheel".equals(family) && !"scratch".equals(family) && !"impact".equals(family))
            y=drawSeparator(c,p,y,width,margin,separator,family)+12;

        if (isQrFamily(family,template)) {
            if(!body.isEmpty()) y=drawBody(c,p,body,y,contentW,margin,bodySize(paper),typography,centerBody(family))+8;
            if(!qr.isEmpty()) y=drawQr(c,qr,y,width,margin,qrPx("XL",paper))+10;
        } else if ("event".equals(family)) {
            if(!body.isEmpty()) y=drawBody(c,p,body,y,contentW,margin,bodySize(paper),typography,false)+10;
            y=perforation(c,p,y,width,margin)+12;
            if(!qr.isEmpty()) y=drawQr(c,qr,y,width,margin,qrPx("XL",paper))+10;
        } else {
            if(!body.isEmpty()) y=drawBody(c,p,body,y,contentW,margin,bodySize(paper),typography,false)+12;
            if(!qr.isEmpty()) y=drawQr(c,qr,y,width,margin,qrPx(qrSize,paper))+10;
        }

        if (image != null && "bottom".equalsIgnoreCase(imagePos)) y=drawImage(c,image,y,width,margin,imagePct)+18;
        y=bottomDecoration(c,p,family,template,y,width,margin,paper);
        y=drawFooter(c,p,y,width,margin,paper,family)+22;
        if(image!=null&&!image.isRecycled()) image.recycle();
        return crop(work,(int)Math.min(MAX_HEIGHT,Math.max(120,y)));
    }

    private static String family(String t){
        String s=safe(t).toLowerCase();
        if(s.contains("solo imagen"))return "image";
        if(s.contains("ruleta"))return "wheel";
        if(s.contains("rasca"))return "scratch";
        if(s.contains("entrada")||s.contains("evento"))return "event";
        if(s.contains("premio canjeable"))return "prize";
        if(s.contains("vale regalo"))return "gift";
        if(s.contains("cupón")||s.contains("cupon")||s.contains("próxima")||s.contains("proxima"))return "coupon";
        if(s.startsWith("qr ")||s.contains("qr carta")||s.contains("qr menú")||s.contains("qr menu")||s.contains("wifi")||s.contains("reseñas")||s.contains("instagram"))return "qr";
        if(s.contains("oferta flash"))return "impact";
        if(s.contains("promoción")||s.contains("promocion")||s.contains("promo del"))return "promo";
        if(s.contains("retro"))return "retro";
        if(s.contains("editorial")||s.contains("completa")||s.contains("novedad"))return "editorial";
        if(s.contains("elegante"))return "elegant";
        if(s.contains("cliente"))return "card";
        if(s.contains("clásica")||s.contains("clasica")||s.contains("texto libre"))return "classic";
        return "minimal";
    }
    private static boolean isQrFamily(String f,String t){return "qr".equals(f)||safe(t).toLowerCase().contains("qr carta")||safe(t).toLowerCase().contains("qr menú");}
    private static boolean centerBody(String f){return "qr".equals(f);}

    private static float topDecoration(Canvas c,Paint p,String f,String t,float y,int w,int m,int paper){
        if("event".equals(f)||"card".equals(f)){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);c.drawRoundRect(new RectF(10,10,w-10,MAX_HEIGHT-10),20,20,p);p.setStyle(Paint.Style.FILL);return 34;}
        if("retro".equals(f)){p.setStrokeWidth(3);c.drawLine(m,16,w-m,16,p);c.drawLine(m,22,w-m,22,p);return 36;}
        if("coupon".equals(f)){return perforation(c,p,12,w,m)+10;}
        if("gift".equals(f)){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(5);c.drawRoundRect(new RectF(m,14,w-m,86),14,14,p);p.setStyle(Paint.Style.FILL);drawCentered(c,p,"VALE · O FARO",24,w-m*2,m,paper<=58?18:22,true);return 104;}
        if("qr".equals(f)){drawCentered(c,p,"ESCANEA · O FARO",14,w-m*2,m,paper<=58?17:21,true);return 50;}
        return y;
    }
    private static float bottomDecoration(Canvas c,Paint p,String f,String t,float y,int w,int m,int paper){
        if("coupon".equals(f)||"event".equals(f))return perforation(c,p,y,w,m)+8;
        if("retro".equals(f)){p.setStrokeWidth(3);c.drawLine(m,y,w-m,y,p);c.drawLine(m,y+6,w-m,y+6,p);return y+14;}
        if("gift".equals(f)){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRoundRect(new RectF(m,y,w-m,y+18),9,9,p);p.setStyle(Paint.Style.FILL);return y+26;}
        return y;
    }
    private static float drawBandHeader(Canvas c,Paint p,String title,String subtitle,float y,int w,int m,int paper,String typography){
        int h=paper<=58?104:126;p.setColor(Color.BLACK);c.drawRoundRect(new RectF(m,y,w-m,y+h),16,16,p);p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(typeface(typography,true));p.setTextSize(paper<=58?30:38);float yy=y+42;if(!title.isEmpty()){for(String line:wrap(p,title.toUpperCase(),w-m*4)){c.drawText(line,w/2f,yy,p);yy+=paper<=58?33:41;}}if(!subtitle.isEmpty()){p.setTextSize(paper<=58?17:21);c.drawText(subtitle.toUpperCase(),w/2f,y+h-18,p);}p.setTextAlign(Paint.Align.LEFT);p.setColor(Color.BLACK);return y+h;
    }
    private static float drawFooter(Canvas c,Paint p,float y,int w,int m,int paper,String f){p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));p.setTextSize(paper<=58?12:15);p.setColor(Color.DKGRAY);c.drawText("MESÓN O FARO",w/2f,y+14,p);p.setColor(Color.BLACK);p.setTextAlign(Paint.Align.LEFT);return y+20;}
    private static float perforation(Canvas c,Paint p,float y,int w,int m){p.setStrokeWidth(2);p.setPathEffect(new DashPathEffect(new float[]{8,8},0));c.drawLine(m,y+5,w-m,y+5,p);p.setPathEffect(null);c.drawCircle(m,y+5,6,p);c.drawCircle(w-m,y+5,6,p);return y+10;}
    private static float drawSeparator(Canvas c,Paint p,float y,int w,int m,String kind,String f){if("none".equalsIgnoreCase(kind))return y;p.setStrokeWidth("double".equalsIgnoreCase(kind)?3:2);if("dots".equalsIgnoreCase(kind)||"retro".equals(f)){for(int x=m;x<w-m;x+=12)c.drawCircle(x,y+5,2,p);return y+10;}if("dashes".equalsIgnoreCase(kind)||"coupon".equals(f)){p.setPathEffect(new DashPathEffect(new float[]{12,8},0));c.drawLine(m,y+5,w-m,y+5,p);p.setPathEffect(null);return y+10;}c.drawLine(m,y+4,w-m,y+4,p);if("double".equalsIgnoreCase(kind))c.drawLine(m,y+10,w-m,y+10,p);return y+12;}
    private static float drawCentered(Canvas c,Paint p,String text,float y,int maxW,int x,int size,boolean bold){p.setTextSize(size);p.setTypeface(Typeface.create(p.getTypeface(),bold?Typeface.BOLD:Typeface.NORMAL));p.setTextAlign(Paint.Align.CENTER);float line=size*1.24f;for(String s:wrap(p,text,maxW)){y+=size;c.drawText(s,x+maxW/2f,y,p);y+=line-size;}p.setTextAlign(Paint.Align.LEFT);return y;}
    private static float drawBody(Canvas c,Paint p,String text,float y,int maxW,int x,int size,String typography,boolean centered){p.setTextSize(size);p.setTypeface(typeface(typography,false));p.setTextAlign(centered?Paint.Align.CENTER:Paint.Align.LEFT);float line=size*1.35f;for(String para:text.split("\\n",-1)){if(para.trim().isEmpty()){y+=line*.6f;continue;}for(String s:wrap(p,para,maxW)){y+=size;c.drawText(s,centered?x+maxW/2f:x,y,p);y+=line-size;}}p.setTextAlign(Paint.Align.LEFT);return y;}
    private static float drawBox(Canvas c,Paint p,String text,float y,int w,int m,int size,String typography){p.setTypeface(typeface(typography,true));p.setTextSize(size);List<String> lines=wrap(p,text,w-m*4);float h=lines.size()*size*1.32f+28;p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);c.drawRoundRect(new RectF(m,y,w-m,y+h),15,15,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);float yy=y+17;for(String s:lines){yy+=size;c.drawText(s,w/2f,yy,p);yy+=size*.32f;}p.setTextAlign(Paint.Align.LEFT);return y+h;}
    private static float drawImage(Canvas c,Bitmap img,float y,int w,int m,int pct){int avail=w-m*2,target=Math.max(48,avail*pct/100);float scale=Math.min(target/(float)Math.max(1,img.getWidth()),(MAX_HEIGHT-y-60)/Math.max(1f,img.getHeight()));int iw=Math.max(1,Math.round(img.getWidth()*scale)),ih=Math.max(1,Math.round(img.getHeight()*scale));float left=(w-iw)/2f;c.drawBitmap(img,null,new RectF(left,y,left+iw,y+ih),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));return y+ih;}
    private static float drawQr(Canvas c,String data,float y,int w,int m,int size)throws Exception{size=Math.min(size,w-m*2);BitMatrix matrix=new MultiFormatWriter().encode(data,BarcodeFormat.QR_CODE,size,size);Bitmap qr=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);for(int yy=0;yy<size;yy++)for(int xx=0;xx<size;xx++)qr.setPixel(xx,yy,matrix.get(xx,yy)?Color.BLACK:Color.WHITE);c.drawBitmap(qr,(w-size)/2f,y,null);qr.recycle();return y+size;}
    private static List<String> wrap(Paint p,String text,int maxW){List<String> out=new ArrayList<>();String clean=text==null?"":text.trim();if(clean.isEmpty()){out.add("");return out;}String[] words=clean.split("\\s+");String line="";for(String word:words){String test=line.isEmpty()?word:line+" "+word;if(p.measureText(test)<=maxW)line=test;else{if(!line.isEmpty())out.add(line);if(p.measureText(word)<=maxW)line=word;else{String part="";for(int i=0;i<word.length();i++){String t=part+word.charAt(i);if(p.measureText(t)>maxW&&!part.isEmpty()){out.add(part);part="";}part+=word.charAt(i);}line=part;}}}if(!line.isEmpty())out.add(line);return out;}
    private static Typeface typeface(String preset,boolean bold){String s=safe(preset).toLowerCase(),fam="sans-serif";int style=bold?Typeface.BOLD:Typeface.NORMAL;if(s.contains("editorial")||s.contains("elegante"))fam="serif";else if(s.contains("retro")||s.contains("clásico")||s.contains("clasico"))fam="monospace";else if(s.contains("minimal"))fam="sans-serif-light";else if(s.contains("o faro"))fam="sans-serif-condensed";else if(s.contains("promocional")||s.contains("impacto")){fam="sans-serif-black";style=Typeface.BOLD;}return Typeface.create(fam,style);}
    private static int titleSize(int paper,String f){int b=paper<=58?31:40;if("event".equals(f)||"prize".equals(f))return b+4;if("minimal".equals(f))return b-3;return b;}
    private static int subtitleSize(int paper,String f){int b=paper<=58?21:27;if("prize".equals(f)||"gift".equals(f))return b+4;return b;}
    private static int bodySize(int paper){return paper<=58?19:24;}
    private static int qrPx(String s,int paper){int max=paper<=58?290:420;String q=s.toUpperCase();if("S".equals(q))return Math.min(max,paper<=58?145:180);if("M".equals(q))return Math.min(max,paper<=58?190:230);if("XL".equals(q))return Math.min(max,paper<=58?285:400);return Math.min(max,paper<=58?235:300);}
    private static Bitmap decodeImage(String data){try{if(data==null||data.isEmpty())return null;int comma=data.indexOf(',');String b64=comma>=0?data.substring(comma+1):data;byte[] bytes=Base64.decode(b64,Base64.DEFAULT);return BitmapFactory.decodeByteArray(bytes,0,bytes.length);}catch(Exception e){return null;}}
    private static Bitmap crop(Bitmap src,int h){h=Math.max(1,Math.min(src.getHeight(),h));Bitmap out=Bitmap.createBitmap(src,0,0,src.getWidth(),h);if(out!=src)src.recycle();return out;}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static String safe(String s){return s==null?"":s.trim();}
}
