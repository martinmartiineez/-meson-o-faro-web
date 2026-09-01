package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import org.json.JSONObject;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppCore core;
    private FrameLayout content;
    private Button navGenerate;
    private Button navScan;
    private Button navSettings;
    private TextView generateStatus;
    private EditText quantityInput;
    private EditText manualCodeInput;
    private TextView validationTitle;
    private TextView validationDetail;
    private Button redeemButton;
    private String validatedCode = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        core = new AppCore(this);
        setContentView(buildRoot());
        showGenerate();
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(246,246,246));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20),dp(18),dp(20),dp(16));
        header.setBackgroundColor(Color.rgb(17,17,17));
        header.addView(text("MESÓN O FARO",23,Color.WHITE,true));
        header.addView(text("Participaciones y premios",14,Color.rgb(210,210,210),false));
        root.addView(header);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(10),dp(10),dp(10),dp(10));
        nav.setBackgroundColor(Color.WHITE);
        navGenerate = navButton("GENERAR");
        navScan = navButton("ESCANEAR");
        navSettings = navButton("AJUSTES");
        nav.addView(navGenerate,weightParams());
        nav.addView(navScan,weightParams());
        nav.addView(navSettings,weightParams());
        root.addView(nav);

        content = new FrameLayout(this);
        root.addView(content,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        navGenerate.setOnClickListener(v -> showGenerate());
        navScan.setOnClickListener(v -> showScan());
        navSettings.setOnClickListener(v -> showSettings());
        return root;
    }

    private void showGenerate() {
        selectNav(navGenerate);
        ScrollView scroll = scroll();
        LinearLayout box = verticalBox();
        scroll.addView(box);
        box.addView(sectionTitle("Generar participación"));
        box.addView(paragraph("Cada pulsación solicita un código único al servidor, lo registra en Google Sheets y lo imprime como ticket QR."));

        LinearLayout quantityRow = new LinearLayout(this);
        quantityRow.setOrientation(LinearLayout.HORIZONTAL);
        quantityRow.setGravity(Gravity.CENTER_VERTICAL);
        quantityRow.addView(text("Tickets",16,Color.DKGRAY,true),new LinearLayout.LayoutParams(0,dp(52),1));
        quantityInput = input("1");
        quantityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantityInput.setGravity(Gravity.CENTER);
        quantityInput.setSelectAllOnFocus(true);
        quantityRow.addView(quantityInput,new LinearLayout.LayoutParams(dp(100),dp(52)));
        box.addView(card(quantityRow));

        Button generate = primaryButton("GENERAR E IMPRIMIR");
        generate.setOnClickListener(v -> generateBatch(generate,true));
        box.addView(generate,marginTopParams(dp(14)));
        Button generateOnly = secondaryButton("GENERAR SIN IMPRIMIR");
        generateOnly.setOnClickListener(v -> generateBatch(generateOnly,false));
        box.addView(generateOnly,marginTopParams(dp(10)));

        generateStatus = text("Listo.",15,Color.DKGRAY,false);
        generateStatus.setPadding(dp(4),dp(18),dp(4),dp(6));
        box.addView(generateStatus);
        content.removeAllViews();
        content.addView(scroll);
    }

    private void showScan() {
        selectNav(navScan);
        ScrollView scroll = scroll();
        LinearLayout box = verticalBox();
        scroll.addView(box);
        box.addView(sectionTitle("Validar y canjear"));
        box.addView(paragraph("Escanea el QR del ticket. El lector está incluido dentro de la app y después consulta Google Sheets para validar el código."));

        Button scan = primaryButton("ESCANEAR QR");
        scan.setOnClickListener(v -> startScanner());
        box.addView(scan);

        TextView alternative = text("O introduce el código manualmente",14,Color.DKGRAY,false);
        alternative.setPadding(0,dp(18),0,dp(8));
        box.addView(alternative);
        manualCodeInput = input("");
        manualCodeInput.setHint("OF-XXXXX-XXXXX");
        manualCodeInput.setSingleLine(true);
        box.addView(manualCodeInput,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
        Button validate = secondaryButton("VALIDAR CÓDIGO");
        validate.setOnClickListener(v -> validateCode(manualCodeInput.getText().toString()));
        box.addView(validate,marginTopParams(dp(10)));

        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(18),dp(18),dp(18),dp(18));
        result.setBackground(roundRect(Color.WHITE,dp(14),Color.rgb(225,225,225),1));
        validationTitle = text("Sin código validado",20,Color.rgb(20,20,20),true);
        validationDetail = text("Escanea un ticket para ver el premio y su estado.",15,Color.DKGRAY,false);
        validationDetail.setPadding(0,dp(8),0,dp(14));
        redeemButton = primaryButton("CANJEAR PREMIO");
        redeemButton.setVisibility(View.GONE);
        redeemButton.setOnClickListener(v -> confirmRedeem());
        result.addView(validationTitle);
        result.addView(validationDetail);
        result.addView(redeemButton);
        box.addView(result,marginTopParams(dp(18)));

        content.removeAllViews();
        content.addView(scroll);
    }

    private void showSettings() {
        selectNav(navSettings);
        ScrollView scroll = scroll();
        LinearLayout box = verticalBox();
        scroll.addView(box);
        box.addView(sectionTitle("Ajustes"));
        box.addView(paragraph("Estos datos se guardan solo en este dispositivo."));

        SharedPreferences prefs = core.prefs();
        EditText api = field(box,"Endpoint Apps Script",prefs.getString("api",AppCore.DEFAULT_API),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_URI);
        EditText key = field(box,"Clave API",prefs.getString("key",""),InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText printerIp = field(box,"IP impresora ESC/POS",prefs.getString("printerIp",""),InputType.TYPE_CLASS_PHONE);
        EditText printerPort = field(box,"Puerto",String.valueOf(prefs.getInt("printerPort",9100)),InputType.TYPE_CLASS_NUMBER);
        EditText terminal = field(box,"Nombre del terminal",prefs.getString("terminal","Caja O Faro"),InputType.TYPE_CLASS_TEXT);

        Button save = primaryButton("GUARDAR AJUSTES");
        save.setOnClickListener(v -> {
            int port = safeInt(printerPort.getText().toString(),9100,1,65535);
            prefs.edit()
                    .putString("api",api.getText().toString().trim())
                    .putString("key",key.getText().toString().trim())
                    .putString("printerIp",printerIp.getText().toString().trim())
                    .putInt("printerPort",port)
                    .putString("terminal",terminal.getText().toString().trim())
                    .apply();
            toast("Ajustes guardados");
        });
        box.addView(save,marginTopParams(dp(16)));

        Button testApi = secondaryButton("PROBAR CONEXIÓN CON GOOGLE");
        testApi.setOnClickListener(v -> testApi(testApi,api.getText().toString().trim(),key.getText().toString().trim(),terminal.getText().toString().trim()));
        box.addView(testApi,marginTopParams(dp(10)));

        Button testPrinter = secondaryButton("IMPRIMIR TICKET DE PRUEBA");
        testPrinter.setOnClickListener(v -> testPrinter(testPrinter,printerIp.getText().toString().trim(),safeInt(printerPort.getText().toString(),9100,1,65535)));
        box.addView(testPrinter,marginTopParams(dp(10)));
        content.removeAllViews();
        content.addView(scroll);
    }

    private void generateBatch(Button button, boolean print) {
        int count = safeInt(quantityInput.getText().toString(),1,1,20);
        if (!requireApi()) return;
        if (print && core.printerIp().isEmpty()) {
            alert("Falta la impresora","Configura la IP de la impresora ESC/POS en Ajustes antes de imprimir.");
            return;
        }
        button.setEnabled(false);
        setGenerateStatus(print ? "Generando e imprimiendo…" : "Generando códigos…");
        io.execute(() -> {
            int done = 0;
            String last = "";
            try {
                for (int i=0;i<count;i++) {
                    JSONObject created = core.post(core.action("participationCreate").put("origin",print ? "APK" : "APK-sin-impresion"));
                    core.ensureOk(created);
                    String code = created.optString("code","");
                    String qr = created.optString("qrPayload","OFARO:"+code);
                    String createdAt = created.optString("createdAt","");
                    if (print) {
                        core.printQrTicket("PARTICIPACION",code+"\n"+createdAt+"\nConserva este ticket.",qr);
                        try { core.ensureOk(core.post(core.action("participationMarkPrinted").put("code",code))); } catch (Exception ignored) {}
                    }
                    done++;
                    last = code;
                    int shown = done;
                    runOnUiThread(() -> setGenerateStatus((print ? "Impresos " : "Generados ")+shown+" de "+count+"…"));
                }
                int finalDone = done;
                String finalLast = last;
                runOnUiThread(() -> {
                    setGenerateStatus("Completado: "+finalDone+(finalDone==1?" ticket":" tickets")+". Último código: "+finalLast);
                    button.setEnabled(true);
                });
            } catch (Exception e) {
                int finalDone = done;
                runOnUiThread(() -> {
                    setGenerateStatus("Se detuvo tras "+finalDone+". Error: "+cleanError(e));
                    button.setEnabled(true);
                });
            }
        });
    }

    private void startScanner() {
        if (!requireApi()) return;
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(java.util.Collections.singletonList("QR_CODE"));
        integrator.setPrompt("Apunta al código QR del ticket");
        integrator.setBeepEnabled(false);
        integrator.setOrientationLocked(true);
        integrator.setBarcodeImageEnabled(false);
        integrator.initiateScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode,resultCode,data);
        if (result != null) {
            String raw = result.getContents();
            if (raw == null) {
                toast("Escaneo cancelado");
                return;
            }
            raw = raw.trim();
            if (raw.isEmpty()) {
                toast("QR vacío");
                return;
            }
            if (manualCodeInput != null) manualCodeInput.setText(raw);
            validateCode(raw);
            return;
        }
        super.onActivityResult(requestCode,resultCode,data);
    }

    private void validateCode(String code) {
        if (!requireApi()) return;
        final String cleaned = code == null ? "" : code.trim();
        if (cleaned.isEmpty()) {
            alert("Código vacío","Introduce o escanea un código.");
            return;
        }
        setValidation("Validando…","Consultando Google Sheets.",false);
        io.execute(() -> {
            try {
                JSONObject response = core.post(core.action("participationValidate").put("code",cleaned));
                core.ensureOk(response);
                String codeOut = response.optString("code",cleaned);
                String state = response.optString("state","");
                String prize = response.optString("prize","Sin premio");
                boolean hasPrize = response.optBoolean("hasPrize",false);
                boolean canRedeem = response.optBoolean("canRedeem",false);
                String redeemedAt = response.optString("redeemedAt","");
                String redeemedBy = response.optString("redeemedBy","");
                validatedCode = codeOut;
                String title;
                String detail;
                if ("Canjeada".equalsIgnoreCase(state)) {
                    title = "YA CANJEADO";
                    detail = codeOut+"\nPremio: "+prize+"\nCanjeado: "+valueOrDash(redeemedAt)+"\nTerminal: "+valueOrDash(redeemedBy);
                } else if (!hasPrize) {
                    title = "SIN PREMIO";
                    detail = codeOut+"\nEstado: "+state+"\nEste código es válido, pero no tiene premio canjeable.";
                } else {
                    title = prize.toUpperCase(Locale.ROOT);
                    detail = codeOut+"\nEstado: "+state+"\nPremio pendiente de canje.";
                }
                runOnUiThread(() -> setValidation(title,detail,canRedeem));
            } catch (Exception e) {
                validatedCode = "";
                runOnUiThread(() -> setValidation("CÓDIGO NO VÁLIDO",cleanError(e),false));
            }
        });
    }

    private void confirmRedeem() {
        if (validatedCode.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Confirmar canje")
                .setMessage("Al confirmar, este código quedará inutilizado para siempre. ¿Entregar el premio ahora?")
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("CANJEAR",(d,w) -> redeemValidated())
                .show();
    }

    private void redeemValidated() {
        final String code = validatedCode;
        redeemButton.setEnabled(false);
        setValidation("Canjeando…","Bloqueando el código en Google Sheets.",false);
        io.execute(() -> {
            try {
                JSONObject response = core.post(core.action("participationRedeem").put("code",code));
                if (!response.optBoolean("ok",false)) {
                    String message = response.optString("error","No se pudo canjear");
                    if (response.optBoolean("alreadyRedeemed",false)) {
                        message += "\nCanjeado: "+valueOrDash(response.optString("redeemedAt",""))+"\nTerminal: "+valueOrDash(response.optString("redeemedBy",""));
                    }
                    throw new Exception(message);
                }
                String prize = response.optString("prize","Premio");
                String at = response.optString("redeemedAt","");
                runOnUiThread(() -> {
                    setValidation("PREMIO CANJEADO",prize+"\n"+code+"\n"+at,false);
                    redeemButton.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setValidation("NO SE PUDO CANJEAR",cleanError(e),false);
                    redeemButton.setEnabled(true);
                });
            }
        });
    }

    private void testApi(Button button,String api,String key,String terminal) {
        if (api.isEmpty() || key.isEmpty()) {
            alert("Faltan datos","Completa el endpoint y la clave API.");
            return;
        }
        button.setEnabled(false);
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject()
                        .put("action","participationPing")
                        .put("key",key)
                        .put("terminal",terminal)
                        .put("appVersion","2.0.1");
                JSONObject result = core.postTo(api,body);
                core.ensureOk(result);
                runOnUiThread(() -> {
                    toast("Conexión correcta");
                    button.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    alert("Error de conexión",cleanError(e));
                    button.setEnabled(true);
                });
            }
        });
    }

    private void testPrinter(Button button,String ip,int port) {
        if (ip.isEmpty()) {
            alert("Falta la IP","Introduce la IP de la impresora.");
            return;
        }
        button.setEnabled(false);
        io.execute(() -> {
            try {
                core.printTest(ip,port);
                runOnUiThread(() -> {
                    toast("Ticket de prueba enviado");
                    button.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    alert("Error de impresora",cleanError(e));
                    button.setEnabled(true);
                });
            }
        });
    }

    private boolean requireApi() {
        if (!core.configured()) {
            alert("Falta configurar la API","Ve a Ajustes e introduce el endpoint de Apps Script y la clave de la app.");
            return false;
        }
        return true;
    }

    private void setGenerateStatus(String value) {
        if (generateStatus != null) generateStatus.setText(value);
    }

    private void setValidation(String title,String detail,boolean canRedeem) {
        if (validationTitle == null || validationDetail == null || redeemButton == null) return;
        validationTitle.setText(title);
        validationDetail.setText(detail);
        redeemButton.setVisibility(canRedeem ? View.VISIBLE : View.GONE);
        redeemButton.setEnabled(true);
    }

    private ScrollView scroll() {
        ScrollView s = new ScrollView(this);
        s.setFillViewport(true);
        return s;
    }

    private LinearLayout verticalBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18),dp(20),dp(18),dp(28));
        return box;
    }

    private TextView sectionTitle(String value) {
        TextView t = text(value,26,Color.rgb(20,20,20),true);
        t.setPadding(0,0,0,dp(8));
        return t;
    }

    private TextView paragraph(String value) {
        TextView t = text(value,15,Color.DKGRAY,false);
        t.setLineSpacing(0,1.16f);
        t.setPadding(0,0,0,dp(18));
        return t;
    }

    private LinearLayout card(View child) {
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(16),dp(8),dp(16),dp(8));
        card.setBackground(roundRect(Color.WHITE,dp(14),Color.rgb(225,225,225),1));
        card.addView(child,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private EditText field(LinearLayout parent,String label,String value,int inputType) {
        TextView l = text(label,14,Color.DKGRAY,true);
        l.setPadding(dp(2),dp(12),dp(2),dp(6));
        parent.addView(l);
        EditText edit = input(value);
        edit.setInputType(inputType);
        edit.setSingleLine(true);
        parent.addView(edit,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54)));
        return edit;
    }

    private EditText input(String value) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextSize(16);
        edit.setTextColor(Color.rgb(20,20,20));
        edit.setHintTextColor(Color.GRAY);
        edit.setPadding(dp(14),0,dp(14),0);
        edit.setBackground(roundRect(Color.WHITE,dp(12),Color.rgb(205,205,205),1));
        return edit;
    }

    private Button navButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setPadding(dp(4),0,dp(4),0);
        return b;
    }

    private void selectNav(Button selected) {
        Button[] all = {navGenerate,navScan,navSettings};
        for (Button button : all) {
            boolean active = button == selected;
            button.setTextColor(active ? Color.WHITE : Color.rgb(35,35,35));
            button.setTypeface(Typeface.DEFAULT,active ? Typeface.BOLD : Typeface.NORMAL);
            button.setBackground(roundRect(active ? Color.rgb(17,17,17) : Color.rgb(240,240,240),dp(10),Color.TRANSPARENT,0));
        }
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundRect(Color.rgb(17,17,17),dp(12),Color.TRANSPARENT,0));
        b.setMinHeight(dp(54));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(Color.rgb(20,20,20));
        b.setBackground(roundRect(Color.WHITE,dp(12),Color.rgb(190,190,190),1));
        b.setMinHeight(dp(52));
        return b;
    }

    private TextView text(String value,int sp,int color,boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return t;
    }

    private GradientDrawable roundRect(int fill,int radius,int stroke,int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth),stroke);
        return d;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,dp(44),1);
        p.setMargins(dp(3),0,dp(3),0);
        return p;
    }

    private LinearLayout.LayoutParams marginTopParams(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(54));
        p.topMargin = top;
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int safeInt(String value,int fallback,int min,int max) {
        try {
            int n = Integer.parseInt(value.trim());
            return Math.max(min,Math.min(max,n));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String valueOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private String cleanError(Throwable t) {
        String message = t == null ? "Error desconocido" : t.getMessage();
        if (message == null || message.trim().isEmpty()) message = t == null ? "Error desconocido" : t.toString();
        return message.length() > 420 ? message.substring(0,420) : message;
    }

    private void toast(String message) {
        Toast.makeText(this,message,Toast.LENGTH_SHORT).show();
    }

    private void alert(String title,String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Aceptar",null).show();
    }
}
