/* WindChaser single-package experiment, LGPL-3.0-or-later. */
package com.morgoo.droidplugin.unified;

import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.*;

/** Defer native home/permission transitions while an external window owns startup. */
public final class StartupActivityGate {
    private static final String SESSION="windchaser.init.session",UNTIL="windchaser.init.until";
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<String,Pending> pending=new LinkedHashMap<>();
    private static boolean scheduled;
    public interface Replay {void start(Object[] arguments)throws Throwable;}
    private static final class Pending {
        final Context host;final Intent intent;final String session;final Object[] arguments;final Replay replay;
        Pending(Context c,Intent i,String value,Object[] args,Replay callback){host=c.getApplicationContext();intent=new Intent(i);session=value;arguments=args;replay=callback;}
    }
    private StartupActivityGate(){}
    private static boolean home(Intent intent){
        ComponentName component=intent.getComponent();if(component==null)return false;
        return "com.tencent.qqmusiccar".equals(component.getPackageName())&&"com.tencent.qqmusiccar.v2.activity.home.MainActivity".equals(component.getClassName())
            ||"com.autonavi.amapauto".equals(component.getPackageName())&&"com.autonavi.amapauto.MainMapActivity".equals(component.getClassName());
    }
    private static Bundle state(Context host){
        try{return host.getContentResolver().call(Uri.parse("content://cn.windchaser.unified.startup_windows"),"state",null,null);}
        catch(RuntimeException unavailable){return null;}
    }
    public static boolean beforeStart(Context host,Object[] args){return beforeStart(host,args,null);}
    public static boolean beforeStart(Context host,Object[] args,Replay replay){
        if(args==null)return false;
        boolean guestCaller=false;
        for(Object arg:args)if("com.tencent.qqmusiccar".equals(arg)||"com.autonavi.amapauto".equals(arg))guestCaller=true;
        for(int n=0;n<args.length;n++)if(args[n] instanceof Intent){
            Intent intent=(Intent)args[n];boolean nativeHome=home(intent);
            boolean permission=replay!=null&&"android.content.pm.action.REQUEST_PERMISSIONS".equals(intent.getAction())
                &&(guestCaller||com.morgoo.droidplugin.core.PluginProcessManager.isPluginProcess(host));
            if(!nativeHome&&!permission)return false;
            if(nativeHome&&(n+4>=args.length||!(args[n+4] instanceof Integer)||((Integer)args[n+4])>=0))return false;
            Bundle observed=state(host);String value=intent.getStringExtra(SESSION);
            if(value==null&&permission&&observed!=null)value=observed.getString("session");
            if(value==null)return false;
            if(observed!=null&&!value.equals(observed.getString("session")))return true;
            if(observed!=null&&observed.getBoolean("allowed"))return false;
            Object[] saved=null;
            String key=nativeHome?intent.getComponent().flattenToString():"permission/"+System.identityHashCode(args[n+2])+"/"+args[n+4];
            if(permission){saved=args.clone();saved[n]=new Intent(intent).putExtra(SESSION,value);}
            synchronized(pending){pending.put(key,new Pending(host,intent,value,saved,permission?replay:null));schedule();}
            android.util.Log.i("WindChaserStartup",(permission?"native permission":"native home")+" deferred while external window owns startup");return true;
        }
        return false;
    }
    private static void schedule(){if(!scheduled){scheduled=true;main.postDelayed(StartupActivityGate::drain,250);}}
    private static void drain(){
        ArrayList<Pending> ready=new ArrayList<>();
        synchronized(pending){
            scheduled=false;
            for(Iterator<Pending> iterator=pending.values().iterator();iterator.hasNext();){
                Pending item=iterator.next();Bundle observed=state(item.host);
                if(observed==null)continue;
                if(!item.session.equals(observed.getString("session"))){iterator.remove();continue;}
                if(!observed.getBoolean("allowed"))continue;
                long budget=Math.max(1,Math.min(300000,observed.getLong("remaining_ms",45000)));
                if(item.replay==null)item.intent.putExtra(UNTIL,SystemClock.elapsedRealtime()+budget);iterator.remove();ready.add(item);
            }
            if(!pending.isEmpty())schedule();
        }
        for(Pending item:ready)try{
            // Home handoffs can outlive their finished native starter. A
            // permission request instead retains its original result token and
            // arguments, so its native permission callback remains connected.
            if(item.replay!=null)item.replay.start(item.arguments);
            else item.host.startActivity(item.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION));
            android.util.Log.i("WindChaserStartup",(item.replay==null?"native home":"native permission")+" resumed after owned startup focus: "+item.intent.getComponent());
        }catch(Throwable rejected){android.util.Log.w("WindChaserStartup","deferred native home could not resume",rejected);}
    }
}
