package ch.casamk.smarthome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 2D plan import/editor:
 * raster/PDF -> edit-ready vector walls and room suggestions -> interactive HA markers.
 * No cloud upload, no invisible "AI" processing. Converter is best-effort, not CAD.
 */
public final class FloorPlanActivity extends Activity implements FloorPlanView.SelectionListener {
    private static final int PICK_PLAN=801,PICK_BACKUP=802,WRITE_BACKUP=803,TAKE_PHOTO=804;
    private FloorPlanStore store;
    private SecureConfig secret;
    private HomeRepository repo;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private List<HaEntity> devices=new ArrayList<>();
    private FloorPlanView map;
    private LinearLayout root,controls,statusStrip;
    private TextView statusText;
    private int floorIndex=0,threshold=145;
    private String mode="browse",layer="all",selectedMarkerId="",selectedRoomId="";
    private Uri cameraUri;
    private boolean vectorMode=false,busy=false,remoteOnline=false;
    private final android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable refreshJob=new Runnable(){
        @Override public void run(){
            if(secret.configured()&&!busy)loadDevices();
            timer.postDelayed(this,20000);
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        Appearance.setTheme(this,Appearance.oled(this));
        CasaUi.applyTheme(this);
        getWindow().setStatusBarColor(CasaUi.BG);
        getWindow().setNavigationBarColor(CasaUi.BG);
        store=new FloorPlanStore(this);
        secret=new SecureConfig(this);
        if(secret.configured()){
            repo=new HomeRepository(secret.url(),secret.token());
            devices=new ArrayList<>();
        } else {
            DemoRepository demo=new DemoRepository();
            devices=demo.entities();
        }
        setup();
        if(repo!=null)loadDevices();
    }
    @Override protected void onResume(){super.onResume();timer.removeCallbacks(refreshJob);timer.postDelayed(refreshJob,20000);}
    @Override protected void onPause(){timer.removeCallbacks(refreshJob);super.onPause();}
    @Override protected void onDestroy(){timer.removeCallbacks(refreshJob);executor.shutdownNow();super.onDestroy();}
    private int dp(int n){return CasaUi.dp(this,n);}
    private FloorPlanStore.Floor current(){return store.at(floorIndex);}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private TextView label(String value,int size,int color,boolean bold){
        return CasaUi.label(this,value,size,color,bold);
    }
    private void setup(){
        root=CasaUi.column(this);root.setBackgroundColor(CasaUi.BG);
        root.setPadding(0,dp(4),0,dp(5));
        LinearLayout header=CasaUi.row(this);header.setPadding(dp(16),dp(8),dp(16),dp(8));
        Button back=CasaUi.pill(this,"‹",false,this::finish);
        header.addView(back,new LinearLayout.LayoutParams(dp(45),dp(43)));
        TextView title=label("Hausgrundriss",23,CasaUi.WHITE,true);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-2,1f);tp.leftMargin=dp(12);
        header.addView(title,tp);
        Button floors=CasaUi.pill(this,"Stockwerke ▾",false,this::chooseFloor);
        header.addView(floors,new LinearLayout.LayoutParams(-2,dp(43)));
        root.addView(header);

        statusStrip=CasaUi.column(this);statusStrip.setPadding(dp(18),dp(3),dp(18),dp(8));
        statusText=label("Grundriss wird geladen",12,CasaUi.MUTED,false);
        statusStrip.addView(statusText);root.addView(statusStrip);

        map=new FloorPlanView(this);
        map.setOled(Appearance.oled(this));
        map.configure(current(),store.getBitmap(current()),this,
            id -> (repo!=null&&!remoteOnline)?null:find(id));
        root.addView(map,new LinearLayout.LayoutParams(-1,0,1f));

        controls=CasaUi.column(this);
        controls.setPadding(dp(8),dp(4),dp(8),dp(4));
        root.addView(controls,new LinearLayout.LayoutParams(-1,-2));
        setContentView(root);
        if(android.os.Build.VERSION.SDK_INT>=35){
            root.setOnApplyWindowInsetsListener((v,insets)->{
                android.graphics.Insets bars=insets.getInsets(
                    android.view.WindowInsets.Type.statusBars()|android.view.WindowInsets.Type.navigationBars());
                root.setPadding(0,bars.top,0,bars.bottom);
                return insets;
            });
        }
        toolbar();
    }
    private void toolbar(){
        controls.removeAllViews();
        statusText.setText(current().name+" · "+current().rooms.length()+" Räume · "
            +current().markers.length()+" Geräte"+(repo==null?" · DEMO":" · Home Assistant"));
        HorizontalScrollView scroll=new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout top=CasaUi.row(this);
        action(top,"Import",this::chooseImport);
        action(top,"Konvertieren",this::convert);
        action(top,"＋ Raum",this::beginRoom);
        action(top,"＋ Gerät",this::chooseEntity);
        action(top,vectorMode?"2D ✓":"2D",()->{vectorMode=!vectorMode;map.vectorOnly(vectorMode);toolbar();});
        action(top,"Ebenen",this::chooseLayer);
        scroll.addView(top);controls.addView(scroll,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout bottom=CasaUi.row(this);
        if("room".equals(mode)){
            action(bottom,"↶ Punkt",()->{map.undoPoint();toolbar();});
            action(bottom,"✓ Raum speichern",this::saveDraftRoom);
            action(bottom,"Abbrechen",this::cancelEdit);
        }else if("marker".equals(mode)||"move".equals(mode)||"vertex".equals(mode)){
            action(bottom,"Abbrechen",this::cancelEdit);
            TextView hint=label("Auf Position im Plan tippen",12,CasaUi.TEAL,true);
            bottom.addView(hint);
        }else{
            action(bottom,"−",()->map.zoomBy(.8f));
            action(bottom,"⌖",()->map.resetZoom());
            action(bottom,"＋",()->map.zoomBy(1.25f));
            action(bottom,"Export",this::exportBackup);
            action(bottom,"Mehr",this::more);
        }
        controls.addView(bottom,new LinearLayout.LayoutParams(-1,dp(48)));
    }
    private void action(LinearLayout bar,String name,Runnable click){
        Button button=CasaUi.pill(this,name,false,click);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(42));
        p.setMargins(dp(3),0,dp(3),0);
        bar.addView(button,p);
    }
    private void chooseFloor(){
        List<FloorPlanStore.Floor> all=store.all();
        String[] names=new String[all.size()+1];
        for(int i=0;i<all.size();i++)names[i]=all.get(i).name;
        names[all.size()]="＋ Stockwerk hinzufügen";
        new AlertDialog.Builder(this).setTitle("Stockwerk wählen")
            .setItems(names,(dialog,index)->{
                if(index>=all.size()){addFloor();return;}
                floorIndex=index;cancelEdit();reloadPlan();
            }).show();
    }
    private void addFloor(){
        EditText input=new EditText(this);input.setHint("z. B. Dachgeschoss");
        new AlertDialog.Builder(this).setTitle("Neues Stockwerk").setView(input)
            .setNegativeButton("Abbrechen",null).setPositiveButton("Hinzufügen",(d,w)->{
                String name=input.getText().toString().trim();
                if(name.isEmpty()||name.length()>40){toast("Name fehlt oder zu lang");return;}
                store.add(name);floorIndex=store.size()-1;reloadPlan();
            }).show();
    }
    private void reloadPlan(){
        map.configure(current(),store.getBitmap(current()),this,
            id -> (repo!=null&&!remoteOnline)?null:find(id));
        map.setMode(mode);map.setLayer(layer);map.vectorOnly(vectorMode);
        toolbar();
    }
    private void chooseImport(){
        new AlertDialog.Builder(this).setTitle("Grundriss importieren")
            .setItems(new String[]{"Bild oder PDF auswählen","Papierplan fotografieren",
                    "Leeren Grundriss verwenden"},(dialog,which)->{
                if(which==0)pickPlan();
                if(which==1)camera();
                if(which==2){
                    new AlertDialog.Builder(this).setTitle("Leerer Grundriss")
                        .setMessage("Du kannst Räume von Hand einzeichnen, auch ohne importiertes Bild. Bestehende Räume werden nicht gelöscht.")
                        .setNegativeButton("Abbrechen",null)
                        .setPositiveButton("Erstellen",(d,w)->{vectorMode=true;map.vectorOnly(true);toolbar();})
                        .show();
                }
            }).show();
    }
    private void pickPlan(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/png","image/jpeg","image/webp","application/pdf"});
        startActivityForResult(intent,PICK_PLAN);
    }
    private void camera(){
        try{
            ContentValues values=new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME,"CasaMK-Plan-"+System.currentTimeMillis()+".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
            if(android.os.Build.VERSION.SDK_INT>=29)
                values.put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/CasaMK");
            cameraUri=getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
            if(cameraUri==null)throw new IllegalStateException("Speicher nicht verfügbar");
            Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            startActivityForResult(intent,TAKE_PHOTO);
        }catch(Exception e){toast("Kamera kann nicht geöffnet werden: "+e.getMessage());}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK)return;
        if(request==PICK_PLAN&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            String type=getContentResolver().getType(uri);
            try{
                getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }catch(Exception ignored){}
            if("application/pdf".equals(type)||uri.toString().toLowerCase().endsWith(".pdf"))importPdf(uri);
            else importBitmap(uri);
        }else if(request==TAKE_PHOTO&&cameraUri!=null){importBitmap(cameraUri);}
        else if(request==WRITE_BACKUP&&data!=null&&data.getData()!=null)writeBackup(data.getData());
        else if(request==PICK_BACKUP&&data!=null&&data.getData()!=null)loadBackup(data.getData());
    }
    private Bitmap loadScaledImage(Uri uri)throws Exception{
        BitmapFactory.Options bounds=new BitmapFactory.Options();
        bounds.inJustDecodeBounds=true;
        try(InputStream stream=getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(stream,null,bounds);}
        if(bounds.outWidth<=0||bounds.outHeight<=0)throw new IllegalArgumentException("Bild kann nicht gelesen werden");
        BitmapFactory.Options opt=new BitmapFactory.Options();
        opt.inSampleSize=1;
        while(bounds.outWidth/opt.inSampleSize>2400||bounds.outHeight/opt.inSampleSize>2400)opt.inSampleSize*=2;
        Bitmap bitmap;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            bitmap=BitmapFactory.decodeStream(stream,null,opt);
        }
        if(bitmap==null)throw new IllegalArgumentException("Bild kann nicht dekodiert werden");
        int orientation=ExifInterface.ORIENTATION_NORMAL;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            orientation=new ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
        }catch(Exception ignored){}
        Matrix matrix=new Matrix();
        if(orientation==ExifInterface.ORIENTATION_ROTATE_90)matrix.postRotate(90);
        if(orientation==ExifInterface.ORIENTATION_ROTATE_180)matrix.postRotate(180);
        if(orientation==ExifInterface.ORIENTATION_ROTATE_270)matrix.postRotate(270);
        if(!matrix.isIdentity())bitmap=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);
        return bitmap;
    }
    private void importBitmap(Uri uri){
        if(busy)return;busy=true;statusText.setText("Bild wird verarbeitet…");
        executor.execute(()->{
            try{
                Bitmap image=loadScaledImage(uri);
                store.setImage(current(),image);
                runOnUiThread(()->{
                    busy=false;mode="browse";reloadPlan();
                    askConvert();
                });
            }catch(Exception ex){
                runOnUiThread(()->{busy=false;toast("Bildimport fehlgeschlagen: "+ex.getMessage());});
            }
        });
    }
    private void importPdf(Uri uri){
        if(busy)return;
        try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");
            PdfRenderer renderer=new PdfRenderer(fd)){
            int pages=renderer.getPageCount();
            if(pages>1){
                String[] choices=new String[pages];
                for(int i=0;i<pages;i++)choices[i]="Seite "+(i+1);
                new AlertDialog.Builder(this).setTitle("PDF-Seite auswählen")
                    .setItems(choices,(dlg,index)->renderPdf(uri,index)).show();
            }else renderPdf(uri,0);
        }catch(Exception e){toast("PDF kann nicht geöffnet werden: "+e.getMessage());}
    }
    private void renderPdf(Uri uri,int index){
        if(busy)return;
        busy=true;statusText.setText("PDF-Seite wird konvertiert…");
        executor.execute(()->{
            try(ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");
                PdfRenderer renderer=new PdfRenderer(fd);
                PdfRenderer.Page page=renderer.openPage(index)){
                int width=page.getWidth(),height=page.getHeight();
                float ratio=Math.min(2000f/width,2000f/height);
                int outW=Math.max(1,Math.round(width*ratio)),outH=Math.max(1,Math.round(height*ratio));
                Bitmap bmp=Bitmap.createBitmap(outW,outH,Bitmap.Config.ARGB_8888);
                bmp.eraseColor(android.graphics.Color.WHITE);
                page.render(bmp,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                store.setImage(current(),bmp);
                runOnUiThread(()->{busy=false;mode="browse";reloadPlan();askConvert();});
            }catch(Exception err){runOnUiThread(()->{busy=false;toast("PDF-Fehler: "+err.getMessage());});}
        });
    }
    private void askConvert(){
        new AlertDialog.Builder(this).setTitle("Plan automatisch umwandeln?")
            .setMessage("CasaMK erkennt offline Wandlinien und schlägt Räume vor. Das ist eine vereinfachte 2D-Konvertierung, keine zuverlässige CAD-Rekonstruktion. Räume und Positionen bitte kontrollieren.")
            .setNegativeButton("Nur Bild anzeigen",null)
            .setPositiveButton("Konvertieren",(dlg,which)->convert()).show();
    }
    private void convert(){
        if(busy)return;
        Bitmap bitmap=store.getBitmap(current());
        if(bitmap==null){toast("Zuerst einen Plan importieren");return;}
        if(current().rooms.length()>0){
            new AlertDialog.Builder(this).setTitle("Raumerkennung erneuern?")
                .setMessage("Erkannte und manuell eingezeichnete Raumflächen werden ersetzt. Gerätemarker bleiben erhalten.")
                .setNegativeButton("Abbrechen",null)
                .setPositiveButton("Ersetzen",(dlg,which)->runConversion(bitmap)).show();
        }else runConversion(bitmap);
    }
    private void runConversion(Bitmap bmp){
        busy=true;statusText.setText("Wände und Räume werden offline erkannt…");
        executor.execute(()->{
            try{
                PlanConverter.Result result=PlanConverter.analyze(bmp,threshold);
                runOnUiThread(()->{
                    busy=false;
                    new AlertDialog.Builder(this).setTitle("Converter-Ergebnis")
                        .setMessage(result.notes+"\n\nRaumflächen sind Vorschläge und können ungenau sein. Mit «Übernehmen» werden sie bearbeitbar.")
                        .setNegativeButton("Verwerfen",(dlg,w)->toolbar())
                        .setNeutralButton("Schwellwert ändern",(dlg,w)->chooseThreshold())
                        .setPositiveButton("Übernehmen",(dlg,w)->{
                            current().walls=result.walls;current().rooms=result.rooms;
                            store.save();vectorMode=true;reloadPlan();
                        }).show();
                });
            }catch(Exception ex){runOnUiThread(()->{busy=false;toast(ex.getMessage());});}
        });
    }
    private void chooseThreshold(){
        final String[] variants={"Dunkler Plan (100)","Standard (145)","Heller Scan (185)"};
        new AlertDialog.Builder(this).setTitle("Kontrast / Schwelle wählen")
            .setItems(variants,(dlg,which)->{threshold=new int[]{100,145,185}[which];convert();}).show();
    }
    private void chooseLayer(){
        String[] options={"Alle Geräte","Licht & Schalter","Heizung & Klima","Kameras","Sicherheit","Sensoren"};
        String[] ids={"all","light","climate","camera","security","sensor"};
        new AlertDialog.Builder(this).setTitle("Ebenenfilter")
            .setSingleChoiceItems(options,Math.max(0,java.util.Arrays.asList(ids).indexOf(layer)),(dlg,index)->{
                layer=ids[index];map.setLayer(layer);dlg.dismiss();toolbar();
            }).show();
    }
    private void beginRoom(){cancelEdit();mode="room";map.setMode("room");toolbar();
        toast("Raumecken nacheinander antippen; anschliessend «Raum speichern».");}
    private void saveDraftRoom(){
        JSONArray points=map.draftPoints();
        if(points.length()<3){toast("Mindestens drei Eckpunkte setzen");return;}
        EditText input=new EditText(this);input.setHint("z. B. Wohnzimmer");
        new AlertDialog.Builder(this).setTitle("Neuen Raum benennen").setView(input)
            .setNegativeButton("Abbrechen",null)
            .setPositiveButton("Speichern",(dlg,which)->{
                String name=input.getText().toString().trim();
                if(name.isEmpty()||name.length()>45){toast("Bitte gültigen Namen eingeben");return;}
                try{
                    JSONObject room=new JSONObject();
                    room.put("id","room_"+System.currentTimeMillis());
                    room.put("name",name);room.put("auto",false);room.put("points",points);
                    current().rooms.put(room);store.save();
                    cancelEdit();reloadPlan();
                }catch(Exception e){toast("Raum konnte nicht gespeichert werden");}
            }).show();
    }
    private void chooseEntity(){
        if(devices.isEmpty()){toast("Noch keine Geräte vorhanden");return;}
        List<HaEntity> candidates=new ArrayList<>(devices);
        String[] names=new String[candidates.size()];
        for(int i=0;i<names.length;i++)names[i]=candidates.get(i).name+" ("+candidates.get(i).domain+")";
        new AlertDialog.Builder(this).setTitle("Gerät für Grundriss wählen")
            .setItems(names,(dlg,index)->{
                selectedMarkerId=candidates.get(index).id;mode="marker";
                map.setMode("marker");toolbar();
                toast("Gewünschten Ort auf dem Grundriss antippen");
            }).show();
    }
    private void cancelEdit(){
        mode="browse";selectedMarkerId="";selectedRoomId="";
        map.setMode("browse");map.clearDraft();toolbar();
    }
    @Override public void onPoint(float x,float y){
        if("room".equals(mode)){toolbar();return;}
        if("marker".equals(mode)){
            HaEntity entity=find(selectedMarkerId);
            if(entity==null){cancelEdit();return;}
            try{
                JSONObject m=new JSONObject();
                m.put("id","marker_"+System.currentTimeMillis());m.put("entity_id",entity.id);
                m.put("label",entity.name);m.put("domain",entity.domain);
                m.put("x",x);m.put("y",y);current().markers.put(m);
                store.save();cancelEdit();map.invalidate();
            }catch(Exception e){toast("Marker konnte nicht gespeichert werden");}
        }else if("move".equals(mode)){
            JSONObject marker=marker(selectedMarkerId);
            if(marker!=null)try{
                marker.put("x",x);marker.put("y",y);store.save();
                cancelEdit();map.invalidate();
            }catch(Exception e){toast(e.getMessage());}
        }else if("vertex".equals(mode)){
            JSONObject room=room(selectedRoomId);
            if(room==null){cancelEdit();return;}
            JSONArray polygon=room.optJSONArray("points");
            if(polygon==null||polygon.length()==0){cancelEdit();return;}
            int nearest=0;double min=Double.MAX_VALUE;
            for(int i=0;i<polygon.length();i++){
                JSONArray p=polygon.optJSONArray(i);if(p==null)continue;
                double dist=Math.pow(x-p.optDouble(0),2)+Math.pow(y-p.optDouble(1),2);
                if(dist<min){min=dist;nearest=i;}
            }
            JSONArray changed=new JSONArray();changed.put(x);changed.put(y);
            try{polygon.put(nearest,changed);store.save();map.invalidate();}
            catch(Exception e){toast(e.getMessage());}
            // Keep mode active for additional corner adjustments.
            toast("Nächste Ecke verschieben oder «Abbrechen» wählen");
        }
    }
    private JSONObject marker(String id){
        for(int i=0;i<current().markers.length();i++){
            JSONObject m=current().markers.optJSONObject(i);
            if(m!=null&&id.equals(m.optString("id")))return m;
        }
        return null;
    }
    private JSONObject room(String id){
        for(int i=0;i<current().rooms.length();i++){
            JSONObject r=current().rooms.optJSONObject(i);
            if(r!=null&&id.equals(r.optString("id")))return r;
        }
        return null;
    }
    private void remove(JSONArray array,String id){
        for(int i=array.length()-1;i>=0;i--){
            JSONObject item=array.optJSONObject(i);
            if(item!=null&&id.equals(item.optString("id")))array.remove(i);
        }
        store.save();map.invalidate();toolbar();
    }
    @Override public void onMarker(String id){
        JSONObject marker=marker(id);if(marker==null)return;
        String entityId=marker.optString("entity_id");
        HaEntity entity=find(entityId);
        String[] actions={"Gerätedetails öffnen","Marker verschieben","Beschriftung ändern",
            "Gerät neu zuordnen","Marker entfernen"};
        new AlertDialog.Builder(this).setTitle(marker.optString("label","Gerät"))
            .setMessage(entity==null?"Gerät noch nicht verbunden":entity.name+" · "+entity.state)
            .setItems(actions,(dlg,index)->{
                if(index==0){
                    Intent intent=new Intent(this,DashboardActivity.class);
                    intent.putExtra("open_entity",entityId);
                    startActivity(intent);
                }
                if(index==1){
                    selectedMarkerId=id;mode="move";map.setMode("move");toolbar();
                    toast("Neue Position im Plan antippen");
                }
                if(index==2){
                    EditText input=new EditText(this);input.setText(marker.optString("label"));
                    new AlertDialog.Builder(this).setTitle("Beschriftung").setView(input)
                        .setNegativeButton("Abbrechen",null)
                        .setPositiveButton("Speichern",(d,w)->{
                            try{marker.put("label",input.getText().toString().trim());
                                store.save();map.invalidate();}
                            catch(Exception e){toast(e.getMessage());}
                        }).show();
                }
                if(index==3){
                    String[] names=new String[devices.size()];
                    for(int i=0;i<devices.size();i++)names[i]=devices.get(i).name;
                    new AlertDialog.Builder(this).setTitle("Gerät verknüpfen")
                        .setItems(names,(d,w)->{
                            HaEntity e=devices.get(w);
                            try{
                                marker.put("entity_id",e.id);marker.put("domain",e.domain);
                                marker.put("label",e.name);store.save();map.invalidate();
                            }catch(Exception error){toast(error.getMessage());}
                        }).show();
                }
                if(index==4)new AlertDialog.Builder(this).setTitle("Marker entfernen?")
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Entfernen",(d,w)->remove(current().markers,id)).show();
            }).show();
    }
    @Override public void onRoom(String id){
        JSONObject room=room(id);if(room==null)return;
        String[] actions={"Raum öffnen","Raum umbenennen","Eckpunkt verschieben","Raum löschen"};
        new AlertDialog.Builder(this).setTitle(room.optString("name","Raum"))
            .setItems(actions,(dialog,which)->{
                if(which==0){
                    // Room markers inside polygon.
                    JSONArray polygon=room.optJSONArray("points");
                    ArrayList<String> list=new ArrayList<>();
                    ArrayList<String> ids=new ArrayList<>();
                    for(int i=0;i<current().markers.length();i++){
                        JSONObject m=current().markers.optJSONObject(i);if(m==null)continue;
                        if(FloorPlanView.contains(polygon,(float)m.optDouble("x"),(float)m.optDouble("y"))){
                            list.add(m.optString("label","Gerät"));ids.add(m.optString("id"));
                        }
                    }
                    if(list.isEmpty())toast("Diesem Raum sind noch keine Marker zugeordnet.");
                    else new AlertDialog.Builder(this).setTitle(room.optString("name"))
                        .setItems(list.toArray(new String[0]),(d,w)->onMarker(ids.get(w)))
                        .show();
                }
                if(which==1){
                    EditText input=new EditText(this);input.setText(room.optString("name"));
                    new AlertDialog.Builder(this).setTitle("Raum umbenennen").setView(input)
                        .setNegativeButton("Abbrechen",null).setPositiveButton("Speichern",(d,w)->{
                            try{room.put("name",input.getText().toString().trim());
                                store.save();map.invalidate();}
                            catch(Exception ex){toast(ex.getMessage());}
                        }).show();
                }
                if(which==2){
                    selectedRoomId=id;mode="vertex";map.setMode("vertex");toolbar();
                    toast("Tippe auf die neue Position der nächstgelegenen Raumecke");
                }
                if(which==3)new AlertDialog.Builder(this).setTitle("Raum löschen?")
                    .setNegativeButton("Abbrechen",null)
                    .setPositiveButton("Löschen",(d,w)->remove(current().rooms,id)).show();
            }).show();
    }
    private HaEntity find(String id){
        for(HaEntity e:devices)if(e.id.equals(id))return e;return null;
    }
    private void loadDevices(){
        if(repo==null||busy)return;
        executor.execute(()->{
            try{
                List<HaEntity> next=repo.entities();
                runOnUiThread(()->{
                    devices=next;remoteOnline=true;map.invalidate();statusText.setText(
                        current().name+" · "+current().rooms.length()+" Räume · "
                        +current().markers.length()+" Marker · Online");
                });
            }catch(Exception ex){
                runOnUiThread(()->{
                    remoteOnline=false;map.invalidate();
                    statusText.setText(current().name+" · Offline/Status unbekannt");
                });
            }
        });
    }
    private void more(){
        String[] actions={"Grundriss-Backup importieren","Stockwerk umbenennen",
            "Bild-/Vektoransicht wechseln","Kontrast der Erkennung einstellen"};
        new AlertDialog.Builder(this).setTitle("Planwerkzeuge")
            .setItems(actions,(dialog,index)->{
                if(index==0)importBackup();
                if(index==1){
                    EditText input=new EditText(this);input.setText(current().name);
                    new AlertDialog.Builder(this).setTitle("Stockwerk umbenennen").setView(input)
                        .setNegativeButton("Abbrechen",null)
                        .setPositiveButton("Speichern",(d,w)->{
                            current().name=input.getText().toString().trim();store.save();toolbar();
                        }).show();
                }
                if(index==2){vectorMode=!vectorMode;map.vectorOnly(vectorMode);toolbar();}
                if(index==3)chooseThreshold();
            }).show();
    }
    private void exportBackup(){
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_TITLE,"CasaMK-Grundriss-Backup.json");
        startActivityForResult(intent,WRITE_BACKUP);
    }
    private void writeBackup(Uri uri){
        if(busy)return;
        busy=true;
        executor.execute(()->{
            try{
                JSONObject data=store.exportJson();
                try(OutputStream stream=getContentResolver().openOutputStream(uri,"w")){
                    if(stream==null)throw new IllegalStateException("Ausgabedatei fehlt");
                    stream.write(data.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(()->{busy=false;toast("Grundriss-Backup exportiert");});
            }catch(Exception ex){runOnUiThread(()->{busy=false;toast(ex.getMessage());});}
        });
    }
    private void importBackup(){
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent,PICK_BACKUP);
    }
    private void loadBackup(Uri uri){
        new AlertDialog.Builder(this).setTitle("Grundriss-Backup wiederherstellen?")
            .setMessage("Alle lokalen Stockwerke, Räume und Marker werden überschrieben.")
            .setNegativeButton("Abbrechen",null)
            .setPositiveButton("Wiederherstellen",(dlg,which)->{
                if(busy)return;busy=true;
                executor.execute(()->{
                    try(InputStream in=getContentResolver().openInputStream(uri)){
                        if(in==null)throw new IllegalStateException("Datei nicht lesbar");
                        ByteArrayOutputStream out=new ByteArrayOutputStream();
                        byte[] buf=new byte[16384];int n,total=0;
                        while((n=in.read(buf))>0){
                            total+=n;if(total>30_000_000)throw new IllegalArgumentException("Backup zu gross");
                            out.write(buf,0,n);
                        }
                        store.importBackup(new JSONObject(out.toString(StandardCharsets.UTF_8.name())));
                        runOnUiThread(()->{
                            busy=false;floorIndex=0;mode="browse";reloadPlan();
                            toast("Backup wiederhergestellt");
                        });
                    }catch(Exception ex){runOnUiThread(()->{busy=false;toast(ex.getMessage());});}
                });
            }).show();
    }
}
