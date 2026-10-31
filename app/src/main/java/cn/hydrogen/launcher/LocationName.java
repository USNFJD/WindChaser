package cn.hydrogen.launcher;
import android.content.Context;
import android.location.*;
import android.content.SharedPreferences;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** System geocoder first, with structured and versioned administrative-name cache. */
final class LocationName {
    private static final ThreadPoolExecutor SYSTEM=new ThreadPoolExecutor(1,1,30,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(1),r->{Thread t=new Thread(r,"SystemAddressLookup");t.setDaemon(true);return t;});
    static String join(String... parts){return PlaceName.join(parts);}
    private static boolean city(String s){return s!=null&&(s.endsWith("市")||s.endsWith("地区")||s.endsWith("盟")||s.endsWith("自治州"));}
    private static boolean county(String s){return s!=null&&(s.endsWith("县")||s.endsWith("区")||s.endsWith("旗"));}
    private static boolean town(String s){return s!=null&&(s.endsWith("镇")||s.endsWith("乡")||s.endsWith("街道")||s.endsWith("办事处"));}
    static PlaceName addressParts(Address a){
        String province=PlaceName.clean(a.getAdminArea()),local=PlaceName.clean(a.getLocality()),sub=PlaceName.clean(a.getSubAdminArea()),part=PlaceName.clean(a.getSubLocality());
        String c="",district="",t="",v="";
        if(city(sub)&&!city(local)){c=sub;district=local;}
        else if(city(local)){c=local;if(county(sub))district=sub;}
        else {if(county(local))district=local;else if(county(sub))district=sub;}
        if(c.isEmpty()&&PlaceName.municipality(province))c=province;
        if(county(part)&&district.isEmpty())district=part;else if(town(part))t=part;else if(part.endsWith("村")||part.endsWith("社区"))v=part;
        return new PlaceName(province,c,district,t,v);
    }
    static PlaceName jsonParts(JSONObject a){
        String province=a.optString("province",a.optString("state",""));
        String c=a.optString("city",a.optString("municipality","")),district=a.optString("county",a.optString("city_district",a.optString("district","")));
        if(county(c)&&!city(c)){if(district.isEmpty())district=c;c="";}
        if(c.isEmpty()&&PlaceName.municipality(province))c=province;
        String t=a.optString("town",a.optString("suburb",a.optString("quarter",""))),v=a.optString("village",a.optString("hamlet",""));
        if(county(t)){if(district.isEmpty())district=t;t="";}else if(!town(t)&&!t.isEmpty()){if(t.endsWith("村")||t.endsWith("社区"))v=t;t="";}
        return new PlaceName(province,c,district,t,v);
    }
    static String address(Address a){return addressParts(a).full();}
    static String json(JSONObject a){return jsonParts(a).full();}
    static PlaceName cached(Context c,double lat,double lon){
        SharedPreferences p=c.getSharedPreferences("location-name",0);String key=String.format(Locale.US,"%.2f,%.2f",lat,lon);long age=System.currentTimeMillis()-p.getLong("time",0);
        if(p.getInt("schema",0)!=3||!key.equals(p.getString("key",""))||age<0||age>=7*24*3600000L)return null;
        try{PlaceName name=PlaceName.parse(new JSONObject(p.getString("parts","{}")));return name.empty()?null:name;}catch(Exception e){return null;}
    }
    static PlaceName resolveParts(Context c,double lat,double lon)throws Exception{
        PlaceName name=cached(c,lat,lon);if(name!=null&&!name.province.isEmpty()&&!name.city.isEmpty()&&!name.county.isEmpty())return name;
        SharedPreferences cache=c.getSharedPreferences("location-name",0);String key=String.format(Locale.US,"%.2f,%.2f",lat,lon);
        if(name==null)name=new PlaceName("","","","","");
        try{PlaceName domestic=ChinaLocation.resolve(c,lat,lon);if(domestic!=null)name=domestic;}catch(Exception unavailable){LauncherApp.record("国内行政地点查询暂不可用，尝试系统地址服务",unavailable);}
        Future<List<Address>> system=null;
        if(Geocoder.isPresent())try{
            system=SYSTEM.submit(()->new Geocoder(c,Locale.SIMPLIFIED_CHINESE).getFromLocation(lat,lon,1));
            List<Address> addresses=system.get(2500,TimeUnit.MILLISECONDS);
            if(addresses!=null&&!addresses.isEmpty()){PlaceName systemName=addressParts(addresses.get(0));if(agrees(name,systemName))name=name.fill(systemName);}
        }catch(Exception ignored){}finally{if(system!=null)system.cancel(true);}
        if(name.province.isEmpty()||name.city.isEmpty()||name.county.isEmpty())try{
            long last=cache.getLong("attempt",0),now=System.currentTimeMillis();
            if(now-last<60000&&now>=last)throw new java.io.IOException("地址服务稍后重试");
            cache.edit().putLong("attempt",now).apply();
            JSONObject result=new JSONObject(WeatherHttp.get(c,"https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat="+lat+"&lon="+lon+"&zoom=16&addressdetails=1&accept-language=zh-CN",2500,2500,256*1024));
            if(result.has("address")){PlaceName richer=jsonParts(result.getJSONObject("address"));boolean agrees=(name.province.isEmpty()||richer.province.isEmpty()||name.province.equals(richer.province))&&(name.city.isEmpty()||richer.city.isEmpty()||name.city.equals(richer.city));if(agrees)name=name.fill(richer);}
        }catch(Exception unavailable){if(name.empty())throw unavailable;}
        if(name.empty())throw new java.io.IOException("暂未取得行政地点名称");
        cache.edit().putInt("schema",3).putString("key",key).putString("parts",name.json().toString()).putString("name",name.full()).putLong("time",System.currentTimeMillis()).apply();return name;
    }
    private static boolean agrees(PlaceName a,PlaceName b){return (a.province.isEmpty()||b.province.isEmpty()||a.province.equals(b.province))&&(a.city.isEmpty()||b.city.isEmpty()||a.city.equals(b.city));}
    static String resolve(Context c,double lat,double lon)throws Exception{return resolveParts(c,lat,lon).full();}
}
