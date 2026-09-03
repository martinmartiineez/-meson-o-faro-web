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

    /**
     * Una promoción está lista solo si:
     * - tiene reparto activo;
     * - los porcentajes activos suman exactamente 100 %;
     * - existe al menos un premio real (no P000 / SIN_PREMIO).
     */
    static boolean distributionReady(JSONObject detail){
        if(detail==null)return false;
        JSONObject c=detail.optJSONObject("campaign");if(c==null)return false;
        String type=c.optString("type","");
        if("Ruleta".equalsIgnoreCase(type)){
            JSONArray a=detail.optJSONArray("segments");
            return validDistribution(a,true);
        }
        JSONArray a=detail.optJSONArray("prizeLinks");
        return validDistribution(a,false);
    }

    private static boolean validDistribution(JSONArray items,boolean wheel){
        if(items==null||items.length()==0)return false;
        double total=0d;boolean realPrize=false;int activeCount=0;
        for(int i=0;i<items.length();i++){
            JSONObject o=items.optJSONObject(i);if(o==null||!o.optBoolean("active",true))continue;
            double pct=o.optDouble("percentage",0d);
            if(pct<=0d)return false;
            activeCount++;total+=pct;
            String prizeId=o.optString("prizeId","").trim();
            String result=o.optString("resultType","").trim();
            if(!prizeId.isEmpty()&&!"P000".equalsIgnoreCase(prizeId))realPrize=true;
            if(wheel&&"PREMIO".equalsIgnoreCase(result)&&!"P000".equalsIgnoreCase(prizeId))realPrize=true;
        }
        return activeCount>0&&realPrize&&Math.abs(total-100d)<0.01;
    }

    static boolean basicsReady(JSONObject c){
        if(c==null)return false;
        String name=c.optString("name","").trim();
        String type=c.optString("type","").trim();
        return !name.isEmpty()&&("Ruleta".equals(type)||"Rasca".equals(type)||"Premio directo".equals(type)||"Código premiado".equals(type));
    }

    static double sum(JSONArray a,String field){double total=0;if(a==null)return total;for(int i=0;i<a.length();i++){JSONObject o=a.optJSONObject(i);if(o!=null&&o.optBoolean("active",true))total+=o.optDouble(field,0);}return Math.round(total*1000d)/1000d;}
    static int clampInt(int value,int min,int max){return Math.max(min,Math.min(max,value));}
}
