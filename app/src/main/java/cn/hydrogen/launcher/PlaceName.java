package cn.hydrogen.launcher;

import org.json.*;
import java.util.*;

/** Administrative components stay separate; never guess missing levels from a joined label. */
final class PlaceName {
    final String province,city,county,town,village;
    PlaceName(String province,String city,String county,String town,String village){
        this.province=clean(province);this.city=clean(city);this.county=clean(county);this.town=clean(town);this.village=clean(village);
    }
    static String clean(String s){return s==null?"":s.trim();}
    static boolean municipality(String s){return s.equals("北京市")||s.equals("北京")||s.equals("上海市")||s.equals("上海")||s.equals("天津市")||s.equals("天津")||s.equals("重庆市")||s.equals("重庆");}
    private static String identity(String s){if(municipality(s))return s.replace("市","");return s;}
    static String join(String... values){List<String> names=new ArrayList<>();Set<String> seen=new HashSet<>();for(String s:values){s=clean(s);if(!s.isEmpty()&&seen.add(identity(s)))names.add(s);}StringBuilder out=new StringBuilder();for(String s:names){if(out.length()>0)out.append(' ');out.append(s);}return out.toString();}
    boolean empty(){return full().isEmpty();}
    String full(){return join(province,city,county,town,village);}
    String compact(){return join(city.isEmpty()&&municipality(province)?province:city,county);}
    String home(int level){
        String middle=city.isEmpty()||identity(city).equals(identity(province))?county:city;
        if(level==0)return join(province,middle);
        if(level==2)return join(province,middle,!town.isEmpty()?town:county);
        if(level==3)return join(province,middle,!village.isEmpty()?village:!town.isEmpty()?town:county);
        String base=join(province,city,county);
        // A municipality occupies one name; use the known street as the third name.
        if(join(province,city).equals(province)&&!county.isEmpty()&&!town.isEmpty())return join(province,county,town);
        return base;
    }
    JSONObject json()throws JSONException{return new JSONObject().put("province",province).put("city",city).put("county",county).put("town",town).put("village",village);}
    static PlaceName parse(JSONObject o){return o==null?new PlaceName("","","","",""):new PlaceName(o.optString("province"),o.optString("city"),o.optString("county"),o.optString("town"),o.optString("village"));}
    PlaceName fill(PlaceName other){return new PlaceName(first(province,other.province),first(city,other.city),first(county,other.county),first(town,other.town),first(village,other.village));}
    private static String first(String a,String b){return a.isEmpty()?b:a;}
}
