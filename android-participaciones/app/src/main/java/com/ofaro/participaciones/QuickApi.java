package com.ofaro.participaciones;

import org.json.JSONObject;

/** Compatibilidad: toda la red pasa por AppCore y su política única. */
final class QuickApi {
    private QuickApi() {}
    static JSONObject post(AppCore core,JSONObject body)throws Exception{return core.post(body);}
}
