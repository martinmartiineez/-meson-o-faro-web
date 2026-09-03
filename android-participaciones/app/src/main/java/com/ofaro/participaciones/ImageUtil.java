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
    private ImageUtil() {}

    static String toDataUri(Context context,String uriText)throws Exception{
        if(uriText==null||uriText.trim().isEmpty())return"";
        Uri uri=Uri.parse(uriText.trim());
        Bitmap bitmap;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new Exception("No se pudo abrir la imagen.");
            bitmap=BitmapFactory.decodeStream(in);
        }
        if(bitmap==null)throw new Exception("Imagen no compatible.");
        Bitmap scaled=bitmap;
        try{
            int max=1200;
            if(bitmap.getWidth()>max||bitmap.getHeight()>max){
                float f=Math.min(max/(float)bitmap.getWidth(),max/(float)bitmap.getHeight());
                scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*f)),Math.max(1,Math.round(bitmap.getHeight()*f)),true);
            }
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            if(!scaled.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception("No se pudo preparar la imagen.");
            if(out.size()>3_500_000)throw new Exception("La imagen es demasiado grande para un ticket. Usa una imagen más pequeña.");
            return "data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
        }finally{
            if(scaled!=bitmap&&!scaled.isRecycled())scaled.recycle();
            if(!bitmap.isRecycled())bitmap.recycle();
        }
    }
}
