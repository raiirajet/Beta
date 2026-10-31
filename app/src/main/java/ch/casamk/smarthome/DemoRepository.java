package ch.casamk.smarthome;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** Offline example house. These are simulated devices; never sends external commands. */
public final class DemoRepository {
    private final List<HaEntity> entities=new ArrayList<>();
    public DemoRepository(){
        add("light.wohnzimmer_decke","Wohnzimmer · Decke","on","{\"brightness\":194}");
        add("light.wohnzimmer_ambient","Wohnzimmer · Ambiente","on","{\"brightness\":118}");
        add("light.kueche_decke","Küche · Decke","off","{\"brightness\":156}");
        add("light.kueche_arbeitsplatte","Küche · Arbeitsplatte","on","{\"brightness\":255}");
        add("light.schlafzimmer","Schlafzimmer · Licht","off","{\"brightness\":144}");
        add("light.bad_spiegel","Bad · Spiegel","off","{\"brightness\":210}");
        add("light.eingang","Eingang · Licht","off","{}");
        add("light.garten","Garten · Licht","on","{}");
        add("switch.garten_pumpe","Garten · Bewässerung","off","{}");
        add("climate.fussbodenheizung_wohnzimmer","Fussbodenheizung · Wohnzimmer","heat",
                "{\"temperature\":22,\"current_temperature\":21.3,\"min_temp\":16,\"max_temp\":28,\"target_temp_step\":0.5,\"hvac_modes\":[\"off\",\"heat\"]}");
        add("climate.fussbodenheizung_bad","Fussbodenheizung · Bad","heat",
                "{\"temperature\":23,\"current_temperature\":22.2,\"min_temp\":16,\"max_temp\":28,\"target_temp_step\":0.5,\"hvac_modes\":[\"off\",\"heat\"]}");
        add("climate.fancoil_schlafzimmer","Fan-Coil · Schlafzimmer","cool",
                "{\"temperature\":21,\"current_temperature\":23.8,\"min_temp\":17,\"max_temp\":28,\"target_temp_step\":0.5,\"hvac_modes\":[\"off\",\"heat\",\"cool\",\"fan_only\"],\"fan_modes\":[\"low\",\"medium\",\"high\"],\"fan_mode\":\"medium\"}");
        add("climate.fancoil_wohnzimmer","Fan-Coil · Wohnzimmer","off",
                "{\"temperature\":22,\"current_temperature\":21.8,\"min_temp\":17,\"max_temp\":28,\"target_temp_step\":0.5,\"hvac_modes\":[\"off\",\"heat\",\"cool\",\"fan_only\"],\"fan_modes\":[\"low\",\"medium\",\"high\"],\"fan_mode\":\"low\"}");
        add("lock.haustuer","Haustür","locked","{}");
        add("camera.eingang","Eingang · Kamera","idle","{}");
        add("camera.garten","Garten · Kamera","idle","{}");
        add("sensor.aussentemperatur","Aussentemperatur","17.3","{\"unit_of_measurement\":\"°C\"}");
        add("sensor.feuchtigkeit_bad","Bad · Luftfeuchtigkeit","48","{\"unit_of_measurement\":\"%\"}");
        add("binary_sensor.fenster_wohnzimmer","Wohnzimmer · Fenster","off","{\"device_class\":\"window\"}");
        add("cover.wohnzimmer","Wohnzimmer · Rollladen","closed","{}");
        add("cover.schlafzimmer","Schlafzimmer · Rollladen","open","{}");
        add("media_player.wohnzimmer","Wohnzimmer · Musik","idle","{}");
        add("scene.alle_lichter_aus","Szene · Licht aus","scening","{}");
    }
    private void add(String id,String name,String state,String attrs){
        try{entities.add(new HaEntity(id,name,state,new JSONObject(attrs)));}
        catch(JSONException ignored){}
    }
    public List<HaEntity> entities(){return new ArrayList<>(entities);}
    public void service(HaEntity entity,String action,JSONObject data) throws JSONException{
        switch(action){
            case "turn_on": entity.state="on";
                if(data!=null&&data.has("brightness"))entity.attributes.put("brightness",data.getInt("brightness"));
                break;
            case "turn_off": entity.state="off";break;
            case "unlock":entity.state="unlocked";break;
            case "lock":entity.state="locked";break;
            case "open_cover":entity.state="open";break;
            case "close_cover":entity.state="closed";break;
            case "stop_cover":break;
            case "set_temperature":
                if(data!=null)entity.attributes.put("temperature",data.getDouble("temperature"));
                break;
            case "set_hvac_mode":
                if(data!=null)entity.state=data.getString("hvac_mode");
                break;
            case "set_fan_mode":
                if(data!=null)entity.attributes.put("fan_mode",data.getString("fan_mode"));
                break;
            case "media_play_pause":
                entity.state="playing".equals(entity.state)?"paused":"playing";break;
            case "press":break;
            default:break;
        }
    }
}
