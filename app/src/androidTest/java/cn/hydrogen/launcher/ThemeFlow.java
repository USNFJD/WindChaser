package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import org.json.*;

/** Uses measured launcher views; external map engine is not simulated as a success. */
final class ThemeFlow extends RevisionFlow {
    ThemeFlow(Instrumentation t){super(t);}
    TextView text(View root,String value){if(root instanceof TextView&&((TextView)root).getText().toString().startsWith(value))return (TextView)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){TextView t=text(((ViewGroup)root).getChildAt(i),value);if(t!=null)return t;}return null;}
    @Override void run(){Config cfg=new Config(test.getTargetContext());Map<String,?> saved=new HashMap<>(cfg.p.getAll());
        try{
            ThemeOptions.reset(cfg,null);cfg.put("autoRecoverApps",false);cfg.put("pageAnimation",false);cfg.put("homePage",0);
            check("single 6.5 map selection normalizes old preferences and backup",()->{
                cfg.p.edit().putString("mapPackage","com.gaode.remaq").putBoolean("amap65Bridge",false).remove("mapOnly65v5").commit();new Config(test.getTargetContext());
                require(Config.MAP.equals(cfg.pkg(false))&&!cfg.p.contains("amap65Bridge"),"migration");
                JSONObject backup=new JSONObject(cfg.exportJson());backup.getJSONObject("settings").put("mapPackage","com.gaode.remaq");cfg.restore(backup.toString());require(Config.MAP.equals(cfg.s("mapPackage","")),"old backup redirected map");
                require(!AmapFloatingController.supported(test.getTargetContext(),"com.gaode.remaq"),"old package accepted");
            });
            check("6.5 bridge marker requires modern layout, input and base identity",()->{
                JSONObject m=new JSONObject().put("bridge_version",1).put("bridge_revision",8).put("sequenced_window_commands",true).put("foreground_host_guard",true).put("base_version","6.5.0.601323").put("base_sha256","7daa40731d4f1bf2b1e521280bc031e5892c48f9092d918ded12da963b7c3950").put("hmi_foreground_guard",true).put("card_relayout",true).put("surface_buffer_matches_card",true).put("buffer_touch_coordinates",true);
                require(AmapFloatingController.validMarker(m),"new bridge rejected");m.put("bridge_revision",4);require(!AmapFloatingController.validMarker(m),"old bridge accepted");m.put("bridge_revision",8).put("card_relayout",false);require(!AmapFloatingController.validMarker(m),"missing feature accepted");
            });
            check("all catalog settings participate in backup and visual invalidation",()->{
                for(ThemeOptions.Option o:ThemeOptions.ALL)if(o.color)cfg.put(o.key,o.def);else cfg.put(o.key,o.number());
                String json=cfg.exportJson();JSONObject settings=new JSONObject(json).getJSONObject("settings");require(settings.length()>=ThemeOptions.ALL.size(),"settings omitted");Map<String,Object> state=cfg.visualState();ThemeOptions.reset(cfg,null);cfg.restore(json);require(state.equals(cfg.visualState()),"catalog roundtrip differs");
                for(ThemeOptions.Option o:ThemeOptions.ALL){JSONObject bad=new JSONObject(json);bad.getJSONObject("settings").put(o.key,o.color?"bad-color":o.max+1);boolean rejected=false;try{cfg.restore(bad.toString());}catch(JSONException expected){rejected=true;}require(rejected&&state.equals(cfg.visualState()),o.key+" non-atomic validation");}
            });
            ThemeOptions.reset(cfg,null);
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(450);
            check("global font and role colors reach music; repeat application is stable",()->main(()->{
                cfg.put("textScale",120);cfg.put("titleSize",110);cfg.put("titleColor","#FFE0AA");activity.render();MusicCard card=(MusicCard)find(root(),"简洁页无边框音乐区");TextView title=(TextView)field(card,"title");require(title!=null,"music title missing");require(title.getCurrentTextColor()==0xffffe0aa,"title color");float before=title.getTextSize();for(int i=0;i<8;i++)ThemeStyle.apply(card,"cardText");require(Math.abs(before-title.getTextSize())<.05,"font multiplied repeatedly");require(before>activity.u(32)*1.3f,"size not applied");
            }));
            check("lyrics keep owned active styles while respecting size and palette",()->main(()->{
                cfg.put("lyricActiveColor","#FFCC66");cfg.put("lyricIdleColor","#CCBBDD");activity.render();ViewGroup words=(ViewGroup)find(root(),"歌词，可上下拖动或点击调整播放进度");boolean active=false;for(int i=0;i<words.getChildCount();i++){TextView row=(TextView)words.getChildAt(i);if(row.isSelected()){active=true;require(row.getCurrentTextColor()==0xffffcc66,"active lyric color");}else require(row.getCurrentTextColor()==0xffccbbdd,"idle lyric color");}require(active,"no centered active lyric");
            }));
            check("remote app widget content is excluded from launcher theme",()->main(()->{
                android.appwidget.AppWidgetHostView widget=new android.appwidget.AppWidgetHostView(activity);TextView remote=Ui.text(activity,"Remote",19,0xff123456);widget.addView(remote);ThemeStyle.apply(widget,"cardText");require(remote.getCurrentTextColor()==0xff123456,"remote content recolored");
            }));
            check("map width changes measured slot, quick width and borders render",()->{
                main(()->{ThemeOptions.reset(cfg,null);cfg.put("mapShare",65);cfg.put("mapBorder","#FF9900");cfg.put("borderOpacity",80);activity.render();activity.showPage(1);});Thread.sleep(160);View first=find(root(),"高德悬浮地图区域");int width=first.getWidth();main(()->{cfg.put("mapShare",35);activity.render();});Thread.sleep(160);require(find(root(),"高德悬浮地图区域").getWidth()<width,"map width unchanged");require((ThemeStyle.border(cfg,"map")&0xffffff)==0xff9900,"border color");
            });
            check("theme inheritance and group reset preserve unrelated options",()->{
                cfg.put("themeText","#FFEEDD");cfg.put("dateColor","");cfg.put("titleSize",120);cfg.put("quickWidth",130);ThemeOptions.reset(cfg,"音乐");require(!cfg.p.contains("titleSize")&&cfg.n("quickWidth",0)==130&&cfg.s("themeText","").equals("#FFEEDD"),"group reset spilled");
            });
            check("all nine theme categories open and expose actual catalog controls",()->{
                SettingsActivity settings=(SettingsActivity)test.startActivitySync(new Intent(activity,SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main(()->{
                    String[] names={"背景","全局配色","文字与字号","卡片与布局","音乐与歌词","时间与天气","图标与快捷","关联与启动","备份与维护"};
                    for(String name:names){View nav=find(settings.getWindow().getDecorView(),"设置分类："+name);require(nav!=null,"missing "+name);nav.performClick();String contents=allText(settings.getWindow().getDecorView());require(!contents.contains("7.5"),"old version UI");}
                    find(settings.getWindow().getDecorView(),"设置分类：音乐与歌词").performClick();for(String label:new String[]{"歌曲名称大小","歌词大小","播放控制栏宽度"})require(find(settings.getWindow().getDecorView(),label) instanceof SeekBar,"missing "+label);ThemeStyle.apply(settings.getWindow().getDecorView(),"settingsText");settings.finish();
                });
            });
            check("new file-center content inherits its own text role",()->{
                cfg.put("filesTextColor","#CCEEAA");FileCenterActivity files=(FileCenterActivity)test.startActivitySync(new Intent(activity,FileCenterActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main(()->{ThemeStyle.activity(files);TextView title=text(files.getWindow().getDecorView(),"文件中心");require(title!=null&&title.getCurrentTextColor()==0xffcceeaa,"file theme missing");files.finish();});
            });
        }catch(Throwable e){failed++;report.append("FAIL fixture ").append(e).append('\n');}
        finally{if(activity!=null)main(activity::finish);restore(cfg,saved);}
        report.append("THEME_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
