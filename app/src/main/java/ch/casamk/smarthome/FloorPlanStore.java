package ch.casamk.smarthome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** All positions are normalized to [0,1]. Private app storage, not public GitHub. */
public final class FloorPlanStore {
    public static final class Floor {
        public String id,name;
        public String imageFile;
        public JSONArray rooms=new JSONArray(),markers=new JSONArray(),walls=new JSONArray();
        public Floor(String id,String name){this.id=id;this.name=name;}
        JSONObject json() throws Exception {
            JSONObject o=new JSONObject();
            o.put("id",id);o.put("name",name);
            o.put("image",imageFile==null?"":imageFile);
            o.put("rooms",rooms);o.put("markers",markers);o.put("walls",walls);
            return o;
        }
        static Floor parse(JSONObject data){
            Floor f=new Floor(data.optString("id"),data.optString("name"));
            f.imageFile=data.optString("image","");
            f.rooms=data.optJSONArray("rooms");if(f.rooms==null)f.rooms=new JSONArray();
            f.markers=data.optJSONArray("markers");if(f.markers==null)f.markers=new JSONArray();
            f.walls=data.optJSONArray("walls");if(f.walls==null)f.walls=new JSONArray();
            return f;
        }
    }
    private final Context context;
    private final ArrayList<Floor> floors=new ArrayList<>();
    public FloorPlanStore(Context context){this.context=context.getApplicationContext();load();}
    private File folder(){
        File dir=new File(context.getFilesDir(),"floorplans");
        if(!dir.exists())dir.mkdirs();
        return dir;
    }
    private void load(){
        File file=new File(folder(),"layout.json");
        if(file.isFile())try(InputStream in=new FileInputStream(file)){
            byte[] bytes=new byte[(int)Math.min(file.length(),2_000_000)];
            int used=0,n;
            while(used<bytes.length&&(n=in.read(bytes,used,bytes.length-used))>0)used+=n;
            JSONObject root=new JSONObject(new String(bytes,0,used,StandardCharsets.UTF_8));
            JSONArray array=root.optJSONArray("floors");
            if(array!=null)for(int i=0;i<array.length();i++){
                JSONObject entry=array.optJSONObject(i);
                if(entry!=null)floors.add(Floor.parse(entry));
            }
        }catch(Exception ignored){}
        if(floors.isEmpty()){
            floors.add(new Floor("eg","Erdgeschoss"));
            floors.add(new Floor("og","Obergeschoss"));
            floors.add(new Floor("ug","Untergeschoss"));
        }
    }
    public List<Floor> all(){return new ArrayList<>(floors);}
    public Floor at(int index){return floors.get(Math.max(0,Math.min(index,floors.size()-1)));}
    public int size(){return floors.size();}
    public Floor add(String name){
        Floor f=new Floor("floor_"+System.currentTimeMillis(),name.trim());
        floors.add(f);save();return f;
    }
    public void save(){
        try{
            JSONObject o=new JSONObject();o.put("version",2);
            JSONArray floorsJson=new JSONArray();
            for(Floor f:floors)floorsJson.put(f.json());
            o.put("floors",floorsJson);
            File tmp=new File(folder(),"layout.tmp");
            try(OutputStream out=new FileOutputStream(tmp)){
                out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            File target=new File(folder(),"layout.json");
            if(target.exists()&&!target.delete())throw new IOException("Could not replace layout");
            if(!tmp.renameTo(target))throw new IOException("Could not save layout");
        }catch(Exception ex){throw new IllegalStateException("Grundriss konnte nicht gespeichert werden",ex);}
    }
    public void setImage(Floor floor,Bitmap image) throws IOException {
        if(image==null)throw new IOException("Leeres Bild");
        File output=new File(folder(),"plan_"+floor.id.replaceAll("[^a-zA-Z0-9_-]","")+".png");
        try(OutputStream out=new FileOutputStream(output)){
            if(!image.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Bild kann nicht gespeichert werden");
        }
        floor.imageFile=output.getName();save();
    }
    public Bitmap getBitmap(Floor floor){
        if(floor.imageFile==null||floor.imageFile.isEmpty())return null;
        File image=new File(folder(),new File(floor.imageFile).getName());
        if(!image.isFile())return null;
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;BitmapFactory.decodeFile(image.getPath(),options);
        options.inSampleSize=1;
        while(options.outWidth/options.inSampleSize>2400||options.outHeight/options.inSampleSize>2400)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;
        return BitmapFactory.decodeFile(image.getPath(),options);
    }
    public JSONObject exportJson() throws Exception {
        JSONObject result=new JSONObject();result.put("version",2);
        JSONArray entries=new JSONArray();
        for(Floor f:floors) {
            JSONObject obj=f.json();
            Bitmap bitmap=getBitmap(f);
            // Embed lower resolution plan images so a single backup file can restore the plan.
            if(bitmap!=null){
                if(bitmap.getWidth()>1600||bitmap.getHeight()>1600){
                    float k=Math.min(1600f/bitmap.getWidth(),1600f/bitmap.getHeight());
                    bitmap=Bitmap.createScaledBitmap(bitmap,(int)(k*bitmap.getWidth()),(int)(k*bitmap.getHeight()),true);
                }
                ByteArrayOutputStream out=new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG,85,out);
                obj.put("image_base64",android.util.Base64.encodeToString(out.toByteArray(),android.util.Base64.NO_WRAP));
            }
            entries.put(obj);
        }
        result.put("floors",entries);return result;
    }
    public void importBackup(JSONObject root) throws Exception {
        if(root.optInt("version",0)<1)throw new IOException("Kein CasaMK Backup");
        JSONArray incoming=root.getJSONArray("floors");
        if(incoming.length()<1||incoming.length()>20)throw new IOException("Ungültige Anzahl Stockwerke");
        ArrayList<Floor> restored=new ArrayList<>();
        for(int i=0;i<incoming.length();i++){
            JSONObject data=incoming.getJSONObject(i);
            Floor f=Floor.parse(data);
            String encoded=data.optString("image_base64","");
            if(!encoded.isEmpty()){
                if(encoded.length()>15_000_000)throw new IOException("Bild zu gross");
                byte[] bytes=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT);
                Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
                if(bitmap==null)throw new IOException("Defektes Grundrissbild");
                setImage(f,bitmap);
            }else f.imageFile="";
            restored.add(f);
        }
        floors.clear();floors.addAll(restored);save();
    }
}
