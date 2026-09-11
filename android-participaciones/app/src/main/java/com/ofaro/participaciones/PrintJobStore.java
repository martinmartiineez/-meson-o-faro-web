package com.ofaro.participaciones;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Cola/histórico local de impresión.
 * Los trabajos se guardan como archivos internos para no inflar SharedPreferences,
 * especialmente cuando una plantilla incluye imágenes en base64.
 */
final class PrintJobStore {
    static final String STATUS_QUEUED="QUEUED";
    static final String STATUS_SENDING="SENDING";
    static final String STATUS_SENT="SENT";
    static final String STATUS_ERROR="ERROR";
    private static final String DIR="print_jobs_v5";
    private static final int MAX_FILES=120;

    private PrintJobStore(){}

    static JSONObject create(AppCore core,JSONObject job)throws Exception{
        File dir=dir(core);cleanup(dir);
        long now=System.currentTimeMillis();
        JSONObject rec=new JSONObject()
                .put("id",UUID.randomUUID().toString())
                .put("createdAt",now)
                .put("updatedAt",now)
                .put("status",STATUS_QUEUED)
                .put("attempts",0)
                .put("title",job==null?"":job.optString("title",""))
                .put("templateId",job==null?"":job.optString("templateId",""))
                .put("copies",job==null?1:Math.max(1,job.optInt("copies",1)))
                .put("terminal",core.terminal())
                .put("error","")
                .put("job",job==null?new JSONObject():new JSONObject(job.toString()));
        write(new File(dir,rec.getString("id")+".json"),rec);
        return rec;
    }

    static JSONObject markSending(AppCore core,JSONObject rec)throws Exception{
        JSONObject x=copy(rec);x.put("status",STATUS_SENDING).put("updatedAt",System.currentTimeMillis()).put("attempts",x.optInt("attempts",0)+1).put("error","");save(core,x);return x;
    }

    static void markSent(AppCore core,JSONObject rec)throws Exception{
        JSONObject x=copy(rec);long now=System.currentTimeMillis();x.put("status",STATUS_SENT).put("updatedAt",now).put("finishedAt",now).put("error","");save(core,x);
    }

    static void markError(AppCore core,JSONObject rec,Throwable error){
        try{JSONObject x=copy(rec);x.put("status",STATUS_ERROR).put("updatedAt",System.currentTimeMillis()).put("error",message(error));save(core,x);}catch(Exception ignored){}
    }

    static List<JSONObject> list(AppCore core){
        File[] files=dir(core).listFiles((d,n)->n.endsWith(".json"));if(files==null)return Collections.emptyList();
        List<JSONObject> out=new ArrayList<>();for(File f:files){try{out.add(read(f));}catch(Exception ignored){}}
        out.sort((a,b)->Long.compare(b.optLong("createdAt",0),a.optLong("createdAt",0)));return out;
    }

    static JSONObject get(AppCore core,String id){
        if(id==null||id.trim().isEmpty())return null;File f=new File(dir(core),id.trim()+".json");if(!f.exists())return null;try{return read(f);}catch(Exception e){return null;}
    }

    static JSONObject job(JSONObject rec){JSONObject j=rec==null?null:rec.optJSONObject("job");try{return j==null?new JSONObject():new JSONObject(j.toString());}catch(Exception e){return new JSONObject();}}

    static void delete(AppCore core,String id){if(id==null)return;try{new File(dir(core),id+".json").delete();}catch(Exception ignored){}}

    static int pendingCount(AppCore core){int n=0;for(JSONObject x:list(core)){String s=x.optString("status","");if(STATUS_ERROR.equals(s)||STATUS_QUEUED.equals(s)||STATUS_SENDING.equals(s))n++;}return n;}

    private static void save(AppCore core,JSONObject rec)throws Exception{write(new File(dir(core),rec.getString("id")+".json"),rec);}
    private static File dir(AppCore core){File d=new File(core.context().getFilesDir(),DIR);if(!d.exists())d.mkdirs();return d;}
    private static JSONObject copy(JSONObject x){try{return new JSONObject(x==null?"{}":x.toString());}catch(Exception e){return new JSONObject();}}

    private static void cleanup(File dir){
        File[] files=dir.listFiles((d,n)->n.endsWith(".json"));if(files==null||files.length<MAX_FILES)return;
        List<File> list=new ArrayList<>();Collections.addAll(list,files);list.sort(Comparator.comparingLong(File::lastModified));
        int remove=Math.max(0,list.size()-MAX_FILES+1);for(int i=0;i<remove;i++)list.get(i).delete();
    }

    private static void write(File f,JSONObject data)throws Exception{
        File tmp=new File(f.getParentFile(),f.getName()+".tmp");byte[] bytes=data.toString().getBytes(StandardCharsets.UTF_8);
        try(FileOutputStream out=new FileOutputStream(tmp)){out.write(bytes);out.flush();}
        if(f.exists()&&!f.delete())throw new Exception("No se pudo actualizar la cola de impresión.");
        if(!tmp.renameTo(f))throw new Exception("No se pudo guardar la cola de impresión.");
    }

    private static JSONObject read(File f)throws Exception{
        StringBuilder sb=new StringBuilder();try(BufferedReader br=new BufferedReader(new InputStreamReader(new FileInputStream(f),StandardCharsets.UTF_8))){String line;while((line=br.readLine())!=null)sb.append(line);}return new JSONObject(sb.toString());
    }
    private static String message(Throwable t){String m=t==null?"Error desconocido":t.getMessage();if(m==null||m.trim().isEmpty())m=String.valueOf(t);return m.length()>400?m.substring(0,400):m;}
}
