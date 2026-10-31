package ch.casamk.smarthome;

import org.json.JSONException;
import org.json.JSONObject;

public final class HaEntity {
    public final String id;
    public final String domain;
    public final String name;
    public String state;
    public final JSONObject attributes;

    public HaEntity(String id, String name, String state, JSONObject attributes) {
        this.id = id;
        int dot = id.indexOf('.');
        this.domain = dot > 0 ? id.substring(0, dot) : "unknown";
        this.name = name;
        this.state = state;
        this.attributes = attributes != null ? attributes : new JSONObject();
    }

    public static HaEntity fromJson(JSONObject obj) throws JSONException {
        String id = obj.getString("entity_id");
        JSONObject attrs = obj.optJSONObject("attributes");
        if (attrs == null) attrs = new JSONObject();
        String fallbackName = id.contains(".") ? id.substring(id.indexOf('.') + 1).replace('_', ' ') : id;
        return new HaEntity(id, attrs.optString("friendly_name", fallbackName),
                obj.optString("state", "unknown"), attrs);
    }

    public boolean isUnavailable() {
        return "unknown".equals(state) || "unavailable".equals(state);
    }
}
