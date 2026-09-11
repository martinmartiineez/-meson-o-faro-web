package com.ofaro.participaciones;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Búsqueda/filtros puros para histórico y operación de reservas. */
final class ReservationSearchEngine {
    static final class Filter {
        String query="";
        String state="";
        String zone="";
        String table="";
        LocalDate from=null;
        LocalDate to=null;
        boolean includeClosed=true;
        int minPeople=0;
        int maxPeople=0;
    }

    private ReservationSearchEngine(){}

    static JSONArray filter(JSONArray raw,Filter f){
        Filter x=f==null?new Filter():f;
        List<JSONObject> out=new ArrayList<>();
        if(raw==null)raw=new JSONArray();
        for(int i=0;i<raw.length();i++){
            JSONObject r=raw.optJSONObject(i);if(r==null||!matches(r,x))continue;
            try{out.add(new JSONObject(r.toString()));}catch(Exception ignored){out.add(r);}
        }
        out.sort(Comparator.comparing(ReservationSearchEngine::sortKey));
        JSONArray result=new JSONArray();for(JSONObject r:out)result.put(r);return result;
    }

    static boolean matches(JSONObject r,Filter f){
        String query=norm(f.query);
        if(!query.isEmpty()){
            String hay=norm(join(
                    first(r,"name","nombre"),first(r,"phone","telefono"),first(r,"email","correo"),
                    first(r,"id","reservationId"),first(r,"table","mesa"),first(r,"zone","zona"),
                    first(r,"notes","observaciones"),first(r,"state","estado"),first(r,"serviceState","estadoServicio")
            ));
            String[] words=query.split("\\s+");for(String w:words)if(!w.isEmpty()&&!hay.contains(w))return false;
        }

        if(!norm(f.state).isEmpty()&&!norm(first(r,"state","estado","serviceState","estadoServicio")).contains(norm(f.state)))return false;
        if(!norm(f.zone).isEmpty()&&!norm(first(r,"zone","zona")).contains(norm(f.zone)))return false;
        if(!norm(f.table).isEmpty()&&!norm(first(r,"table","mesa")).equals(norm(f.table)))return false;

        LocalDate date=date(first(r,"date","fecha"));
        if(f.from!=null&&(date==null||date.isBefore(f.from)))return false;
        if(f.to!=null&&(date==null||date.isAfter(f.to)))return false;

        int people=intValue(r,"people","personas");
        if(f.minPeople>0&&people<f.minPeople)return false;
        if(f.maxPeople>0&&people>f.maxPeople)return false;

        if(!f.includeClosed){
            String state=norm(first(r,"state","estado"));String service=norm(first(r,"serviceState","estadoServicio"));
            if(state.contains("cancel")||state.contains("deneg")||service.contains("complet")||service.contains("no se present")||service.contains("noshow")||service.contains("no_show"))return false;
        }
        return true;
    }

    static JSONArray nextMinutes(JSONArray raw,int minutes){
        LocalDate today=LocalDate.now();java.time.LocalTime now=java.time.LocalTime.now();java.time.LocalTime until=now.plusMinutes(Math.max(1,minutes));
        JSONArray out=new JSONArray();List<JSONObject> list=new ArrayList<>();
        for(int i=0;raw!=null&&i<raw.length();i++){
            JSONObject r=raw.optJSONObject(i);if(r==null||!today.equals(date(first(r,"date","fecha"))))continue;
            try{java.time.LocalTime time=java.time.LocalTime.parse(first(r,"time","hora"));if(!time.isBefore(now)&&!time.isAfter(until)){if(!isClosed(r))list.add(r);}}catch(Exception ignored){}
        }
        list.sort(Comparator.comparing(ReservationSearchEngine::sortKey));for(JSONObject r:list)out.put(r);return out;
    }

    static int people(JSONArray raw){int total=0;for(int i=0;raw!=null&&i<raw.length();i++){JSONObject r=raw.optJSONObject(i);if(r!=null)total+=Math.max(0,intValue(r,"people","personas"));}return total;}

    private static boolean isClosed(JSONObject r){String state=norm(first(r,"state","estado")),service=norm(first(r,"serviceState","estadoServicio"));return state.contains("cancel")||state.contains("deneg")||service.contains("complet")||service.contains("no se present")||service.contains("noshow")||service.contains("no_show");}
    private static String sortKey(JSONObject r){return first(r,"date","fecha")+" "+first(r,"time","hora")+" "+first(r,"name","nombre");}
    private static LocalDate date(String value){try{return LocalDate.parse(value.trim().substring(0,10));}catch(Exception e){return null;}}
    private static int intValue(JSONObject x,String...keys){for(String k:keys)if(x.has(k))return x.optInt(k,0);return 0;}
    private static String first(JSONObject x,String...keys){for(String k:keys){Object v=x.opt(k);if(v!=null&&v!=JSONObject.NULL&&String.valueOf(v).trim().length()>0)return String.valueOf(v);}return"";}
    private static String join(String...values){StringBuilder b=new StringBuilder();for(String v:values)b.append(v==null?"":v).append(' ');return b.toString();}
    private static String norm(String value){return value==null?"":value.trim().toLowerCase(new Locale("es","ES"));}
}
