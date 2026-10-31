package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;

/** Best-effort public-service binding. No wake locks, overlay opening or playback commands. */
public final class CompanionService extends Service {
    private static final String CHANNEL="companion_protection";
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Config cfg;private LinkedAppCoordinator coordinator;
    private final Runnable interfaceChanged=this::syncLinks;
    private Link music,map;
    private boolean shutdown,registered;
    private final BroadcastReceiver power=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){shutdown=true;release();stopForeground(true);stopSelf();}};
    static boolean enabled(Config config){return config.b("keepCompanions",true)&&config.b("autoRecoverApps",true);}
    static void sync(Context context){
        Config config=new Config(context);Intent intent=new Intent(context,CompanionService.class);
        if(!enabled(config)||(!LinkedAppCoordinator.get(context).canBind(config.pkg(true))&&!LinkedAppCoordinator.get(context).canBind(config.pkg(false)))||(!available(context,config,true)&&!available(context,config,false))){context.stopService(intent);return;}
        try{if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);}
        catch(RuntimeException error){LauncherApp.record("关联应用后台保护启动受限",error);}
    }
    static boolean available(Context c,Config cfg,boolean music){
        if(music?!Config.MUSIC.equals(cfg.pkg(true)):!AmapFloatingController.selected(cfg)||!cfg.b("amapFloating",true))return false;
        try{ServiceInfo s=c.getPackageManager().getServiceInfo(BackgroundRecovery.serviceIntent(music,cfg).getComponent(),0);
            return s.enabled&&s.exported&&s.applicationInfo.enabled&&(s.permission==null||c.checkCallingOrSelfPermission(s.permission)==PackageManager.PERMISSION_GRANTED);
        }catch(PackageManager.NameNotFoundException error){return false;}
    }
    static void prewarm(Context context,Config cfg,boolean music){
        if(!available(context,cfg,music))return;
        try{context.startService(BackgroundRecovery.serviceIntent(music,cfg));}
        catch(RuntimeException error){LauncherApp.record("关联接口后台恢复受限 "+cfg.pkg(music),error);}
    }
    static boolean prepareMusicDuringStartup(Context context,Config cfg){
        if(!available(context,cfg,true))return false;
        try{
            // A new QQMusicApiService already binds its original MainService in
            // onCreate. Sending cmd_rebind_main_service at the same time would
            // issue a second bind while the first connection is still pending.
            Intent intent=new Intent().setComponent(BackgroundRecovery.serviceIntent(true,cfg).getComponent());
            return context.startService(intent)!=null;
        }catch(RuntimeException error){LauncherApp.record("QQ音乐启动期准备不可用",error);return false;}
    }
    @Override public void onCreate(){super.onCreate();cfg=new Config(this);coordinator=LinkedAppCoordinator.get(this);coordinator.listen(interfaceChanged);music=new Link(true);map=new Link(false);registerReceiver(power,new IntentFilter(Intent.ACTION_SHUTDOWN));registered=true;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        // Promote immediately, including a sticky restart on modern Android.
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"关联应用后台保护",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        Intent settings=new Intent(this,SettingsActivity.class).putExtra("category",7);
        PendingIntent action=PendingIntent.getActivity(this,58,settings,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0));
        startForeground(58,builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle("追风桌面 · 后台保护")
            .setContentText("保持关联服务；可在主题设置中关闭").setContentIntent(action).setOngoing(true).build());
        if(shutdown||!enabled(cfg)){release();stopForeground(true);stopSelf();return START_NOT_STICKY;}
        syncLinks();
        if(!music.bound&&!map.bound){stopForeground(true);stopSelf();return START_NOT_STICKY;}
        return START_STICKY;
    }
    private void syncLinks(){if(music!=null)music.sync();if(map!=null)map.sync();}
    private final class Link implements ServiceConnection {
        final boolean isMusic;boolean bound;int failures;String boundPackage="";
        final Runnable retry;
        Link(boolean music){isMusic=music;retry=()->{if(!shutdown&&enabled(cfg)&&available(CompanionService.this,cfg,isMusic)&&coordinator.canBind(cfg.pkg(isMusic))){unbind();bind();}};}
        void sync(){if(bound&&!boundPackage.equals(cfg.pkg(isMusic)))unbind();if(available(CompanionService.this,cfg,isMusic)&&coordinator.canBind(cfg.pkg(isMusic))){if(!bound)bind();}else unbind();}
        void bind(){
            try{bound=bindService(BackgroundRecovery.serviceIntent(isMusic,cfg),this,BIND_AUTO_CREATE|BIND_IMPORTANT);boundPackage=bound?cfg.pkg(isMusic):"";}
            catch(RuntimeException error){LauncherApp.record("关联服务绑定受限",error);bound=false;}
        }
        void unbind(){handler.removeCallbacks(retry);boundPackage="";if(bound){bound=false;try{unbindService(this);}catch(IllegalArgumentException ignored){}}}
        @Override public void onServiceConnected(ComponentName name,IBinder binder){failures=0;handler.removeCallbacks(retry);}
        @Override public void onServiceDisconnected(ComponentName name){
            // The framework also reconnects AUTO_CREATE bindings. Back off if the process keeps failing.
            if(!shutdown&&failures<5)handler.postDelayed(retry,Math.min(60000,2000L<<failures++));
        }
    }
    private void release(){handler.removeCallbacksAndMessages(null);if(music!=null)music.unbind();if(map!=null)map.unbind();}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){if(coordinator!=null)coordinator.unlisten(interfaceChanged);release();if(registered)unregisterReceiver(power);stopForeground(true);super.onDestroy();}
}
