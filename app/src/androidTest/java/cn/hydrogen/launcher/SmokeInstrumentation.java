package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real-device/simulator instrumentation, no third-party test framework. */
public class SmokeInstrumentation extends Instrumentation {
    private final StringBuilder output=new StringBuilder();private boolean liveQq,liveWeather,liveLocation,smallWindows,containerInstall,amapFloating;private int passed,failed;private MainActivity activity;
    private Bundle taskArgs;private boolean deviceInfo;private boolean audit;private boolean seekSync;private boolean theme;private boolean revision;private boolean adaptive,recovery,windchaser,files,startupGuard;
    private interface Check {void run()throws Exception;}
    private void check(String title,Check body){try{body.run();passed++;output.append("PASS ");}catch(Throwable e){failed++;output.append("FAIL ").append(e.toString()).append(" · ");}output.append(title).append('\n');}
    private void require(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    @Override public void onCreate(Bundle args){super.onCreate(args);taskArgs=args;deviceInfo=args!=null&&"true".equals(args.getString("deviceInfo"));audit=args!=null&&"true".equals(args.getString("audit"));seekSync=args!=null&&"true".equals(args.getString("seekSync"));theme=args!=null&&"true".equals(args.getString("theme"));revision=args!=null&&"true".equals(args.getString("revision"));startupGuard=args!=null&&"true".equals(args.getString("startupGuard"));files=args!=null&&"true".equals(args.getString("files"));windchaser=args!=null&&"true".equals(args.getString("windchaser"));adaptive=args!=null&&"true".equals(args.getString("adaptive"));recovery=args!=null&&"true".equals(args.getString("recovery"));liveQq=args!=null&&"true".equals(args.getString("qqLive"));liveWeather=args!=null&&"true".equals(args.getString("weatherLive"));liveLocation=args!=null&&"true".equals(args.getString("locationLive"));smallWindows=args!=null&&"true".equals(args.getString("smallWindows"));containerInstall=args!=null&&"true".equals(args.getString("containerInstall"));amapFloating=args!=null&&"true".equals(args.getString("amapFloating"));start();}
    @Override public void onStart(){
        if(taskArgs!=null&&"true".equals(taskArgs.getString("firstMapSetup"))){new FirstMapSetupFlow(this,"off".equals(taskArgs.getString("mapPreference"))).run();return;}
        // Test-only prerequisite: use the production bootstrap and genuine dual
        // endpoint admission. Never set its ready flag from instrumentation.
        if(taskArgs!=null&&"true".equals(taskArgs.getString("unifiedReady"))){
            getTargetContext().startActivity(new Intent(getTargetContext(),UnifiedBootstrapActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long until=SystemClock.elapsedRealtime()+90000;boolean[] ready={false};
            while(SystemClock.elapsedRealtime()<until){runOnMainSync(()->ready[0]=UnifiedInitService.admitted());if(ready[0])break;try{Thread.sleep(200);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}}
            if(!ready[0]){Bundle result=new Bundle();result.putString("stream","FAIL production unified admission prerequisite: "+UnifiedInitService.status());finish(Activity.RESULT_CANCELED,result);return;}
        }
        if(taskArgs!=null&&"true".equals(taskArgs.getString("reverseReturn"))){new ReverseReturnFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("startupReturnAudit"))){new StartupReturnAuditFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("qqPlaybackAudit"))){new QqPlaybackAuditFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("unifiedLifecycleAudit"))){new UnifiedLifecycleAuditFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("videoLoop"))){new VideoLoopFlow(this,taskArgs).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("wallpaperDefaults"))){new WallpaperDefaultsFlow(this).run();return;}
        if(taskArgs!=null&&("true".equals(taskArgs.getString("qqReentry"))||"true".equals(taskArgs.getString("qqDiagnose")))){new QqReentryFlow(this,"true".equals(taskArgs.getString("qqDiagnose"))).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("qqObserve"))){new MusicObserveFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("weakNetwork"))){new WeakNetworkFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("refinement"))){new RefinementFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("tirePressure"))){new TirePressureFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("weatherTimeout"))){new WeatherTimeoutFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("webTransfer"))){runWebTransfer();return;}
        if(taskArgs!=null&&("true".equals(taskArgs.getString("pageEntry"))||"true".equals(taskArgs.getString("mapTimeline")))){new MapTimelineFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("mapRetentionGuard"))){new MapRetentionGuardFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("mapRetention"))){new MapRetentionFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("mapParkProbe"))){new MapParkProbe(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("compactControls"))){new CompactControlsFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("albumNetwork"))){new AlbumArtFlow(this).runLive();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("albumArt"))){new AlbumArtFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("mapReturn"))){new MapRecoveryFlow(this,true).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("bluetoothAssociation"))){new BluetoothAssociationFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("mapRecovery"))){new MapRecoveryFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("mapDialog"))){new AmapFloatingFlow(this,true).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("backgroundMusic"))){new BackgroundMusicFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("radius"))){new ThemeRadiusFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("memory"))){new MemoryFlow(this).run();return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("carFixes"))){new CarFixFlow(this).run("true".equals(taskArgs.getString("headerOnly")));return;}
        if(taskArgs!=null&&"true".equals(taskArgs.getString("mapSwitch"))){new MapSwitchFlow(this).run();return;}
        if(deviceInfo){new DeviceInfoFlow(this).run();return;}
        if(audit){new AuditFlow(this).run();return;}
        if(seekSync){new SeekSyncFlow(this).run();return;}
        if(theme){new ThemeFlow(this).run();return;}
        if(revision){new RevisionFlow(this).run();return;}
        if(startupGuard){new StartupGuardFlow(this).run();return;}
        if(files){new FileCenterFlow(this).run();return;}if(windchaser){new WindChaserFlow(this).run();return;}
        if(adaptive||recovery){new AdaptiveFlow(this,recovery).run();return;}
        if(amapFloating){new AmapFloatingFlow(this).run();return;}
        if(containerInstall){new ContainerInstallFlow(this).run();return;}
        if(smallWindows){runSmallWindows();return;}
        if(liveQq){runLiveQq();return;}
        if(liveWeather){runWeatherLive();return;}
        if(liveLocation){runLocationLive();return;}
        Config c=new Config(getTargetContext());String backup="",wallpaperKind=c.s("wallpaperKind","");
        try{
            backup=c.exportJson();
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();
            check("native launcher starts",()->require(activity!=null&&!activity.isFinishing(),"activity not alive"));
            check("real application list, six measured columns",()->{runOnMainSync(()->activity.showPage(MainActivity.APPS));Thread.sleep(800);waitForIdleSync();GridView g=grid();require(g.getNumColumns()==6,"columns="+g.getNumColumns());require(g.getAdapter().getCount()>0,"empty application list");});
            check("left swipe opens app page",()->{runOnMainSync(()->activity.showPage(MainActivity.CARDS));swipe(true);require(grid().getVisibility()==View.VISIBLE,"swipe did not change page");});
            check("right swipe returns home",()->{swipe(false);require(grid().getVisibility()==View.GONE,"swipe did not return home");});
            check("nine permanent dock buttons",()->{for(String text:new String[]{"主页","音量减","静音","音量加","主题设置"})require(desc(activity.getWindow().getDecorView(),text)!=null,"missing "+text);});
            check("volume and mute use real media stream",()->{
                AudioManager a=(AudioManager)getTargetContext().getSystemService(Context.AUDIO_SERVICE);int before=a.getStreamVolume(AudioManager.STREAM_MUSIC),max=a.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                try{a.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(1,max/2),0);int v=a.getStreamVolume(AudioManager.STREAM_MUSIC);clickDesc("音量减");require(a.getStreamVolume(AudioManager.STREAM_MUSIC)==v-1,"volume down");clickDesc("音量加");require(a.getStreamVolume(AudioManager.STREAM_MUSIC)==v,"volume up");clickDesc("静音");require(a.getStreamVolume(AudioManager.STREAM_MUSIC)==0,"mute");clickDesc("已静音");require(a.getStreamVolume(AudioManager.STREAM_MUSIC)==v,"unmute");}finally{a.setStreamVolume(AudioManager.STREAM_MUSIC,before,0);}
            });
            check("valid config round-trip and invalid range rejection",()->{c.put("mapShare",42);String exported=c.exportJson();c.put("mapShare",65);c.restore(exported);require(c.n("mapShare",0)==42,"roundtrip");boolean rejected=false;try{c.restore("{\"format\":\"hydrogen-config\",\"version\":1,\"settings\":{\"mapShare\":999}}");}catch(Exception expected){rejected=true;}require(rejected&&c.n("mapShare",0)==42,"invalid import changed settings");});
            check("shortcut binds a real installed activity and persists",()->{android.content.pm.ResolveInfo app=Ui.apps(getTargetContext()).get(0);String component=Ui.component(app);c.put("slot0",component);require(new Config(getTargetContext()).s("slot0","").equals(component),"shortcut not saved");runOnMainSync(()->activity.render());});
            check("sampled image wallpaper decodes",()->{
                File old=new File(getTargetContext().getFilesDir(),"wallpaper"),saved=new File(getTargetContext().getFilesDir(),"wallpaper.test-backup");if(old.exists()&&!old.renameTo(saved))throw new IOException("cannot preserve wallpaper");
                try{Bitmap b=Bitmap.createBitmap(1600,900,Bitmap.Config.RGB_565);b.eraseColor(0xff153566);try(FileOutputStream f=new FileOutputStream(old)){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();c.put("wallpaperKind","image");runOnMainSync(()->activity.render());boolean[] decoded={false};for(int n=0;n<25&&!decoded[0];n++){Thread.sleep(200);runOnMainSync(()->{try{Field field=MainActivity.class.getDeclaredField("wallpaper");field.setAccessible(true);WallpaperLayer layer=(WallpaperLayer)field.get(activity);Field bitmap=WallpaperLayer.class.getDeclaredField("bitmap");bitmap.setAccessible(true);decoded[0]=bitmap.get(layer)!=null;}catch(Exception e){throw new RuntimeException(e);}});}require(decoded[0],"image failed to decode");}finally{c.put("wallpaperKind",wallpaperKind);old.delete();if(saved.exists())saved.renameTo(old);runOnMainSync(()->activity.render());}
            });
            check("real MP4 wallpaper loops with MediaPlayer",()->{
                File old=new File(getTargetContext().getFilesDir(),"wallpaper"),saved=new File(getTargetContext().getFilesDir(),"wallpaper.test-backup");if(old.exists()&&!old.renameTo(saved))throw new IOException("cannot preserve wallpaper");
                try{try(InputStream in=getContext().getAssets().open("wallpaper-test.mp4");OutputStream out=new FileOutputStream(old)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}c.put("wallpaperKind","video");runOnMainSync(()->activity.render());Thread.sleep(3400);waitForIdleSync();Field field=MainActivity.class.getDeclaredField("wallpaper");field.setAccessible(true);WallpaperLayer layer=(WallpaperLayer)field.get(activity);Field player=WallpaperLayer.class.getDeclaredField("player");player.setAccessible(true);android.media.MediaPlayer media=(android.media.MediaPlayer)player.get(layer);require(media!=null&&media.isPlaying()&&media.isLooping(),"video is not playing and looping");}finally{c.put("wallpaperKind",wallpaperKind);runOnMainSync(()->activity.render());old.delete();if(saved.exists())saved.renameTo(old);}
            });
            check("settings opens using native scrollable controls",()->{Activity settings=startActivitySync(new Intent(getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();require(settings.getWindow().getDecorView()!=null,"no settings window");runOnMainSync(settings::finish);waitForIdleSync();});
            check("both private built-in module assets match their declared hashes",()->{
                org.json.JSONObject spec;try(InputStream in=getTargetContext().getAssets().open("unified/modules.json")){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);spec=new org.json.JSONObject(out.toString("UTF-8"));}
                org.json.JSONArray modules=spec.getJSONArray("modules");require(modules.length()==2,"module count");
                for(int i=0;i<modules.length();i++){org.json.JSONObject module=modules.getJSONObject(i);java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long size=0;try(InputStream in=getTargetContext().getAssets().open("unified/"+module.getString("file"))){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){digest.update(b,0,n);size+=n;}}StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.US,"%02x",b&255));require(size==module.getLong("bytes")&&hash.toString().equals(module.getString("sha256")),"module identity: "+module.getString("package"));}
            });
            check("private unified module manager remains connected",()->require(UnifiedRuntime.ready(),"module manager disconnected"));
            check("diagnostics opens and includes system version",()->{Activity diagnostics=startActivitySync(new Intent(getTargetContext(),DiagnosticsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();require(text(diagnostics.getWindow().getDecorView(),"API "+Build.VERSION.SDK_INT),"missing API diagnosis");runOnMainSync(diagnostics::finish);waitForIdleSync();});
            check("minimal home hides chrome and keeps native controls reachable",()->{
                runOnMainSync(()->activity.showPage(MainActivity.MINIMAL));waitForIdleSync();
                require(text(activity.getWindow().getDecorView(),"QQ音乐"),"music card missing");
                View play=desc(activity.getWindow().getDecorView(),"播放 / 暂停");require(play!=null,"native transport missing");
                View homeButton=desc(activity.getWindow().getDecorView(),"打开QQ音乐");require(homeButton!=null&&homeButton.getWidth()>0&&homeButton.getHeight()>0,"QQ home button missing");
                Field f=MainActivity.class.getDeclaredField("standardContent");f.setAccessible(true);require(((View)f.get(activity)).getVisibility()==View.GONE,"standard dock still visible");
                Field m=MainActivity.class.getDeclaredField("minimalPage");m.setAccessible(true);View min=(View)m.get(activity);require(min.getVisibility()==View.VISIBLE&&min.getWidth()>0,"minimal page not laid out");
                swipe(true);require(grid().getVisibility()==View.GONE,"minimal left swipe should return card home");
                runOnMainSync(()->activity.showPage(MainActivity.CARDS));
            });
            check("QQ explicit broadcast commands target original receiver",()->{
                int[] indices={5,4,12,13};for(int command=0;command<indices.length;command++){Intent intent=QqMusicBridge.commandIntent(command);require(Config.MUSIC.equals(intent.getComponent().getPackageName()),"wrong package");require(intent.getData()==null&&QqMusicBridge.WEDRIVE_ACTION.equals(intent.getAction())&&intent.getComponent().getClassName().endsWith("BroadcastReceiverCenterForWeDrive"),"controls must bypass Activity-dependent URI dispatch");require(new org.json.JSONObject(intent.getStringExtra(QqMusicBridge.WEDRIVE_EXTRA)).getJSONObject("command").getJSONObject("extData").getInt("index")==indices[command],"wrong direct player command "+command);}
            });
            check("borderless minimal music shows cover and real adjustable volume",()->{
                runOnMainSync(()->activity.showPage(MainActivity.MINIMAL));waitForIdleSync();View min=desc(activity.getWindow().getDecorView(),"简洁页无边框音乐区");require(min!=null&&min.getBackground()==null,"minimal music still has a container background");
                require(desc(min,"当前歌曲封面")!=null,"minimal cover missing");require(desc(activity.getWindow().getDecorView(),"首页天气")!=null,"weather missing");
                AudioManager audio=(AudioManager)getTargetContext().getSystemService(Context.AUDIO_SERVICE);int before=audio.getStreamVolume(AudioManager.STREAM_MUSIC),max=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.max(1,max/2),0);int v=audio.getStreamVolume(AudioManager.STREAM_MUSIC);clickDesc("简洁页音量减");require(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==v-1,"minimal volume down failed");clickDesc("简洁页音量加");require(audio.getStreamVolume(AudioManager.STREAM_MUSIC)==v,"minimal volume up failed");View slider=desc(min,"媒体音量滑块");require(slider instanceof SeekBar&&slider.getWidth()>0,"volume slider missing");}finally{audio.setStreamVolume(AudioManager.STREAM_MUSIC,before,0);}
                runOnMainSync(()->activity.showPage(MainActivity.CARDS));require(desc(activity.getWindow().getDecorView(),"当前歌曲封面")!=null,"card cover missing");
            });
            check("weather rejects missing data and invalid imported coordinates",()->{
                boolean rejected=false;try{new WeatherController.Reading(new org.json.JSONObject("{\"current\":{\"temperature_2m\":null},\"daily\":{}}"));}catch(Exception expected){rejected=true;}require(rejected,"missing weather became a reading");
                WeatherController.City z=WeatherController.City.parse(WeatherController.DEFAULT_CITY);require(z.label.equals("北京市")&&z.shortLabel().equals("北京市")&&Math.abs(z.lat-39.9075)<0.000001&&Math.abs(z.lon-116.39723)<0.000001,"wrong default city");
                rejected=false;try{WeatherController.City.parse("{\"label\":\"bad\",\"lat\":999,\"lon\":0}");}catch(Exception expected){rejected=true;}require(rejected,"invalid coordinate accepted");
                c.put("weatherCity",z.json());c.put("weatherAuto",true);String settings=c.exportJson();c.restore(settings);require(c.b("weatherAuto",false)&&c.s("weatherCity","").equals(z.json()),"weather settings lost");c.put("weatherAuto",false);
            });
            check("cover accepts QQ CDN and clears previous song artwork",()->{
                require(AlbumArt.safeUrl("http://y.gtimg.cn/music/photo.jpg").startsWith("https://"),"QQ HTTPS upgrade failed");require(AlbumArt.safeUrl("file:///data/private.jpg").isEmpty()&&AlbumArt.safeUrl("https://127.0.0.1/a.jpg").isEmpty(),"unsafe cover source accepted");
                QqMusicBridge b=new QqMusicBridge(getTargetContext(),()->{});b.cover.bitmap=Bitmap.createBitmap(2,2,Bitmap.Config.RGB_565);b.accept("{\"module\":\"play\",\"command\":{\"method\":\"update_song\",\"data\":{\"key_title\":\"new song\",\"key_artist\":[]}}}");require(b.cover.bitmap==null,"old album art survived song change");b.stop();
            });
            check("LRC parsing supports multiple tags, offset and millisecond precision",()->{
                ArrayList<QqMusicBridge.Line> lines=QqMusicBridge.parseLrc("[offset:-100]\n[00:02.50][00:03.005]second\n[00:00.1]first\n[ar:artist]\nmalformed");
                require(lines.size()==3&&lines.get(0).time==0&&lines.get(1).time==2400&&lines.get(2).time==2905,"LRC timing incorrect");require(QqMusicBridge.parseQqLrc("[00:02.50]QQ").get(0).time==2050,"QQ SimpleDateFormat SS must preserve milliseconds");
            });
            check("QQ parser follows real nested artist schema and clears stale lyrics",()->{
                QqMusicBridge b=new QqMusicBridge(getTargetContext(),()->{});
                b.accept("{\"module\":\"play\",\"command\":{\"method\":\"update_lyric\",\"data\":{\"key_title\":\"test A\",\"key_artist\":[{\"singer\":\"artist A\"}],\"curr_time\":2200,\"total_time\":60000,\"code\":0,\"lyric\":\"[00:00.00]one\\n[00:02.00]two\"}}}");
                require(b.artist.equals("artist A")&&b.lyricLines()[0].equals("two"),"real payload schema failed");
                b.accept("{\"module\":\"play\",\"command\":{\"method\":\"update_song\",\"data\":{\"key_title\":\"test B\",\"key_artist\":[{\"singer\":\"artist B\"}]}}}");
                require(b.lyrics.isEmpty()&&b.duration==0,"stale lyric survived track change");
                b.accept("{\"module\":\"play\",\"command\":{\"method\":\"update_lyric\",\"data\":{\"code\":-1,\"lyric\":\"\"}}}");
                require(b.lyrics.isEmpty()&&b.lyricMessage.contains("稍后重试"),"unavailable lyric not reported");
            });
            check("eight repeated activity recreations retain config",()->{c.put("mapShare",57);for(int n=0;n<8;n++){final MainActivity previous=activity;runOnMainSync(previous::finish);waitForIdleSync();activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();require(c.n("mapShare",0)==57,"lost config");}require(!activity.isFinishing(),"activity dead");});
        }catch(Throwable e){failed++;output.append("FAIL suite ").append(e).append('\n');}
        finally{try{if(!backup.isEmpty())c.restore(backup);c.put("wallpaperKind",wallpaperKind);if(activity!=null)runOnMainSync(()->{activity.render();activity.showPage(MainActivity.CARDS);});}catch(Exception e){output.append("restore error ").append(e);}}
        output.insert(0,"Android "+Build.VERSION.RELEASE+" API="+Build.VERSION.SDK_INT+" ABI="+Build.CPU_ABI+"\n");output.append("RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');
        try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(getTargetContext().getFilesDir(),"smoke-report.txt")),"UTF-8")){w.write(output.toString());}catch(Exception ignored){}
        Bundle result=new Bundle();result.putString("stream",output.toString());finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private interface Condition {boolean ok();}
    private void runSmallWindows(){
        StringBuilder report=new StringBuilder("Native overlay and QQ app launch integration; no embedded Activity claim\n");boolean ok=false;Config cfg=new Config(getTargetContext());int oldMode=cfg.n("musicMode",0);
        try{
            if(Build.VERSION.SDK_INT>=18){android.accessibilityservice.AccessibilityServiceInfo automation=getUiAutomation().getServiceInfo();automation.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS|android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;if(Build.VERSION.SDK_INT>=21)automation.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(automation);}
            cfg.put("musicMode",0);activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();
            if(Build.VERSION.SDK_INT>=21&&Ui.installed(getTargetContext(),Config.MUSIC)){
                for(int page:new int[]{MainActivity.CARDS,MainActivity.MINIMAL}){
                    runOnMainSync(()->getTargetContext().startActivity(QqNavigation.homeIntent().setData(android.net.Uri.parse("qqmusiccar://qqmusic.com/PlayerFragment"))));await(()->externalId("com.tencent.qqmusiccar:id/qqMusicCarSeekBar"),15);report.append("Verified original QQ player before page ").append(page).append('\n');
                    Field transport=MainActivity.class.getDeclaredField("musicBridge");transport.setAccessible(true);QqMusicBridge continuing=(QqMusicBridge)transport.get(activity);runOnMainSync(()->continuing.command(0));await(()->continuing.state==2&&!continuing.title.isEmpty(),15);String playingSong=continuing.title;long requestedAt=SystemClock.elapsedRealtime();
                    runOnMainSync(()->getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));Thread.sleep(500);runOnMainSync(()->activity.showPage(page));waitForIdleSync();Thread.sleep(500);saveScreen("qq-app-button-page-"+page+".png");tapVisibleHome(page);
                    await(()->externalId("com.tencent.qqmusiccar:id/tv_greeting")||externalId("com.tencent.qqmusiccar:id/qqMusicCarSeekBar"),15);report.append("PASS launcher page ").append(page).append(" icon -> QQ application\n");
                    runOnMainSync(continuing::subscribe);await(()->continuing.state==2&&continuing.receivedAt>=requestedAt&&continuing.title.equals(playingSong),15);long p=continuing.currentPosition();Thread.sleep(1300);require(continuing.currentPosition()>p+500,"playback stopped while returning home");report.append("PASS QQ app launch keeps same song playing with fresh real state\n");runOnMainSync(()->continuing.command(1));await(()->continuing.state==3,15);
                }
            }
            runOnMainSync(()->getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));Thread.sleep(700);
            final boolean[] started={false};runOnMainSync(()->started[0]=FloatingMusicService.show(activity));require(started[0],"overlay authorization unavailable");Thread.sleep(2500);require(floatingRunning(),"foreground overlay not running");report.append("PASS foreground floating service starts on API ").append(Build.VERSION.SDK_INT).append('\n');
            if(Build.VERSION.SDK_INT>=21&&Ui.installed(getTargetContext(),Config.MUSIC)){
                android.accessibilityservice.AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();info.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(info);
                runOnMainSync(()->Ui.launch(activity,"com.autonavi.amapauto"));Thread.sleep(1800);
                android.view.accessibility.AccessibilityNodeInfo activeRoot=getUiAutomation().getRootInActiveWindow();if(activeRoot!=null&&String.valueOf(activeRoot.getPackageName()).contains("permissioncontroller")){sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(700);report.append("Deferred emulator notification-permission prompt with BACK; no permission granted\n");}
                await(()->overlayNode("悬浮歌曲歌词")!=null,10);await(()->{android.view.accessibility.AccessibilityNodeInfo n=overlayNode("悬浮歌曲歌词");return n!=null&&n.getText()!=null&&!n.getText().toString().contains("等待");},20);
                Field bridgeField=MainActivity.class.getDeclaredField("musicBridge");bridgeField.setAccessible(true);QqMusicBridge realBridge=(QqMusicBridge)bridgeField.get(activity);runOnMainSync(()->realBridge.command(1));await(()->realBridge.state==3,15);tapOverlay("悬浮播放 / 暂停");await(()->realBridge.state==2,15);tapOverlay("悬浮播放 / 暂停");await(()->realBridge.state==3,15);report.append("PASS floating touch play/pause confirmed by real QQ states 2 -> 3\n");
                android.view.accessibility.AccessibilityNodeInfo drag=overlayNode("拖动悬浮音乐");android.graphics.Rect before=new android.graphics.Rect();drag.getBoundsInScreen(before);long now=SystemClock.uptimeMillis();int x=before.centerX(),y=before.centerY();int dx=before.left>160?-110:110;
                for(int n=0;n<7;n++){int action=n==0?MotionEvent.ACTION_DOWN:n==6?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;MotionEvent e=MotionEvent.obtain(now,now+n*30,action,x+dx*n/6f,y+40*n/6f,0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();}Thread.sleep(500);
                android.graphics.Rect after=new android.graphics.Rect();overlayNode("拖动悬浮音乐").getBoundsInScreen(after);require(Math.abs(after.left-before.left)>40,"drag did not move overlay");report.append("PASS overlay visible above original Amap; real lyric received; touch controls and drag executed\n");
            }
            if(Build.VERSION.SDK_INT>=19)saveScreen("floating-live.png");
            if(Build.VERSION.SDK_INT>=21&&Ui.installed(getTargetContext(),Config.MUSIC))tapOverlay("关闭悬浮音乐");else runOnMainSync(()->getTargetContext().stopService(new Intent(getTargetContext(),FloatingMusicService.class)));Thread.sleep(700);require(!floatingRunning(),"floating service survived close");report.append("PASS close removes foreground service\n");ok=true;
        }catch(Throwable e){report.append("FAIL ").append(e).append('\n');if(Build.VERSION.SDK_INT>=18)try{Bitmap shot=getUiAutomation().takeScreenshot();try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),"floating-failure.png"))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();if(Build.VERSION.SDK_INT>=21)report.append("Accessibility windows: ").append(getUiAutomation().getWindows()).append('\n');}catch(Exception diagnostic){report.append(diagnostic).append('\n');}}finally{getTargetContext().stopService(new Intent(getTargetContext(),FloatingMusicService.class));cfg.put("musicMode",oldMode);}
        report.append("SMALL_WINDOWS_RESULT=").append(ok?"PASS":"FAIL").append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private boolean floatingRunning(){for(android.app.ActivityManager.RunningServiceInfo s:((android.app.ActivityManager)getTargetContext().getSystemService(Context.ACTIVITY_SERVICE)).getRunningServices(100))if(s.service.getClassName().equals(FloatingMusicService.class.getName())&&s.foreground)return true;return false;}
    private void saveScreen(String name)throws IOException{Bitmap shot=getUiAutomation().takeScreenshot();require(shot!=null,"no screenshot "+name);try(OutputStream out=new FileOutputStream(new File(getTargetContext().getFilesDir(),name))){shot.compress(Bitmap.CompressFormat.PNG,100,out);}shot.recycle();}
    private boolean externalId(String id){return containsId(getUiAutomation().getRootInActiveWindow(),id);}
    private boolean containsId(android.view.accessibility.AccessibilityNodeInfo n,String id){if(n==null)return false;if(id.equals(n.getViewIdResourceName()))return true;for(int i=0;i<n.getChildCount();i++)if(containsId(n.getChild(i),id))return true;return false;}
    private void tapVisibleHome(int page){int[] point=new int[2];runOnMainSync(()->{View card=desc(activity.getWindow().getDecorView(),page==MainActivity.MINIMAL?"简洁页无边框音乐区":"QQ音乐卡片");View v=desc(card,"打开QQ音乐");require(v!=null&&v.isShown(),"home button not visible on page "+page);v.getLocationOnScreen(point);point[0]+=v.getWidth()/2;point[1]+=v.getHeight()/2;});long now=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(now,now+40,action,point[0],point[1],0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();}}
    private android.view.accessibility.AccessibilityNodeInfo overlayNode(String label){for(android.view.accessibility.AccessibilityWindowInfo w:getUiAutomation().getWindows()){android.view.accessibility.AccessibilityNodeInfo n=findNode(w.getRoot(),label);if(n!=null)return n;}return null;}
    private android.view.accessibility.AccessibilityNodeInfo findNode(android.view.accessibility.AccessibilityNodeInfo n,String label){if(n==null)return null;if(label.contentEquals(n.getContentDescription()==null?"":n.getContentDescription()))return n;for(int i=0;i<n.getChildCount();i++){android.view.accessibility.AccessibilityNodeInfo found=findNode(n.getChild(i),label);if(found!=null)return found;}return null;}
    private void tapOverlay(String label){android.view.accessibility.AccessibilityNodeInfo n=overlayNode(label);require(n!=null,"missing overlay control "+label);android.graphics.Rect r=new android.graphics.Rect();n.getBoundsInScreen(r);long now=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(now,now+40,action,r.centerX(),r.centerY(),0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();}}
    private void runWeatherLive(){
        StringBuilder report=new StringBuilder("Real Open-Meteo HTTPS request on device; no synthetic readings\n");boolean ok=false;
        try{WeatherController.City city=WeatherController.City.parse(WeatherController.DEFAULT_CITY);String raw=WeatherHttp.get(getTargetContext(),"https://api.open-meteo.com/v1/forecast?latitude="+city.lat+"&longitude="+city.lon+"&current=temperature_2m,relative_humidity_2m,weather_code,is_day,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=Asia%2FShanghai");WeatherController.Reading r=new WeatherController.Reading(new org.json.JSONObject(raw));report.append("API=").append(Build.VERSION.SDK_INT).append(" city=").append(city.label).append(" time=").append(r.observed).append(" temperature=").append(r.temperature).append(" code=").append(r.code).append('\n');ok=true;}catch(Throwable e){report.append("FAIL ").append(e).append('\n');}
        report.append("WEATHER_LIVE_RESULT=").append(ok?"PASS":"FAIL").append('\n');try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(getTargetContext().getFilesDir(),"weather-live-report.txt")),"UTF-8")){w.write(report.toString());}catch(Exception ignored){}Bundle result=new Bundle();result.putString("stream",report.toString());finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void runLocationLive(){
        StringBuilder report=new StringBuilder("Emulator GNSS integration; coordinates injected with adb emu geo fix, not real car GPS\n");Config cfg=new Config(getTargetContext());boolean before=cfg.b("weatherAuto",false),ok=false;
        try{cfg.put("weatherAuto",true);activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();Field f=MainActivity.class.getDeclaredField("weather");f.setAccessible(true);WeatherController weather=(WeatherController)f.get(activity);await(()->weather.city.label.equals("当前位置"),15);require(Math.abs(weather.city.lat-26.06667)<0.01&&Math.abs(weather.city.lon-105.76667)<0.01,"unexpected emulator location");report.append("PASS system LocationManager callback -> current-location weather coordinates\n");Activity settings=startActivitySync(new Intent(getTargetContext(),WeatherSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();Field active=WeatherController.class.getDeclaredField("active");active.setAccessible(true);require(!active.getBoolean(weather),"weather remained active after leaving launcher");runOnMainSync(settings::finish);report.append("PASS location lifecycle pauses outside launcher\n");ok=true;}catch(Throwable e){report.append("FAIL ").append(e).append('\n');}finally{cfg.put("weatherAuto",before);}
        report.append("LOCATION_EMULATOR_RESULT=").append(ok?"PASS":"FAIL").append('\n');try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(getTargetContext().getFilesDir(),"location-emulator-report.txt")),"UTF-8")){w.write(report.toString());}catch(Exception ignored){}Bundle result=new Bundle();result.putString("stream",report.toString());finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private void await(Condition condition,int seconds)throws Exception{for(int i=0;i<seconds*5;i++){final boolean[] ok={false};runOnMainSync(()->ok[0]=condition.ok());if(ok[0])return;Thread.sleep(200);}throw new AssertionError("timed out after "+seconds+"s");}
    private void runLiveQq(){
        StringBuilder report=new StringBuilder("Real QQ Music integration; no synthetic broadcasts\n");boolean pass=false;
        try{
            android.content.pm.PackageInfo info=getTargetContext().getPackageManager().getPackageInfo(Config.MUSIC,0);require("2.1.1.1".equals(info.versionName),"different QQ version");
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();
            Field field=MainActivity.class.getDeclaredField("musicBridge");field.setAccessible(true);QqMusicBridge bridge=(QqMusicBridge)field.get(activity);
            runOnMainSync(()->{activity.showPage(MainActivity.CARDS);bridge.subscribe();});await(()->bridge.connected&&bridge.trackConfirmed&&!bridge.title.isEmpty(),30);
            if(taskArgs!=null&&"true".equals(taskArgs.getString("qqWarmSession"))){
                report.append("Warm-session prerequisite: deliberate pause before play; this is not a cold first-command guarantee.\n");
                Thread.sleep(3200);runOnMainSync(()->bridge.command(1));Thread.sleep(1200);
            }
            String first=bridge.title;report.append("Metadata received: ").append(first).append(" / ").append(bridge.artist).append('\n');
            runOnMainSync(()->bridge.command(0));await(()->bridge.state==2,20);long p=bridge.currentPosition();Thread.sleep(2200);require(bridge.currentPosition()>p+1000,"position did not advance");report.append("PASS in-card play, real QQ state=2, progress advances\n");
            await(()->!bridge.lyrics.isEmpty(),25);report.append("PASS real QQ LRC received; lines=").append(bridge.lyrics.size()).append('\n');
            await(()->bridge.cover.bitmap!=null,25);report.append("PASS automatic QQ album cover decoded: ").append(bridge.cover.bitmap.getWidth()).append('x').append(bridge.cover.bitmap.getHeight()).append('\n');
            String warmTrack=bridge.title;long beforeNative=bridge.currentPosition();
            runOnMainSync(()->Ui.launch(activity,Config.MUSIC));await(()->!activity.hasWindowFocus(),15);Thread.sleep(1000);
            runOnMainSync(()->getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));
            await(()->activity.hasWindowFocus()&&bridge.connected&&bridge.trackConfirmed&&bridge.state==2,20);
            require(warmTrack.equals(bridge.title)&&bridge.currentPosition()>=beforeNative,"return from actual QQ lost current playback");
            await(()->!bridge.lyrics.isEmpty()&&bridge.cover.bitmap!=null,25);
            report.append("PASS returning from native QQ restores desktop song, playback, lyrics and cover\n");
            runOnMainSync(()->bridge.command(1));await(()->bridge.state==3,15);long paused=pausedPositionAfterSettling(bridge);require(bridge.state==3&&Math.abs(bridge.currentPosition()-paused)<300,"paused progress is not stable after actual native samples settle");report.append("PASS in-card pause; QQ state=").append(bridge.state).append('\n');
            runOnMainSync(()->bridge.command(2));await(()->!bridge.title.equals(first),25);report.append("PASS previous track: ").append(bridge.title).append('\n');
            runOnMainSync(()->{activity.showPage(MainActivity.MINIMAL);bridge.command(0);});await(()->bridge.state==2&&!bridge.lyrics.isEmpty(),25);report.append("PASS minimal page shares playback and real lyrics\n");
            await(()->bridge.cover.bitmap!=null,25);report.append("PASS minimal page receives current cover after previous track\n");
            report.append("Final track: ").append(bridge.title).append(" / ").append(bridge.artist).append("; lyric lines=").append(bridge.lyrics.size()).append('\n');runOnMainSync(()->bridge.command(1));await(()->bridge.state==3,15);pass=true;
        }catch(Throwable e){report.append("FAIL ").append(e).append('\n');}
        report.append("QQ_LIVE_RESULT=").append(pass?"PASS":"FAIL").append('\n');
        try(Writer w=new OutputStreamWriter(new FileOutputStream(new File(getTargetContext().getFilesDir(),"qq-live-report.txt")),"UTF-8")){w.write(report.toString());}catch(Exception ignored){}
        Bundle result=new Bundle();result.putString("stream",report.toString());finish(pass?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    /** Native QQ can report its final decoder position after the first pause ACK. */
    private long pausedPositionAfterSettling(QqMusicBridge bridge)throws Exception{long began=SystemClock.elapsedRealtime(),stable=began,last=bridge.currentPosition();while(SystemClock.elapsedRealtime()-began<6000){require(bridge.state==3,"QQ resumed without a play command");long now=SystemClock.elapsedRealtime(),current=bridge.currentPosition();if(Math.abs(current-last)>=250)stable=now;last=current;if(now-began>=2500&&now-stable>=1200)return current;Thread.sleep(100);}throw new AssertionError("actual paused position did not settle");}
    private GridView grid(){return findGrid(activity.getWindow().getDecorView());}
    private GridView findGrid(View v){if(v instanceof GridView)return(GridView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){GridView g=findGrid(((ViewGroup)v).getChildAt(n));if(g!=null)return g;}return null;}
    private View desc(View v,String label){if(label.equals(String.valueOf(v.getContentDescription())))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View found=desc(((ViewGroup)v).getChildAt(n),label);if(found!=null)return found;}return null;}
    private void clickDesc(String text){runOnMainSync(()->{View b=desc(activity.getWindow().getDecorView(),text);require(b!=null,"button not found");b.performClick();});waitForIdleSync();}
    private boolean text(View v,String part){if(v instanceof TextView&&((TextView)v).getText().toString().contains(part))return true;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++)if(text(((ViewGroup)v).getChildAt(n),part))return true;return false;}
    private void swipe(boolean left){runOnMainSync(()->{View decor=activity.getWindow().getDecorView();int w=decor.getWidth();float start=w*(left?.8f:.2f),end=w*(left?.2f:.8f),y=activity.u(45);long time=SystemClock.uptimeMillis();dispatch(decor,MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,start,y,0));for(int n=1;n<=12;n++)dispatch(decor,MotionEvent.obtain(time,time+n*20,MotionEvent.ACTION_MOVE,start+(end-start)*n/12,y,0));dispatch(decor,MotionEvent.obtain(time,time+260,MotionEvent.ACTION_UP,end,y,0));});waitForIdleSync();}
    private void dispatch(View view,MotionEvent event){try{view.dispatchTouchEvent(event);}finally{event.recycle();}}
    private void runWebTransfer(){
        TransferServer server=null;File folder=null,stop=new File(getTargetContext().getFilesDir(),"audit-transfer-stop"),ready=new File(getTargetContext().getFilesDir(),"audit-transfer-ready");
        Bundle result=new Bundle();boolean ok=false;
        try{stop.delete();ready.delete();folder=new File(StorageFiles.root(getTargetContext()),"WindChaser-WebAudit-"+System.currentTimeMillis());if(!folder.mkdir())throw new IOException("fixture mkdir");server=new TransferServer(getTargetContext(),folder,()->{});server.start(8888);try(FileOutputStream out=new FileOutputStream(ready)){out.write(folder.getAbsolutePath().getBytes("UTF-8"));}long until=SystemClock.elapsedRealtime()+600000;while(!stop.exists()&&SystemClock.elapsedRealtime()<until)Thread.sleep(200);result.putString("stream","WEB_TRANSFER_RESULT actual production HTTP / HTTPS server stopped");ok=true;
        }catch(Exception e){result.putString("stream","FAIL web transfer fixture "+e);}finally{if(server!=null)server.close();ready.delete();stop.delete();if(folder!=null)try{StorageFiles.delete(StorageFiles.root(getTargetContext()),folder,0);}catch(Exception cleanup){ok=false;result.putString("stream","FAIL web transfer fixture cleanup "+cleanup);}}
        // Instrumentation.finish terminates the target process, so clean up before calling it.
        finish(ok?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);

    }
}

