package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Regression: location timeout must keep future refreshes even with fresh cached data. */
final class WeatherTimeoutFlow {
    private final Instrumentation test;private int passed,failed;private final StringBuilder report=new StringBuilder();
    WeatherTimeoutFlow(Instrumentation test){this.test=test;}
    private interface Task{void run()throws Exception;}
    private void check(String title,Task task){try{task.run();passed++;report.append("PASS ");}catch(Throwable e){failed++;report.append("FAIL ").append(e).append(' ');}report.append(title).append('\n');}
    private void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    private Field field(String name)throws Exception{Field f=WeatherController.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private void main(Task task){Throwable[] e={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable x){e[0]=x;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    void run(){
        Activity activity=null;WeatherController[] value={null};Context context=test.getTargetContext();Config cfg=new Config(context);String savedCity=cfg.s("weatherCity",WeatherController.DEFAULT_CITY);boolean savedAuto=cfg.b("weatherAuto",true);AtomicInteger requests=new AtomicInteger();
        try{
            cfg.put("weatherCity",WeatherController.DEFAULT_CITY);cfg.put("weatherAuto",false);
            activity=test.startActivitySync(new Intent(context,DeviceInfoActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));final Activity a=activity;
            main(()->{value[0]=new WeatherController(a,()->{},url->{requests.incrementAndGet();throw new AssertionError("fresh data must not fetch");});WeatherController w=value[0];w.reading=new WeatherController.Reading(new org.json.JSONObject("{\"current\":{\"temperature_2m\":22,\"wind_speed_10m\":8,\"relative_humidity_2m\":60,\"weather_code\":0,\"is_day\":1,\"time\":\"2026-10-28T10:00\"},\"daily\":{\"temperature_2m_max\":[24],\"temperature_2m_min\":[16]}}"));w.fetchedAt=System.currentTimeMillis();field("active").setBoolean(w,true);});
            check("fresh fallback preserves reading and schedules the next refresh",()->main(()->{WeatherController w=value[0];Object reading=w.reading;Handler h=(Handler)field("main").get(w);Runnable timer=(Runnable)field("timer").get(w);require(!h.hasCallbacks(timer),"fixture already scheduled");((Runnable)field("locationTimeout").get(w)).run();require(w.reading==reading&&requests.get()==0,"fresh reading lost or downloaded again");require(h.hasCallbacks(timer),"future weather refresh lost after location timeout");}));
            check("repeated timeout leaves one scheduled callback and no network burst",()->main(()->{WeatherController w=value[0];for(int i=0;i<4;i++)((Runnable)field("locationTimeout").get(w)).run();require(((Handler)field("main").get(w)).hasCallbacks((Runnable)field("timer").get(w))&&requests.get()==0,"retry lost or burst");}));
            check("pausing cancels the scheduled refresh",()->main(()->{WeatherController w=value[0];w.pause();require(!((Handler)field("main").get(w)).hasCallbacks((Runnable)field("timer").get(w)),"paused callback retained");}));
            check("closing releases both executors and all callbacks",()->main(()->{WeatherController w=value[0];w.close();require(((java.util.concurrent.ExecutorService)field("io").get(w)).isShutdown()&&((java.util.concurrent.ExecutorService)field("geo").get(w)).isShutdown(),"worker retained");require(!((Handler)field("main").get(w)).hasCallbacks((Runnable)field("timer").get(w)),"closed callback retained");}));
        }catch(Throwable e){failed++;report.append("FAIL setup ").append(e).append('\n');}
        finally{if(value[0]!=null)main(value[0]::close);if(activity!=null){final Activity a=activity;main(a::finish);}cfg.put("weatherCity",savedCity);cfg.put("weatherAuto",savedAuto);}
        report.append("WEATHER_TIMEOUT_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle b=new Bundle();b.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,b);
    }
}
