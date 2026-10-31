package ch.casamk.smarthome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Vector overlays stay attached to normalized world positions across all zoom levels. */
public final class FloorPlanView extends View {
    public interface SelectionListener {
        void onPoint(float x,float y);
        void onMarker(String id);
        void onRoom(String id);
    }
    public interface StateProvider {
        HaEntity find(String entityId);
    }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector zoomDetector;
    private final GestureDetector gestureDetector;
    private final List<float[]> draft=new ArrayList<>();
    private Bitmap image;
    private FloorPlanStore.Floor floor;
    private SelectionListener listener;
    private StateProvider states;
    private String mode="browse";
    private String layer="all";
    private boolean vectorOnly=false,oled=true;
    private float zoom=1f,panX=0f,panY=0f,lastX,lastY;
    private boolean dragging=false,wasScaling=false;
    private float drawLeft,drawTop,drawWidth,drawHeight;

    public FloorPlanView(Context context){
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE,null);
        zoomDetector=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector){
                float before=zoom;
                zoom=Math.max(0.6f,Math.min(9f,zoom*detector.getScaleFactor()));
                // Keep the focus under the fingers during zoom.
                float ratio=zoom/before;
                panX=detector.getFocusX()-(detector.getFocusX()-panX)*ratio;
                panY=detector.getFocusY()-(detector.getFocusY()-panY)*ratio;
                invalidate();return true;
            }
        });
        gestureDetector=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent e){return true;}
            @Override public boolean onDoubleTap(MotionEvent e){
                if(zoom>2.5f)resetZoom();else{
                    float before=zoom;zoom=Math.min(9f,zoom*2f);float ratio=zoom/before;
                    panX=e.getX()-(e.getX()-panX)*ratio;
                    panY=e.getY()-(e.getY()-panY)*ratio;invalidate();
                }
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e){
                tap(e.getX(),e.getY());return true;
            }
        });
    }
    public void configure(FloorPlanStore.Floor floor,Bitmap image,SelectionListener listener,StateProvider provider){
        this.floor=floor;this.image=image;this.listener=listener;this.states=provider;
        draft.clear();resetZoom();
    }
    public void setMode(String mode){this.mode=mode;invalidate();}
    public String mode(){return mode;}
    public void setLayer(String layer){this.layer=layer;invalidate();}
    public void vectorOnly(boolean only){this.vectorOnly=only;invalidate();}
    public boolean vectorOnly(){return vectorOnly;}
    public void setOled(boolean oled){this.oled=oled;invalidate();}
    public int draftCount(){return draft.size();}
    public JSONArray draftPoints(){
        JSONArray points=new JSONArray();
        for(float[] p:draft){
            try{
                JSONArray a=new JSONArray();a.put((double)p[0]);a.put((double)p[1]);points.put(a);
            }catch(org.json.JSONException ignored){}
        }
        return points;
    }
    public void clearDraft(){draft.clear();invalidate();}
    public void undoPoint(){if(!draft.isEmpty())draft.remove(draft.size()-1);invalidate();}
    public void resetZoom(){zoom=1f;panX=0;panY=0;invalidate();}
    public void zoomBy(float factor){zoom=Math.max(0.6f,Math.min(9f,zoom*factor));invalidate();}
    private int dp(int n){return Math.round(getResources().getDisplayMetrics().density*n);}
    private void color(int argb){paint.reset();paint.setAntiAlias(true);paint.setColor(argb);}
    private int ink(){return oled?Color.WHITE:0xFF243349;}
    private int bg(){return oled?0xFF0B0F16:0xFFF4F7FB;}

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        canvas.drawColor(bg());
        if(floor==null)return;
        float aspect=image!=null&&image.getWidth()>0?image.getWidth()/(float)image.getHeight():1.4f;
        float usableW=Math.max(10,getWidth()-dp(24)),usableH=Math.max(10,getHeight()-dp(28));
        drawWidth=Math.min(usableW,usableH*aspect);
        drawHeight=drawWidth/aspect;
        drawLeft=(getWidth()-drawWidth)/2;
        drawTop=(getHeight()-drawHeight)/2;
        canvas.save();
        canvas.translate(panX,panY);
        canvas.scale(zoom,zoom);
        float left=drawLeft,top=drawTop;
        if(image!=null&&!vectorOnly){
            color(Color.WHITE);
            canvas.drawBitmap(image,null,new RectF(left,top,left+drawWidth,top+drawHeight),paint);
        }else{
            color(oled?0xFF152035:0xFFFFFFFF);
            canvas.drawRoundRect(new RectF(left,top,left+drawWidth,top+drawHeight),dp(2),dp(2),paint);
        }
        drawRooms(canvas);
        if(vectorOnly || image==null)drawWalls(canvas);
        drawDraft(canvas);
        drawMarkers(canvas);
        canvas.restore();
        if(image==null){
            color(oled?0xFFA4B4C5:0xFF65738A);
            paint.setTextSize(dp(15));
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("Importiere einen Plan oder starte mit einem leeren Grundriss.",
                    getWidth()/2f,getHeight()/2f,paint);
        }
    }
    private void drawRooms(Canvas canvas){
        if(floor.rooms==null)return;
        for(int i=0;i<floor.rooms.length();i++){
            JSONObject room=floor.rooms.optJSONObject(i);
            if(room==null)continue;
            JSONArray polygon=room.optJSONArray("points");
            if(polygon==null||polygon.length()<3)continue;
            Path path=asPath(polygon);
            color(vectorOnly?0x3346AFC3:0x1838A6BE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path,paint);
            color(oled?0xFF72DEC9:0xFF147B83);
            paint.setStrokeWidth(Math.max(1.2f,dp(2)/zoom));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawPath(path,paint);paint.setStyle(Paint.Style.FILL);
            float[] center=centroid(polygon);
            color(oled?0xFFD9F8EE:0xFF174A56);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(Math.max(dp(11)/zoom,dp(8)/zoom));
            canvas.drawText(room.optString("name","Raum"),atX(center[0]),atY(center[1]),paint);
        }
    }
    private void drawWalls(Canvas canvas){
        color(oled?0xFFB8CEDB:0xFF55677E);
        paint.setStrokeWidth(Math.max(1.7f,dp(2)/zoom));
        paint.setStrokeCap(Paint.Cap.SQUARE);
        if(floor.walls!=null)for(int i=0;i<floor.walls.length();i++){
            JSONObject w=floor.walls.optJSONObject(i);if(w==null)continue;
            canvas.drawLine(atX((float)w.optDouble("x1")),atY((float)w.optDouble("y1")),
                atX((float)w.optDouble("x2")),atY((float)w.optDouble("y2")),paint);
        }
    }
    private Path asPath(JSONArray points){
        Path path=new Path();
        for(int j=0;j<points.length();j++){
            JSONArray p=points.optJSONArray(j);if(p==null)continue;
            float x=atX((float)p.optDouble(0)),y=atY((float)p.optDouble(1));
            if(j==0)path.moveTo(x,y);else path.lineTo(x,y);
        }
        path.close();return path;
    }
    private float[] centroid(JSONArray points){
        float x=0,y=0;int n=0;
        for(int i=0;i<points.length();i++){JSONArray a=points.optJSONArray(i);
            if(a!=null){x+=a.optDouble(0);y+=a.optDouble(1);n++;}}
        return new float[]{n==0?0.5f:x/n,n==0?0.5f:y/n};
    }
    private void drawDraft(Canvas canvas){
        if(draft.isEmpty())return;
        color(0xFFFFBC58);paint.setStrokeWidth(Math.max(1.7f,dp(3)/zoom));
        for(int i=0;i<draft.size();i++){
            float[] point=draft.get(i);
            canvas.drawCircle(atX(point[0]),atY(point[1]),dp(4)/zoom,paint);
            if(i>0){float[] previous=draft.get(i-1);
                canvas.drawLine(atX(previous[0]),atY(previous[1]),atX(point[0]),atY(point[1]),paint);}
        }
    }
    private void drawMarkers(Canvas canvas){
        if(floor.markers==null)return;
        int size=Math.round(dp(18)/zoom);
        for(int i=0;i<floor.markers.length();i++){
            JSONObject marker=floor.markers.optJSONObject(i);if(marker==null)continue;
            String domain=marker.optString("domain","");
            if(!"all".equals(layer)&&!layer.equals(domainGroup(domain)))continue;
            float x=atX((float)marker.optDouble("x",0.5)),y=atY((float)marker.optDouble("y",0.5));
            HaEntity entity=states==null?null:states.find(marker.optString("entity_id",""));
            int stateColor=statusColor(entity);
            float radius=dp(15)/zoom;
            color(stateColor);
            canvas.drawCircle(x,y,radius+dp(3)/zoom,paint);
            color(oled?0xFF16212F:Color.WHITE);
            canvas.drawCircle(x,y,radius,paint);
            color(stateColor);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(android.graphics.Typeface.create("sans-serif",android.graphics.Typeface.BOLD));
            paint.setTextSize(dp(16)/zoom);
            String symbol=marker.optString("symbol",symbolFor(domain));
            canvas.drawText(symbol,x,y+dp(5)/zoom,paint);
            if(zoom>1.3f || floor.markers.length()<12){
                color(oled?Color.WHITE:0xFF172436);
                paint.setTextSize(dp(10)/zoom);
                String label=marker.optString("label",entity==null?"Gerät":entity.name);
                if(label.length()>19)label=label.substring(0,18)+"…";
                canvas.drawText(label,x,y-radius-dp(6)/zoom,paint);
            }
        }
    }
    private static String symbolFor(String d){
        switch(d){
            case "light":return "L";
            case "climate":return "°";
            case "camera":return "●";
            case "lock":return "⌑";
            case "switch":return "S";
            case "sensor":return "◈";
            case "cover":return "▤";
            default:return "●";
        }
    }
    private static String domainGroup(String d){
        if("light".equals(d)||"switch".equals(d))return "light";
        if("climate".equals(d)||"fan".equals(d))return "climate";
        if("camera".equals(d))return "camera";
        if("lock".equals(d)||"binary_sensor".equals(d)||"cover".equals(d))return "security";
        return "sensor";
    }
    private int statusColor(HaEntity e){
        if(e==null || e.isUnavailable())return 0xFFE6AF52;
        String state=e.state.toLowerCase(java.util.Locale.ROOT);
        if(state.contains("error")||state.contains("fault")||state.contains("problem")||
           state.contains("alarm")||e.attributes.optBoolean("problem",false))return 0xFFFF5F6D;
        if("climate".equals(e.domain)){
            String action=e.attributes.optString("hvac_action","").toLowerCase(java.util.Locale.ROOT);
            if("heating".equals(action)||"cooling".equals(action))return 0xFF33D7A0;
            if("idle".equals(action)||"off".equals(state))return 0xFF99A3AD;
            return 0xFFE6AF52; // HVAC mode alone does not prove active operation.
        }
        if("on".equals(state)||"open".equals(state)||"unlocked".equals(state)||
           "playing".equals(state)||"heating".equals(state))return 0xFF33D7A0;
        if("off".equals(state)||"locked".equals(state)||"closed".equals(state)||
           "idle".equals(state)||"paused".equals(state))return 0xFF99A3AD;
        if("sensor".equals(e.domain))return 0xFF80AFFF;
        return 0xFFE6AF52;
    }
    private float atX(float x){return drawLeft+drawWidth*x;}
    private float atY(float y){return drawTop+drawHeight*y;}
    private void tap(float tx,float ty){
        if(listener==null||floor==null)return;
        float wx=(tx-panX)/zoom,wy=(ty-panY)/zoom;
        float x=(wx-drawLeft)/Math.max(1,drawWidth),y=(wy-drawTop)/Math.max(1,drawHeight);
        if(x<0||x>1||y<0||y>1)return;
        if("room".equals(mode)){draft.add(new float[]{x,y});invalidate();listener.onPoint(x,y);return;}
        if("marker".equals(mode)||"move".equals(mode)){listener.onPoint(x,y);return;}
        for(int i=floor.markers.length()-1;i>=0;i--){
            JSONObject m=floor.markers.optJSONObject(i);if(m==null)continue;
            float mx=(float)m.optDouble("x"),my=(float)m.optDouble("y");
            float radius=dp(24)/(Math.max(1f,zoom)*Math.max(1,drawWidth));
            if(Math.hypot((x-mx)*drawWidth,(y-my)*drawHeight)<dp(25)/zoom){
                listener.onMarker(m.optString("id"));return;
            }
        }
        for(int i=0;i<floor.rooms.length();i++){
            JSONObject r=floor.rooms.optJSONObject(i);if(r==null)continue;
            if(contains(r.optJSONArray("points"),x,y)){listener.onRoom(r.optString("id"));return;}
        }
    }
    static boolean contains(JSONArray points,float x,float y){
        if(points==null||points.length()<3)return false;
        boolean inside=false;int n=points.length();
        for(int i=0,j=n-1;i<n;j=i++){
            JSONArray ai=points.optJSONArray(i),aj=points.optJSONArray(j);
            if(ai==null||aj==null)continue;
            double xi=ai.optDouble(0),yi=ai.optDouble(1),xj=aj.optDouble(0),yj=aj.optDouble(1);
            if((yi>y)!=(yj>y) && x<(xj-xi)*(y-yi)/(yj-yi)+xi)inside=!inside;
        }
        return inside;
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        zoomDetector.onTouchEvent(event);
        gestureDetector.onTouchEvent(event);
        int action=event.getActionMasked();
        switch(action){
            case MotionEvent.ACTION_DOWN:
                lastX=event.getX();lastY=event.getY();dragging=false;wasScaling=false;return true;
            case MotionEvent.ACTION_POINTER_DOWN:wasScaling=true;return true;
            case MotionEvent.ACTION_MOVE:
                if(event.getPointerCount()>1){wasScaling=true;return true;}
                if(zoomDetector.isInProgress())return true;
                float x=event.getX(),y=event.getY();
                if(Math.abs(x-lastX)>dp(2)||Math.abs(y-lastY)>dp(2)){
                    dragging=true;panX+=x-lastX;panY+=y-lastY;invalidate();
                }
                lastX=x;lastY=y;return true;
            case MotionEvent.ACTION_UP:
                if(dragging||wasScaling){dragging=false;return true;}
                return true;
            case MotionEvent.ACTION_CANCEL:dragging=false;return true;
        }
        return true;
    }
}
