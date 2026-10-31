package ch.casamk.smarthome;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class DemoRepository {
    private final List<HaEntity> entities = new ArrayList<>();

    public DemoRepository() {
        add("light.wohnzimmer", "Wohnzimmer", "on", "{}");
        add("light.kueche", "Küche", "off", "{}");
        add("switch.garten_pumpe", "Gartenpumpe", "off", "{}");
        add("climate.fussbodenheizung_wohnen", "Fussbodenheizung – Wohnen", "heat", "{\"temperature\":22,\"current_temperature\":20.5,\"min_temp\":16,\"max_temp\":28,\"target_temp_step\":0.5,\"hvac_modes\":[\"off\",\"heat\"]}");
        add("climate.fancoil_schlafzimmer", "Fan-Coil – Schlafzimmer", "off", "{\"temperature\":21,\"current_temperature\":21.4,\"min_temp\":17,\"max_temp\":28,\"target_temp_step\":0.5,\"hvac_modes\":[\"off\",\"heat\",\"cool\",\"fan_only\"],\"fan_modes\":[\"low\",\"medium\",\"high\"],\"fan_mode\":\"low\"}");
        add("lock.haustuer", "Haustür", "locked", "{}");
        add("camera.eingang", "Eingangskamera", "idle", "{}");
        add("sensor.aussentemperatur", "Aussentemperatur", "17.3", "{\"unit_of_measurement\":\"°C\"}");
        add("binary_sensor.fenster", "Fenster Wohnzimmer", "off", "{\"device_class\":\"window\"}");
        add("cover.wohnzimmer", "Rollladen", "closed", "{}");
        add("media_player.wohnzimmer", "Musikanlage", "idle", "{}");
    }

    private void add(String id, String name, String state, String attrs) {
        try { entities.add(new HaEntity(id, name, state, new JSONObject(attrs))); }
        catch (JSONException ignored) { }
    }

    public List<HaEntity> entities() { return new ArrayList<>(entities); }

    public void service(HaEntity entity, String action, JSONObject data) throws JSONException {
        if ("turn_on".equals(action)) entity.state = "on";
        else if ("turn_off".equals(action)) entity.state = "off";
        else if ("unlock".equals(action)) entity.state = "unlocked";
        else if ("lock".equals(action)) entity.state = "locked";
        else if ("open_cover".equals(action)) entity.state = "open";
        else if ("close_cover".equals(action)) entity.state = "closed";
        else if ("set_temperature".equals(action) && data != null) {
            entity.attributes.put("temperature", data.getDouble("temperature"));
        } else if ("set_hvac_mode".equals(action) && data != null) {
            entity.state = data.getString("hvac_mode");
        } else if ("set_fan_mode".equals(action) && data != null) {
            entity.attributes.put("fan_mode", data.getString("fan_mode"));
        }
    }
}
