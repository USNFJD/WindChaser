package cn.hydrogen.launcher;

import android.content.Context;
import org.json.*;
import java.io.*;

/** Administrative polygons only; weather continues to come from Open-Meteo.
 * Public Alibaba Cloud DataV boundary files are cached, never used as a weather API.
 */
final class ChinaLocation {
    interface Data {JSONObject load(int code)throws Exception;}
    private static final long MAX_AGE=30L*24*3600000;
    static PlaceName resolve(Context context,double lat,double lon)throws Exception{
        return resolve(lat,lon,code->load(context,code));
    }
    static PlaceName resolve(double lat,double lon,Data data)throws Exception{
        if(lat<3||lat>54||lon<73||lon>136)return null;
        JSONObject province=containing(data.load(100000),lon,lat);if(province==null)return null;
        JSONObject p=province.getJSONObject("properties");String state=p.getString("name");
        PlaceName result=new PlaceName(state,PlaceName.municipality(state)?state:"","","","");
        try{
            JSONObject city=containing(data.load(p.getInt("adcode")),lon,lat);if(city==null)return result;
            JSONObject c=city.getJSONObject("properties");String name=c.getString("name");
            if("district".equals(c.optString("level")))return new PlaceName(state,result.city,name,"","");
            result=new PlaceName(state,name,"","","");
            try{JSONObject county=containing(data.load(c.getInt("adcode")),lon,lat);if(county!=null)result=new PlaceName(state,name,county.getJSONObject("properties").getString("name"),"","");}
            catch(Exception unavailable){LauncherApp.record("县级边界暂不可用，保留已确定的省市",unavailable);}
        }catch(Exception unavailable){LauncherApp.record("市级边界暂不可用，保留已确定的省份",unavailable);}
        return result;
    }
    static JSONObject containing(JSONObject collection,double x,double y)throws JSONException{
        JSONArray features=collection.getJSONArray("features");
        for(int n=0;n<features.length();n++){JSONObject feature=features.getJSONObject(n),geometry=feature.optJSONObject("geometry");if(geometry==null)continue;JSONArray coordinates=geometry.getJSONArray("coordinates");String type=geometry.getString("type");
            if("Polygon".equals(type)&&polygon(coordinates,x,y))return feature;
            if("MultiPolygon".equals(type))for(int i=0;i<coordinates.length();i++)if(polygon(coordinates.getJSONArray(i),x,y))return feature;
        }return null;
    }
    private static boolean polygon(JSONArray rings,double x,double y)throws JSONException{
        if(rings.length()==0||!ring(rings.getJSONArray(0),x,y))return false;
        for(int i=1;i<rings.length();i++)if(ring(rings.getJSONArray(i),x,y))return false;
        return true;
    }
    private static boolean ring(JSONArray points,double x,double y)throws JSONException{
        boolean inside=false;int size=points.length();if(size<3)return false;
        for(int i=0,j=size-1;i<size;j=i++){
            JSONArray a=points.getJSONArray(i),b=points.getJSONArray(j);double ax=a.getDouble(0),ay=a.getDouble(1),bx=b.getDouble(0),by=b.getDouble(1);
            double cross=(x-ax)*(by-ay)-(y-ay)*(bx-ax);
            if(Math.abs(cross)<1e-10&&x>=Math.min(ax,bx)&&x<=Math.max(ax,bx)&&y>=Math.min(ay,by)&&y<=Math.max(ay,by))return true;
            if((ay>y)!=(by>y)&&x<(bx-ax)*(y-ay)/(by-ay)+ax)inside=!inside;
        }return inside;
    }
    private static JSONObject load(Context context,int code)throws Exception{
        File folder=new File(context.getCacheDir(),"administrative-boundaries"),file=new File(folder,code+".json");
        long age=System.currentTimeMillis()-file.lastModified();
        if(file.isFile()&&file.length()<4*1024*1024&&age>=0&&age<MAX_AGE)try{return read(file);}catch(Exception ignored){}
        try{
            String body=WeatherHttp.get(context,"https://geo.datav.aliyun.com/areas_v3/bound/"+code+"_full.json",2500,2500,4*1024*1024);
            JSONObject data=new JSONObject(body);data.getJSONArray("features");
            if(folder.isDirectory()||folder.mkdirs()){File temp=new File(folder,code+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(body.getBytes("UTF-8"));}if(!temp.renameTo(file))temp.delete();}
            return data;
        }catch(Exception unavailable){if(file.isFile())try{return read(file);}catch(Exception ignored){}throw unavailable;}
    }
    private static JSONObject read(File file)throws Exception{
        try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>4*1024*1024)throw new IOException("行政边界文件过大");out.write(bytes,0,n);}return new JSONObject(out.toString("UTF-8"));
        }
    }
}
