package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;
import java.util.*;

/** Acceptance checks for palette, weather, page transitions, volume and service admission. */
class RevisionFlow {
    final Instrumentation test;MainActivity activity;int passed,failed;final StringBuilder report=new StringBuilder("API19 UI and synthetic weather/service fixtures; no commercial app executed.\n");
    interface Task{void run()throws Exception;}
    RevisionFlow(Instrumentation test){this.test=test;}
    void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    void check(String name,Task action){try{action.run();passed++;report.append("PASS ");}catch(Throwable e){failed++;report.append("FAIL ").append(e).append(" ");}report.append(name).append('\n');}
    void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable e){error[0]=e;}});test.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    Object field(Object target,String name){try{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(Exception e){throw new RuntimeException(e);}}
    View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View v=find(((ViewGroup)root).getChildAt(i),label);if(v!=null)return v;}return null;}
    String allText(View view){StringBuilder text=new StringBuilder();if(view instanceof TextView)text.append(((TextView)view).getText()).append('\n');if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)text.append(allText(((ViewGroup)view).getChildAt(i)));return text.toString();}
    View root(){return activity.getWindow().getDecorView();}
    View dialogRoot(String label)throws Exception{Class<?> type=Class.forName("android.view.WindowManagerGlobal");Method get=type.getDeclaredMethod("getInstance");get.setAccessible(true);Object manager=get.invoke(null);Field views=type.getDeclaredField("mViews");views.setAccessible(true);for(Object view:(java.util.List<?>)views.get(manager))if(find((View)view,label)!=null)return (View)view;throw new AssertionError("dialog missing: "+label);}
    void restore(Config cfg,Map<String,?> saved){SharedPreferences.Editor edit=cfg.p.edit().clear();for(Map.Entry<String,?> e:saved.entrySet()){Object v=e.getValue();if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);}edit.commit();}
    void run(){Config cfg=new Config(test.getTargetContext());Map<String,?> saved=new HashMap<>(cfg.p.getAll());
        try{
            cfg.put("autoRecoverApps",false);cfg.put("pageAnimation",false);cfg.put("homePage",0);
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(500);
            check("palette backup roundtrip validates whole import atomically",()->{
                cfg.put("themeAccent","#9BCDF5");cfg.put("themeSurface","#202125");cfg.put("pageAnimation",false);cfg.put("keepCompanions",false);String backup=cfg.exportJson();cfg.put("themeAccent",ThemeColors.ACCENT);cfg.restore(backup);require(cfg.s("themeAccent","").equals("#9BCDF5")&&!cfg.b("keepCompanions",true),"palette/preferences lost");
                boolean denied=false;try{cfg.restore("{\"format\":\"hydrogen-config\",\"version\":1,\"settings\":{\"mapShare\":40,\"themeAccent\":\"bad\"}}");}catch(org.json.JSONException expected){denied=true;}require(denied&&cfg.n("mapShare",60)!=40&&cfg.s("themeAccent","").equals("#9BCDF5"),"invalid import partially applied");
            });
            check("configured palette changes rendered active lyric and player icons",()->main(()->{
                activity.render();MusicCard card=(MusicCard)find(root(),"简洁页无边框音乐区");ViewGroup words=(ViewGroup)find(card,"歌词，可上下拖动或点击调整播放进度");ThemeColors palette=new ThemeColors(cfg);boolean found=false;for(int i=0;i<words.getChildCount();i++)if(words.getChildAt(i).isSelected()){found=true;require(((TextView)words.getChildAt(i)).getCurrentTextColor()==palette.highlight(),"lyric palette missing");}require(found,"no active lyric");TransportIcon icon=(TransportIcon)((ImageView)find(card,"播放 / 暂停")).getDrawable();require(((Integer)field(icon,"color"))==palette.accent,"transport palette missing");
            }));
            check("personalization backup includes widths sizes and colors; rejects invalid values atomically",()->{
                cfg.put("minimalMusicWidth",70);cfg.put("quickWidth",140);cfg.put("transportWidth",75);cfg.put("clockSize",115);cfg.put("lyricSize",125);cfg.put("weatherPillAlpha",70);cfg.put("themeText","#DDEEFF");cfg.put("themeClock","#EDF5FF");cfg.put("themeWeather","#E8C18B");Map<String,Object> state=cfg.visualState();String backup=cfg.exportJson();cfg.put("lyricSize",90);cfg.restore(backup);require(cfg.visualState().equals(state),"new settings lost from backup or visual state");boolean rejected=false;try{org.json.JSONObject invalid=new org.json.JSONObject(backup);invalid.getJSONObject("settings").put("lyricSize",146);cfg.restore(invalid.toString());}catch(org.json.JSONException expected){rejected=true;}require(rejected&&cfg.visualState().equals(state),"invalid size partially applied");for(String key:new String[]{"minimalMusicWidth","quickWidth","transportWidth","clockSize","lyricSize","weatherPillAlpha","themeText","themeClock","themeWeather"})cfg.p.edit().remove(key).commit();main(activity::render);
            });
            check("wordmark contains attribution and quick tiles remove subtitle rows",()->main(()->{
                require(allText(root()).contains("W I N D C H A S E R ·MarLous"),"attribution missing");activity.showPage(1);for(String name:new String[]{"应用列表","系统设置"})require(((ViewGroup)find(root(),name)).getChildCount()==2,"empty subtitle row retained");require(!allText(root()).contains("全部应用")&&!allText(root()).contains("设备设置"),"subtitle text remains");
            }));
            check("both compact weather chips reuse one live reading and show cached state",()->main(()->{
                WeatherController weather=(WeatherController)field(activity,"weather");weather.pause();try{weather.reading=new WeatherController.Reading(new org.json.JSONObject("{\"current\":{\"temperature_2m\":22,\"wind_speed_10m\":8,\"relative_humidity_2m\":60,\"weather_code\":0,\"is_day\":1,\"time\":\"2026-10-23T10:00\"},\"daily\":{\"temperature_2m_max\":[24],\"temperature_2m_min\":[16]}}"));}catch(Exception e){throw new RuntimeException(e);}weather.fetchedAt=System.currentTimeMillis();
                WeatherChip quick=(WeatherChip)field(activity,"quickWeather"),header=(WeatherChip)field(activity,"headerWeather");quick.update();header.update();require(allText(quick).contains("22°  晴")&&allText(header).contains("22°  晴"),"weather missing");require(field(quick,"weather")==field(header,"weather"),"duplicate controllers");weather.fetchedAt-=WeatherController.FRESH+1000;quick.update();header.update();require(allText(quick).contains("缓存")&&allText(header).contains("缓存"),"stale reading presented as fresh");weather.reading=null;weather.loading=false;quick.update();require(allText(quick).contains("天气待更新"),"empty weather fabricated");
            }));
            check("all extra app pages reuse weather header and omit battery",()->main(()->{
                @SuppressWarnings("unchecked") ArrayList<ResolveInfo> apps=(ArrayList<ResolveInfo>)field(activity,"applications");ResolveInfo original=Ui.apps(activity).get(0);apps.clear();for(int i=0;i<100;i++)apps.add(new ResolveInfo(original));activity.showPage(2);View header=find(root(),"应用页日期与天气");require(header.isShown(),"third header missing");activity.showPage(3);require(header==find(root(),"应用页日期与天气")&&header.isShown(),"extra header differs");String status=((TextView)find(root(),"蓝牙和网络状态")).getText().toString();require(!status.contains("%")&&!status.contains("▰"),"battery remains");
            }));
            check("fast animation settles after rapid page changes; disabled mode is immediate",()->{
                main(()->{cfg.put("pageAnimation",true);activity.showPage(0);activity.showPage(1);activity.showPage(2);require((Boolean)field(activity,"transitioning"),"animation not active");});Thread.sleep(260);main(()->{require(!(Boolean)field(activity,"transitioning")&&field(activity,"animatedPage")==null,"animation never settled");View grid=(View)field(activity,"grid");require(grid.getTranslationX()==0&&grid.getAlpha()==1,"animation left transform");cfg.put("pageAnimation",false);activity.showPage(0);require(!(Boolean)field(activity,"transitioning"),"disabled animation still runs");});
            });
            check("volume thumb is 75 percent of ROM size while full slider touch area remains",()->main(()->{
                SeekBar slider=(SeekBar)find(root(),"媒体音量滑块");Drawable original=new SeekBar(activity).getThumb();require(slider.getThumb().getIntrinsicWidth()==Math.round(original.getIntrinsicWidth()*.75f)&&slider.getThumb().getIntrinsicHeight()==Math.round(original.getIntrinsicHeight()*.75f),"wrong thumb scale");require(slider.getHeight()==activity.u(52),"touch area shrunk");
                AudioManager audio=(AudioManager)activity.getSystemService(Context.AUDIO_SERVICE);int old=audio.getStreamVolume(AudioManager.STREAM_MUSIC);try{long now=SystemClock.uptimeMillis();for(int action:new int[]{0,1}){MotionEvent event=MotionEvent.obtain(now,now+50,action,slider.getWidth()-1,slider.getHeight()/2f,0);slider.dispatchTouchEvent(event);event.recycle();}require(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==slider.getMax(),"slider cannot reach full volume");}finally{audio.setStreamVolume(AudioManager.STREAM_MUSIC,old,0);}
            }));
            check("companion protection respects both toggles, selection and exported permissions",()->{
                cfg.put("keepCompanions",true);cfg.put("autoRecoverApps",false);require(!CompanionService.enabled(cfg),"autostart off ignored");cfg.put("autoRecoverApps",true);cfg.put("musicPackage",Config.MUSIC);require(CompanionService.enabled(cfg),"enabled policy");
                final ServiceInfo service=new ServiceInfo();service.enabled=true;service.exported=true;service.applicationInfo=new ApplicationInfo();service.applicationInfo.enabled=true;
                Context fake=new ContextWrapper(activity){@Override public PackageManager getPackageManager(){return new android.test.mock.MockPackageManager(){@Override public ServiceInfo getServiceInfo(ComponentName n,int flags){return service;}};}@Override public int checkCallingOrSelfPermission(String p){return PackageManager.PERMISSION_DENIED;}};
                require(CompanionService.available(fake,cfg,true),"public service rejected");service.exported=false;require(!CompanionService.available(fake,cfg,true),"private service allowed");service.exported=true;service.permission="private.permission";require(!CompanionService.available(fake,cfg,true),"protected service allowed");service.permission=null;cfg.put("musicPackage","other.music");require(!CompanionService.available(fake,cfg,true),"unselected QQ started");cfg.put("keepCompanions",false);require(!CompanionService.enabled(cfg),"off ignored");cfg.put("autoRecoverApps",false);
            });
            check("theme settings contain startup protection and ROM guide entry",()->{
                SettingsActivity settings=(SettingsActivity)test.startActivitySync(new Intent(activity,SettingsActivity.class).putExtra("category",7).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(150);main(()->{String text=allText(settings.getWindow().getDecorView());require(text.contains("关联应用后台保护")&&text.contains("车机 ROM 后台保护设置")&&text.contains("正常关机可以结束"),"ROM options missing");ToggleButton toggle=(ToggleButton)find(settings.getWindow().getDecorView(),"关联应用后台保护");require(toggle!=null,"no protection toggle");settings.finish();});
            });
            check("personalization settings and Chinese license open with intact original notices",()->{
                SettingsActivity settings=(SettingsActivity)test.startActivitySync(new Intent(activity,SettingsActivity.class).putExtra("category",4).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(160);main(()->{View root=settings.getWindow().getDecorView();find(root,"设置分类：音乐与歌词").performClick();root=settings.getWindow().getDecorView();for(String label:new String[]{"播放控制栏宽度","歌词大小"})require(find(root,label) instanceof SeekBar,"missing slider "+label);find(root,"设置分类：备份与维护").performClick();try{Method licenses=SettingsActivity.class.getDeclaredMethod("licenses");licenses.setAccessible(true);licenses.invoke(settings);View dialog=dialogRoot("开源许可中文说明");String text=allText(dialog);require(text.contains("中文阅读说明")&&text.contains("Conscrypt")&&!text.contains("\ufffd"),"Chinese license not decoded");TextView original=(TextView)dialog.findViewById(android.R.id.button3);require(original.getText().toString().equals("查看许可原文"),"original button missing");original.performClick();}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(150);main(()->{try{View source=dialogRoot("开源许可原文");require(allText(source).contains("Apache")&&allText(source).contains("GNU"),"original notices missing");source.findViewById(android.R.id.button1).performClick();}catch(Exception e){throw new RuntimeException(e);}settings.finish();});
            });
        }catch(Throwable e){failed++;report.append("FAIL fixture ").append(e).append('\n');}
        finally{if(activity!=null)main(activity::finish);restore(cfg,saved);}
        report.append("REVISION_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
