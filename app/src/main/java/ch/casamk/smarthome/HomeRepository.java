package ch.casamk.smarthome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class HomeRepository {
    private final String url;
    private final String token;

    public HomeRepository(String url, String token) {
        this.url = url;
        this.token = token;
    }

    public List<HaEntity> entities() throws Exception {
        String json = request("GET", "/api/states", null);
        JSONArray result = new JSONArray(json);
        List<HaEntity> entities = new ArrayList<>();
        for (int i = 0; i < result.length(); i++) {
            entities.add(HaEntity.fromJson(result.getJSONObject(i)));
        }
        return entities;
    }

    public void service(HaEntity entity, String service, JSONObject data) throws Exception {
        JSONObject payload = data == null ? new JSONObject() : data;
        payload.put("entity_id", entity.id);
        request("POST", "/api/services/" + entity.domain + "/" + service, payload.toString());
    }

    public Bitmap snapshot(HaEntity entity) throws Exception {
        if (!"camera".equals(entity.domain)) throw new IllegalArgumentException("Keine Kamera");
        HttpURLConnection conn = connect("GET", "/api/camera_proxy/" + entity.id, null);
        try {
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("Kamerabild: HTTP " + status);
            try (InputStream stream = conn.getInputStream()) {
                Bitmap image = BitmapFactory.decodeStream(stream);
                if (image == null) throw new IOException("Kein lesbares Kamerabild");
                return image;
            }
        } finally { conn.disconnect(); }
    }

    private String request(String method, String path, String body) throws Exception {
        HttpURLConnection conn = connect(method, path, body);
        try {
            int status = conn.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? conn.getInputStream() : conn.getErrorStream();
            String response = stream == null ? "" : readString(stream);
            if (status < 200 || status >= 300) {
                if (status == 401) throw new IOException("Anmeldung fehlgeschlagen (401). Token prüfen.");
                throw new IOException("Home Assistant antwortet mit HTTP " + status);
            }
            return response;
        } finally { conn.disconnect(); }
    }

    private HttpURLConnection connect(String method, String path, String body) throws Exception {
        URL endpoint = new URL(url + path);
        if (!"https".equals(endpoint.getProtocol())) throw new IOException("Nur HTTPS erlaubt");
        HttpURLConnection conn = (HttpURLConnection) endpoint.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");
        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            try (OutputStream stream = conn.getOutputStream()) {
                stream.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        return conn;
    }

    private static String readString(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
