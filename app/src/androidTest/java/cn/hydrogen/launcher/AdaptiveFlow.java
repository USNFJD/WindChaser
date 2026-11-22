package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real measured layouts; protocol transport and music metadata are synthetic fixtures. */
final class AdaptiveFlow {
    private final Instrumentation test;private final boolean recovery;
    private MainActivity activity;private Activity captureActivity;private View captureRoot;private int passed,failed;private final StringBuilder report=new StringBuilder();
    private interface Check{void run()throws Exception;}
    AdaptiveFlow(Instrumentation test,boolean recovery){this.test=test;this.recovery=recovery;}
    private void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    private void check(String name,Check task){android.util.Log.i("AdaptiveTest","START "+name);try{task.run();passed++;report.append("PASS ");}catch(Throwable e){failed++;report.append("FAIL ").append(e).append(" ");}report.append(name).append('\n');}
    private void idle(){
        // A playing media UI keeps invalidating. An unbounded queue-idle wait
        // stalls under KitKat's software-rendered emulator despite live frames.
        // Keep all geometry assertions; only bound the harness's idle wait.
        java.util.concurrent.CountDownLatch settled=new java.util.concurrent.CountDownLatch(1);
        test.waitForIdle(settled::countDown);
        try{if(!settled.await(1500,java.util.concurrent.TimeUnit.MILLISECONDS))android.util.Log.i("AdaptiveTest","Continuous media redraw: checking measured geometry after bounded wait");}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
    }
    private void main(Runnable task){final Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable failure){error[0]=failure;}});idle();if(error[0]!=null)throw new AssertionError(error[0]);}
    private View find(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View v=find(((ViewGroup)root).getChildAt(n),label);if(v!=null)return v;}return null;}
    private View weatherChip(String field){
        // Weather accessibility labels contain live city/condition text.
        // Resolve the actual production view rather than its obsolete static label.
        try{Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);View view=(View)f.get(activity);require(view instanceof WeatherChip,"missing weather view "+field);require(view.getContentDescription()!=null&&!view.getContentDescription().toString().isEmpty(),"missing spoken weather");return view;}
        catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private Rect rect(View view){
        // API19 recycled GridView children can return (0,0) from getLocationOnScreen.
        // Resolve their actual drawing rectangle through the measured parent hierarchy.
        View root=view.getRootView();Rect bounds=new Rect();view.getDrawingRect(bounds);
        if(root!=view)((ViewGroup)root).offsetDescendantRectToMyCoords(view,bounds);
        int[] xy=new int[2];root.getLocationOnScreen(xy);bounds.offset(xy[0],xy[1]);return bounds;
    }
    private void fits(View view){require(view!=null&&view.isShown()&&view.getWidth()>0&&view.getHeight()>0,"missing visible control "+(view==null?"null":view.getContentDescription()+" shown="+view.isShown()+" size="+view.getWidth()+"x"+view.getHeight()));Rect outer=rect(activity.findViewById(android.R.id.content));require(outer.contains(rect(view)),"out of content bounds: "+view.getContentDescription()+" "+rect(view)+" parent "+outer);Rect visible=new Rect();boolean showing=view.getGlobalVisibleRect(visible);int[] origin=new int[2];view.getRootView().getLocationOnScreen(origin);visible.offset(origin[0],origin[1]);require(showing&&visible.equals(rect(view)),"clipped control "+view.getContentDescription()+" actual "+rect(view)+" visible "+visible);}
    private void capture(String name)throws Exception{
        // API19's compositor screenshot can fail or letterbox when wm differs from
        // the emulator's physical framebuffer. Render the actual measured View tree.
        final Bitmap[] rendered={null};main(()->{View root=captureRoot!=null?captureRoot:(captureActivity==null?activity:captureActivity).getWindow().getDecorView();rendered[0]=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(rendered[0]));});Bitmap image=rendered[0];report.append("CAPTURE actual measured view rendering ").append(name).append('\n');
        try(OutputStream out=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),name))){image.compress(Bitmap.CompressFormat.PNG,100,out);}finally{image.recycle();}
    }
    private void noHints(View root){if(root instanceof TextView){String s=((TextView)root).getText().toString();require(!s.contains("滑动空白处切换桌面")&&!s.matches("[●○ \\s]{5,}"),"removed hint remains: "+s);}if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)noHints(((ViewGroup)root).getChildAt(i));}
    private void savePrefs(Config config,Map<String,?> values){android.content.SharedPreferences.Editor edit=config.p.edit().clear();for(Map.Entry<String,?> e:values.entrySet()){Object v=e.getValue();if(v instanceof Integer)edit.putInt(e.getKey(),(Integer)v);else if(v instanceof Boolean)edit.putBoolean(e.getKey(),(Boolean)v);else if(v instanceof Long)edit.putLong(e.getKey(),(Long)v);else if(v instanceof String)edit.putString(e.getKey(),(String)v);else if(v instanceof Float)edit.putFloat(e.getKey(),(Float)v);}edit.commit();}
    void run(){
        Config config=new Config(test.getTargetContext());Map<String,?> backup=new HashMap<>(config.p.getAll());
        try{
            require(!Ui.installed(test.getTargetContext(),AmapFloatingController.PACKAGE),"isolated emulator only");
            config.put("wallpaperKind","");config.put("autoRecoverApps",false);config.put("pageAnimation",false);AmapFloatingController.select(config);config.put("homePage",MainActivity.CARDS);config.put("mapShare",60);config.put("swap",false);config.put("musicMode",0);for(String key:new String[]{"minimalMusicWidth","quickWidth","transportWidth","clockSize","lyricSize","weatherPillAlpha","quickLayout"})config.p.edit().remove(key).commit();
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(700);idle();
            if(recovery)recoveryChecks(config);else {layoutChecks(config);personalizationChecks(config);}
        }catch(Throwable error){failed++;report.append("FAIL fixture ").append(error).append('\n');}
        finally{if(activity!=null)main(activity::finish);savePrefs(config,backup);}
        report.append(recovery?"RECOVERY_RESULT":"ADAPTIVE_RESULT").append(" passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void layoutChecks(Config config)throws Exception{
        report.append("content ").append(rect(activity.findViewById(android.R.id.content))).append("; density ").append(activity.getResources().getDisplayMetrics().density).append('\n');
        main(()->{try{Field f=MainActivity.class.getDeclaredField("musicBridge");f.setAccessible(true);QqMusicBridge bridge=(QqMusicBridge)f.get(activity);bridge.title="屏幕适配测试 · 这是一段较长的歌曲标题";bridge.artist="布局样例（非真实播放）";bridge.lyrics.clear();for(int n=0;n<20;n++)bridge.lyrics.add(new QqMusicBridge.Line(n*1000,"第 "+(n+1)+" 行 · 自适应歌词布局样例"));bridge.position=10000;bridge.state=0;bridge.connected=false;bridge.cover.load("");activity.render();}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(250);
        check("card controls, four lyrics and 75-percent app icon remain inside content",()->main(()->{
            View root=activity.getWindow().getDecorView(),music=find(root,"QQ音乐卡片");fits(music);fits(find(music,"当前歌曲封面"));fits(find(music,"歌曲播放进度，可点击或拖动"));for(int n=1;n<=4;n++)fits(find(music,"歌词第"+n+"行"));View app=find(music,"打开QQ音乐");fits(app);require(Math.abs(((ViewGroup)app).getChildAt(0).getAlpha()-.75f)<.01f,"wrong opacity");
            for(String label:new String[]{"切换高德悬浮地图","重连高德悬浮地图","打开高德地图","地图卡片菜单","蓝牙电话","应用列表","系统设置","主页","主题设置"})fits(find(root,label));noHints(root);
            require(find(root,"顶部时间与设备状态").getVisibility()==View.GONE,"second page top status remains");
            View clock=find(root,"快捷区时间日期，横跨两列"),phone=find(root,"蓝牙电话"),apps=find(root,"应用列表"),settings=find(root,"系统设置");fits(clock);fits(weatherChip("quickWeather"));
            require(clock.getWidth()==phone.getWidth(),"spanning tiles differ");require(Math.abs(clock.getHeight()-(phone.getHeight()*2+activity.u(10)))<=2,"clock must span two of five rows");require(rect(apps).top==rect(settings).top&&rect(apps).right<rect(settings).left,"shortcuts not two columns");
            require(music.getHeight()>activity.findViewById(android.R.id.content).getHeight()*.7f,"cards did not gain height");
            for(int n=1;n<=4;n++)require(find(music,"歌词第"+n+"行").getHeight()>=activity.u(40),"tight lyric spacing");
            View words=find(music,"歌词，可上下拖动或点击调整播放进度");int lyricCount=0;for(int n=0;n<((ViewGroup)words).getChildCount();n++)if(((ViewGroup)words).getChildAt(n).isShown()){fits(((ViewGroup)words).getChildAt(n));lyricCount++;}require(lyricCount>=4,"not enough lyrics");
            require(rect(music).bottom-rect(find(music,"播放 / 暂停")).bottom<=activity.u(24),"large music bottom gap");
            require(((ImageView)find(music,"切换播放模式")).getDrawable() instanceof PlaybackModeIcon,"mode symbol missing");ViewGroup transport=(ViewGroup)find(music,"居中播放控制栏");require(transport.getChildCount()==5,"not five buttons");for(int n=0;n<5;n++)fits(transport.getChildAt(n));View play=transport.getChildAt(2);require(Math.abs((play.getLeft()+play.getRight())/2f-transport.getWidth()/2f)<=1,"play button not centered");report.append("  adaptive lyric rows=").append(lyricCount).append('\n');
        }));capture("v0510-adaptive-card.png");
        check("narrow swapped map header does not overlap",()->{main(()->{config.put("mapShare",35);config.put("swap",true);activity.render();});Thread.sleep(220);main(()->{View root=activity.getWindow().getDecorView();Rect previous=null;for(String label:new String[]{"重连高德悬浮地图","切换高德悬浮地图","打开高德地图","地图卡片菜单"}){View v=find(root,label);fits(v);Rect r=rect(v);if(previous!=null)require(previous.right<=r.left,"header overlap");previous=r;}});});
        check("minimal music retains original width; weather, volume and controls fit",()->{main(()->activity.showPage(MainActivity.MINIMAL));Thread.sleep(180);main(()->{View root=activity.getWindow().getDecorView(),music=find(root,"简洁页无边框音乐区"),parent=(View)music.getParent();fits(music);require(Math.abs(music.getWidth()-(parent.getWidth()-activity.u(54))*.57f)<=2,"original music width changed");for(String label:new String[]{"当前歌曲封面","打开QQ音乐","媒体音量滑块","播放 / 暂停"})fits(find(music,label));for(int n=1;n<=4;n++)fits(find(music,"歌词第"+n+"行"));fits(find(root,"首页天气"));noHints(root);});});capture("v0510-adaptive-minimal.png");
        check("application pages adapt capacity with every icon and label fully visible",()->{
            main(()->{try{Field f=MainActivity.class.getDeclaredField("applications");f.setAccessible(true);@SuppressWarnings("unchecked") ArrayList<android.content.pm.ResolveInfo> apps=(ArrayList<android.content.pm.ResolveInfo>)f.get(activity);android.content.pm.ResolveInfo source=Ui.apps(activity).get(0);apps.clear();for(int n=0;n<100;n++){android.content.pm.ResolveInfo item=new android.content.pm.ResolveInfo(source);item.nonLocalizedLabel="测试应用 "+(n+1);apps.add(item);}activity.showPage(MainActivity.APPS);}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(200);
            main(()->{try{Field f=MainActivity.class.getDeclaredField("grid");f.setAccessible(true);GridView grid=(GridView)f.get(activity);require(activity.pageCount()>3,"no extra app page");require(grid.getLastVisiblePosition()==grid.getAdapter().getCount()-1,"overflowed app grid");for(int n=0;n<grid.getChildCount();n++){ViewGroup item=(ViewGroup)grid.getChildAt(n);fits(item);for(int k=0;k<item.getChildCount();k++)fits(item.getChildAt(k));}report.append("  grid columns=").append(grid.getNumColumns()).append(" capacity=").append(grid.getAdapter().getCount()).append(" pages=").append(activity.pageCount()).append('\n');}catch(Exception e){throw new RuntimeException(e);}});
        });main(()->{View root=activity.getWindow().getDecorView();fits(weatherChip("headerWeather"));fits(find(root,"应用页日期与天气"));TextView state=(TextView)find(root,"蓝牙和网络状态");fits(state);require(!state.getText().toString().contains("%")&&!state.getText().toString().contains("▰"),"battery remains");});capture("v0510-adaptive-apps.png");
        check("settings categories and recovery switch accessible",()->{
            SettingsActivity settings=(SettingsActivity)test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));try{Thread.sleep(250);main(()->{View root=settings.getWindow().getDecorView();require(find(root,"主题设置默认背景")!=null&&find(root,"主题设置默认背景").isShown(),"default settings wallpaper missing");for(String name:new String[]{"背景","全局配色","文字与字号","卡片与布局","音乐与歌词","时间与天气","图标与快捷","关联与启动","备份与维护"}){View v=find(root,"设置分类："+name);require(v!=null&&v.isShown(),"missing category");v.requestRectangleOnScreen(new Rect(0,0,v.getWidth(),v.getHeight()),true);Rect visible=new Rect();require(v.getGlobalVisibleRect(visible)&&visible.equals(rect(v)),"clipped category "+name);}find(root,"设置分类：关联与启动").performClick();require(find(settings.getWindow().getDecorView(),"启动桌面时启动高德地图和 QQ音乐")!=null,"missing recovery toggle");});Thread.sleep(180);captureActivity=settings;capture("v0510-adaptive-settings.png");}finally{captureActivity=null;main(settings::finish);}
        });
    }
    private void personalizationChecks(Config config)throws Exception{
        check("individual widths and maximum lyric size keep player controls visible",()->{
            main(()->{config.put("quickWidth",140);config.put("transportWidth",75);config.put("lyricSize",145);config.put("minimalMusicWidth",70);config.put("clockSize",115);config.put("weatherPillAlpha",70);config.put("mapShare",60);config.put("swap",false);activity.showPage(1);activity.render();});Thread.sleep(180);
            main(()->{View root=activity.getWindow().getDecorView(),music=find(root,"QQ音乐卡片");for(String name:new String[]{"居中播放控制栏","歌曲播放进度，可点击或拖动","歌曲信息","歌词第1行"})fits(find(music,name));ViewGroup controls=(ViewGroup)find(music,"居中播放控制栏");require(controls.getWidth()<=activity.u(460)*.75f+1,"transport width ignored");});capture("v0510-adaptive-personalized-card.png");
            main(()->activity.showPage(0));Thread.sleep(150);main(()->{View root=activity.getWindow().getDecorView(),music=find(root,"简洁页无边框音乐区"),lane=(View)music.getParent();fits(music);require(Math.abs(music.getWidth()-lane.getWidth()*.70f)<=2&&music.getRight()==lane.getWidth(),"minimal width or alignment ignored");for(String label:new String[]{"媒体音量滑块","歌曲信息","播放 / 暂停"})fits(find(music,label));});capture("v0510-adaptive-personalized-minimal.png");
        });
        check("compact clock spans fit and song information dialog adapts to this screen",()->{
            main(()->{config.put("quickWidth",80);try{config.put("quickLayout",QuickGrid.resize(config,"clock",1,1));}catch(Exception e){throw new RuntimeException(e);}activity.showPage(1);activity.render();});Thread.sleep(160);capture("v0510-adaptive-compact-clock.png");main(()->{View root=activity.getWindow().getDecorView();fits(find(root,"快捷区时间日期，横跨两列"));fits(find(root,"快捷区时间"));fits(find(root,"快捷区日期"));fits(weatherChip("quickWeather"));find(root,"歌曲信息").performClick();try{java.lang.reflect.Field f=MusicCard.class.getDeclaredField("songInfo");f.setAccessible(true);SongInfoDialog dialog=(SongInfoDialog)f.get(find(root,"QQ音乐卡片"));captureRoot=dialog.getWindow().getDecorView();}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(160);capture("v0510-adaptive-song-info.png");main(()->{fits(find(captureRoot,"关闭歌曲信息"));fits(find(captureRoot,"歌曲信息打开QQ音乐"));fits(find(captureRoot,"歌曲信息歌名"));});main(()->{find(captureRoot,"关闭歌曲信息").performClick();captureRoot=null;});
        });
    }
    private void recoveryChecks(Config config)throws Exception{
        check("saved association and recovery switch survive config recreation",()->{config.put("autoRecoverApps",true);String backup=config.exportJson();config.put("autoRecoverApps",false);config.restore(backup);Config again=new Config(test.getTargetContext());require(again.b("autoRecoverApps",false)&&AmapFloatingController.selected(again),"association lost");});
        check("background intents use only explicit exported service contracts",()->{Intent qq=BackgroundRecovery.serviceIntent(true),map=BackgroundRecovery.serviceIntent(false);require(BackgroundRecovery.QQ_SERVICE.equals(qq.getComponent().getClassName())&&BackgroundRecovery.MAP_SERVICE.equals(map.getComponent().getClassName()),"wrong target");require("cmd_rebind_main_service".equals(qq.getStringExtra("cmd")),"missing rebind");require(qq.getFlags()==0&&map.getFlags()==0,"activity flags present");require(QqMusicBridge.subscriptionIntent().getIntExtra("action",-1)==100&&QqMusicBridge.subscriptionIntent().getData()==null,"subscription could navigate");});
        check("prewarm retries failed service, keeps successful service and stops on pause",()->{
            List<Intent> calls=Collections.synchronizedList(new ArrayList<>());final BackgroundRecovery[] controller={null};main(()->{config.put("autoRecoverApps",true);config.put("musicPackage",Config.MUSIC);controller[0]=new BackgroundRecovery(config,i->{calls.add(i);return !Config.MUSIC.equals(i.getComponent().getPackageName());},()->{});controller[0].resume();});Thread.sleep(1800);main(controller[0]::pause);int size=calls.size();require(size==3,"expected initial pair plus one retry: "+size);Thread.sleep(3200);require(calls.size()==size,"request after pause");
        });
        check("disabled recovery starts nothing; fixed map and other music selection stay distinct",()->{
            final int[] count={0};final BackgroundRecovery[] other={null};main(()->{config.put("autoRecoverApps",false);BackgroundRecovery off=new BackgroundRecovery(config,i->{count[0]++;return true;},()->{});off.resume();});Thread.sleep(100);require(count[0]==0,"disabled recovery started app");main(()->{config.put("autoRecoverApps",true);config.put("mapPackage",Config.MAP);config.put("musicPackage","other.music");other[0]=new BackgroundRecovery(config,i->{count[0]++;return true;},()->{});other[0].resume();});Thread.sleep(100);main(other[0]::pause);require(count[0]==1,"fixed 6.5 map should prewarm, other music must not start");
        });
        check("temporary missing widget provider retains persistent widget id",()->main(()->{config.put("mapWidget",987654);config.put("mapPackage",Config.MAP);WidgetCoordinator widgets=new WidgetCoordinator(activity,config);require(widgets.view(false)==null&&config.n("mapWidget",-1)==987654,"persistent widget binding was deleted");}));
        check("last track restored as cached, without claiming active playback",()->{
            config.p.edit().putString("qqLastTitle","恢复样例").putString("qqLastArtist","测试歌手").putLong("qqLastDuration",120000).putLong("qqLastPosition",15000).commit();final QqMusicBridge[] bridge={null};main(()->bridge[0]=new QqMusicBridge(test.getTargetContext(),()->{}));require("恢复样例".equals(bridge[0].title)&&bridge[0].position==15000&&!bridge[0].connected&&bridge[0].state==0,"cache falsely claimed playback");main(bridge[0]::stop);
        });
    }
}
