package com.ofaro.participaciones;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;

final class RemotePrinter {
    private static final int MAX_IMAGE_DOTS = 576;

    static void print(AppCore core, JSONObject job) throws Exception {
        String ip = core.printerIp();
        int port = core.printerPort();
        if (ip == null || ip.trim().isEmpty()) throw new Exception("IP de impresora no configurada");
        int copies = Math.max(1,Math.min(5,job.optInt("copies",1)));
        for (int c=0;c<copies;c++) printOne(ip,port,job);
    }

    private static void printOne(String ip, int port, JSONObject job) throws Exception {
        Bitmap rendered = null;
        try { rendered = TicketRenderer.render(job); } catch (Exception ignored) {}
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip.trim(),port),4500);
            socket.setSoTimeout(8000);
            try (OutputStream out = socket.getOutputStream()) {
                init(out);
                if (rendered != null) {
                    align(out,1);
                    bitmap(out,rendered);
                    text(out,"\n\n");
                } else {
                    legacy(out,job);
                }
                cut(out);
                out.flush();
            }
        } finally {
            if (rendered != null && !rendered.isRecycled()) rendered.recycle();
        }
    }

    private static void legacy(OutputStream out, JSONObject job) throws Exception {
        Bitmap image = decodeImage(job.optString("imageData",""));
        String pos = job.optString("imagePosition","none");
        if (image != null && "top".equals(pos)) { align(out,1); bitmap(out,image); text(out,"\n"); }
        String title = safe(job.optString("title",""));
        String subtitle = safe(job.optString("subtitle",""));
        String body = safe(job.optString("text",""));
        String qr = safe(job.optString("qr",""));
        if (!title.isEmpty()) { align(out,1); bold(out,true); size2(out,true); text(out,title.toUpperCase()+"\n"); size2(out,false); bold(out,false); }
        if (!subtitle.isEmpty()) { align(out,1); bold(out,true); text(out,subtitle.toUpperCase()+"\n"); bold(out,false); }
        if (!title.isEmpty() || !subtitle.isEmpty()) { align(out,1); text(out,"------------------------------\n"); }
        if (!body.isEmpty()) { align(out,0); text(out,"\n"+body+"\n"); }
        if (!qr.isEmpty()) { align(out,1); text(out,"\n"); qr(out,qr,7); text(out,"\n"); }
        if (image != null && "bottom".equals(pos)) { align(out,1); text(out,"\n"); bitmap(out,image); text(out,"\n"); }
        if (image != null && !image.isRecycled()) image.recycle();
        text(out,"\n\n");
    }

    private static Bitmap decodeImage(String data) {
        try {
            if (data == null || data.isEmpty()) return null;
            int comma = data.indexOf(',');
            String b64 = comma >= 0 ? data.substring(comma+1) : data;
            byte[] bytes = Base64.decode(b64,Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        } catch (Exception e) { return null; }
    }

    private static void bitmap(OutputStream out, Bitmap original) throws Exception {
        int sw=Math.max(1,original.getWidth()),sh=Math.max(1,original.getHeight());
        int tw=Math.min(MAX_IMAGE_DOTS,sw);tw-=tw%8;if(tw<8)tw=8;
        int th=Math.max(1,Math.round(sh*(tw/(float)sw)));
        Bitmap b=sw==tw?original:Bitmap.createScaledBitmap(original,tw,th,true);
        int wb=(tw+7)/8;byte[] data=new byte[wb*th];
        for(int y=0;y<th;y++)for(int x=0;x<tw;x++){
            int px=b.getPixel(x,y),a=Color.alpha(px),g=(Color.red(px)*30+Color.green(px)*59+Color.blue(px)*11)/100;
            if(a>80&&g<180)data[y*wb+x/8]|=(byte)(0x80>>(x%8));
        }
        out.write(new byte[]{0x1D,0x76,0x30,0,(byte)(wb&255),(byte)((wb>>8)&255),(byte)(th&255),(byte)((th>>8)&255)});out.write(data);
        if(b!=original)b.recycle();
    }
    private static void init(OutputStream o)throws Exception{o.write(new byte[]{0x1B,0x40});}
    private static void align(OutputStream o,int m)throws Exception{o.write(new byte[]{0x1B,0x61,(byte)m});}
    private static void bold(OutputStream o,boolean x)throws Exception{o.write(new byte[]{0x1B,0x45,(byte)(x?1:0)});}
    private static void size2(OutputStream o,boolean x)throws Exception{o.write(new byte[]{0x1D,0x21,(byte)(x?0x11:0)});}
    private static void text(OutputStream o,String s)throws Exception{o.write(s.getBytes(Charset.forName("CP850")));}
    private static void cut(OutputStream o)throws Exception{o.write(new byte[]{0x1D,0x56,0});}
    private static void qr(OutputStream o,String data,int module)throws Exception{
        byte[] b=data.getBytes(Charset.forName("UTF-8"));
        o.write(new byte[]{0x1D,0x28,0x6B,0x04,0,0x31,0x41,0x32,0});
        o.write(new byte[]{0x1D,0x28,0x6B,0x03,0,0x31,0x43,(byte)Math.max(2,Math.min(12,module))});
        o.write(new byte[]{0x1D,0x28,0x6B,0x03,0,0x31,0x45,0x31});
        int len=b.length+3;ByteArrayOutputStream s=new ByteArrayOutputStream();s.write(new byte[]{0x1D,0x28,0x6B,(byte)(len&255),(byte)((len>>8)&255),0x31,0x50,0x30});s.write(b);o.write(s.toByteArray());
        o.write(new byte[]{0x1D,0x28,0x6B,0x03,0,0x31,0x51,0x30});
    }
    private static String safe(String s){return s==null?"":s.trim();}
}
