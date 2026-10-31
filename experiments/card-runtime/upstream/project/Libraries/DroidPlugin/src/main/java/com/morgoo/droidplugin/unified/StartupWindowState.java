package com.morgoo.droidplugin.unified;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.*;
import com.morgoo.helper.compat.BundleCompat;
import java.util.*;

/** Same-UID, lifecycle-owned startup windows. A process or window death releases its lease. */
public final class StartupWindowState {
    private static final Map<Activity,Watch> local=new WeakHashMap<>();
    private static final class Watch {
        final java.lang.ref.WeakReference<Activity> activity;
        final Context host;final IBinder token=new Binder();boolean resumed;Object focus;
        Watch(Context c,Activity a){host=c.getApplicationContext();activity=new java.lang.ref.WeakReference<>(a);}
        void send(boolean active){
            Bundle data=new Bundle();BundleCompat.putBinder(data,"window",token);data.putBoolean("resumed",active);Activity owner=activity.get();data.putString("package",owner==null?null:owner.getPackageName());
            try{host.getContentResolver().call(Uri.parse("content://cn.windchaser.unified.startup_windows"),"window",null,data);}
            catch(RuntimeException unavailable){android.util.Log.w("WindChaserStartup","startup window lease unavailable",unavailable);}
        }
    }
    @android.annotation.TargetApi(18)
    private static final class Focus implements android.view.ViewTreeObserver.OnWindowFocusChangeListener {
        final Watch watch;Focus(Watch w){watch=w;}
        public void onWindowFocusChanged(boolean focus){watch.send(watch.resumed&&focus);}
        static void install(Watch w,Activity a){Focus f=new Focus(w);w.focus=f;a.getWindow().getDecorView().getViewTreeObserver().addOnWindowFocusChangeListener(f);}
        static void remove(Watch w,Activity a){android.view.ViewTreeObserver tree=a.getWindow().getDecorView().getViewTreeObserver();if(tree.isAlive()&&w.focus!=null)tree.removeOnWindowFocusChangeListener((Focus)w.focus);w.focus=null;}
    }
    private static final Map<IBinder,IBinder.DeathRecipient> resumed=new HashMap<>();
    private static final Map<IBinder,String> packages=new HashMap<>();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static Runnable listener;
    private static long epoch;
    private StartupWindowState(){}
    public static boolean guest(Activity a){String p=a.getPackageName();return "com.autonavi.amapauto".equals(p)||"com.tencent.qqmusiccar".equals(p);}
    public static void report(Context host,Activity a,boolean active){
        Watch watch=local.get(a);
        if(watch==null){
            if(!active)return;
            watch=new Watch(host,a);local.put(a,watch);
            if(Build.VERSION.SDK_INT>=18)Focus.install(watch,a);
        }
        watch.resumed=active;watch.send(active&&(Build.VERSION.SDK_INT<18||a.hasWindowFocus()));
    }
    public static void forget(Context host,Activity a){
        Watch watch=local.remove(a);if(watch==null)return;watch.resumed=false;watch.send(false);
        if(Build.VERSION.SDK_INT>=18)Focus.remove(watch,a);
    }
    public static synchronized boolean allowed(){return !resumed.isEmpty();}
    public static synchronized boolean foreground(String pkg){return packages.containsValue(pkg);}
    public static synchronized long epoch(){return epoch;}
    public static synchronized void listen(Runnable change){listener=change;}
    private static void changed(){if(resumed.isEmpty())epoch++;Runnable target=listener;if(target!=null)main.post(()->{synchronized(StartupWindowState.class){if(listener!=target)return;}target.run();});}
    public static synchronized void update(final IBinder token,final String pkg,boolean active){
        if(token==null)return;
        if(active){
            if(!"cn.windchaser.unified".equals(pkg)&&!"com.autonavi.amapauto".equals(pkg)&&!"com.tencent.qqmusiccar".equals(pkg))return;
            if(resumed.containsKey(token))return;
            IBinder.DeathRecipient death=()->update(token,pkg,false);
            try{token.linkToDeath(death,0);}catch(RemoteException dead){return;}
            resumed.put(token,death);packages.put(token,pkg);changed();
        }else{
            IBinder.DeathRecipient death=resumed.remove(token);packages.remove(token);
            if(death!=null){try{token.unlinkToDeath(death,0);}catch(RuntimeException ignored){}changed();}
        }
    }
}
