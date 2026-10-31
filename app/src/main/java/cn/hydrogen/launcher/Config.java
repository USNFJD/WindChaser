package cn.hydrogen.launcher;

import android.content.*;
import org.json.*;
import java.util.*;

public final class Config {
    public static final String MAP = "com.autonavi.amapauto", MUSIC = "com.tencent.qqmusiccar";
    public static final String BLUETOOTH_MUSIC_COMPONENT = "bluetoothMusicComponent";
    public static final int CARD_ALPHA = 52, DOCK_ALPHA = 30;
    public final SharedPreferences p;
    private static final String[] KEYS = {"mapPackage", "musicPackage", BLUETOOTH_MUSIC_COMPONENT, "amapFloating", "autoRecoverApps", "mapShare", "cardAlpha", "radius", "border", "borderColor", "titles", "swap", "iconSize", "spacing", "fontSize", "dockAlpha", "homePage", "musicMode", "boot", "fit", "slot0", "slot1", "slot2", "slot3", "weatherCity", "weatherAuto", "weatherPlaceLevel", "quickLayout", "quickSlot0", "quickSlot1", "themeAccent", "themeSurface", "pageAnimation", "keepCompanions", "themeText", "themeClock", "themeWeather", "minimalMusicWidth", "quickWidth", "transportWidth", "clockSize", "lyricSize", "weatherPillAlpha"};
    public Config(Context c) {
        p = c.getSharedPreferences("desktop", Context.MODE_PRIVATE);
        if(!p.getBoolean("weatherStartupV2",false))p.edit().putBoolean("weatherAuto",true).putBoolean("weatherStartupV2",true).apply();
        if(!p.getBoolean("pageOrder055",false)){
            SharedPreferences.Editor e=p.edit();if(p.contains("homePage"))e.putInt("homePage",oldPage(n("homePage",0)));
            e.putString("mapPackage",MAP).remove("amap65Bridge").remove("amapFloatDetected").putBoolean("pageOrder055",true).apply();
        }
        if(!p.getBoolean("mapOnly65v5",false)){
            SharedPreferences.Editor e=p.edit().putString("mapPackage",MAP).remove("amap65Bridge").remove("amapFloatDetected").putBoolean("mapOnly65v5",true);
            if(!p.contains("themeBorder")&&p.contains("borderColor")){String[] colors={"#6E9BB4","#39BAFF","#B18CE3","#5CDBB0"};e.putString("themeBorder",colors[Math.max(0,Math.min(3,n("borderColor",0)))]);}
            e.apply();
        }
    }
    private static Set<String> keys(){Set<String> keys=new LinkedHashSet<>(Arrays.asList(KEYS));keys.addAll(ThemeOptions.keys());return keys;}
    private static int oldPage(int n){return n==0?1:n==1?2:0;}
    public void applyGlassStyle() {
        if (b("glassStyle053", false)) return;
        // Apply the requested appearance once; keep any already lighter custom values.
        p.edit().putInt("cardAlpha", Math.min(n("cardAlpha", CARD_ALPHA), CARD_ALPHA))
                .putInt("dockAlpha", Math.min(n("dockAlpha", DOCK_ALPHA), DOCK_ALPHA))
                .putBoolean("glassStyle053", true).apply();
    }
    public int n(String k, int def) { return p.getInt(k, def); }
    public boolean b(String k, boolean def) { return p.getBoolean(k, def); }
    public String s(String k, String def) { return p.getString(k, def); }
    public void put(String k, int v) { p.edit().putInt(k, v).apply(); }
    public void put(String k, boolean v) { p.edit().putBoolean(k, v).apply(); }
    public void put(String k, String v) { p.edit().putString(k, v).apply(); }
    public String pkg(boolean music) { return music?s("musicPackage",MUSIC):MAP; }
    Map<String,Object> visualState(){
        Map<String,?> all=p.getAll();Map<String,Object> result=new HashMap<>();
        for(String key:keys())if(all.containsKey(key))result.put(key,all.get(key));
        for(String key:new String[]{"wallpaperKind","mapWidget","musicWidget"})if(all.containsKey(key))result.put(key,all.get(key));
        return result;
    }
    public String exportJson() throws JSONException {
        JSONObject o = new JSONObject(); o.put("format", "hydrogen-config"); o.put("version", 1);o.put("pageOrder","minimal-cards-apps");
        JSONObject values = new JSONObject(); Map<String, ?> all = p.getAll();
        for (String k : keys()) if (all.containsKey(k)) values.put(k, all.get(k));
        o.put("settings", values); return o.toString(2);
    }
    public void restore(String text) throws JSONException {
        JSONObject root = new JSONObject(text);
        if (!"hydrogen-config".equals(root.optString("format")) || root.optInt("version") != 1) throw new JSONException("不是本版本的追风桌面配置");
        JSONObject o = root.getJSONObject("settings"); SharedPreferences.Editor e = p.edit();
        // Validate the complete document before applying any changes.
        for (String k : keys()) {
            if (!o.has(k)) continue; Object v = o.get(k);
            ThemeOptions.Option option=ThemeOptions.find(k);
            if(option!=null){
                if(option.color){if(!(v instanceof String)||(!((String)v).isEmpty()&&!ThemeColors.valid((String)v)))throw new JSONException(k+" 颜色应为 #RRGGBB");e.putString(k,(String)v);}
                else {if(!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).intValue())throw new JSONException(k+" 需要整数");int value=((Number)v).intValue();if(value<option.min||value>option.max)throw new JSONException(k+" 超出允许范围");e.putInt(k,value);}
            } else if (Arrays.asList("titles", "swap", "boot", "weatherAuto", "amapFloating", "autoRecoverApps", "pageAnimation", "keepCompanions").contains(k)) {
                if (!(v instanceof Boolean)) throw new JSONException(k + " 类型错误"); e.putBoolean(k, (Boolean)v);
            } else if(k.equals("themeAccent")||k.equals("themeSurface")||k.equals("themeText")||k.equals("themeClock")||k.equals("themeWeather")){
                if(!(v instanceof String)||!ThemeColors.valid((String)v))throw new JSONException("颜色应为 #RRGGBB");e.putString(k,(String)v);
            } else if(k.equals("weatherCity")){
                if(!(v instanceof String))throw new JSONException("城市格式错误");WeatherController.City.parse((String)v);e.putString(k,(String)v);
            } else if(k.equals("quickLayout")){
                if(!(v instanceof String))throw new JSONException("快捷区布局格式错误");QuickGrid.parse((String)v);e.putString(k,(String)v);
            } else if(k.equals(BLUETOOTH_MUSIC_COMPONENT)){
                if(!(v instanceof String)||((String)v).length()>512||(!((String)v).isEmpty()&&ComponentName.unflattenFromString((String)v)==null))throw new JSONException("蓝牙音乐关联格式错误");
                e.putString(k,(String)v);
            } else if (k.endsWith("Package") || k.startsWith("slot") || k.startsWith("quickSlot")) {
                if (!(v instanceof String) || ((String)v).length() > 512) throw new JSONException(k + " 内容错误"); e.putString(k, (String)v);
            } else {
                if (!(v instanceof Number)||((Number)v).doubleValue()!=((Number)v).intValue()) throw new JSONException(k + " 需要整数");
                int n = ((Number)v).intValue(), lo = 0, hi = 100;
                if (k.equals("mapShare")) { lo=35; hi=70; }
                if (k.equals("minimalMusicWidth")) { lo=70; hi=100; }
                if (k.equals("quickWidth")) { lo=80; hi=140; }
                if (k.equals("transportWidth")) { lo=75; hi=100; }
                if (k.equals("clockSize")) { lo=85; hi=115; }
                if (k.equals("lyricSize")) { lo=90; hi=125; }
                if (k.equals("weatherPillAlpha")) hi=70;
                if (k.equals("weatherPlaceLevel")) hi=3;
                if (k.equals("radius")) hi=40;
                if (k.equals("border")) hi=5;
                if (k.equals("borderColor")) hi=3;
                if (k.equals("iconSize")) { lo=40; hi=90; }
                if (k.equals("spacing")) { lo=0; hi=35; }
                if (k.equals("fontSize")) { lo=12; hi=24; }
                if (k.equals("homePage")) hi=2;
                if (k.equals("musicMode")) hi=1;
                if (k.equals("fit")) hi=2;
                if (n<lo || n>hi) throw new JSONException(k + " 超出允许范围"); e.putInt(k, n);
            }
        }
        for (String k : keys()) if (!o.has(k)) e.remove(k);
        if(!o.has("themeBorder")&&o.has("borderColor")){String[] colors={"#6E9BB4","#39BAFF","#B18CE3","#5CDBB0"};e.putString("themeBorder",colors[o.getInt("borderColor")]);}
        if(!root.has("pageOrder")&&o.has("homePage"))e.putInt("homePage",oldPage(o.getInt("homePage")));
        e.putString("mapPackage",MAP).remove("amap65Bridge").remove("amapFloatDetected").putBoolean("pageOrder055",true).apply();
    }
}
