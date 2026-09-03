package com.ofaro.participaciones;

import org.json.JSONArray;
import org.json.JSONObject;

final class PromoRules {
    private PromoRules() {}

    static String cleanCode(String raw){
        String s=raw==null?"":raw.trim().toUpperCase();
        int p=s.indexOf("OFARO:PROMO:");if(p>=0)s=s.substring(p+12);
        if(s.startsWith("OF-")&&s.length()>=13)return s.split("[?&#\\s]")[0];
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("OF-[A-Z0-9]{5}-[A-Z0-9]{5}").matcher(s);
        return m.find()?m.group():s;
    }

    static boolean distributionReady(JSONObject detail){
        if(detail==null)return false;
        JSONObject c=detail.optJSONObject("campaign");if(c==null)return false;
        String type=c.optString("type","");
        if("Ruleta".equalsIgnoreCase(type)){
            JSONArray a=detail.optJSONArray("segments");
            return a!=null&&a.length()>0&&Math.abs(sum(a,"percentage")-100d)<0.01;
        }
        JSONArray a=detail.optJSONArray("prizeLinks");
        return a!=null&&a.length()>0&&Math.abs(sum(a,"percentage")-100d)<0.01;
    }

    static boolean basicsReady(JSONObject c){return c!=null&&!c.optString("name","").trim().isEmpty()&&!c.optString("type","").trim().isEmpty();}
    static double sum(JSONArray a,String field){double total=0;if(a==null)return total;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optBoolean("active",true))total+=o.optDouble(field,0);}return Math.round(total*1000d)/1000d;}
    static int clampInt(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
