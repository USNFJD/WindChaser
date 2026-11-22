package cn.hydrogen.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;

/** Foreground-only weather and opt-in location. Open-Meteo data are model estimates. */
final class WeatherController {
    static final long FRESH=30*60*1000L;
    private static final long RETRY=5*60*1000L;
    interface Fetcher{String get(String url)throws Exception;}
    static final String DEFAULT_CITY="{\"label\":\"北京市\",\"lat\":39.9075,\"lon\":116.39723,\"place\":{\"province\":\"北京市\",\"city\":\"北京市\",\"county\":\"\"}}";
    private static final String ZHENNING_CITY="{\"label\":\"贵州省 安顺市 镇宁县\",\"lat\":26.06667,\"lon\":105.76667,\"place\":{\"province\":\"贵州省\",\"city\":\"安顺市\",\"county\":\"镇宁县\"}}";
    static final class City{
        final String label;final double lat,lon;final PlaceName place;
        City(String label,double lat,double lon){this(label,lat,lon,null);}
        City(String label,double lat,double lon,PlaceName place){this.label=place==null||place.empty()?label:place.full();this.lat=lat;this.lon=lon;this.place=place;}
        String homeLabel(int level){String text=place==null?"":place.home(level);return text.isEmpty()?label:text;}
        String shortLabel(){String text=place==null?"":place.compact();return text.isEmpty()?label.split(" · ")[0]:text;}
        String json()throws JSONException{JSONObject o=new JSONObject().put("label",label).put("lat",lat).put("lon",lon);if(place!=null)o.put("place",place.json());return o.toString();}
        String key(){return String.format(Locale.US,"%.2f,%.2f",lat,lon);}
        static City parse(String raw)throws JSONException{
            JSONObject o=new JSONObject(raw);String label=o.getString("label");double lat=o.getDouble("lat"),lon=o.getDouble("lon");
            if(label.trim().isEmpty()||label.length()>100||Double.isNaN(lat)||Double.isInfinite(lat)||Double.isNaN(lon)||Double.isInfinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new JSONException("城市坐标不正确");
            PlaceName parts=PlaceName.parse(o.optJSONObject("place"));
            if(parts.empty()&&label.contains("镇宁")&&Math.abs(lat-26.06667)<.02&&Math.abs(lon-105.76667)<.02)parts=new PlaceName("贵州省","安顺市","镇宁县","","");
            return new City(label,lat,lon,parts);
        }
    }
    static final class Reading{
        final double temperature,wind,high,low;final int humidity,code;final boolean day;final String observed;
        Reading(JSONObject root)throws JSONException{
            JSONObject c=root.getJSONObject("current"),daily=root.getJSONObject("daily");temperature=number(c,"temperature_2m");wind=number(c,"wind_speed_10m");humidity=c.getInt("relative_humidity_2m");code=c.getInt("weather_code");day=c.getInt("is_day")==1;observed=c.getString("time");
            high=daily.getJSONArray("temperature_2m_max").getDouble(0);low=daily.getJSONArray("temperature_2m_min").getDouble(0);
            if(humidity<0||humidity>100||temperature< -100||temperature>70||wind<0||!observed.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*")||Double.isNaN(high)||Double.isNaN(low))throw new JSONException("天气数据不完整");
        }
        private double number(JSONObject o,String key)throws JSONException{double n=o.getDouble(key);if(Double.isInfinite(n)||Double.isNaN(n))throw new JSONException("天气数值缺失");return n;}
    }
    private final Activity a;private final Config cfg;private final Runnable changed;private final Fetcher fetcher;private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService geo=Executors.newSingleThreadExecutor();
    private final ExecutorService io=Executors.newSingleThreadExecutor();private final SharedPreferences cache;
    private final LocationManager locations;private boolean active,closed,busy,locating;private boolean forceAfterLocation;private int nameGeneration;private int generation;private long lastAttempt;private String selectedManualKey;private String nameRequested="";
    City city;Reading reading;long fetchedAt;String notice="";boolean loading;
    private final Runnable timer=()->refresh(false);
    private final Runnable locationTimeout=()->{stopLocation();notice="";select(manualCity(),forceAfterLocation);schedule(reading!=null?Math.max(RETRY,FRESH-Math.max(0,System.currentTimeMillis()-fetchedAt)):RETRY);};
    private final LocationListener listener=new LocationListener(){
        public void onLocationChanged(Location l){if(validLocation(l)){located(l);}}
        public void onProviderDisabled(String p){}public void onProviderEnabled(String p){}public void onStatusChanged(String p,int status,Bundle b){}
    };
    WeatherController(Activity a,Runnable changed){this(a,changed,url->WeatherHttp.get(a,url));}
    WeatherController(Activity a,Runnable changed,Fetcher fetcher){this.a=a;this.fetcher=fetcher;cfg=new Config(a);this.changed=changed;cache=a.getSharedPreferences("weather-cache",0);locations=(LocationManager)a.getSystemService(Context.LOCATION_SERVICE);city=manualCity();selectedManualKey=city.key();loadCache();}
    private City manualCity(){try{return City.parse(cfg.s("weatherCity",DEFAULT_CITY));}catch(Exception e){try{return City.parse(DEFAULT_CITY);}catch(Exception impossible){throw new IllegalStateException(impossible);}}}
    void start(){if(closed)return;active=true;City manual=manualCity();if(!cfg.b("weatherAuto",true)||!manual.key().equals(selectedManualKey))changeCity(manual);selectedManualKey=manual.key();refresh(false);changed.run();}
    void pause(){active=false;main.removeCallbacks(timer);stopLocation();}
    void close(){pause();closed=true;generation++;io.shutdownNow();geo.shutdownNow();nameGeneration++;main.removeCallbacksAndMessages(null);}
    void refresh(boolean force){
        if(!active||closed)return;
        if(cfg.b("weatherAuto",true)){forceAfterLocation=force;if(locate()){changed.run();return;}}else notice="";
        resolveName(city);fetch(force);schedule(reading!=null?Math.max(RETRY,FRESH-Math.max(0,System.currentTimeMillis()-fetchedAt)):RETRY);
    }
    private void schedule(long delay){main.removeCallbacks(timer);if(active&&!closed)main.postDelayed(timer,delay);}
    private void changeCity(City next){if(!city.key().equals(next.key())){generation++;nameGeneration++;nameRequested="";city=next;reading=null;fetchedAt=0;lastAttempt=0;loadCache();}else city=next;}
    private void select(City next,boolean force){changeCity(next);changed.run();fetch(force);}
    private void loadCache(){try{if(city.key().equals(cache.getString("key",""))){reading=new Reading(new JSONObject(cache.getString("body","")));fetchedAt=cache.getLong("fetched",0);if(fetchedAt>System.currentTimeMillis()+60000){reading=null;fetchedAt=0;}}}catch(Exception ignored){reading=null;fetchedAt=0;}}
    private void fetch(boolean force){
        if(!active||closed||busy)return;long now=System.currentTimeMillis();
        if(!force&&reading!=null&&now>=fetchedAt&&now-fetchedAt<FRESH)return;
        if(now-lastAttempt<(force?15000:5*60000)&&now>=lastAttempt)return;
        busy=true;loading=true;lastAttempt=now;changed.run();final City request=city;final int token=generation;
        io.execute(()->{String raw=null,error=null;Reading result=null;try{raw=fetcher.get("https://api.open-meteo.com/v1/forecast?latitude="+request.lat+"&longitude="+request.lon+"&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=Asia%2FShanghai");result=new Reading(new JSONObject(raw));}catch(Exception e){error="天气更新失败，请检查网络或系统时间";LauncherApp.record("天气请求失败",e);}
            final Reading data=result;final String body=raw,problem=error;main.post(()->{busy=false;loading=false;if(closed)return;if(token!=generation){fetch(false);return;}if(data!=null){reading=data;fetchedAt=System.currentTimeMillis();cache.edit().putString("key",request.key()).putString("body",body).putLong("fetched",fetchedAt).apply();}else notice=problem;schedule(data!=null?FRESH:RETRY);if(active)changed.run();});});
    }
    @android.annotation.SuppressLint("MissingPermission")
    private boolean locate(){
        if(a.checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED&&a.checkCallingOrSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){notice="";changeCity(manualCity());return false;}
        stopLocation();Location best=null;boolean requested=false;
        for(String p:new String[]{LocationManager.NETWORK_PROVIDER,LocationManager.GPS_PROVIDER})try{
            if(!locations.isProviderEnabled(p))continue;Location l=locations.getLastKnownLocation(p);if(validLocation(l)&&(best==null||l.getTime()>best.getTime()))best=l;
            locations.requestLocationUpdates(p,1000,100,listener,Looper.getMainLooper());requested=true;
        }catch(Exception e){LauncherApp.record("天气定位不可用 "+p,e);}
        if(best!=null){located(best);return true;}
        else if(requested){locating=true;notice="正在定位";main.postDelayed(locationTimeout,8000);return true;}
        else {notice="";changeCity(manualCity());return false;}
    }
    private void located(Location l){
        stopLocation();notice="";final double lat=l.getLatitude(),lon=l.getLongitude();
        PlaceName names=LocationName.cached(a,lat,lon);
        City found=new City(names==null?"正在获取地点":names.full(),lat,lon,names);
        select(found,forceAfterLocation);resolveName(found);
        schedule(reading!=null?Math.max(RETRY,FRESH-Math.max(0,System.currentTimeMillis()-fetchedAt)):RETRY);
    }
    private void resolveName(City target){
        if(closed||!active||target.key().equals(nameRequested))return;
        if(target.place!=null&&!target.place.province.isEmpty()&&!target.place.city.isEmpty()&&!target.place.county.isEmpty()&&(!target.place.town.isEmpty()||!target.place.village.isEmpty()))return;
        nameRequested=target.key();final int request=++nameGeneration;final String key=target.key();
        final Runnable timeout=()->{if(closed||request!=nameGeneration||!key.equals(city.key()))return;
            nameGeneration++;nameRequested="";
            if(city.place==null||city.place.empty())city=new City("地点暂不可用",target.lat,target.lon);
            if(active)changed.run();};
        main.postDelayed(timeout,15000);
        geo.execute(()->{PlaceName value=null;try{value=LocationName.resolveParts(a,target.lat,target.lon);}catch(Exception e){LauncherApp.record("地点名称暂不可用",e);}final PlaceName resolved=value;
            main.post(()->{main.removeCallbacks(timeout);if(closed||request!=nameGeneration||!key.equals(city.key()))return;
                if(resolved==null){nameRequested="";if(city.place==null||city.place.empty())city=new City("地点暂不可用",target.lat,target.lon);}
                if(resolved!=null){PlaceName merged=target.place==null?resolved:target.place.fill(resolved);city=new City(merged.full(),target.lat,target.lon,merged);if(!cfg.b("weatherAuto",true))try{cfg.put("weatherCity",city.json());}catch(Exception ignored){} }
                if(active)changed.run();});});
    }
    private boolean validLocation(Location l){return l!=null&&Math.abs(l.getLatitude())<=90&&Math.abs(l.getLongitude())<=180&&System.currentTimeMillis()-l.getTime()>=0&&System.currentTimeMillis()-l.getTime()<15*60000&&(!l.hasAccuracy()||l.getAccuracy()<=5000);}
    private void stopLocation(){locating=false;main.removeCallbacks(locationTimeout);try{locations.removeUpdates(listener);}catch(Exception ignored){}}
    static String description(int code){if(code==0)return "晴";if(code==1)return "少云";if(code==2)return "多云";if(code==3)return "阴";if(code==45||code==48)return "雾";if(code>=51&&code<=57)return "毛毛雨";if(code>=61&&code<=67)return "雨";if(code>=71&&code<=77)return "雪";if(code>=80&&code<=82)return "阵雨";if(code==85||code==86)return "阵雪";if(code>=95&&code<=99)return "雷雨";return "天气未知";}
    static ArrayList<City> search(Context c,String query)throws Exception{
        ArrayList<City> list=new ArrayList<>();if(query.contains("镇宁")||query.equalsIgnoreCase("zhenning"))list.add(City.parse(ZHENNING_CITY));
        JSONObject root=new JSONObject(WeatherHttp.get(c,"https://geocoding-api.open-meteo.com/v1/search?name="+java.net.URLEncoder.encode(query,"UTF-8")+"&count=10&language=zh&format=json"));JSONArray results=root.optJSONArray("results");
        if(results!=null)for(int n=0;n<results.length();n++){JSONObject r=results.getJSONObject(n);String raw=r.getString("name"),province=r.optString("admin1",""),prefecture=r.optString("admin2","");
            boolean district=raw.endsWith("县")||raw.endsWith("区")||raw.endsWith("旗");
            PlaceName parts=new PlaceName(province,district?prefecture:raw,district?raw:"","","");
            String name=parts.empty()?raw:parts.full();list.add(new City(name,r.getDouble("latitude"),r.getDouble("longitude"),parts));}return list;
    }
}
