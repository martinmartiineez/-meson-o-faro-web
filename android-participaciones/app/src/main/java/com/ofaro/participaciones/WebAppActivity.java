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
    private static final int REQ_FILE=3001,REQ_CAMERA=3002;
    private static final String WEB_URL="https://mesonofaro.es/gestion/?android=1&native=4";
    private WebView webView;private ValueCallback<Uri[]> fileCallback;private PermissionRequest pendingCameraRequest;private AppCore core;

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);core=new AppCore(this);
        getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);
        webView=new WebView(this);webView.setBackgroundColor(Color.rgb(245,244,241));setContentView(webView);
        WebSettings s=webView.getSettings();
        s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);s.setAllowContentAccess(true);s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString()+" OFaroAndroid/"+BuildConfig.VERSION_NAME);
        CookieManager.getInstance().setAcceptCookie(true);
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.LOLLIPOP)CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        webView.addJavascriptInterface(new NativeBridge(),"OfaroAndroid");
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,String url){return handleNavigation(url);}
            @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest request){return handleNavigation(request.getUrl().toString());}
            @Override public void onPageFinished(WebView view,String url){super.onPageFinished(view,url);if(isTrustedUrl(url))hideRemoteUi();}
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onPermissionRequest(PermissionRequest request){runOnUiThread(()->{
                if(!isTrustedUrl(webView.getUrl())){request.deny();return;}
                boolean camera=false;for(String r:request.getResources())if(PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r))camera=true;
                if(!camera){request.deny();return;}
                if(Build.VERSION.SDK_INT<23||checkSelfPermission(Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                else{pendingCameraRequest=request;requestPermissions(new String[]{Manifest.permission.CAMERA},REQ_CAMERA);}
            });}
            @Override public boolean onShowFileChooser(WebView webView,ValueCallback<Uri[]> cb,FileChooserParams params){
                if(!isTrustedUrl(webView.getUrl()))return false;
                if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/*");
                startActivityForResult(Intent.createChooser(i,"Elegir imagen"),REQ_FILE);return true;
            }
        });
        webView.loadUrl(WEB_URL);core.startPrinterWatchdog();requestNotificationPermissionOnce();
    }

    private boolean isTrustedUrl(String url){
        try{
            Uri u=Uri.parse(url==null?"":url);String scheme=u.getScheme(),host=u.getHost();
            if(!"https".equalsIgnoreCase(scheme)||host==null)return false;
            return "mesonofaro.es".equalsIgnoreCase(host)||"www.mesonofaro.es".equalsIgnoreCase(host);
        }catch(Exception e){return false;}
    }
    private boolean handleNavigation(String url){
        if(url==null)return false;
        if(isTrustedUrl(url))return false;
        try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url)));}
        catch(Exception e){Toast.makeText(this,"No se pudo abrir el enlace",Toast.LENGTH_SHORT).show();}
        return true;
    }
    private void hideRemoteUi(){String js="(function(){function h(){document.querySelectorAll('#tsRemote,#pqrRemote,[data-remote-print],.remote-print-send').forEach(function(e){e.style.display='none'});document.querySelectorAll('button').forEach(function(b){if((b.textContent||'').toUpperCase().indexOf('ENVIAR A ANDROID')>=0)b.style.display='none'});}h();new MutationObserver(h).observe(document.body,{childList:true,subtree:true});})()";webView.evaluateJavascript(js,null);}
    private void requestNotificationPermissionOnce(){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED&&!core.prefs().getBoolean("askedNotifications",false)){core.prefs().edit().putBoolean("askedNotifications",true).apply();requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},3003);}}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==REQ_FILE){if(fileCallback==null)return;Uri[] result=null;if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)result=new Uri[]{data.getData()};fileCallback.onReceiveValue(result);fileCallback=null;}}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==REQ_CAMERA&&pendingCameraRequest!=null){if(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED)pendingCameraRequest.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});else pendingCameraRequest.deny();pendingCameraRequest=null;}}
    @Override public void onBackPressed(){webView.evaluateJavascript("(function(){var b=document.getElementById('backButton');if(b&&!b.hidden){b.click();return 'handled'}return 'home'})()",v->{if(v==null||v.contains("home"))super.onBackPressed();});}
    @Override protected void onDestroy(){if(webView!=null){webView.removeJavascriptInterface("OfaroAndroid");webView.stopLoading();webView.destroy();webView=null;}super.onDestroy();}

    public class NativeBridge{
        @android.webkit.JavascriptInterface public String getPrinterSettings(){try{return new JSONObject().put("printerIp",core.printerIp()).put("printerPort",core.printerPort()).put("receiverEnabled",!core.printerIp().isEmpty()).put("terminal",core.terminal()).put("version",BuildConfig.VERSION_NAME).put("status",core.printerStatus()).toString();}catch(Exception e){return"{}";}}
        @android.webkit.JavascriptInterface public void syncAuth(String key,String terminal){if(!isTrustedUrl(webView==null?null:webView.getUrl()))return;core.prefs().edit().putString("key",key==null?"":key.trim()).putString("terminal",terminal==null?"Caja O Faro":terminal.trim()).apply();core.startPrinterWatchdog();}
        @android.webkit.JavascriptInterface public String savePrinterSettings(String ip,int port,boolean ignored){if(!isTrustedUrl(webView==null?null:webView.getUrl()))return"Origen no permitido";int safePort=port>0&&port<65536?port:9100;core.prefs().edit().putString("printerIp",ip==null?"":ip.trim()).putInt("printerPort",safePort).apply();core.startPrinterWatchdog();new Thread(core::reconnectPrinter).start();return"Conexión local permanente activada";}
        @android.webkit.JavascriptInterface public String testPrinter(){if(!isTrustedUrl(webView==null?null:webView.getUrl()))return"Origen no permitido";try{core.printTest(core.printerIp(),core.printerPort());return"Ticket de prueba enviado correctamente";}catch(Exception e){return"Error: "+(e.getMessage()==null?e.toString():e.getMessage());}}
        @android.webkit.JavascriptInterface public String printTicket(String jobJson){if(!isTrustedUrl(webView==null?null:webView.getUrl()))return"Origen no permitido";try{JSONObject job=new JSONObject(jobJson==null?"{}":jobJson);if(!job.has("copies"))job.put("copies",1);RemotePrinter.print(core,job);return"Impreso directamente en "+core.printerIp()+":"+core.printerPort();}catch(Exception e){return"Error: "+(e.getMessage()==null?e.toString():e.getMessage());}}
        @android.webkit.JavascriptInterface public String openAndroidSettings(){if(!isTrustedUrl(webView==null?null:webView.getUrl()))return"Origen no permitido";try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));return"OK";}catch(Exception e){return"Error";}}
    }
}
