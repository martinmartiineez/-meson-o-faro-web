package com.ofaro.participaciones;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Prueba de humo del contrato APK ↔ Apps Script.
 * Solo ejecuta lecturas/diagnóstico; no crea, edita, canjea ni elimina datos.
 */
final class BackendContractSelfTest {
    private BackendContractSelfTest() {}

    static Result run(AppCore core) {
        long started=System.currentTimeMillis();
        List<String> ok=new ArrayList<>();
        List<String> errors=new ArrayList<>();
        String firstPromotionId="";

        if(!core.configured()) {
            errors.add("Configuración · falta endpoint o clave de gestión");
            return new Result(ok,errors,System.currentTimeMillis()-started);
        }
        if(!core.internetAvailable()) {
            errors.add("Red · Android no detecta conexión a Internet");
            return new Result(ok,errors,System.currentTimeMillis()-started);
        }

        try {
            JSONObject r=core.post(core.action("appPing"));
            requireOk(r,"appPing");
            ok.add("Servidor interno");
        } catch(Exception e){errors.add("appPing · "+message(e));}

        try {
            JSONObject r=core.post(core.action("reservationList").put("limit",1).put("includeClosed",true));
            requireArray(r,"items","reservationList");
            ok.add("Reservas");
        } catch(Exception e){errors.add("reservationList · "+message(e));}

        try {
            JSONObject r=core.post(core.action("qrList"));
            requireArray(r,"items","qrList");
            ok.add("QR rápidos");
        } catch(Exception e){errors.add("qrList · "+message(e));}

        try {
            JSONObject r=core.post(core.action("templateList"));
            requireArray(r,"items","templateList");
            ok.add("Plantillas guardadas");
        } catch(Exception e){errors.add("templateList · "+message(e));}

        try {
            JSONObject r=core.post(core.action("historyList").put("limit",1));
            requireArray(r,"items","historyList");
            ok.add("Historial");
        } catch(Exception e){errors.add("historyList · "+message(e));}

        try {
            JSONObject r=core.post(core.action("webSections"));
            requireArray(r,"items","webSections");
            ok.add("Gestión web");
        } catch(Exception e){errors.add("webSections · "+message(e));}

        try {
            JSONObject r=core.post(core.action("promotionPing"));
            requireOk(r,"promotionPing");
            ok.add("Promociones · servidor");
        } catch(Exception e){errors.add("promotionPing · "+message(e));}

        try {
            JSONObject r=core.post(core.action("promotionList"));
            JSONArray items=requireArray(r,"items","promotionList");
            if(items.length()>0) {
                JSONObject first=items.optJSONObject(0);
                if(first!=null)firstPromotionId=first.optString("id","").trim();
            }
            ok.add("Promociones · campañas");
        } catch(Exception e){errors.add("promotionList · "+message(e));}

        try {
            JSONObject r=core.post(core.action("promotionPrizeList"));
            requireArray(r,"items","promotionPrizeList");
            ok.add("Promociones · premios");
        } catch(Exception e){errors.add("promotionPrizeList · "+message(e));}

        if(!firstPromotionId.isEmpty()) {
            try {
                JSONObject r=core.post(core.action("promotionGet").put("id",firstPromotionId));
                requireOk(r,"promotionGet");
                if(r.optJSONObject("campaign")==null)throw new Exception("falta objeto campaign");
                if(r.optJSONArray("segments")==null)throw new Exception("falta array segments");
                if(r.optJSONArray("prizeLinks")==null)throw new Exception("falta array prizeLinks");
                if(r.optJSONArray("prizes")==null)throw new Exception("falta array prizes");
                ok.add("Promociones · detalle");
            } catch(Exception e){errors.add("promotionGet · "+message(e));}
        }

        return new Result(ok,errors,System.currentTimeMillis()-started);
    }

    private static void requireOk(JSONObject r,String action)throws Exception{
        if(r==null||!r.optBoolean("ok",false))throw new Exception(r==null?"respuesta vacía":r.optString("error",action+" rechazado"));
    }
    private static JSONArray requireArray(JSONObject r,String key,String action)throws Exception{
        requireOk(r,action);
        JSONArray a=r.optJSONArray(key);
        if(a==null)throw new Exception("falta array "+key);
        return a;
    }
    private static String message(Throwable t){String m=t==null?"Error desconocido":t.getMessage();return m==null||m.trim().isEmpty()?String.valueOf(t):m.trim();}

    static final class Result {
        final List<String> passed;
        final List<String> errors;
        final long elapsedMs;
        Result(List<String> passed,List<String> errors,long elapsedMs){this.passed=passed;this.errors=errors;this.elapsedMs=elapsedMs;}
        boolean ok(){return errors.isEmpty();}
        String summary(){return (ok()?"CORRECTO":"CON FALLOS")+" · "+passed.size()+" contratos OK · "+errors.size()+" fallos · "+elapsedMs+" ms";}
        String details(){StringBuilder s=new StringBuilder(summary());for(String p:passed)s.append("\n✓ ").append(p);for(String e:errors)s.append("\n✗ ").append(e);return s.toString();}
    }
}
