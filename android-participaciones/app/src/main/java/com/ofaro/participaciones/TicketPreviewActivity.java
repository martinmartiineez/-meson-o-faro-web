package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

public class TicketPreviewActivity extends Activity {
    private static final int PICK_IMAGE = 4401;
    private AppCore core;
    private LinearLayout receipt;
    private RadioGroup imagePosition;
    private TextView imageStatus;
    private String imageUri = "";
    private String title = "";
    private String subtitle = "";
    private String body = "";
    private String qr = "";
    private String reference = "";
    private String detail = "";
    private String reservationId = "";
    private String participationCode = "";
    private String type = "Ticket";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        core = new AppCore(this);
        Intent in = getIntent();
        title = value(in, "title");
        subtitle = value(in, "subtitle");
        body = value(in, "body");
        qr = value(in, "qr");
        reference = value(in, "reference");
        detail = value(in, "detail");
        reservationId = value(in, "reservationId");
        participationCode = value(in, "participationCode");
        String passedType = value(in, "type");
        if (!passedType.isEmpty()) type = passedType;
        imageUri = core.prefs().getString("ticketImageUri", "");
        setContentView(buildUi());
        refreshPreview();
    }

    private String value(Intent i, String key) {
        String s = i.getStringExtra(key);
        return s == null ? "" : s;
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(238,238,238));
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20),dp(18),dp(20),dp(16));
        header.setBackgroundColor(Color.rgb(17,17,17));
        header.addView(text("MESÓN O FARO",22,Color.WHITE,true));
        header.addView(text("Previsualización antes de imprimir",14,Color.rgb(210,210,210),false));
        root.addView(header);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16),dp(18),dp(16),dp(28));
        scroll.addView(box);
        TextView hint = text("Puedes ver el ticket aunque no tengas conexión con la impresora.",14,Color.DKGRAY,false);
        hint.setPadding(0,0,0,dp(12));
        box.addView(hint);
        receipt = new LinearLayout(this);
        receipt.setOrientation(LinearLayout.VERTICAL);
        receipt.setGravity(Gravity.CENTER_HORIZONTAL);
        receipt.setPadding(dp(18),dp(22),dp(18),dp(24));
        receipt.setBackground(roundRect(Color.WHITE,4,Color.rgb(210,210,210),1));
        LinearLayout.LayoutParams receiptParams = new LinearLayout.LayoutParams(dp(330),ViewGroup.LayoutParams.WRAP_CONTENT);
        receiptParams.gravity = Gravity.CENTER_HORIZONTAL;
        box.addView(receipt,receiptParams);
        TextView imageTitle = text("Imagen opcional",15,Color.rgb(25,25,25),true);
        imageTitle.setPadding(0,dp(18),0,dp(6));
        box.addView(imageTitle);
        imageStatus = text("",13,Color.DKGRAY,false);
        box.addView(imageStatus);
        Button choose = secondaryButton("ELEGIR / CAMBIAR IMAGEN");
        choose.setOnClickListener(v -> chooseImage());
        box.addView(choose,marginTop(dp(8)));
        imagePosition = new RadioGroup(this);
        imagePosition.setOrientation(RadioGroup.VERTICAL);
        RadioButton none = radio("NO IMPRIMIR IMAGEN",0);
        RadioButton top = radio("IMAGEN ARRIBA DEL TICKET",1);
        RadioButton bottom = radio("IMAGEN DEBAJO DEL TICKET",2);
        imagePosition.addView(none); imagePosition.addView(top); imagePosition.addView(bottom);
        none.setChecked(true);
        imagePosition.setOnCheckedChangeListener((g,id) -> refreshPreview());
        box.addView(imagePosition,marginTopWrap(dp(10)));
        Button print = primaryButton("IMPRIMIR");
        print.setOnClickListener(v -> confirmPrint(print));
        box.addView(print,marginTop(dp(16)));
        Button close = secondaryButton("CERRAR");
        close.setOnClickListener(v -> finish());
        box.addView(close,marginTop(dp(10)));
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        return root;
    }

    private RadioButton radio(String label,int position) {
        RadioButton b = new RadioButton(this);
        b.setId(View.generateViewId());
        b.setText(label);
        b.setTextSize(14);
        b.setTextColor(Color.rgb(25,25,25));
        b.setTag(position);
        b.setPadding(dp(2),dp(3),dp(2),dp(3));
        return b;
    }

    private int selectedPosition() {
        int id = imagePosition == null ? -1 : imagePosition.getCheckedRadioButtonId();
        if (id == -1) return 0;
        View v = imagePosition.findViewById(id);
        if (v == null || v.getTag() == null) return 0;
        return (Integer)v.getTag();
    }

    private void refreshPreview() {
        if (receipt == null) return;
        receipt.removeAllViews();
        Bitmap image = null;
        if (!imageUri.trim().isEmpty()) {
            try { image = core.loadBitmap(imageUri); } catch(Exception ignored) {}
        }
        int pos = selectedPosition();
        imageStatus.setText(image == null ? "No hay imagen seleccionada." : "Imagen seleccionada · elige si se imprime arriba, abajo o no se imprime.");
        if (image != null && pos == 1) receipt.addView(imageView(image));
        if (!title.trim().isEmpty()) {
            TextView t = text(title.toUpperCase(),24,Color.BLACK,true); t.setGravity(Gravity.CENTER); receipt.addView(t);
        }
        if (!subtitle.trim().isEmpty()) {
            TextView s = text(subtitle.toUpperCase(),17,Color.BLACK,true); s.setGravity(Gravity.CENTER); s.setPadding(0,dp(5),0,dp(8)); receipt.addView(s);
        }
        if (!title.trim().isEmpty() || !subtitle.trim().isEmpty()) {
            TextView line = text("--------------------------------",14,Color.BLACK,false); line.setTypeface(Typeface.MONOSPACE); line.setGravity(Gravity.CENTER); receipt.addView(line);
        }
        if (!body.trim().isEmpty()) {
            TextView b = text(body,15,Color.BLACK,false); b.setTypeface(Typeface.MONOSPACE); b.setPadding(0,dp(10),0,dp(8)); b.setTextIsSelectable(true); receipt.addView(b,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (!qr.trim().isEmpty()) {
            try {
                ImageView q = new ImageView(this); q.setImageBitmap(qrBitmap(qr,520)); q.setAdjustViewBounds(true);
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(210),dp(210)); p.gravity=Gravity.CENTER_HORIZONTAL; p.topMargin=dp(8); receipt.addView(q,p);
            } catch(Exception e) {
                TextView q = text("[QR: "+qr+"]",12,Color.BLACK,false); q.setGravity(Gravity.CENTER); receipt.addView(q);
            }
        }
        if (image != null && pos == 2) receipt.addView(imageView(image));
        if (receipt.getChildCount()==0) {
            TextView empty = text("Selecciona una imagen para previsualizarla e imprimirla.",15,Color.DKGRAY,false); empty.setGravity(Gravity.CENTER); receipt.addView(empty);
        }
    }

    private ImageView imageView(Bitmap bitmap) {
        ImageView v = new ImageView(this);
        v.setImageBitmap(bitmap);
        v.setAdjustViewBounds(true);
        v.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(180));
        p.bottomMargin=dp(10); p.topMargin=dp(10); v.setLayoutParams(p);
        return v;
    }

    private Bitmap qrBitmap(String value,int size) throws Exception {
        BitMatrix matrix = new MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE,size,size);
        Bitmap bitmap = Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        for(int y=0;y<size;y++) for(int x=0;x<size;x++) bitmap.setPixel(x,y,matrix.get(x,y)?Color.BLACK:Color.WHITE);
        return bitmap;
    }

    private void chooseImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,PICK_IMAGE);
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==PICK_IMAGE && resultCode==RESULT_OK && data!=null && data.getData()!=null) {
            Uri uri=data.getData();
            try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {}
            imageUri=uri.toString();
            core.prefs().edit().putString("ticketImageUri",imageUri).apply();
            RadioButton top = (RadioButton) imagePosition.getChildAt(1);
            imagePosition.check(top.getId());
            refreshPreview();
        }
    }

    private void confirmPrint(Button button) {
        int pos=selectedPosition();
        boolean hasImage=!imageUri.trim().isEmpty();
        String imageText = (!hasImage || pos==0) ? "SIN imagen" : (pos==1 ? "con la imagen ARRIBA" : "con la imagen ABAJO");
        new AlertDialog.Builder(this)
                .setTitle("Confirmar impresión")
                .setMessage("Se imprimirá "+imageText+".\n\n¿Continuar?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("IMPRIMIR",(d,w)->printNow(button,pos))
                .show();
    }

    private void printNow(Button button,int pos) {
        if(core.printerIp().isEmpty()) {
            alert("Impresora no disponible","La previsualización funciona sin impresora. Para imprimir, configura la IP en Ajustes.");
            return;
        }
        button.setEnabled(false);
        new Thread(() -> {
            try {
                core.printTicket(title,subtitle,body,qr,imageUri,pos);
                if(core.configured()) {
                    try { core.post(core.action("historyAdd").put("type",type).put("reference",reference).put("event","Impreso").put("printer","IMP001").put("detail",detail).put("state","OK")); } catch(Exception ignored) {}
                    if(!reservationId.isEmpty()) try { core.post(core.action("reservationMarkPrinted").put("id",reservationId)); } catch(Exception ignored) {}
                    if(!participationCode.isEmpty()) try { core.post(core.action("participationMarkPrinted").put("code",participationCode)); } catch(Exception ignored) {}
                }
                runOnUiThread(() -> { button.setEnabled(true); Toast.makeText(this,"Impresión enviada",Toast.LENGTH_SHORT).show(); });
            } catch(Exception e) {
                runOnUiThread(() -> { button.setEnabled(true); alert("Error de impresión",e.getMessage()==null?String.valueOf(e):e.getMessage()); });
            }
        }).start();
    }

    private TextView text(String value,int sp,int color,boolean bold) { TextView t=new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color); if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return t; }
    private Button primaryButton(String label) { Button b=new Button(this); b.setText(label); b.setTextSize(14); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackground(roundRect(Color.rgb(17,17,17),12,Color.TRANSPARENT,0)); b.setMinHeight(dp(54)); return b; }
    private Button secondaryButton(String label) { Button b=new Button(this); b.setText(label); b.setTextSize(13); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setTextColor(Color.rgb(20,20,20)); b.setAllCaps(false); b.setBackground(roundRect(Color.WHITE,12,Color.rgb(190,190,190),1)); b.setMinHeight(dp(52)); return b; }
    private GradientDrawable roundRect(int fill,int radius,int stroke,int strokeWidth) { GradientDrawable d=new GradientDrawable(); d.setColor(fill); d.setCornerRadius(dp(radius)); if(strokeWidth>0)d.setStroke(dp(strokeWidth),stroke); return d; }
    private LinearLayout.LayoutParams marginTop(int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)); p.topMargin=top; return p; }
    private LinearLayout.LayoutParams marginTopWrap(int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT); p.topMargin=top; return p; }
    private int dp(int v) { return Math.round(v*getResources().getDisplayMetrics().density); }
    private void alert(String title,String message) { if(!isFinishing())new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Aceptar",null).show(); }
}
