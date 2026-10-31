package cn.hydrogen.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.*;
import android.view.*;

/** The first completed decor draw plus live window focus admits the one startup owner. */
final class StartupApps {
    static final String RETURN="windchaser.startup.return";
    static final String TOKEN="windchaser.startup.token",PAGE="windchaser.startup.page";
    private static boolean attempted;
    static void initialized(){attempted=true;}
    private final Activity activity;private final Config cfg;private final View decor;
    private final LinkedAppCoordinator coordinator;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean listening,drawn,scheduled,closed;
    private ViewTreeObserver observed;
    private final ViewTreeObserver.OnDrawListener draw=()->{
        if(drawn||closed)return;drawn=true;
        // Posting from OnDraw means admission runs after the traversal returns.
        handler.post(this::resume);
    };
    StartupApps(Activity activity,Config cfg){
        this.activity=activity;this.cfg=cfg;decor=activity.getWindow().getDecorView();coordinator=LinkedAppCoordinator.get(activity);
        if(!StartupSequenceService.active()&&StartupSequenceService.hasPending(activity)){attempted=true;StartupSequenceService.userReturned(activity);}
    }
    void resume(){
        if(closed||activity.isFinishing())return;
        // KitKat can replace the floating ViewTreeObserver when decor attaches.
        // A listener registered before attachment may never see the first frame.
        if(decor.getWindowToken()==null||!decor.isShown())return;
        ViewTreeObserver current=decor.getViewTreeObserver();
        if(!listening||observed!=current||!observed.isAlive()){
            if(observed!=null&&observed.isAlive())observed.removeOnDrawListener(draw);
            observed=current;observed.addOnDrawListener(draw);listening=true;decor.invalidate();
        }
        if(!drawn||!activity.hasWindowFocus()||!decor.isShown()||decor.getWindowToken()==null||scheduled)return;
        coordinator.hostReady();
        if(activity.getIntent().getBooleanExtra(RETURN,false)){attempted=true;coordinator.refreshSoon();return;}
        if(StartupSequenceService.active()){attempted=true;return;}
        if(attempted||!cfg.b("autoRecoverApps",true)){coordinator.refreshSoon();return;}
        scheduled=true;
        handler.post(()->{
            scheduled=false;
            if(closed||activity.isFinishing()||!activity.hasWindowFocus()||!cfg.b("autoRecoverApps",true))return;
            if(attempted||StartupSequenceService.active())return;
            attempted=true;coordinator.beginSequence();
            Intent intent=new Intent(activity,StartupSequenceService.class).putExtra("task",activity.getTaskId());
            if(activity instanceof MainActivity)intent.putExtra(PAGE,((MainActivity)activity).visiblePage());
            try{if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(intent);else activity.startService(intent);}
            catch(RuntimeException error){attempted=false;coordinator.endSequence();LauncherApp.record("关联启动流程受系统限制",error);}
        });
    }
    void pause(){coordinator.hostPaused();}
    void close(){closed=true;handler.removeCallbacksAndMessages(null);if(observed!=null&&observed.isAlive())observed.removeOnDrawListener(draw);observed=null;listening=false;}
}
