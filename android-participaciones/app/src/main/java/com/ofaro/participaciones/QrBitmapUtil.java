package com.ofaro.participaciones;

import android.graphics.Bitmap;
import android.graphics.Color;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

final class QrBitmapUtil {
    private QrBitmapUtil(){}

    static Bitmap create(String value,int size)throws Exception{
        String data=value==null?"":value.trim();if(data.isEmpty())throw new Exception("El QR no tiene contenido.");
        int px=Math.max(180,Math.min(1200,size));
        Map<EncodeHintType,Object> hints=new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN,1);hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);hints.put(EncodeHintType.CHARACTER_SET,"UTF-8");
        BitMatrix m=new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE,px,px,hints);
        Bitmap b=Bitmap.createBitmap(px,px,Bitmap.Config.ARGB_8888);
        for(int y=0;y<px;y++)for(int x=0;x<px;x++)b.setPixel(x,y,m.get(x,y)? Color.BLACK:Color.WHITE);
        return b;
    }
}
