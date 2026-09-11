package com.ofaro.participaciones;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Plantillas creadas por el usuario en este terminal.
 * Solo guardan estilo/configuración: nunca datos de clientes ni contenido dinámico.
 * Pueden incluir hasta dos imágenes optimizadas embebidas en el propio preset.
 */
final class V5TemplateStore {
    static final String PREF_KEY = "local_templates_v5";
    static final String FORMAT = "ofaro-template-v2";

    private V5TemplateStore() {}

    static List<JSONObject> list(AppCore core) {
        List<JSONObject> out = new ArrayList<>();
        boolean changed=false;
        try {
            JSONArray a = new JSONArray(core.prefs().getString(PREF_KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null) {
                    JSONObject migrated=migrate(new JSONObject(x.toString()));
                    out.add(migrated);
                    if(!migrated.toString().equals(x.toString()))changed=true;
                }
            }
        } catch (Exception ignored) {}
        if(changed)persist(core,out);
        return out;
    }

    static JSONObject get(AppCore core, String id) {
        if (id == null || id.trim().isEmpty()) return null;
        for (JSONObject x : list(core)) {
            if (id.equalsIgnoreCase(x.optString("id", ""))) return x;
        }
        return null;
    }

    static JSONObject create(String name, String baseTemplate, int paperWidth, String separator, String qrSize) {
        long now = System.currentTimeMillis();
        JSONObject x = new JSONObject();
        try {
            x.put("id", "LOCAL-" + now)
                    .put("name", cleanName(name))
                    .put("baseTemplate", safe(baseTemplate, "Minimal Premium"))
                    .put("paperWidth", paperWidth <= 58 ? 58 : 80)
                    .put("separator", safe(separator, "line"))
                    .put("qrSize", safe(qrSize, "L"))
                    .put("image1Data", "")
                    .put("image1Position", "header")
                    .put("image1Align", "center")
                    .put("image1WidthPercent", 55)
                    .put("image2Data", "")
                    .put("image2Position", "footer")
                    .put("image2Align", "center")
                    .put("image2WidthPercent", 55)
                    .put("createdAt", now)
                    .put("updatedAt", now)
                    .put("schemaVersion",2);
        } catch (Exception ignored) {}
        return x;
    }

    static JSONObject duplicate(JSONObject source) {
        if (source == null) return create("Mi plantilla", "Minimal Premium", 80, "line", "L");
        long now = System.currentTimeMillis();
        try {
            JSONObject x = migrate(new JSONObject(source.toString()));
            x.put("id", "LOCAL-" + now)
                    .put("name", cleanName(source.optString("name", "Mi plantilla")) + " · copia")
                    .put("createdAt", now)
                    .put("updatedAt", now);
            return x;
        } catch (Exception ignored) {
            return create(source.optString("name", "Mi plantilla") + " · copia",
                    source.optString("baseTemplate", "Minimal Premium"),
                    source.optInt("paperWidth", 80), source.optString("separator", "line"),
                    source.optString("qrSize", "L"));
        }
    }

    static void save(AppCore core, JSONObject template) {
        if (template == null) return;
        template=migrate(template);
        try { template.put("updatedAt", System.currentTimeMillis()); } catch (Exception ignored) {}
        List<JSONObject> all = list(core);
        String id = template.optString("id", "");
        boolean replaced = false;
        JSONArray out = new JSONArray();
        for (JSONObject x : all) {
            if (!id.isEmpty() && id.equalsIgnoreCase(x.optString("id", ""))) {
                out.put(template);
                replaced = true;
            } else out.put(x);
        }
        if (!replaced) out.put(template);
        core.prefs().edit().putString(PREF_KEY, out.toString()).apply();
    }

    static JSONObject importJson(AppCore core,String raw)throws Exception{
        if(raw==null||raw.trim().isEmpty())throw new Exception("El archivo está vacío.");
        JSONObject root;
        try{root=new JSONObject(raw.trim());}catch(Exception e){throw new Exception("El archivo no contiene una plantilla O Faro válida.");}
        String format=root.optString("format","");
        JSONObject template=root.optJSONObject("template");
        if(template==null && root.has("baseTemplate")) template=root;
        if(template==null)throw new Exception("No se encontró la plantilla dentro del archivo.");
        if(!format.isEmpty() && !format.startsWith("ofaro-template-v"))throw new Exception("Formato de plantilla no compatible.");

        JSONObject imported=migrate(new JSONObject(template.toString()));
        long now=System.currentTimeMillis();
        imported.put("id","LOCAL-"+now)
                .put("name",cleanName(imported.optString("name","Plantilla importada")))
                .put("createdAt",now)
                .put("updatedAt",now)
                .put("schemaVersion",2);
        validateImage(imported,"image1Data");
        validateImage(imported,"image2Data");
        save(core,imported);
        return imported;
    }

    static void delete(AppCore core, String id) {
        JSONArray out = new JSONArray();
        for (JSONObject x : list(core)) {
            if (!id.equalsIgnoreCase(x.optString("id", ""))) out.put(x);
        }
        core.prefs().edit().putString(PREF_KEY, out.toString()).apply();
        clearDefaultIf(core, "defaultTemplateGeneral", id);
        clearDefaultIf(core, "defaultTemplateQr", id);
        clearDefaultIf(core, "defaultTemplateReservation", id);
    }

    static void apply(JSONObject job, JSONObject template) {
        if (job == null || template == null) return;
        try {
            JSONObject t=migrate(new JSONObject(template.toString()));
            job.put("templateId", t.optString("baseTemplate", "Minimal Premium"));
            job.put("paperWidth", t.optInt("paperWidth", job.optInt("paperWidth", 80)) <= 58 ? 58 : 80);
            job.put("separator", t.optString("separator", job.optString("separator", "line")));
            job.put("qrSize", t.optString("qrSize", job.optString("qrSize", "L")));
            copyImage(t, job, 1);
            copyImage(t, job, 2);
        } catch (Exception ignored) {}
    }

    private static void copyImage(JSONObject source, JSONObject job, int slot) throws Exception {
        String p = "image" + slot;
        String data = source.optString(p + "Data", "");
        if (data.isEmpty()) {
            job.remove(p + "Data");
            return;
        }
        job.put(p + "Data", data)
                .put(p + "Position", validPosition(source.optString(p + "Position", slot == 1 ? "header" : "footer")))
                .put(p + "Align", validAlign(source.optString(p + "Align", "center")))
                .put(p + "WidthPercent", clamp(source.optInt(p + "WidthPercent", 55), 25, 100));
    }

    static String exportJson(JSONObject template) {
        JSONObject root = new JSONObject();
        try {
            root.put("format", FORMAT)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("template", template == null ? new JSONObject() : migrate(new JSONObject(template.toString())));
        } catch (Exception ignored) {}
        return root.toString();
    }

    static int count(AppCore core) { return list(core).size(); }

    static void resetDefaults(AppCore core) {
        core.prefs().edit()
                .remove("defaultTemplateGeneral")
                .remove("defaultTemplateQr")
                .remove("defaultTemplateReservation")
                .remove("lastPrintTemplate")
                .apply();
    }

    private static JSONObject migrate(JSONObject x){
        if(x==null)x=new JSONObject();
        try{
            long now=System.currentTimeMillis();
            if(x.optString("id","").trim().isEmpty())x.put("id","LOCAL-"+now);
            if(x.optString("name","").trim().isEmpty())x.put("name","Mi plantilla");
            if(x.optString("baseTemplate","").trim().isEmpty())x.put("baseTemplate","Minimal Premium");
            x.put("paperWidth",x.optInt("paperWidth",80)<=58?58:80);
            if(x.optString("separator","").trim().isEmpty())x.put("separator","line");
            if(x.optString("qrSize","").trim().isEmpty())x.put("qrSize","L");
            if(!x.has("image1Data"))x.put("image1Data","");
            if(!x.has("image1Position"))x.put("image1Position","header");
            if(!x.has("image1Align"))x.put("image1Align","center");
            if(!x.has("image1WidthPercent"))x.put("image1WidthPercent",55);
            if(!x.has("image2Data"))x.put("image2Data","");
            if(!x.has("image2Position"))x.put("image2Position","footer");
            if(!x.has("image2Align"))x.put("image2Align","center");
            if(!x.has("image2WidthPercent"))x.put("image2WidthPercent",55);
            x.put("image1Position",validPosition(x.optString("image1Position","header")));
            x.put("image2Position",validPosition(x.optString("image2Position","footer")));
            x.put("image1Align",validAlign(x.optString("image1Align","center")));
            x.put("image2Align",validAlign(x.optString("image2Align","center")));
            x.put("image1WidthPercent",clamp(x.optInt("image1WidthPercent",55),25,100));
            x.put("image2WidthPercent",clamp(x.optInt("image2WidthPercent",55),25,100));
            if(!x.has("createdAt"))x.put("createdAt",now);
            if(!x.has("updatedAt"))x.put("updatedAt",now);
            x.put("schemaVersion",2);
        }catch(Exception ignored){}
        return x;
    }

    private static void validateImage(JSONObject x,String key)throws Exception{
        String data=x.optString(key,"").trim();
        if(data.isEmpty())return;
        if(!data.startsWith("data:image/"))throw new Exception("Una de las imágenes de la plantilla no es válida.");
        if(data.length()>1_800_000)throw new Exception("Una de las imágenes es demasiado grande para importarla.");
    }

    private static String validPosition(String value){
        String v=safe(value,"header");
        return "header".equals(v)||"below_title".equals(v)||"body".equals(v)||"footer".equals(v)?v:"header";
    }
    private static String validAlign(String value){
        String v=safe(value,"center");
        return "left".equals(v)||"center".equals(v)||"right".equals(v)?v:"center";
    }
    private static void persist(AppCore core,List<JSONObject> all){JSONArray a=new JSONArray();for(JSONObject x:all)a.put(x);core.prefs().edit().putString(PREF_KEY,a.toString()).apply();}
    private static void clearDefaultIf(AppCore core, String key, String id) {
        if (id != null && id.equalsIgnoreCase(core.prefs().getString(key, ""))) {
            core.prefs().edit().remove(key).apply();
        }
    }
    private static String cleanName(String s) {String v = s == null ? "" : s.trim();return v.isEmpty() ? "Mi plantilla" : v;}
    private static String safe(String s, String fallback) {String v = s == null ? "" : s.trim();return v.isEmpty() ? fallback : v;}
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
