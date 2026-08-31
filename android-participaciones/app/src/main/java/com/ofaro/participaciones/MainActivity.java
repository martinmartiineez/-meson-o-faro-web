package com.ofaro.participaciones;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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

import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanner;
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScannerOptions;
import com.google.android.gms.mlkit.codescanner.GmsBarcodeScanning;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String PREFS = "ofaro_participaciones";
    private static final String DEFAULT_API = "https://script.google.com/macros/s/AKfycbwyICNMM0CHeSFQqOaO4d6g_d84vougY6OivfrMi6G5DIIVy7Y1qK_v2tBsZKmnQ2njkQ/exec";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
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
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
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
        root.setBackgroundColor(Color.rgb(246, 246, 246));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(16));
        header.setBackgroundColor(Color.rgb(17, 17, 17));

        TextView title = text("MESÓN O FARO", 23, Color.WHITE, true);
        TextView subtitle = text("Participaciones y premios", 14, Color.rgb(210, 210, 210), false);
        header.addView(title);
        header.addView(subtitle);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setPadding(dp(10), dp(10), dp(10), dp(10));
        nav.setBackgroundColor(Color.WHITE);

        navGenerate = navButton("GENERAR");
        navScan = navButton("ESCANEAR");
        navSettings = navButton("AJUSTES");
        nav.addView(navGenerate, weightParams());
        nav.addView(navScan, weightParams());
        nav.addView(navSettings, weightParams());
        root.addView(nav);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

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
        TextView label = text("Tickets", 16, Color.DKGRAY, true);
        quantityInput = input("1");
        quantityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        quantityInput.setGravity(Gravity.CENTER);
        quantityInput.setSelectAllOnFocus(true);
        quantityRow.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1));
        quantityRow.addView(quantityInput, new LinearLayout.LayoutParams(dp(100), dp(52)));
        box.addView(card(quantityRow));

        Button generate = primaryButton("GENERAR E IMPRIMIR");
        generate.setOnClickListener(v -> generateBatch(generate));
        box.addView(generate, marginTopParams(dp(14)));

        Button generateOnly = secondaryButton("GENERAR SIN IMPRIMIR");
        generateOnly.setOnClickListener(v -> generateOnly(generateOnly));
        box.addView(generateOnly, marginTopParams(dp(10)));

        generateStatus = text("Listo.", 15, Color.DKGRAY, false);
        generateStatus.setPadding(dp(4), dp(18), dp(4), dp(6));
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
        box.addView(paragraph("Escanea el QR del ticket. La app consulta Google Sheets antes de permitir el canje."));

        Button scan = primaryButton("ESCANEAR QR");
        scan.setOnClickListener(v -> startScanner());
        box.addView(scan);

        TextView alternative = text("O introduce el código manualmente", 14, Color.DKGRAY, false);
        alternative.setPadding(0, dp(18), 0, dp(8));
        box.addView(alternative);

        manualCodeInput = input("");
        manualCodeInput.setHint("OF-XXXXX-XXXXX");
        manualCodeInput.setSingleLine(true);
        box.addView(manualCodeInput, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        Button validate = secondaryButton("VALIDAR CÓDIGO");
        validate.setOnClickListener(v -> validateCode(manualCodeInput.getText().toString()));
        box.addView(validate, marginTopParams(dp(10)));

        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(18), dp(18), dp(18), dp(18));
        result.setBackground(roundRect(Color.WHITE, dp(14), Color.rgb(225,225,225), 1));

        validationTitle = text("Sin código validado", 20, Color.rgb(20,20,20), true);
        validationDetail = text("Escanea un ticket para ver el premio y su estado.", 15, Color.DKGRAY, false);
        validationDetail.setPadding(0, dp(8), 0, dp(14));
        redeemButton = primaryButton("CANJEAR PREMIO");
        redeemButton.setVisibility(View.GONE);
        redeemButton.setOnClickListener(v -> confirmRedeem());

        result.addView(validationTitle);
        result.addView(validationDetail);
        result.addView(redeemButton);
        box.addView(result, marginTopParams(dp(18)));

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

        EditText api = field(box, "Endpoint Apps Script", prefs.getString("api", DEFAULT_API), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText key = field(box, "Clave API", prefs.getString("key", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText printerIp = field(box, "IP impresora ESC/POS", prefs.getString("printerIp", ""), InputType.TYPE_CLASS_PHONE);
        EditText printerPort = field(box, "Puerto", String.valueOf(prefs.getInt("printerPort", 9100)), InputType.TYPE_CLASS_NUMBER);
        EditText terminal = field(box, "Nombre del terminal", prefs.getString("terminal", "Caja O Faro"), InputType.TYPE_CLASS_TEXT);

        Button save = primaryButton("GUARDAR AJUSTES");
        save.setOnClickListener(v -> {
            int port = safeInt(printerPort.getText().toString(), 9100, 1, 65535);
            prefs.edit()
                    .putString("api", api.getText().toString().trim())
                    .putString("key", key.getText().toString().trim())
                    .putString("printerIp", printerIp.getText().toString().trim())
                    .putInt("printerPort", port)
                    .putString("terminal", terminal.getText().toString().trim())
                    .apply();
            toast("Ajustes guardados");
        });
        box.addView(save, marginTopParams(dp(16)));

        Button testApi = secondaryButton("PROBAR CONEXIÓN CON GOOGLE");
        testApi.setOnClickListener(v -> testApi(testApi, api.getText().toString().trim(), key.getText().toString().trim()));
        box.addView(testApi, marginTopParams(dp(10)));

        Button testPrinter = secondaryButton("IMPRIMIR TICKET DE PRUEBA");
        testPrinter.setOnClickListener(v -> {
            String ip = printerIp.getText().toString().trim();
            int port = safeInt(printerPort.getText().toString(), 9100, 1, 65535);
            testPrinter(testPrinter, ip, port);
        });
        box.addView(testPrinter, marginTopParams(dp(10)));

        content.removeAllViews();
        content.addView(scroll);
    }

    private void generateBatch(Button button) {
        int count = safeInt(quantityInput.getText().toString(), 1, 1, 20);
        String ip = prefs.getString("printerIp", "").trim();
        if (ip.isEmpty()) {
            alert("Falta la impresora", "Configura la IP de la impresora ESC/POS en Ajustes antes de generar e imprimir.");
            return;
        }
        if (!backendConfigured()) return;

        button.setEnabled(false);
        setGenerateStatus("Generando " + count + (count == 1 ? " ticket…" : " tickets…"));
        io.execute(() -> {
            int done = 0;
            String lastCode = "";
            try {
                for (int i = 0; i < count; i++) {
                    JSONObject created = apiPost(action("participationCreate")
                            .put("terminal", terminalName())
                            .put("origin", "APK"));
                    ensureOk(created);
                    String code = created.optString("code", "");
                    String qr = created.optString("qrPayload", "OFARO:" + code);
                    String createdAt = created.optString("createdAt", "");
                    printParticipation(code, qr, createdAt);
                    try {
                        JSONObject marked = apiPost(action("participationMarkPrinted").put("code", code));
                        ensureOk(marked);
                    } catch (Exception ignored) {
                        // El ticket ya está impreso. Si el marcado falla, no invalida el código.
                    }
                    done++;
                    lastCode = code;
                    int shown = done;
                    runOnUiThread(() -> setGenerateStatus("Impresos " + shown + " de " + count + "…"));
                }
                String finalLastCode = lastCode;
                int finalDone = done;
                runOnUiThread(() -> {
                    setGenerateStatus("Completado: " + finalDone + (finalDone == 1 ? " ticket" : " tickets") + ". Último código: " + finalLastCode);
                    button.setEnabled(true);
                });
            } catch (Exception ex) {
                int finalDone1 = done;
                runOnUiThread(() -> {
                    setGenerateStatus("Se detuvo tras " + finalDone1 + " impresos. Error: " + cleanError(ex));
                    button.setEnabled(true);
                });
            }
        });
    }

    private void generateOnly(Button button) {
        int count = safeInt(quantityInput.getText().toString(), 1, 1, 20);
        if (!backendConfigured()) return;
        button.setEnabled(false);
        setGenerateStatus("Generando códigos sin imprimir…");
        io.execute(() -> {
            int done = 0;
            String last = "";
            try {
                for (int i = 0; i < count; i++) {
                    JSONObject created = apiPost(action("participationCreate")
                            .put("terminal", terminalName())
                            .put("origin", "APK-sin-impresion"));
                    ensureOk(created);
                    done++;
                    last = created.optString("code", "");
                }
                int finalDone = done;
                String finalLast = last;
                runOnUiThread(() -> {
                    setGenerateStatus("Generados " + finalDone + " códigos. Último: " + finalLast + ". No se marcaron como impresos.");
                    button.setEnabled(true);
                });
            } catch (Exception ex) {
                int finalDone1 = done;
                runOnUiThread(() -> {
                    setGenerateStatus("Generados " + finalDone1 + ". Error: " + cleanError(ex));
                    button.setEnabled(true);
                });
            }
        });
    }

    private void startScanner() {
        if (!backendConfigured()) return;
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanner scanner = GmsBarcodeScanning.getClient(this, options);
        Task<Barcode> task = scanner.startScan();
        task.addOnSuccessListener(barcode -> {
            String raw = barcode.getRawValue();
            if (raw == null || raw.trim().isEmpty()) {
                toast("QR vacío");
                return;
            }
            manualCodeInput.setText(raw);
            validateCode(raw);
        }).addOnCanceledListener(() -> toast("Escaneo cancelado"))
          .addOnFailureListener(e -> alert("No se pudo abrir el escáner", cleanError(e)));
    }

    private void validateCode(String code) {
        if (!backendConfigured()) return;
        final String cleaned = code == null ? "" : code.trim();
        if (cleaned.isEmpty()) {
            alert("Código vacío", "Introduce o escanea un código.");
            return;
        }
        setValidation("Validando…", "Consultando Google Sheets.", false);
        io.execute(() -> {
            try {
                JSONObject response = apiPost(action("participationValidate").put("code", cleaned));
                if (!response.optBoolean("ok", false)) {
                    throw new Exception(response.optString("error", "Código no válido"));
                }
                String codeOut = response.optString("code", cleaned);
                String state = response.optString("state", "");
                String prize = response.optString("prize", "Sin premio");
                boolean hasPrize = response.optBoolean("hasPrize", false);
                boolean canRedeem = response.optBoolean("canRedeem", false);
                String redeemedAt = response.optString("redeemedAt", "");
                String redeemedBy = response.optString("redeemedBy", "");
                validatedCode = codeOut;

                String title;
                String detail;
                if ("Canjeada".equalsIgnoreCase(state)) {
                    title = "YA CANJEADO";
                    detail = codeOut + "\nPremio: " + prize + "\nCanjeado: " + valueOrDash(redeemedAt) + "\nTerminal: " + valueOrDash(redeemedBy);
                } else if (!hasPrize) {
                    title = "SIN PREMIO";
                    detail = codeOut + "\nEstado: " + state + "\nEste código es válido, pero no tiene premio canjeable.";
                } else {
                    title = prize.toUpperCase(Locale.ROOT);
                    detail = codeOut + "\nEstado: " + state + "\nPremio pendiente de canje.";
                }
                runOnUiThread(() -> setValidation(title, detail, canRedeem));
            } catch (Exception ex) {
                validatedCode = "";
                runOnUiThread(() -> setValidation("CÓDIGO NO VÁLIDO", cleanError(ex), false));
            }
        });
    }

    private void confirmRedeem() {
        if (validatedCode.isEmpty()) return;
        new AlertDialog.Builder(this)
                .setTitle("Confirmar canje")
                .setMessage("Al confirmar, este código quedará inutilizado para siempre. ¿Entregar el premio ahora?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("CANJEAR", (d, w) -> redeemValidated())
                .show();
    }

    private void redeemValidated() {
        final String code = validatedCode;
        redeemButton.setEnabled(false);
        setValidation("Canjeando…", "Bloqueando el código en Google Sheets.", false);
        io.execute(() -> {
            try {
                JSONObject response = apiPost(action("participationRedeem")
                        .put("code", code)
                        .put("terminal", terminalName()));
                if (!response.optBoolean("ok", false)) {
                    String message = response.optString("error", "No se pudo canjear");
                    String at = response.optString("redeemedAt", "");
                    String by = response.optString("redeemedBy", "");
                    if (response.optBoolean("alreadyRedeemed", false)) {
                        message += "\nCanjeado: " + valueOrDash(at) + "\nTerminal: " + valueOrDash(by);
                    }
                    throw new Exception(message);
                }
                String prize = response.optString("prize", "Premio");
                String at = response.optString("redeemedAt", "");
                runOnUiThread(() -> {
                    setValidation("PREMIO CANJEADO", prize + "\n" + code + "\n" + at, false);
                    redeemButton.setEnabled(true);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    setValidation("NO SE PUDO CANJEAR", cleanError(ex), false);
                    redeemButton.setEnabled(true);
                });
            }
        });
    }

    private void testApi(Button button, String api, String key) {
        if (api.isEmpty() || key.isEmpty()) {
            alert("Faltan datos", "Completa el endpoint y la clave API.");
            return;
        }
        button.setEnabled(false);
        io.execute(() -> {
            try {
                JSONObject body = new JSONObject().put("action", "participationPing").put("key", key);
                JSONObject result = apiPostTo(api, body);
                ensureOk(result);
                runOnUiThread(() -> {
                    toast("Conexión correcta");
                    button.setEnabled(true);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    alert("Error de conexión", cleanError(ex));
                    button.setEnabled(true);
                });
            }
        });
    }

    private void testPrinter(Button button, String ip, int port) {
        if (ip.isEmpty()) {
            alert("Falta la IP", "Introduce la IP de la impresora.");
            return;
        }
        button.setEnabled(false);
        io.execute(() -> {
            try {
                printTest(ip, port);
                runOnUiThread(() -> {
                    toast("Ticket de prueba enviado");
                    button.setEnabled(true);
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    alert("Error de impresora", cleanError(ex));
                    button.setEnabled(true);
                });
            }
        });
    }

    private JSONObject action(String action) throws Exception {
        return new JSONObject()
                .put("action", action)
                .put("key", prefs.getString("key", "").trim());
    }

    private JSONObject apiPost(JSONObject body) throws Exception {
        return apiPostTo(prefs.getString("api", DEFAULT_API).trim(), body);
    }

    private JSONObject apiPostTo(String endpoint, JSONObject body) throws Exception {
        if (endpoint == null || endpoint.trim().isEmpty()) throw new Exception("Endpoint vacío.");
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setRequestProperty("Accept", "application/json");
        byte[] payload = body.toString().getBytes(Charset.forName("UTF-8"));
        conn.setFixedLengthStreamingMode(payload.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(payload);
        }

        int status = conn.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? conn.getInputStream() : conn.getErrorStream();
        String text = readAll(stream);
        conn.disconnect();
        if (text == null || text.trim().isEmpty()) throw new Exception("Respuesta vacía del servidor (HTTP " + status + ").");
        try {
            return new JSONObject(text);
        } catch (Exception parse) {
            throw new Exception("Respuesta no válida del servidor: " + text.substring(0, Math.min(text.length(), 180)));
        }
    }

    private void ensureOk(JSONObject json) throws Exception {
        if (!json.optBoolean("ok", false)) throw new Exception(json.optString("error", "Operación rechazada"));
    }

    private String readAll(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, Charset.forName("UTF-8")))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void printParticipation(String code, String qrPayload, String createdAt) throws Exception {
        String ip = prefs.getString("printerIp", "").trim();
        int port = prefs.getInt("printerPort", 9100);
        if (ip.isEmpty()) throw new Exception("IP de impresora no configurada.");

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 4000);
            socket.setSoTimeout(5000);
            try (OutputStream out = socket.getOutputStream()) {
                escInit(out);
                align(out, 1);
                bold(out, true);
                doubleSize(out, true);
                writeText(out, "MESON O FARO\n");
                doubleSize(out, false);
                bold(out, false);
                writeText(out, "PARTICIPACION\n");
                writeText(out, "------------------------------\n");
                bold(out, true);
                writeText(out, code + "\n");
                bold(out, false);
                writeText(out, valueOrDash(createdAt) + "\n\n");
                writeQr(out, qrPayload, 7);
                writeText(out, "\nEscanea este QR en O Faro\n");
                writeText(out, "para validar tu participacion.\n");
                writeText(out, "Conserva este ticket.\n\n\n");
                cut(out);
                out.flush();
            }
        }
    }

    private void printTest(String ip, int port) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 4000);
            try (OutputStream out = socket.getOutputStream()) {
                escInit(out);
                align(out, 1);
                bold(out, true);
                writeText(out, "MESON O FARO\n");
                bold(out, false);
                writeText(out, "Prueba impresora ESC/POS\n");
                writeText(out, ip + ":" + port + "\n\n");
                writeQr(out, "OFARO:PRUEBA", 6);
                writeText(out, "\nConexion correcta\n\n\n");
                cut(out);
                out.flush();
            }
        }
    }

    private void escInit(OutputStream out) throws Exception { out.write(new byte[]{0x1B, 0x40}); }
    private void align(OutputStream out, int mode) throws Exception { out.write(new byte[]{0x1B, 0x61, (byte) mode}); }
    private void bold(OutputStream out, boolean on) throws Exception { out.write(new byte[]{0x1B, 0x45, (byte) (on ? 1 : 0)}); }
    private void doubleSize(OutputStream out, boolean on) throws Exception { out.write(new byte[]{0x1D, 0x21, (byte) (on ? 0x11 : 0x00)}); }
    private void writeText(OutputStream out, String text) throws Exception { out.write(text.getBytes(Charset.forName("CP850"))); }

    private void writeQr(OutputStream out, String data, int moduleSize) throws Exception {
        byte[] bytes = data.getBytes(Charset.forName("UTF-8"));
        out.write(new byte[]{0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00});
        out.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, (byte) Math.max(2, Math.min(12, moduleSize))});
        out.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31});
        int length = bytes.length + 3;
        int pL = length & 0xFF;
        int pH = (length >> 8) & 0xFF;
        ByteArrayOutputStream store = new ByteArrayOutputStream();
        store.write(new byte[]{0x1D, 0x28, 0x6B, (byte) pL, (byte) pH, 0x31, 0x50, 0x30});
        store.write(bytes);
        out.write(store.toByteArray());
        out.write(new byte[]{0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30});
    }

    private void cut(OutputStream out) throws Exception {
        out.write(new byte[]{0x1D, 0x56, 0x00});
    }

    private boolean backendConfigured() {
        String api = prefs.getString("api", DEFAULT_API).trim();
        String key = prefs.getString("key", "").trim();
        if (api.isEmpty() || key.isEmpty()) {
            alert("Falta configurar la API", "Ve a Ajustes e introduce el endpoint de Apps Script y la clave de la app.");
            return false;
        }
        return true;
    }

    private String terminalName() {
        String value = prefs.getString("terminal", "Caja O Faro").trim();
        return value.isEmpty() ? "Caja O Faro" : value;
    }

    private void setGenerateStatus(String text) {
        if (generateStatus != null) generateStatus.setText(text);
    }

    private void setValidation(String title, String detail, boolean canRedeem) {
        validationTitle.setText(title);
        validationDetail.setText(detail);
        redeemButton.setVisibility(canRedeem ? View.VISIBLE : View.GONE);
        redeemButton.setEnabled(true);
    }

    private ScrollView scroll() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        return scroll;
    }

    private LinearLayout verticalBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(20), dp(18), dp(28));
        return box;
    }

    private TextView sectionTitle(String value) {
        TextView view = text(value, 26, Color.rgb(20,20,20), true);
        view.setPadding(0, 0, 0, dp(8));
        return view;
    }

    private TextView paragraph(String value) {
        TextView view = text(value, 15, Color.DKGRAY, false);
        view.setLineSpacing(0, 1.16f);
        view.setPadding(0, 0, 0, dp(18));
        return view;
    }

    private LinearLayout card(View child) {
        LinearLayout card = new LinearLayout(this);
        card.setPadding(dp(16), dp(8), dp(16), dp(8));
        card.setBackground(roundRect(Color.WHITE, dp(14), Color.rgb(225,225,225), 1));
        card.addView(child, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return card;
    }

    private EditText field(LinearLayout parent, String label, String value, int inputType) {
        TextView l = text(label, 14, Color.DKGRAY, true);
        l.setPadding(dp(2), dp(12), dp(2), dp(6));
        parent.addView(l);
        EditText edit = input(value);
        edit.setInputType(inputType);
        edit.setSingleLine(true);
        parent.addView(edit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));
        return edit;
    }

    private EditText input(String value) {
        EditText edit = new EditText(this);
        edit.setText(value);
        edit.setTextSize(16);
        edit.setTextColor(Color.rgb(20,20,20));
        edit.setHintTextColor(Color.GRAY);
        edit.setPadding(dp(14), 0, dp(14), 0);
        edit.setBackground(roundRect(Color.WHITE, dp(12), Color.rgb(205,205,205), 1));
        return edit;
    }

    private Button navButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setPadding(dp(4), 0, dp(4), 0);
        return b;
    }

    private void selectNav(Button selected) {
        Button[] all = {navGenerate, navScan, navSettings};
        for (Button button : all) {
            boolean active = button == selected;
            button.setTextColor(active ? Color.WHITE : Color.rgb(35,35,35));
            button.setTypeface(Typeface.DEFAULT, active ? Typeface.BOLD : Typeface.NORMAL);
            button.setBackground(roundRect(active ? Color.rgb(17,17,17) : Color.rgb(240,240,240), dp(10), Color.TRANSPARENT, 0));
        }
    }

    private Button primaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.WHITE);
        b.setBackground(roundRect(Color.rgb(17,17,17), dp(12), Color.TRANSPARENT, 0));
        b.setMinHeight(dp(54));
        return b;
    }

    private Button secondaryButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(Color.rgb(20,20,20));
        b.setBackground(roundRect(Color.WHITE, dp(12), Color.rgb(190,190,190), 1));
        b.setMinHeight(dp(52));
        return b;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private GradientDrawable roundRect(int fill, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        if (strokeWidth > 0) d.setStroke(dp(strokeWidth), stroke);
        return d;
    }

    private LinearLayout.LayoutParams weightParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private LinearLayout.LayoutParams marginTopParams(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        p.topMargin = top;
        return p;
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }

    private int safeInt(String value, int fallback, int min, int max) {
        try {
            int n = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, n));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String valueOrDash(String value) {
        return value == null || value.trim().isEmpty() ? "—" : value.trim();
    }

    private String cleanError(Throwable throwable) {
        String message = throwable == null ? "Error desconocido" : throwable.getMessage();
        if (message == null || message.trim().isEmpty()) message = throwable == null ? "Error desconocido" : throwable.toString();
        return message.length() > 420 ? message.substring(0, 420) : message;
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void alert(String title, String message) {
        if (isFinishing()) return;
        new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("Aceptar", null).show();
    }
}
