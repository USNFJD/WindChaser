package cn.windchaser.startup;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Activity-owned startup content, never a system overlay or a separate window. */
public final class StartupCurtain {
    public static final String SESSION="windchaser.init.session",UNTIL="windchaser.init.until",END="cn.windchaser.startup.END_INIT_VISUAL";
    private static final String TAG="windchaser.init.visual";
    private static final Handler timer=new Handler(Looper.getMainLooper());
    private static final ArrayList<WeakReference<Activity>> activities=new ArrayList<>();
    private static final java.util.LinkedHashSet<String> completedSessions=new java.util.LinkedHashSet<>();
    private static String session;private static long until;private static boolean installed,firstUse;
    private static String requestedMap;private static long visualStarted;
    private static final Runnable expire=()->{retire(session);clear();};
    private StartupCurtain(){}
    public static void select(Activity a,Intent i){
        if(i!=null){String value=i.getStringExtra(SESSION);long deadline=i.getLongExtra(UNTIL,0),now=SystemClock.elapsedRealtime();
            if(value!=null&&!completedSessions.contains(value)&&value.length()<=128&&deadline>now&&deadline<=now+360000){
                install(a);if(!value.equals(session)){requestedMap=null;visualStarted=now;}session=value;until=deadline;firstUse=i.getBooleanExtra("windchaser.init.first",false);timer.removeCallbacks(expire);timer.postDelayed(expire,deadline-now);track(a);
                android.util.Log.i("WindChaserStartup","unified visual active "+a.getClass().getName());
            }
        }
        if(active())window(a);
    }
    public static void forward(Intent i){if(active())i.putExtra(SESSION,session).putExtra(UNTIL,until).putExtra("windchaser.init.first",firstUse);}
    public static boolean active(){return session!=null&&until>SystemClock.elapsedRealtime();}
    private static void install(Activity a){
        if(installed)return;installed=true;Application app=a.getApplication();
        app.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(END.equals(i.getAction())){String ended=i.getStringExtra(SESSION);retire(ended);if(session!=null&&session.equals(ended))clear();}
        }},new IntentFilter(END),DualSplash.PERMISSION,null);
        IntentFilter power=new IntentFilter(Intent.ACTION_SCREEN_OFF);power.addAction(Intent.ACTION_SHUTDOWN);
        app.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){retire(session);clear();}},power);
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityCreated(Activity a,Bundle b){if(active()){track(a);window(a);}}
            public void onActivityStarted(Activity a){}
            public void onActivityResumed(Activity a){if(active()){
                if(known(a)){track(a);attach(a);offerFirstMap(a);}else clear();
            }}
            public void onActivityPaused(Activity a){}
            public void onActivityStopped(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
            public void onActivityDestroyed(Activity a){remove(a);for(int n=activities.size()-1;n>=0;n--)if(activities.get(n).get()==null||activities.get(n).get()==a)activities.remove(n);}
        });
    }
    public static boolean needsMapSetup(){return firstUse&&active();}
    private static void offerFirstMap(Activity a){
        if(!firstUse||!a.getClass().getName().equals("com.autonavi.amapauto.MainMapActivity")||session.equals(requestedMap))return;
        final WeakReference<Activity> owner=new WeakReference<>(a);final String expected=session;
        timer.post(new Runnable(){public void run(){
            Activity current=owner.get();if(current==null||current.isFinishing()||!active()||!expected.equals(session)||expected.equals(requestedMap))return;
            if(current.hasWindowFocus()){
                requestedMap=expected;current.sendBroadcast(new Intent("cn.windchaser.startup.FIRST_MAP_SETUP").setPackage("cn.windchaser.unified").putExtra(SESSION,expected),DualSplash.PERMISSION);
                android.util.Log.i("WindChaserStartup","first map setup requested");
            }else timer.postDelayed(this,200);
        }});
    }
    private static boolean known(Activity a){
        String n=a.getClass().getName();return n.equals("com.autonavi.auto.remote.fill.UsbFillActivity")||n.equals("com.autonavi.amapauto.MainMapActivity")
            ||n.equals("com.tencent.qqmusiccar.v2.activity.home.MainActivity")||n.endsWith(".AppStarterActivity")||n.endsWith(".SplashActivity");
    }
    private static void track(Activity a){for(WeakReference<Activity> r:activities)if(r.get()==a)return;activities.add(new WeakReference<>(a));}
    public static boolean window(Activity a){
        if(!active()||!known(a))return false;
        a.getWindow().setBackgroundDrawable(new StartupVisual(a));a.getWindow().setWindowAnimations(0);a.overridePendingTransition(0,0);
        int flags=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if(Build.VERSION.SDK_INT>=19)flags|=View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        a.getWindow().getDecorView().setSystemUiVisibility(flags);return true;
    }
    private static void attach(Activity a){
        if(a.isFinishing()||!window(a))return;View decor=a.getWindow().getDecorView();if(!(decor instanceof ViewGroup))return;
        ViewGroup root=(ViewGroup)decor;View cover=root.findViewWithTag(TAG);
        if(cover==null){
            android.widget.FrameLayout layer=new android.widget.FrameLayout(a);layer.addView(StartupVisual.view(a),new android.widget.FrameLayout.LayoutParams(-1,-1));cover=layer;
            final WeakReference<Activity> owner=new WeakReference<>(a);final String expected=session;
            final android.widget.TextView help=new android.widget.TextView(a);help.setText("准备遇到问题？");help.setTextColor(0xffc3dceb);help.setTextSize(14);int pad=Math.round(16*a.getResources().getDisplayMetrics().density);help.setPadding(pad,pad,pad,pad);help.setVisibility(View.GONE);
            layer.addView(help,new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT));
            help.setOnClickListener(v->{Activity current=owner.get();if(current!=null&&current.hasWindowFocus()&&active()&&expected.equals(session))current.sendBroadcast(new Intent("cn.windchaser.startup.RECOVER").setPackage("cn.windchaser.unified").putExtra(SESSION,expected).putExtra("package",current.getPackageName()),DualSplash.PERMISSION);});
            final WeakReference<View> weakHelp=new WeakReference<View>(help);
            timer.postDelayed(()->{View target=weakHelp.get();if(target!=null&&active()&&expected.equals(session))target.setVisibility(View.VISIBLE);},Math.max(0,20000-(SystemClock.elapsedRealtime()-visualStarted)));
            cover.setTag(TAG);if(Build.VERSION.SDK_INT>=21)cover.setElevation(10000);root.addView(cover,new ViewGroup.LayoutParams(-1,-1));
        }
        cover.bringToFront();cover.invalidate();
    }
    private static void remove(Activity a){
        View decor=a.getWindow().getDecorView();View v=decor.findViewWithTag(TAG);if(v!=null&&v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);
        // Do not leave a brand background in later normal application windows.
        a.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff091827));
    }
    private static void retire(String value){if(value==null||value.length()>128)return;completedSessions.add(value);while(completedSessions.size()>16){java.util.Iterator<String> oldest=completedSessions.iterator();oldest.next();oldest.remove();}}
    private static void clear(){
        if(session==null)return;session=null;until=0;timer.removeCallbacks(expire);
        for(WeakReference<Activity> r:activities){Activity a=r.get();if(a!=null)remove(a);}activities.clear();
        android.util.Log.i("WindChaserStartup","unified visual released");
    }
}
