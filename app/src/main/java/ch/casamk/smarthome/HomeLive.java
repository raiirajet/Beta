package ch.casamk.smarthome;

import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight Home Assistant event stream. Uses native HA auth + event subscription.
 * All callbacks are on OkHttp's background thread; Activities marshal to UI thread.
 * Uses REST polling as fallback. It is never used for door unlock authorization.
 */
public final class HomeLive {
    public interface Listener {
        void onState(HaEntity entity);
        void onRemoved(String entityId);
        void onConnected(boolean connected);
    }
    private static final int SUBSCRIPTION_ID=200;
    private final OkHttpClient client;
    private volatile WebSocket socket;
    private volatile boolean stopped=false;
    private final Listener listener;

    public HomeLive(String baseUrl,String bearerToken,Listener listener){
        this.listener=listener;
        client=new OkHttpClient.Builder()
            .connectTimeout(12,TimeUnit.SECONDS)
            .readTimeout(0,TimeUnit.MILLISECONDS)
            .pingInterval(30,TimeUnit.SECONDS)
            .build();
        if(!baseUrl.startsWith("https://"))throw new IllegalArgumentException("HTTPS erforderlich");
        String wsUrl="wss://"+baseUrl.substring(8).replaceAll("/+$","")+"/api/websocket";
        Request request=new Request.Builder().url(wsUrl).build();
        socket=client.newWebSocket(request,new WebSocketListener(){
            @Override public void onMessage(WebSocket webSocket,String text){
                if(stopped)return;
                try{
                    JSONObject msg=new JSONObject(text);
                    String type=msg.optString("type");
                    if("auth_required".equals(type)){
                        JSONObject auth=new JSONObject();
                        auth.put("type","auth");
                        auth.put("access_token",bearerToken);
                        webSocket.send(auth.toString());
                    }else if("auth_ok".equals(type)){
                        JSONObject subscribe=new JSONObject();
                        subscribe.put("id",SUBSCRIPTION_ID);
                        subscribe.put("type","subscribe_events");
                        subscribe.put("event_type","state_changed");
                        webSocket.send(subscribe.toString());
                    }else if("auth_invalid".equals(type)){
                        listener.onConnected(false);
                        webSocket.close(1000,"Authentication failed");
                    }else if("result".equals(type) && msg.optInt("id")==SUBSCRIPTION_ID){
                        boolean success=msg.optBoolean("success",false);
                        listener.onConnected(success);
                        if(!success)webSocket.close(1000,"Subscription not allowed");
                    }else if("event".equals(type) && msg.optInt("id")==SUBSCRIPTION_ID){
                        JSONObject event=msg.optJSONObject("event");
                        JSONObject data=event==null?null:event.optJSONObject("data");
                        if(data==null)return;
                        JSONObject state=data.optJSONObject("new_state");
                        if(state!=null){
                            listener.onState(HaEntity.fromJson(state));
                        }else{
                            String entityId=data.optString("entity_id","");
                            if(!entityId.isEmpty())listener.onRemoved(entityId);
                        }
                    }
                }catch(Exception ignored){
                    // Ignore malformed events, never log access tokens.
                }
            }
            @Override public void onFailure(WebSocket ws,Throwable error,Response response){
                if(!stopped)listener.onConnected(false);
            }
            @Override public void onClosed(WebSocket ws,int code,String reason){
                if(!stopped)listener.onConnected(false);
            }
        });
    }
    public void close(){
        stopped=true;
        WebSocket active=socket;
        if(active!=null)active.close(1000,"App paused");
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }
}
