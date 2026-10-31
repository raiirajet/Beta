package ch.casamk.smarthome;

import java.util.Arrays;
import java.util.List;

public final class ModuleRegistry {
    public static final String ALL = "Alle";
    public static final String LIGHT = "Licht";
    public static final String HEATING = "Heizung";
    public static final String SECURITY = "Sicherheit";
    public static final String CAMERAS = "Kameras";
    public static final String SENSORS = "Sensoren";
    public static final String MORE = "Weitere";
    public static final List<String> CATEGORIES = Arrays.asList(
        ALL, LIGHT, HEATING, SECURITY, CAMERAS, SENSORS, MORE);

    private ModuleRegistry() {}

    public static String category(String domain) {
        switch (domain) {
            case "light": case "switch": return LIGHT;
            case "climate": case "water_heater": case "fan": return HEATING;
            case "lock": case "binary_sensor": case "cover": case "alarm_control_panel": return SECURITY;
            case "camera": return CAMERAS;
            case "sensor": return SENSORS;
            default: return MORE;
        }
    }

    public static String symbol(String domain) {
        switch (domain) {
            case "light": return "☀";
            case "switch": return "⏻";
            case "climate": return "♨";
            case "fan": return "◌";
            case "water_heater": return "♨";
            case "lock": return "⌑";
            case "binary_sensor": return "◉";
            case "cover": return "▤";
            case "camera": return "◉";
            case "sensor": return "◈";
            default: return "◇";
        }
    }
}
