package com.ofaro.participaciones;

import android.graphics.Bitmap;
import android.graphics.Color;

import org.json.JSONObject;

import java.io.OutputStream;

/**
 * Única ruta de impresión de producción.
 * Lo que se previsualiza con TicketRenderer es exactamente lo que se rasteriza.
 */
final class RemotePrinter {
    private static final int MAX_IMAGE_DOTS = 576;
    private RemotePrinter() {}

    static void print(AppCore core,JSONObject job)throws Exception{
        String ip=core.printerIp();
        int port=core.printerPort();
        if(ip==null||ip.trim().isEmpty())throw new Exception("Configura la IP de la impresora antes de imprimir.");
        int copies=clamp(job.optInt("copies",1),1,5);
        int darkness=clamp(job.optInt("darkness",core.printerDarkness()),120,230);
        int feed=clamp(job.optInt("feedLines",core.printerFeed()),0,8);
        String cut=job.optString("cutMode",core.printerCut());

        Bitmap rendered=TicketRenderer.render(job);
        if(rendered==null)throw new Exception("No se pudo renderizar el ticket.");
        try{
            final Bitmap ticket=rendered;
            PrinterConnectionManager.get().execute(ip,port,out->{
                for(int copy=0;copy<copies;copy++){
                    init(out);
                    align(out,1);
                    bitmap(out,ticket,darkness);
                    feed(out,feed);
                    cut(out,cut);
                    if(copy+1<copies)feed(out,1);
                }
            });
        }finally{
            if(!rendered.isRecycled())rendered.recycle();
        }
    }

    private static void bitmap(OutputStream out,Bitmap original,int threshold)throws Exception{
        int sw=Math.max(1,original.getWidth()),sh=Math.max(1,original.getHeight());
        int tw=Math.min(MAX_IMAGE_DOTS,sw);tw-=tw%8;if(tw<8)tw=8;
        int th=Math.max(1,Math.round(sh*(tw/(float)sw)));
        Bitmap b=sw==tw?original:Bitmap.createScaledBitmap(original,tw,th,true);
        try{
            int widthBytes=(tw+7)/8;
            byte[] data=new byte[widthBytes*th];
            for(int y=0;y<th;y++)for(int x=0;x<tw;x++){
                int px=b.getPixel(x,y);
                int alpha=Color.alpha(px);
                int gray=(Color.red(px)*30+Color.green(px)*59+Color.blue(px)*11)/100;
                // Dither mínimo para fotos, sin ensuciar QR/texto.
                int local=threshold+((((x+y)&1)==0)?-5:5);
                if(alpha>70&&gray<local)data[y*widthBytes+(x/8)]|=(byte)(0x80>>(x%8));
            }
            out.write(new byte[]{0x1D,0x76,0x30,0x00,(byte)(widthBytes&255),(byte)((widthBytes>>8)&255),(byte)(th&255),(byte)((th>>8)&255)});
            out.write(data);
        }finally{
            if(b!=original&&!b.isRecycled())b.recycle();
        }
    }

    private static void init(OutputStream out)throws Exception{out.write(new byte[]{0x1B,0x40});}
    private static void align(OutputStream out,int mode)throws Exception{out.write(new byte[]{0x1B,0x61,(byte)mode});}
    private static void feed(OutputStream out,int lines)throws Exception{
        for(int i=0;i<lines;i++)out.write('\n');
    }
    private static void cut(OutputStream out,String mode)throws Exception{
        String m=mode==null?"full":mode.trim().toLowerCase();
        if("none".equals(m))return;
        if("partial".equals(m))out.write(new byte[]{0x1D,0x56,0x01});
        else out.write(new byte[]{0x1D,0x56,0x00});
    }
    private static int clamp(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
