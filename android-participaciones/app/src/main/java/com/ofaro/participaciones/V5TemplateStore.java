package com.ofaro.participaciones;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Plantillas creadas por el usuario en este terminal.
 * Solo guardan estilo/configuración: nunca datos de clientes ni contenido dinámico.
 * Desde alpha9 pueden incluir hasta dos imágenes optimizadas embebidas en el propio preset.
 */
final class V5TemplateStore {
    static final String PREF_KEY = "local_templates_v5";
    static final String FORMAT = "ofaro-template-v2";

    private V5TemplateStore() {}

    static List<JSONObject> list(AppCore core) {
        List<JSONObject> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(core.prefs().getString(PREF_KEY, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject x = a.optJSONObject(i);
                if (x != null) out.add(new JSONObject(x.toString()));
            }
        } catch (Exception ignored) {}
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
                    .put("updatedAt", now);
        } catch (Exception ignored) {}
        return x;
    }

    static JSONObject duplicate(JSONObject source) {
        if (source == null) return create("Mi plantilla", "Minimal Premium", 80, "line", "L");
        long now = System.currentTimeMillis();
        try {
            JSONObject x = new JSONObject(source.toString());
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
            job.put("templateId", template.optString("baseTemplate", "Minimal Premium"));
            job.put("paperWidth", template.optInt("paperWidth", job.optInt("paperWidth", 80)) <= 58 ? 58 : 80);
            job.put("separator", template.optString("separator", job.optString("separator", "line")));
            job.put("qrSize", template.optString("qrSize", job.optString("qrSize", "L")));
            copyImage(template, job, 1);
            copyImage(template, job, 2);
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
                .put(p + "Position", source.optString(p + "Position", slot == 1 ? "header" : "footer"))
                .put(p + "Align", source.optString(p + "Align", "center"))
                .put(p + "WidthPercent", clamp(source.optInt(p + "WidthPercent", 55), 25, 100));
    }

    static String exportJson(JSONObject template) {
        JSONObject root = new JSONObject();
        try {
            root.put("format", FORMAT)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("template", template == null ? new JSONObject() : new JSONObject(template.toString()));
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

    private static void clearDefaultIf(AppCore core, String key, String id) {
        if (id != null && id.equalsIgnoreCase(core.prefs().getString(key, ""))) {
            core.prefs().edit().remove(key).apply();
        }
    }

    private static String cleanName(String s) {
        String v = s == null ? "" : s.trim();
        return v.isEmpty() ? "Mi plantilla" : v;
    }

    private static String safe(String s, String fallback) {
        String v = s == null ? "" : s.trim();
        return v.isEmpty() ? fallback : v;
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
