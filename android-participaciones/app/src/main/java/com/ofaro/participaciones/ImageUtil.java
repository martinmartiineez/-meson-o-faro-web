package com.ofaro.participaciones;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** Conversión acotada de imágenes elegidas por el usuario para tickets. */
final class ImageUtil {
    private static final int MAX_SIDE = 1200;
    private static final int MAX_ENCODED_BYTES = 3_500_000;

    private ImageUtil() {}

    static String toDataUri(Context context,String uriText)throws Exception{
        if(uriText==null||uriText.trim().isEmpty())return"";
        Uri uri=Uri.parse(uriText.trim());

        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new Exception("No se pudo abrir la imagen.");
            BitmapFactory.decodeStream(in,null,bounds);
        }
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new Exception("Imagen no compatible.");

        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inSampleSize=sampleSize(bounds.outWidth,bounds.outHeight,MAX_SIDE);
        options.inPreferredConfig=Bitmap.Config.ARGB_8888;

        Bitmap bitmap;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new Exception("No se pudo abrir la imagen.");
            bitmap=BitmapFactory.decodeStream(in,null,options);
        }catch(OutOfMemoryError oom){
            throw new Exception("La imagen es demasiado grande para procesarla. Usa una versión más pequeña.");
        }
        if(bitmap==null)throw new Exception("Imagen no compatible.");

        Bitmap scaled=bitmap;
        try{
            if(bitmap.getWidth()>MAX_SIDE||bitmap.getHeight()>MAX_SIDE){
                float f=Math.min(MAX_SIDE/(float)bitmap.getWidth(),MAX_SIDE/(float)bitmap.getHeight());
                scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*f)),Math.max(1,Math.round(bitmap.getHeight()*f)),true);
            }
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(!scaled.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception("No se pudo preparar la imagen.");
            if(out.size()>MAX_ENCODED_BYTES)throw new Exception("La imagen es demasiado grande para un ticket. Usa una imagen más pequeña.");
            return "data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
        }finally{
            if(scaled!=bitmap&&!scaled.isRecycled())scaled.recycle();
            if(!bitmap.isRecycled())bitmap.recycle();
        }
    }

    private static int sampleSize(int width,int height,int maxSide){
        int sample=1;
        while(width/sample>maxSide*2||height/sample>maxSide*2){
            if(sample>=128)break;
            sample*=2;
        }
        return Math.max(1,sample);
    }
}
