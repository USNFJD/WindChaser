package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.content.pm.*;
import android.media.MediaPlayer;
import java.io.*;
import java.lang.reflect.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/** Regression cases discovered by reviewing the 0.6.0 implementation. */
final class AuditFlow extends RevisionFlow {
    AuditFlow(Instrumentation test){super(test);}
    @Override void run(){
        Config cfg=new Config(test.getTargetContext());Map<String,?> saved=new HashMap<>(cfg.p.getAll());
        try{
            cfg.put("autoRecoverApps",false);cfg.put("pageAnimation",false);cfg.put("homePage",1);cfg.put("pageInset",36);
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));Thread.sleep(900);
            check("custom horizontal inset survives every page change",()->main(()->{for(int page:new int[]{1,2,0,1}){activity.showPage(page);View content=(View)field(activity,"standardContent");require(content.getPaddingLeft()==activity.u(36)&&content.getPaddingRight()==activity.u(36),"custom inset overwritten on page "+page);}}));
            check("largest app fonts receive enough row height and pagination space",()->{
                main(()->{cfg.put("textScale",135);cfg.put("appTextSize",140);cfg.put("fontSize",28);activity.render();activity.showPage(2);});Thread.sleep(400);
                main(()->{GridView grid=(GridView)field(activity,"grid");require(grid.getChildCount()>0,"empty grid");for(int n=0;n<grid.getChildCount();n++){ViewGroup item=(ViewGroup)grid.getChildAt(n);TextView name=(TextView)item.getChildAt(1);android.graphics.Paint.FontMetricsInt f=name.getPaint().getFontMetricsInt();int required=(f.bottom-f.top)*2+name.getCompoundPaddingTop()+name.getCompoundPaddingBottom();require(name.getHeight()>=required,"application label clipped: "+name.getHeight()+" < "+required);require(item.getBottom()<=grid.getHeight()-grid.getPaddingBottom()+1,"application row exceeds page");}});
            });
            check("fractional integer settings are rejected without modifying preferences",()->{for(String key:new String[]{"homePage","musicMode","fit","borderColor"}){String before=cfg.exportJson();org.json.JSONObject json=new org.json.JSONObject(before);json.getJSONObject("settings").put(key,1.5);boolean rejected=false;try{cfg.restore(json.toString());}catch(org.json.JSONException expected){rejected=true;}require(rejected&&cfg.exportJson().equals(before),"fraction accepted or partially applied: "+key);}});
            check("shortcut grid rejects fractional and string row counts",()->{for(Object rows:new Object[]{1.5,"2"}){boolean rejected=false;try{QuickGrid.parse(new org.json.JSONObject().put("clock",new org.json.JSONObject().put("rows",rows).put("columns",2)).toString());}catch(org.json.JSONException expected){rejected=true;}require(rejected,"invalid rows accepted: "+rows);}});
            startupChecks(cfg);
            wallpaperChecks(cfg);
            transferChecks();
            qqChecks();
            weatherChecks(cfg);
        }catch(Throwable error){failed++;report.append("FAIL setup ").append(error).append('\n');}
        finally{if(activity!=null)main(activity::finish);restore(cfg,saved);}
        report.append("AUDIT_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private Field member(Class<?> type,String name)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);return field;}
    private static final class StartActivity extends Activity{
        boolean focused,deny;int starts;
        StartActivity(Context context){attachBaseContext(context);}
        @Override public android.view.Window getWindow(){return ((Activity)getBaseContext()).getWindow();}
        @Override public Intent getIntent(){return new Intent();}
        @Override public boolean hasWindowFocus(){return focused;}
        @Override public int checkCallingOrSelfPermission(String permission){return PackageManager.PERMISSION_DENIED;}
        @Override public int getTaskId(){return 123;}
        @Override public ComponentName startService(Intent intent){starts++;if(deny)throw new SecurityException("audit denied");return intent.getComponent();}
    }
    private void startupChecks(Config cfg)throws Exception{
        if("cn.windchaser.unified".equals(test.getTargetContext().getPackageName())){report.append("SKIP retired three-APK startup timing fixtures; unified startup covered by production dual-interface admission suite\n");return;}
        Field attempted=member(StartupApps.class,"attempted");boolean before=attempted.getBoolean(null);cfg.put("autoRecoverApps",true);
        try{
            check("real dialog focus recovery resumes associated-app startup",()->{
                Dialog[] dialog={null};cfg.put("autoRecoverApps",false);attempted.setBoolean(null,false);
                main(()->{dialog[0]=new AlertDialog.Builder(activity).setMessage("startup focus fixture").create();dialog[0].show();});Thread.sleep(150);
                try{main(()->{require(!activity.hasWindowFocus(),"dialog did not take focus");cfg.put("autoRecoverApps",true);((StartupApps)field(activity,"startupApps")).resume();});Thread.sleep(950);require(!attempted.getBoolean(null),"started behind dialog");main(dialog[0]::dismiss);Thread.sleep(1100);require(attempted.getBoolean(null),"focus recovery did not schedule startup");}
                finally{cfg.put("autoRecoverApps",false);main(()->{if(dialog[0].isShowing())dialog[0].dismiss();StartupSequenceService.userReturned();});}
            });
            check("temporarily unfocused startup can retry after focus returns",()->{
                cfg.put("autoRecoverApps",true);attempted.setBoolean(null,false);StartActivity[] holder={null};StartupApps[] sequence={null};main(()->{StartActivity fake=holder[0]=new StartActivity(activity);sequence[0]=new StartupApps(fake,cfg);sequence[0].resume();});StartActivity fake=holder[0];Thread.sleep(950);
                require(fake.starts==0&&!attempted.getBoolean(null),"unfocused activity launched external app");main(()->{fake.focused=true;sequence[0].resume();});Thread.sleep(950);
                require(fake.starts==1&&attempted.getBoolean(null),"focus retry skipped");main(()->{sequence[0].resume();sequence[0].close();});require(fake.starts==1,"startup repeated");
            });
            check("service admission failure does not permanently suppress startup",()->{
                attempted.setBoolean(null,false);StartActivity[] holder={null};StartupApps[] sequence={null};main(()->{StartActivity fake=holder[0]=new StartActivity(activity);fake.focused=true;fake.deny=true;sequence[0]=new StartupApps(fake,cfg);sequence[0].resume();});StartActivity fake=holder[0];Thread.sleep(950);require(fake.starts==1&&!attempted.getBoolean(null),"failed admission marked successful");main(()->{fake.deny=false;sequence[0].resume();});Thread.sleep(950);require(fake.starts==2&&attempted.getBoolean(null),"admission not retried");main(sequence[0]::close);
            });
        }finally{attempted.setBoolean(null,before);cfg.put("autoRecoverApps",false);}
    }
    private void wallpaperChecks(Config cfg)throws Exception{
        File file=new File(activity.getFilesDir(),"wallpaper"),saved=new File(activity.getFilesDir(),"audit-original-wallpaper");require(!saved.exists(),"test backup already exists");boolean existed=file.exists();if(existed)require(file.renameTo(saved),"wallpaper backup failed");
        try{
            try(FileOutputStream out=new FileOutputStream(file)){out.write("invalid image and video fixture".getBytes("UTF-8"));}
            check("invalid custom image exposes the actual default wallpaper",()->{
                WallpaperLayer[] layer={null};main(()->{cfg.put("wallpaperKind","image");layer[0]=new WallpaperLayer(activity);});Thread.sleep(500);main(()->{try{ImageView fallback=(ImageView)find(layer[0],"默认静态背景");ImageView custom=(ImageView)field(layer[0],"image");require(fallback!=null&&fallback.getDrawable()!=null&&custom.getDrawable()==null,"default image missing");}finally{layer[0].dispose();}});
            });
            check("invalid video hides failed surface and keeps default wallpaper",()->{
                WallpaperLayer[] layer={null};main(()->{cfg.put("wallpaperKind","video");layer[0]=new WallpaperLayer(activity);try{layer[0].resume();Method prepare=WallpaperLayer.class.getDeclaredMethod("prepareSurface",Surface.class);prepare.setAccessible(true);prepare.invoke(layer[0],new Object[]{null});}catch(Exception error){throw new RuntimeException(error);}});
                try{boolean[] failed={false};for(int i=0;i<25&&!failed[0];i++){Thread.sleep(100);main(()->failed[0]=(Boolean)field(layer[0],"videoFailed")&&field(layer[0],"player")==null);}require(failed[0],"failed player retained after error callback");main(()->{require(((View)field(layer[0],"texture")).getVisibility()==View.GONE&&((ImageView)find(layer[0],"默认静态背景")).getDrawable()!=null,"failed surface obscures fallback");});}finally{main(layer[0]::dispose);}
            });
            check("repeated resume does not start or release an unprepared player",()->main(()->{
                cfg.put("wallpaperKind","");WallpaperLayer layer=new WallpaperLayer(activity);MediaPlayer player=new MediaPlayer();try{member(WallpaperLayer.class,"player").set(layer,player);layer.resume();layer.resume();require(field(layer,"player")==player&&!(Boolean)field(layer,"prepared"),"PREPARING player was started or released");}catch(Exception error){throw new RuntimeException(error);}finally{layer.dispose();}
            }));
        }finally{require(file.delete(),"invalid fixture cleanup failed");if(existed)require(saved.renameTo(file),"wallpaper restore failed");cfg.p.edit().remove("wallpaperKind").commit();}
    }
    private void transferChecks(){
        check("late HTTPS startup cannot revive a destroyed service",()->rejectLateServer(false));
        check("old HTTPS completion cannot overwrite a replacement service",()->rejectLateServer(true));
    }
    private void rejectLateServer(boolean replacement)throws Exception{
        Context context=test.getTargetContext();TransferServer server=new TransferServer(context,StorageFiles.transfer(context),()->{});server.start(0);int listening=server.port();Field owner=member(FileTransferService.class,"owner");Object previous=owner.get(null);boolean running=FileTransferService.running,starting=FileTransferService.starting;int port=FileTransferService.port;String state=FileTransferService.state;
        try{main(()->{try{FileTransferService service=new FileTransferService();member(FileTransferService.class,"destroyed").setBoolean(service,!replacement);owner.set(null,replacement?new FileTransferService():service);FileTransferService.running=false;FileTransferService.starting=false;FileTransferService.port=0;FileTransferService.state="audit closed";Method publish=FileTransferService.class.getDeclaredMethod("publishStarted",TransferServer.class);publish.setAccessible(true);publish.invoke(service,server);require(!FileTransferService.running&&!FileTransferService.starting&&FileTransferService.port==0&&FileTransferService.state.equals("audit closed"),"late callback revived state");}catch(Exception error){throw new RuntimeException(error);}});boolean refused=false;try(Socket socket=new Socket()){socket.connect(new InetSocketAddress("127.0.0.1",listening),500);}catch(IOException expected){refused=true;}require(refused,"orphan listener remains open");}
        finally{server.close();owner.set(null,previous);FileTransferService.running=running;FileTransferService.starting=starting;FileTransferService.port=port;FileTransferService.state=state;}
    }
    private Context qqContext(java.util.List<Intent> sent,boolean deny){return new ContextWrapper(activity){
        @Override public PackageManager getPackageManager(){return new android.test.mock.MockPackageManager(){@Override public ApplicationInfo getApplicationInfo(String pkg,int flags){ApplicationInfo info=new ApplicationInfo();info.packageName=pkg;return info;}};}
        @Override public void sendBroadcast(Intent intent){if(deny)throw new SecurityException("audit subscription denied");sent.add(new Intent(intent));}
    };}
    private void qqChecks(){
        check("ROM subscription rejection does not crash music startup or refresh",()->main(()->{QqMusicBridge bridge=new QqMusicBridge(qqContext(new ArrayList<>(),true),()->{});try{bridge.subscribe();bridge.resume();}finally{bridge.stop();}}));
        check("confirmed seek cancels remaining refreshes without resending seek",()->{
            java.util.List<Intent> sent=new ArrayList<>();QqMusicBridge[] holder={null};main(()->{try{QqMusicBridge bridge=holder[0]=new QqMusicBridge(qqContext(sent,false),()->{});bridge.accept(message("update_song","\"key_title\":\"audit seek\",\"key_artist\":[],\"curr_time\":10000,\"total_time\":180000"));bridge.state=2;bridge.receivedAt=SystemClock.elapsedRealtime();require(bridge.seekTo(60000,bridge.trackRevision),"seek rejected");bridge.accept(message("update_state","\"curr_time\":60000,\"state\":2"));require(!bridge.seekPending(),"confirmation not accepted");sent.clear();}catch(Exception error){throw new RuntimeException(error);}});
            try{Thread.sleep(1900);main(()->require(sent.isEmpty(),"refresh continued after confirmation"));}finally{main(()->holder[0].stop());}
        });
    }
    private static String message(String method,String fields){return "{\"module\":\"play\",\"command\":{\"method\":\""+method+"\",\"data\":{"+fields+"}}}";}
    private static final String WEATHER="{\"current\":{\"temperature_2m\":22,\"wind_speed_10m\":8,\"relative_humidity_2m\":60,\"weather_code\":0,\"is_day\":1,\"time\":\"2026-10-24T10:00\"},\"daily\":{\"temperature_2m_max\":[24],\"temperature_2m_min\":[16]}}";
    private void weatherChecks(Config cfg)throws Exception{
        main(()->((WeatherController)field(activity,"weather")).pause());SharedPreferences cache=activity.getSharedPreferences("weather-cache",0);Map<String,?> saved=new HashMap<>(cache.getAll());
        try{
            check("auto weather without location permission falls back to the configured city",()->main(()->{
                cfg.put("weatherAuto",true);AtomicInteger calls=new AtomicInteger();WeatherController weather=new WeatherController(new StartActivity(activity),()->{},url->{calls.incrementAndGet();return WEATHER;});try{weather.city=new WeatherController.City("当前位置",27,106);weather.reading=new WeatherController.Reading(new org.json.JSONObject(WEATHER));weather.fetchedAt=System.currentTimeMillis();WeatherController.Reading before=weather.reading;weather.start();require(weather.city.key().equals(WeatherController.City.parse(cfg.s("weatherCity",WeatherController.DEFAULT_CITY)).key())&&weather.reading!=before,"denied location retained stale coordinates or reading");}catch(Exception error){throw new RuntimeException(error);}finally{weather.close();}
            }));
            check("manual city change invalidates prior weather even in auto mode",()->main(()->{
                cfg.put("weatherAuto",true);cfg.put("weatherCity",WeatherController.DEFAULT_CITY);WeatherController weather=new WeatherController(new StartActivity(activity),()->{},url->WEATHER);try{weather.city=new WeatherController.City("当前位置",27,106);cfg.put("weatherCity","{\"label\":\"测试城市\",\"lat\":28,\"lon\":107}");weather.start();require(weather.city.key().equals("28.00,107.00"),"manual edit ignored: "+weather.city.key());}finally{weather.close();}
            }));
            check("failed weather request retries after throttle and cannot burst",()->{
                cfg.put("weatherAuto",false);cache.edit().clear().commit();AtomicInteger calls=new AtomicInteger();WeatherController[] holder={null};main(()->{holder[0]=new WeatherController(activity,()->{},url->{if(calls.incrementAndGet()==1)throw new IOException("offline fixture");return WEATHER;});holder[0].start();});
                try{for(int i=0;i<50&&((Boolean)field(holder[0],"busy"));i++)Thread.sleep(40);main(()->{require(calls.get()==1&&holder[0].notice.contains("失败"),"failure missing");holder[0].refresh(false);require(calls.get()==1,"unthrottled retry");try{member(WeatherController.class,"lastAttempt").setLong(holder[0],System.currentTimeMillis()-5*60000-1);}catch(Exception error){throw new RuntimeException(error);}holder[0].refresh(false);});for(int i=0;i<50&&((Boolean)field(holder[0],"busy"));i++)Thread.sleep(40);main(()->require(calls.get()==2&&holder[0].reading!=null,"weather did not recover"));}finally{main(()->holder[0].close());}
            });
        }finally{SharedPreferences.Editor edit=cache.edit().clear();for(Map.Entry<String,?> entry:saved.entrySet()){Object value=entry.getValue();if(value instanceof String)edit.putString(entry.getKey(),(String)value);else if(value instanceof Long)edit.putLong(entry.getKey(),(Long)value);}edit.commit();}
    }
}
