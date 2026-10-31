package ch.casamk.smarthome;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DashboardPrefs {
    private final SharedPreferences prefs;
    public DashboardPrefs(Context context) {
        prefs = context.getSharedPreferences("casamk_dashboard_v2", Context.MODE_PRIVATE);
    }
    private List<String> read(String key, List<String> defaults) {
        String data = prefs.getString(key, null);
        if (data == null) return new ArrayList<>(defaults);
        try {
            JSONArray arr = new JSONArray(data);
            ArrayList<String> result = new ArrayList<>();
            for(int i=0;i<arr.length();i++) {
                String value = arr.optString(i, "");
                if (!value.isEmpty() && !result.contains(value)) result.add(value);
            }
            return result;
        } catch(Exception ex) {
            return new ArrayList<>(defaults);
        }
    }
    private void save(String key, List<String> values) {
        prefs.edit().putString(key, new JSONArray(values).toString()).apply();
    }
    public List<String> rooms() {
        return read("rooms", Arrays.asList("Wohnzimmer", "Küche", "Schlafzimmer", "Bad", "Eingang", "Garten"));
    }
    public void addRoom(String room) {
        String name = room == null ? "" : room.trim();
        if (name.isEmpty() || name.length()>32 || "Nicht zugeordnet".equals(name)) return;
        List<String> list=rooms();
        if(!list.contains(name)) {list.add(name);save("rooms",list);}
    }
    public boolean renameRoom(String oldName,String newName) {
        String name=newName==null?"":newName.trim();
        if(name.isEmpty() || name.length()>32 || "Nicht zugeordnet".equals(name)) return false;
        List<String> list=rooms();
        if(!list.contains(oldName)||(list.contains(name)&&!oldName.equals(name))) return false;
        int idx=list.indexOf(oldName);list.set(idx,name);save("rooms",list);
        for(String key:prefs.getAll().keySet()) {
            if(key.startsWith("entity_room:") && oldName.equals(prefs.getString(key,""))) {
                prefs.edit().putString(key,name).apply();
            }
        }
        List<String> order=ordered("room",list);
        for(int i=0;i<order.size();i++) if(order.get(i).equals(oldName))order.set(i,name);
        save("order:room",order);
        if(isFavorite("room:"+oldName)) {
            prefs.edit().putBoolean("star:room:"+oldName,false).putBoolean("star:room:"+name,true).apply();
        }
        return true;
    }
    public String room(HaEntity e, boolean demo) {
        String saved=prefs.getString("entity_room:"+e.id,null);
        if(saved!=null) return saved;
        if (!demo) return "Nicht zugeordnet";
        String id=e.id.toLowerCase(java.util.Locale.ROOT);
        if(id.contains("wohn") || id.contains("fussboden"))return "Wohnzimmer";
        if(id.contains("kuech"))return "Küche";
        if(id.contains("schlaf") || id.contains("fancoil"))return "Schlafzimmer";
        if(id.contains("garten")||id.contains("aussen"))return "Garten";
        if(id.contains("haustuer")||id.contains("eingang"))return "Eingang";
        return "Nicht zugeordnet";
    }
    public void assignRoom(String entityId,String room) {
        prefs.edit().putString("entity_room:"+entityId,room).apply();
    }
    public boolean isFavorite(String id) { return prefs.getBoolean("star:"+id,false); }
    public void setFavorite(String id,boolean value) {
        prefs.edit().putBoolean("star:"+id,value).apply();
    }
    public List<String> ordered(String section,List<String> available) {
        List<String> previous=read("order:"+section,new ArrayList<>());
        List<String> sorted=new ArrayList<>();
        for(String v:previous)if(available.contains(v)&&!sorted.contains(v))sorted.add(v);
        for(String v:available)if(!sorted.contains(v))sorted.add(v);
        return sorted;
    }
    public void move(String section,List<String> available,String id,int direction) {
        List<String> sorted=ordered(section,available);
        int from=sorted.indexOf(id),to=from+direction;
        if(from<0||to<0||to>=sorted.size())return;
        java.util.Collections.swap(sorted,from,to);
        save("order:"+section,sorted);
        if("room".equals(section))save("rooms",sorted);
    }
    public boolean hidden(String key) {return prefs.getBoolean("hide:"+key,false);}
    public void hide(String key,boolean value) {prefs.edit().putBoolean("hide:"+key,value).apply();}
    public void resetHome() {
        SharedPreferences.Editor edit=prefs.edit();
        for(String key:prefs.getAll().keySet()) {
            if(key.startsWith("order:")||key.startsWith("star:")||key.startsWith("hide:"))edit.remove(key);
        }
        edit.apply();
    }
}
