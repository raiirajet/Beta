package ch.casamk.smarthome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.InputType;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(16,24,40);
    private static final int SURFACE = Color.rgb(31,43,61);
    private static final int FG = Color.WHITE;
    private static final int SUB = Color.rgb(173,187,205);
    private static final int ACCENT = Color.rgb(119,215,173);
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private SecureConfig config;
    private DemoRepository demo;
    private HomeRepository home;
    private boolean demoMode = true;
    private String category = ModuleRegistry.ALL;
    private String status = "Demo-Modus · keine echten Geräte verbunden";
    private List<HaEntity> entities = new ArrayList<>();
    private LinearLayout content;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        config = new SecureConfig(this);
        demo = new DemoRepository();
        entities = demo.entities();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        content = new LinearLayout(this);
        content.setPadding(dp(16), dp(22), dp(16), dp(30));
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        setContentView(scroll);
        render();
        if (config.configured()) loadRemote();
    }

    private int dp(int v) { return (int)(getResources().getDisplayMetrics().density * v + 0.5f); }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(2), dp(3), dp(2), dp(5));
        return t;
    }

    private Button button(String title, Runnable handler) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextSize(12);
        b.setAllCaps(false);
        b.setTextColor(FG);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(SURFACE));
        b.setOnClickListener(v -> handler.run());
        return b;
    }

    private void addButton(LinearLayout container, String title, Runnable handler) {
        Button b = button(title, handler);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        container.addView(b, p);
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(14), dp(12), dp(14), dp(14));
        c.setBackgroundColor(SURFACE);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(5), 0, dp(9));
        content.addView(c, p);
        return c;
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        content.addView(text("CASAMK", 13, ACCENT, true));
        content.addView(text("Haus Mazedonien", 26, FG, true));
        content.addView(text(status, 12, SUB, false));

        LinearLayout controls = row();
        addButton(controls, "Demo", () -> {
            demoMode = true;
            entities = demo.entities();
            status = "Demo-Modus · keine echten Geräte verbunden";
            render();
        });
        addButton(controls, "Aktualisieren", () -> {
            if (demoMode) {
                status = "Demo-Daten aktualisiert";
                render();
            } else loadRemote();
        });
        addButton(controls, "Einstellungen", this::settings);
        content.addView(controls);

        HorizontalScrollView bar = new HorizontalScrollView(this);
        bar.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = row();
        for (String name : ModuleRegistry.CATEGORIES) {
            Button tab = button(name, () -> {
                category = name;
                render();
            });
            if (name.equals(category)) {
                tab.setTextColor(ACCENT);
            }
            tabs.addView(tab);
        }
        bar.addView(tabs);
        content.addView(bar);

        int count = 0;
        for (HaEntity entity : entities) {
            if (!category.equals(ModuleRegistry.ALL)
                    && !category.equals(ModuleRegistry.category(entity.domain))) continue;
            showEntity(entity);
            count++;
        }
        if (count == 0) content.addView(text("Noch keine Geräte in diesem Bereich.", 15, SUB, false));
        content.addView(text("Anbindung: Home Assistant HTTPS API · Version 0.1", 11, SUB, false));
    }

    private void showEntity(HaEntity e) {
        LinearLayout c = card();
        c.addView(text(ModuleRegistry.symbol(e.domain) + "   " + e.name, 17, FG, true));
        String description = e.isUnavailable() ? "Nicht verfügbar" : e.state;
        if ("climate".equals(e.domain)) {
            description += "  ·  Ist: " + e.attributes.optString("current_temperature", "?")
                    + "°C  ·  Soll: " + e.attributes.optString("temperature", "?") + "°C";
        } else if ("sensor".equals(e.domain)) {
            description += " " + e.attributes.optString("unit_of_measurement", "");
        }
        c.addView(text(description, 13, SUB, false));
        if (e.isUnavailable()) return;
        LinearLayout actions = row();
        switch (e.domain) {
            case "light":
            case "switch":
                addButton(actions, "Ein", () -> act(e, "turn_on", null));
                addButton(actions, "Aus", () -> act(e, "turn_off", null));
                break;
            case "climate":
                addButton(actions, "− 0.5°C", () -> temperature(e, -0.5));
                addButton(actions, "+ 0.5°C", () -> temperature(e, 0.5));
                break;
            case "fan":
                addButton(actions, "Ein", () -> act(e, "turn_on", null));
                addButton(actions, "Aus", () -> act(e, "turn_off", null));
                break;
            case "lock":
                addButton(actions, "Verriegeln", () -> act(e, "lock", null));
                addButton(actions, "Entriegeln", () -> unlock(e));
                break;
            case "cover":
                addButton(actions, "Öffnen", () -> act(e, "open_cover", null));
                addButton(actions, "Schliessen", () -> act(e, "close_cover", null));
                break;
            case "camera":
                addButton(actions, "Standbild", () -> camera(e));
                break;
            case "scene":
                addButton(actions, "Aktivieren", () -> act(e, "turn_on", null));
                break;
            case "button":
                addButton(actions, "Auslösen", () -> act(e, "press", null));
                break;
            default: break;
        }
        if (actions.getChildCount() > 0) c.addView(actions);

        if ("climate".equals(e.domain)) {
            JSONArray modes = e.attributes.optJSONArray("hvac_modes");
            if (modes != null) {
                LinearLayout modeRow = row();
                for (int i=0; i<modes.length(); i++) {
                    String mode = modes.optString(i);
                    if (!("off".equals(mode) || "heat".equals(mode) || "cool".equals(mode)
                            || "auto".equals(mode) || "fan_only".equals(mode))) continue;
                    addButton(modeRow, mode, () -> act(e, "set_hvac_mode", json("hvac_mode", mode)));
                }
                if (modeRow.getChildCount() > 0) c.addView(modeRow);
            }
            JSONArray fanModes = e.attributes.optJSONArray("fan_modes");
            if (fanModes != null && fanModes.length() > 0) {
                LinearLayout fanRow = row();
                for (int i=0; i<fanModes.length(); i++) {
                    String mode = fanModes.optString(i);
                    addButton(fanRow, mode, () -> act(e, "set_fan_mode", json("fan_mode", mode)));
                }
                c.addView(text("Fan-Coil Lüfterstufe", 12, SUB, false));
                c.addView(fanRow);
            }
        }
    }

    private static JSONObject json(String key, Object value) {
        JSONObject obj = new JSONObject();
        try { obj.put(key, value); } catch (Exception ignored) {}
        return obj;
    }

    private void temperature(HaEntity e, double step) {
        double min = e.attributes.optDouble("min_temp", 16);
        double max = e.attributes.optDouble("max_temp", 28);
        double current = e.attributes.optDouble("temperature", 22);
        double next = Math.min(max, Math.max(min, Math.round((current + step) * 2) / 2.0));
        act(e, "set_temperature", json("temperature", next));
    }

    private void act(HaEntity e, String command, JSONObject params) {
        if (demoMode) {
            try {
                demo.service(e, command, params);
                status = "Demo-Aktion: " + e.name;
                render();
            } catch (Exception ex) { toast(ex.getMessage()); }
            return;
        }
        if (home == null) { toast("Keine Verbindung zu Home Assistant"); return; }
        status = "Befehl wird gesendet…";
        render();
        worker.execute(() -> {
            try {
                home.service(e, command, params);
                List<HaEntity> updated = home.entities();
                runOnUiThread(() -> {
                    entities = updated;
                    status = "Verbunden · " + updated.size() + " Geräte";
                    render();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> { status = "Fehler: " + ex.getMessage(); render(); });
            }
        });
    }

    private void unlock(HaEntity e) {
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                .setTitle("Haustür entriegeln")
                .setSubtitle("Sicherheitsbestätigung für " + e.name)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG
                    | BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
            prompt.authenticate(new CancellationSignal(), getMainExecutor(),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        act(e, "unlock", null);
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence message) {
                        toast("Entriegelung abgebrochen: " + message);
                    }
                });
        } catch (Exception ex) {
            toast("Bitte sichere Gerätesperre/Biometrie aktivieren.");
        }
    }

    private void camera(HaEntity e) {
        if (demoMode) {
            new AlertDialog.Builder(this).setTitle(e.name)
                .setMessage("Kameravorschau ist im Demo-Modus nicht verfügbar.")
                .setPositiveButton("OK", null).show();
            return;
        }
        status = "Kamerabild wird geladen…"; render();
        worker.execute(() -> {
            try {
                Bitmap image = home.snapshot(e);
                runOnUiThread(() -> {
                    ImageView view = new ImageView(this);
                    view.setImageBitmap(image);
                    view.setAdjustViewBounds(true);
                    new AlertDialog.Builder(this).setTitle(e.name).setView(view)
                        .setPositiveButton("Schliessen", null).show();
                    status = "Verbunden"; render();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> { status = ex.getMessage(); render(); });
            }
        });
    }

    private void loadRemote() {
        if (!config.configured()) { settings(); return; }
        status = "Verbinde zu Home Assistant…";
        render();
        worker.execute(() -> {
            try {
                HomeRepository candidate = new HomeRepository(config.url(), config.token());
                List<HaEntity> updated = candidate.entities();
                runOnUiThread(() -> {
                    home = candidate;
                    demoMode = false;
                    entities = updated;
                    status = "Verbunden · " + updated.size() + " Geräte";
                    render();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    status = "Verbindung fehlgeschlagen: " + ex.getMessage();
                    render();
                });
            }
        });
    }

    private void settings() {
        LinearLayout panel = column();
        panel.setPadding(dp(18), 0, dp(18), 0);
        EditText server = new EditText(this);
        server.setHint("https://dein-server.example");
        server.setSingleLine(true);
        server.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        server.setText(config.url());
        panel.addView(server);
        EditText token = new EditText(this);
        token.setHint("Home Assistant Access Token");
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        panel.addView(token);
        new AlertDialog.Builder(this)
            .setTitle("Home Assistant verbinden")
            .setMessage("Nur HTTPS. Token bleibt verschlüsselt auf deinem Gerät.")
            .setView(panel)
            .setPositiveButton("Verbinden", (dialog, which) -> {
                try {
                    String t = token.getText().toString().trim();
                    if (t.isEmpty()) t = config.token();
                    config.save(server.getText().toString(), t);
                    loadRemote();
                } catch (Exception ex) { toast(ex.getMessage()); }
            })
            .setNegativeButton("Abbrechen", null)
            .setNeutralButton("Zurücksetzen", (dialog, which) -> {
                config.clear();
                home = null;
                demoMode = true;
                entities = demo.entities();
                status = "Demo-Modus · Verbindung gelöscht";
                render();
            }).show();
    }

    private void toast(String message) {
        Toast.makeText(this, message == null ? "Unbekannter Fehler" : message,
            Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
