package com.ofaro.participaciones;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONObject;

public class WebAppActivity extends Activity {
    private static final int REQ_FILE = 3001;
    private static final int REQ_CAMERA = 3002;
    private static final int REQ_NOTIFICATIONS = 3003;
    private static final String WEB_URL = "https://mesonofaro.es/gestion/?android=1";

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private PermissionRequest pendingCameraRequest;
    private AppCore core;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        core = new AppCore(this);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(245,244,241));
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString() + " OFaroAndroid/3.0.0");

        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        }

        webView.addJavascriptInterface(new NativeBridge(), "OfaroAndroid");
        webView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleExternal(url);
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return handleExternal(request.getUrl().toString());
            }
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    boolean camera = false;
                    for (String r : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) camera = true;
                    }
                    if (!camera) { request.deny(); return; }
                    if (Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    } else {
                        pendingCameraRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                    }
                });
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = filePathCallback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(Intent.createChooser(intent,"Elegir imagen"),REQ_FILE);
                return true;
            }
        });

        webView.loadUrl(WEB_URL);
        if (core.prefs().getBoolean("printReceiverEnabled", false)) startReceiver();
        requestNotificationPermission();
    }

    private boolean handleExternal(String url) {
        if (url == null) return false;
        if (url.startsWith("tel:") || url.startsWith("mailto:") || url.startsWith("whatsapp:") || url.contains("wa.me/")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
            catch (Exception e) { Toast.makeText(this,"No se pudo abrir el enlace",Toast.LENGTH_SHORT).show(); }
            return true;
        }
        if (url.startsWith("https://mesonofaro.es/gestion/")) return false;
        return false;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},REQ_NOTIFICATIONS);
        }
    }

    private void startReceiver() {
        Intent i = new Intent(this, PrintReceiverService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i); else startService(i);
    }

    private void stopReceiver() {
        stopService(new Intent(this, PrintReceiverService.class));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == REQ_FILE) {
            if (fileCallback == null) return;
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults);
        if (requestCode == REQ_CAMERA && pendingCameraRequest != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingCameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else pendingCameraRequest.deny();
            pendingCameraRequest = null;
        }
    }

    @Override
    public void onBackPressed() {
        webView.evaluateJavascript("(function(){var b=document.getElementById('backButton');if(b&&!b.hidden){b.click();return 'handled'}return 'home'})()", value -> {
            if (value == null || value.contains("home")) super.onBackPressed();
        });
    }

    public class NativeBridge {
        @android.webkit.JavascriptInterface
        public String getPrinterSettings() {
            try {
                return new JSONObject()
                        .put("printerIp",core.printerIp())
                        .put("printerPort",core.printerPort())
                        .put("receiverEnabled",core.prefs().getBoolean("printReceiverEnabled",false))
                        .put("terminal",core.terminal())
                        .put("version","3.0.0")
                        .toString();
            } catch (Exception e) { return "{}"; }
        }

        @android.webkit.JavascriptInterface
        public void syncAuth(String key, String terminal) {
            core.prefs().edit().putString("key",key == null ? "" : key.trim()).putString("terminal",terminal == null ? "Caja O Faro" : terminal.trim()).apply();
            if (core.prefs().getBoolean("printReceiverEnabled",false) && !core.key().isEmpty()) startReceiver();
        }

        @android.webkit.JavascriptInterface
        public String savePrinterSettings(String ip, int port, boolean enabled) {
            int safePort = port > 0 && port < 65536 ? port : 9100;
            core.prefs().edit().putString("printerIp",ip == null ? "" : ip.trim()).putInt("printerPort",safePort).putBoolean("printReceiverEnabled",enabled).apply();
            if (enabled) startReceiver(); else stopReceiver();
            return enabled ? "Receptor de impresión activo" : "Receptor detenido";
        }

        @android.webkit.JavascriptInterface
        public String testPrinter() {
            try {
                core.printTest(core.printerIp(),core.printerPort());
                return "Ticket de prueba enviado correctamente";
            } catch (Exception e) {
                return "Error: " + (e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }

        @android.webkit.JavascriptInterface
        public String openAndroidSettings() {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:" + getPackageName())));
                return "OK";
            } catch (Exception e) { return "Error"; }
        }
    }
}
