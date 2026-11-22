package cn.hydrogen.launcher;

import android.content.*;
import android.content.pm.*;
import android.os.*;

/** Best-effort cold-process prewarming through exported services. Never starts an Activity. */
final class BackgroundRecovery {
    static final String QQ_SERVICE="com.tencent.qqmusiccar.third.api.QQMusicApiService";
    static final String MAP_SERVICE="com.autonavi.amapauto.protocol.service.ProtocolService";
    interface Starter { boolean start(Intent intent); }
    private final Config config;private final Starter starter;private final Runnable refresh;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean active,qqStarted,mapStarted;private int attempt;private long lastStart;
    private String selection="";
    private final Runnable retry=this::recover;
    BackgroundRecovery(Context context,Config config,Runnable refresh){
        this(config,intent->{
            try{
                ServiceInfo info=context.getPackageManager().getServiceInfo(intent.getComponent(),0);
                if(!info.enabled||!info.exported||!info.applicationInfo.enabled)return false;
                if(info.permission!=null&&context.checkCallingOrSelfPermission(info.permission)!=PackageManager.PERMISSION_GRANTED)return false;
                return context.getApplicationContext().startService(intent)!=null;
            }catch(PackageManager.NameNotFoundException missing){return false;}
            catch(Exception error){LauncherApp.record("关联应用后台预热不可用 "+intent.getComponent(),error);return false;}
        },refresh);
    }
    BackgroundRecovery(Config config,Starter starter,Runnable refresh){this.config=config;this.starter=starter;this.refresh=refresh;}
    static Intent serviceIntent(boolean music){
        Intent intent=new Intent().setComponent(new ComponentName(music?Config.MUSIC:AmapFloatingController.PACKAGE,music?QQ_SERVICE:MAP_SERVICE));
        if(music)intent.putExtra("cmd","cmd_rebind_main_service");
        return intent;
    }
    static Intent serviceIntent(boolean music,Config cfg){Intent intent=serviceIntent(music);if(!music)intent.setComponent(new ComponentName(cfg.pkg(false),MAP_SERVICE));return intent;}
    void resume(){
        if(!config.b("autoRecoverApps",true)){pause();selection="";lastStart=0;return;}
        String next=config.pkg(false)+"/"+config.pkg(true)+"/"+config.b("amapFloating",true);
        if(active)return;
        if(next.equals(selection)&&SystemClock.elapsedRealtime()-lastStart<30000)return;
        selection=next;active=true;attempt=0;qqStarted=mapStarted=false;lastStart=SystemClock.elapsedRealtime();recover();
    }
    private void recover(){
        if(!active||!config.b("autoRecoverApps",true)){pause();return;}
        if(Config.MUSIC.equals(config.pkg(true))&&!qqStarted)qqStarted=starter.start(serviceIntent(true));
        if(AmapFloatingController.selected(config)&&config.b("amapFloating",true)&&!mapStarted)mapStarted=starter.start(serviceIntent(false,config));
        refresh.run();
        int[] delay={1500,3000,5500};
        if(attempt<delay.length)handler.postDelayed(retry,delay[attempt++]);else active=false;
    }
    void pause(){active=false;handler.removeCallbacks(retry);}
}
