package com.ofaro.participaciones;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/** Exporta/importa configuración portable. La clave privada nunca sale del dispositivo. */
final class V5BackupStore {
    static final String FORMAT="ofaro-backup-v1";
    private static final String[] STRING_KEYS={
            "api","terminal","printerIp","printerCut",
            "defaultTemplateGeneral","defaultTemplateQr","defaultTemplateReservation","lastPrintTemplate"
    };
    private static final String[] INT_KEYS={"printerPort","printerPaper","printerFeed","printerDarkness"};

    private V5BackupStore(){}

    static String exportJson(AppCore core){
        JSONObject root=new JSONObject(),settings=new JSONObject();
        try{
            SharedPreferences p=core.prefs();
            for(String k:STRING_KEYS)if(p.contains(k))settings.put(k,p.getString(k,""));
            for(String k:INT_KEYS)if(p.contains(k))settings.put(k,p.getInt(k,0));
            JSONArray templates=new JSONArray();List<JSONObject> all=V5TemplateStore.list(core);for(JSONObject t:all)templates.put(new JSONObject(t.toString()));
            root.put("format",FORMAT)
                    .put("exportedAt",System.currentTimeMillis())
                    .put("appVersion",BuildConfig.VERSION_NAME)
                    .put("settings",settings)
                    .put("templates",templates)
                    .put("secretIncluded",false);
        }catch(Exception ignored){}
        return root.toString();
    }

    static JSONObject importJson(AppCore core,String raw)throws Exception{
        if(raw==null||raw.trim().isEmpty())throw new Exception("La copia está vacía.");
        JSONObject root;try{root=new JSONObject(raw.trim());}catch(Exception e){throw new Exception("El archivo no contiene una copia O Faro válida.");}
        if(!FORMAT.equals(root.optString("format","")))throw new Exception("Formato de copia no compatible.");
        JSONObject settings=root.optJSONObject("settings");JSONArray templates=root.optJSONArray("templates");
        int importedTemplates=0;
        SharedPreferences.Editor editor=core.prefs().edit();
        if(settings!=null){
            for(String k:STRING_KEYS)if(settings.has(k))editor.putString(k,settings.optString(k,""));
            for(String k:INT_KEYS)if(settings.has(k))editor.putInt(k,settings.optInt(k,0));
        }
        editor.apply();
        if(templates!=null){
            for(int i=0;i<templates.length();i++){
                JSONObject t=templates.optJSONObject(i);if(t==null)continue;
                JSONObject wrapper=new JSONObject().put("format",V5TemplateStore.FORMAT).put("template",t);
                V5TemplateStore.importJson(core,wrapper.toString());importedTemplates++;
            }
        }
        return new JSONObject().put("ok",true).put("templates",importedTemplates).put("needsManagementKey",core.key().isEmpty());
    }
}
