package ch.casamk.smarthome;

import android.content.Context;
import android.content.SharedPreferences;

public final class Appearance {
    private static final String FILE="casamk_appearance_v1";
    private Appearance(){}
    private static SharedPreferences preferences(Context c){return c.getSharedPreferences(FILE,Context.MODE_PRIVATE);}
    public static boolean oled(Context c){return "oled".equals(preferences(c).getString("theme","oled"));}
    public static void setTheme(Context c, boolean oled){preferences(c).edit().putString("theme",oled?"oled":"white").apply();}
    public static String start(Context c){return preferences(c).getString("start","combined");}
    public static void setStart(Context c,String start){
        if(!("tiles".equals(start)||"plan".equals(start)||"combined".equals(start)))return;
        preferences(c).edit().putString("start",start).apply();
    }
}