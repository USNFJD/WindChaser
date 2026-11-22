/* Hydrogen card-window experiment, LGPL-3.0-or-later, 2026.
 * Added to DroidPlugin; original guest APKs and platform permission checks are unchanged.
 */
package com.morgoo.droidplugin.card;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.Window;
import android.view.MotionEvent;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import com.morgoo.droidplugin.core.Env;
import com.morgoo.droidplugin.reflect.FieldUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.lang.ref.WeakReference;

/** Experimental same-UID bounded app windows. Not a View or system task embedding API. */
public final class CardWindowAdapter {
    private static final Map<IBinder,String> guests = new ConcurrentHashMap<>();
    private static final Map<String,Boolean> logged = new ConcurrentHashMap<>();
    private static final CopyOnWriteArrayList<WeakReference<Activity>> activities=new CopyOnWriteArrayList<>();
    private static boolean closeReceiverRegistered;
    public static void register(Context host, Activity activity) {
        if ("cn.windchaser.unified".equals(host.getPackageName())) return;
        try {
            ActivityInfo info=activity.getIntent().getParcelableExtra(Env.EXTRA_TARGET_INFO);
            if(info==null) return;
            activities.add(new WeakReference<>(activity));
            if(!closeReceiverRegistered) {
                host.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent intent){
                    for(WeakReference<Activity> ref:activities){Activity a=ref.get();if(a!=null&&!a.isFinishing())a.finish();}
                    activities.clear();
                }},new IntentFilter(host.getPackageName()+".CLOSE_GUESTS"),host.getPackageName()+".CONTROL",null);
                closeReceiverRegistered=true;
            }
            IBinder token=(IBinder)FieldUtils.readField(activity,"mToken",true);
            guests.put(token,info.packageName);
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            final Window.Callback original=activity.getWindow().getCallback();
            activity.getWindow().setCallback((Window.Callback)Proxy.newProxyInstance(Window.Callback.class.getClassLoader(),new Class[]{Window.Callback.class},(proxy,method,args)->{
                if("dispatchTouchEvent".equals(method.getName()) && ((MotionEvent)args[0]).getActionMasked()==MotionEvent.ACTION_DOWN) {
                    try {((ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE)).moveTaskToFront(activity.getTaskId(),0);}
                    catch(RuntimeException e){Log.e("CardLab","FOCUS FAILED",e);}
                }
                try{return method.invoke(original,args);}catch(InvocationTargetException e){throw e.getCause();}
            }));
            Log.i("CardLab","REGISTER "+info.packageName+" activity="+info.name+" token="+token+" uid="+android.os.Process.myUid());
        } catch(Throwable e){Log.e("CardLab","REGISTER FAILED",e);}
    }
    public static void apply(Context host, WindowManager.LayoutParams attr) {
        if ("cn.windchaser.unified".equals(host.getPackageName())) return;
        if(attr.token==null || attr.type<1 || attr.type>99) return;
        String pkg=guests.get(attr.token);
        if(pkg==null) return;
        try {
            Bundle query=new Bundle();query.putString("guest",pkg);
            Bundle config=host.getContentResolver().call(Uri.parse("content://cn.hydrogen.cardlab.bridge"),"window",null,query);
            IBinder parent=config==null?null:com.morgoo.helper.compat.BundleCompat.getBinder(config,"parent");
            int[] box=config==null?null:config.getIntArray("bounds");
            if(parent==null || box==null || box[2]<=0 || box[3]<=0) {
                throw new IllegalStateException("host window unavailable");
            }
            // Keep a top-level app window: API 19 rejects SurfaceView subwindows
            // attached to another subwindow. Bounds are controlled by the host.
            attr.gravity=Gravity.TOP|Gravity.LEFT;
            attr.x=box[0];attr.y=box[1];attr.width=box[2];attr.height=box[3];
            attr.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
            attr.flags&=~(WindowManager.LayoutParams.FLAG_DIM_BEHIND|WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
            attr.dimAmount=0;attr.windowAnimations=0;
            attr.setTitle("CardLab/"+pkg);
            if(logged.put(pkg,Boolean.TRUE)==null) Log.i("CardLab","BOUND "+pkg+" bounds="+java.util.Arrays.toString(box));
        } catch(Throwable e) {
            Log.e("CardLab","ATTACH FAILED "+pkg,e);
            // Never silently launch full screen on an embedding failure.
            attr.width=1;attr.height=1;
        }
    }
}
