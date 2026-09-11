package com.ofaro.participaciones;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Cálculos puros para el dashboard: reservas, ocupación y promociones. */
final class V5StatsEngine {
    enum Period { TODAY, LAST_7_DAYS, LAST_30_DAYS, MONTH, CUSTOM }

    static final class Range {
        final LocalDate start,end,previousStart,previousEnd;
        Range(LocalDate start,LocalDate end){
            this.start=start;this.end=end;
            long days=Math.max(1,java.time.temporal.ChronoUnit.DAYS.between(start,end)+1);
            this.previousEnd=start.minusDays(1);
            this.previousStart=previousEnd.minusDays(days-1);
        }
    }

    static final class ReservationStats {
        int reservations,people,confirmed,pending,cancelled,noShow,completed,interior,terrace;
        String peakHour="—";
        double avgPartySize;
        double reservationChangePct;
        double peopleChangePct;
        final Map<String,Integer> byDay=new LinkedHashMap<>();
        final Map<String,Integer> peopleByDay=new LinkedHashMap<>();
        final Map<String,Integer> byHour=new LinkedHashMap<>();
        final Map<String,Integer> byZone=new LinkedHashMap<>();
    }

    static final class PromotionStats {
        int plays,winners,redeemed,withoutPrize;
        double winRate,redeemRate,playsChangePct;
        final Map<String,Integer> byDay=new LinkedHashMap<>();
        final Map<String,Integer> prizes=new LinkedHashMap<>();
    }

    private V5StatsEngine(){}

    static Range range(Period period,LocalDate customStart,LocalDate customEnd){
        LocalDate today=LocalDate.now();
        if(period==Period.LAST_7_DAYS)return new Range(today.minusDays(6),today);
        if(period==Period.LAST_30_DAYS)return new Range(today.minusDays(29),today);
        if(period==Period.MONTH)return new Range(today.withDayOfMonth(1),today);
        if(period==Period.CUSTOM && customStart!=null && customEnd!=null){
            LocalDate a=customStart.isAfter(customEnd)?customEnd:customStart;
            LocalDate b=customStart.isAfter(customEnd)?customStart:customEnd;
            return new Range(a,b);
        }
        return new Range(today,today);
    }

    static ReservationStats reservations(JSONArray items,Range range){
        ReservationStats current=reservationsFor(items,range.start,range.end);
        ReservationStats previous=reservationsFor(items,range.previousStart,range.previousEnd);
        current.reservationChangePct=change(current.reservations,previous.reservations);
        current.peopleChangePct=change(current.people,previous.people);
        return current;
    }

    private static ReservationStats reservationsFor(JSONArray items,LocalDate start,LocalDate end){
        ReservationStats s=new ReservationStats();
        if(items==null)items=new JSONArray();
        for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1)){String key=d.toString();s.byDay.put(key,0);s.peopleByDay.put(key,0);}
        for(int i=0;i<items.length();i++){
            JSONObject r=items.optJSONObject(i);if(r==null)continue;
            LocalDate date=parseDate(first(r,"date","fecha"));if(date==null||date.isBefore(start)||date.isAfter(end))continue;
            s.reservations++;
            int people=Math.max(0,intValue(r,"people","personas"));s.people+=people;
            String state=norm(first(r,"state","estado"));String service=norm(first(r,"serviceState","estadoServicio"));
            if(state.contains("confirm"))s.confirmed++;else if(state.contains("pend"))s.pending++;
            if(state.contains("cancel")||state.contains("deneg"))s.cancelled++;
            if(service.contains("no se present")||service.contains("no_show")||service.contains("noshow"))s.noShow++;
            if(service.contains("complet"))s.completed++;
            String zone=first(r,"zone","zona").trim();String zn=norm(zone);
            if(zn.contains("terra")){s.terrace++;add(s.byZone,zone.isEmpty()?"Terraza":zone,1);}else if(zn.contains("interior")||zn.contains("sal")){s.interior++;add(s.byZone,zone.isEmpty()?"Interior":zone,1);}else if(!zone.isEmpty())add(s.byZone,zone,1);
            String day=date.toString();add(s.byDay,day,1);add(s.peopleByDay,day,people);
            String hour=hourBucket(first(r,"time","hora"));if(!hour.isEmpty())add(s.byHour,hour,1);
        }
        s.avgPartySize=s.reservations==0?0:s.people/(double)s.reservations;
        int best=-1;for(Map.Entry<String,Integer> e:s.byHour.entrySet())if(e.getValue()>best){best=e.getValue();s.peakHour=e.getKey();}
        return s;
    }

    static PromotionStats promotions(JSONArray history,Range range){
        PromotionStats current=promotionsFor(history,range.start,range.end);
        PromotionStats previous=promotionsFor(history,range.previousStart,range.previousEnd);
        current.playsChangePct=change(current.plays,previous.plays);
        return current;
    }

    private static PromotionStats promotionsFor(JSONArray history,LocalDate start,LocalDate end){
        PromotionStats s=new PromotionStats();if(history==null)history=new JSONArray();
        for(LocalDate d=start;!d.isAfter(end);d=d.plusDays(1))s.byDay.put(d.toString(),0);
        for(int i=0;i<history.length();i++){
            JSONObject x=history.optJSONObject(i);if(x==null)continue;
            LocalDate date=parseDateFlex(first(x,"date","createdAt","created","fecha","timestamp"));if(date==null||date.isBefore(start)||date.isAfter(end))continue;
            String event=norm(first(x,"event","type","action","kind"));
            String state=norm(first(x,"state","status","resultState","estado"));
            boolean play=event.contains("jugad")||event.contains("play")||state.contains("ganado")||state.contains("sin_premio")||state.contains("sin premio")||x.has("hasPrize")||x.has("prize");
            boolean redeemed=event.contains("canje")||state.contains("canjead")||boolValue(x,"redeemed","canjeado");
            boolean winner=boolValue(x,"hasPrize","winner","ganador")||state.contains("ganado")||(!first(x,"prize","prizeName","premio").trim().isEmpty()&&!state.contains("sin"));
            if(play){s.plays++;add(s.byDay,date.toString(),1);if(winner)s.winners++;else s.withoutPrize++;}
            if(redeemed)s.redeemed++;
            String prize=first(x,"prize","prizeName","premio").trim();if(winner&&!prize.isEmpty())add(s.prizes,prize,1);
        }
        s.winRate=s.plays==0?0:s.winners*100d/s.plays;
        s.redeemRate=s.winners==0?0:s.redeemed*100d/s.winners;
        return s;
    }

    static String percent(double value){
        if(Double.isInfinite(value))return value>0?"+∞ %":"—";
        if(Double.isNaN(value))return"—";
        return String.format(new Locale("es","ES"),"%+.0f %%",value);
    }

    private static double change(int current,int previous){if(previous==0)return current==0?0:Double.POSITIVE_INFINITY;return(current-previous)*100d/previous;}
    private static void add(Map<String,Integer> map,String key,int value){if(key==null||key.trim().isEmpty())return;map.put(key,map.getOrDefault(key,0)+value);}
    private static String hourBucket(String raw){try{LocalTime t=LocalTime.parse(raw.trim(),DateTimeFormatter.ofPattern("H:mm"));return String.format(Locale.ROOT,"%02d:00",t.getHour());}catch(Exception e){return"";}}
    private static LocalDate parseDate(String raw){try{return LocalDate.parse(raw.trim().substring(0,10));}catch(Exception e){return null;}}
    private static LocalDate parseDateFlex(String raw){
        if(raw==null)return null;String s=raw.trim();if(s.isEmpty())return null;
        if(s.length()>=10){LocalDate d=parseDate(s);if(d!=null)return d;}
        String[] patterns={"dd/MM/yyyy","d/M/yyyy","dd-MM-yyyy","d-M-yyyy"};
        for(String p:patterns)try{return LocalDate.parse(s,DateTimeFormatter.ofPattern(p));}catch(DateTimeParseException ignored){}
        return null;
    }
    private static String first(JSONObject x,String...keys){if(x==null)return"";for(String k:keys){Object v=x.opt(k);if(v!=null&&v!=JSONObject.NULL&&String.valueOf(v).trim().length()>0)return String.valueOf(v);}return"";}
    private static int intValue(JSONObject x,String...keys){for(String k:keys)if(x!=null&&x.has(k))return x.optInt(k,0);return 0;}
    private static boolean boolValue(JSONObject x,String...keys){for(String k:keys)if(x!=null&&x.has(k)){Object v=x.opt(k);if(v instanceof Boolean)return(Boolean)v;String s=norm(String.valueOf(v));return"true".equals(s)||"1".equals(s)||"sí".equals(s)||"si".equals(s)||"yes".equals(s);}return false;}
    private static String norm(String value){return value==null?"":value.trim().toLowerCase(new Locale("es","ES"));}
}
