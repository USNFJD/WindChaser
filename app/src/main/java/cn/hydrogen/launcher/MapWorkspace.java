package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.ref.WeakReference;
import java.util.*;

/** Owns the map across launcher UI Activities. Internal handoff proof is only
 * used for an OFFSCREEN, non-touchable map, never to display an unfocused map. */
final class MapWorkspace implements Application.ActivityLifecycleCallbacks {
    private static MapWorkspace instance;
    static synchronized MapWorkspace get(Context context) {
        if(instance==null){instance=new MapWorkspace(context.getApplicationContext());((Application)context.getApplicationContext()).registerActivityLifecycleCallbacks(instance);}
        return instance;
    }
    private final Context app;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final WeakHashMap<Activity,State> states=new WeakHashMap<>();
    private final WeakHashMap<Dialog,WeakReference<Activity>> dialogs=new WeakHashMap<>();
    private WeakReference<Activity> main=new WeakReference<>(null);
    private AmapFloatingController controller;
    private boolean external,screen=true,launching;
    private String destination;
    private long transferUntil,dialogUntil,resumeSerial;
    private final Runnable settled=()->{destination=null;transferUntil=0;launching=false;refresh();};
    private final Runnable dialogSettled=()->{dialogUntil=0;refresh();};
    private static final class State {boolean resumed;long order;}

    private MapWorkspace(Context context) {
        app=context;
        IntentFilter power=new IntentFilter(Intent.ACTION_SCREEN_OFF);power.addAction(Intent.ACTION_SCREEN_ON);power.addAction(Intent.ACTION_SHUTDOWN);
        app.registerReceiver(new BroadcastReceiver(){public void onReceive(Context c,Intent i){screen=Intent.ACTION_SCREEN_ON.equals(i.getAction());if(!screen)clearHandoffs();if(controller!=null)controller.setScreenOn(screen);}},power);
    }
    AmapFloatingController controller(Activity activity,Config config) {
        main=new WeakReference<>(activity);
        if(controller==null){controller=new AmapFloatingController(activity,config);controller.setWorkspaceFocus(this::focused);}
        return controller;
    }
    private static boolean ownClass(String name) {
        return name!=null&&(name.equals(MainActivity.class.getName())||name.equals(SettingsActivity.class.getName())||name.equals(WeatherSettingsActivity.class.getName())||name.equals(FileCenterActivity.class.getName())||name.equals(DeviceInfoActivity.class.getName())||name.equals(DiagnosticsActivity.class.getName())||name.equals(ContainerActivity.class.getName()));
    }
    private boolean own(Activity activity){return activity!=null&&ownClass(activity.getClass().getName());}
    private ComponentName target(Intent intent){ComponentName component=intent.getComponent();return component!=null?component:intent.resolveActivity(app.getPackageManager());}
    boolean internal(Intent intent) {
        try{ComponentName component=target(intent);return component!=null&&app.getPackageName().equals(component.getPackageName())&&ownClass(component.getClassName());}
        catch(RuntimeException unavailable){return false;}
    }
    private void transfer(String name,boolean startsActivity) {
        external=false;destination=name;launching=startsActivity;transferUntil=SystemClock.uptimeMillis()+10000;
        handler.removeCallbacks(settled);handler.postDelayed(settled,10000);
    }
    private void clearHandoffs() {
        destination=null;transferUntil=dialogUntil=0;launching=false;
        handler.removeCallbacks(settled);handler.removeCallbacks(dialogSettled);
    }
    void beforeLaunch(Activity activity,Intent intent) {
        if(internal(intent)){transfer(target(intent).getClassName(),true);park();}else exit();
    }
    void launchFailed(){clearHandoffs();external=false;refresh();}
    void beforeFinish(Activity activity) {
        if(activity instanceof MainActivity){exit();return;}
        Activity previous=null;long recent=-1;
        for(Map.Entry<Activity,State> entry:states.entrySet()){
            Activity other=entry.getKey();
            if(other!=null&&other!=activity&&!other.isFinishing()&&other.getTaskId()==activity.getTaskId()&&entry.getValue().order>recent){previous=other;recent=entry.getValue().order;}
        }
        if(previous!=null){transfer(previous.getClass().getName(),false);park();}else exit();
    }
    // Only the pause caused by an explicitly pending internal launch may skip exit.
    // A previous navigation grace must not suppress HOME/camera/external-app cleanup.
    void userLeave(){if(!launching)exit();}
    void recreate(Activity activity){transfer(activity.getClass().getName(),false);park();}
    void close(){exit();}
    boolean ownsMain(Activity activity){return main.get()==activity;}
    void mainLeaving(Activity activity){if(ownsMain(activity)&&controller!=null)controller.setHostState(false,false,false);}
    void detachMain(Activity activity) {
        if(main.get()!=activity)return;
        main.clear();
        if(controller!=null){controller.setChanged(null);controller.setDialogFocus(()->false);controller.setHostState(false,false,false);controller.bind(null);}
    }
    private void park(){if(controller!=null)controller.parkForNavigation();}
    private void exit() {
        external=true;clearHandoffs();
        if(controller!=null){controller.setHostState(false,false,false);controller.closeWindow();}
    }
    boolean focused() {
        if(!screen||external)return false;
        long now=SystemClock.uptimeMillis();
        if((destination!=null&&now<transferUntil)||now<dialogUntil)return true;
        for(Map.Entry<Activity,State> entry:states.entrySet()){
            Activity activity=entry.getKey();
            if(activity==null||!entry.getValue().resumed||activity.isFinishing())continue;
            if(activity.getWindow().getDecorView().hasWindowFocus()||(activity instanceof MainActivity&&((MainActivity)activity).mapWorkspaceFocus()))return true;
            for(Map.Entry<Dialog,WeakReference<Activity>> d:dialogs.entrySet()){
                Dialog dialog=d.getKey();
                if(dialog!=null&&d.getValue().get()==activity&&dialog.isShowing()&&dialog.getWindow()!=null&&dialog.getWindow().getDecorView().hasWindowFocus())return true;
            }
        }
        return false;
    }
    void focus(Activity activity,boolean focused) {
        if(!own(activity))return;
        if(focused){external=false;State state=states.get(activity);if(state!=null&&state.resumed&&activity.getClass().getName().equals(destination))launching=false;}
        refresh();
    }
    private void refresh(){if(controller!=null)controller.refreshWorkspace();}
    private void dialogTransfer(Activity activity) {
        State state=states.get(activity);
        if(external||activity==null||activity.isFinishing()||state==null||!state.resumed)return;
        dialogUntil=SystemClock.uptimeMillis()+1200;handler.removeCallbacks(dialogSettled);handler.postDelayed(dialogSettled,1200);
    }
    void watch(Dialog dialog) {
        Context context=dialog.getContext();
        while(context instanceof ContextWrapper&&!(context instanceof Activity))context=((ContextWrapper)context).getBaseContext();
        if(!(context instanceof Activity)||!own((Activity)context)||dialog.getWindow()==null)return;
        Activity activity=(Activity)context;dialogs.put(dialog,new WeakReference<>(activity));
        if(dialog.getWindow().getAttributes().type==WindowManager.LayoutParams.TYPE_APPLICATION)dialogTransfer(activity);
        View view=dialog.getWindow().getDecorView();
        final Object focus=Build.VERSION.SDK_INT>=18?Focus18.attach(view,this::refresh):null;
        WeakReference<Dialog> reference=new WeakReference<>(dialog);
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            public void onViewAttachedToWindow(View v){refresh();}
            public void onViewDetachedFromWindow(View v){
                if(Build.VERSION.SDK_INT>=18&&focus!=null)Focus18.detach(v,focus);v.removeOnAttachStateChangeListener(this);
                Dialog d=reference.get();if(d!=null){WeakReference<Activity> owner=dialogs.remove(d);Activity a=owner==null?null:owner.get();if(a!=null)dialogTransfer(a);}refresh();
            }
        });
        refresh();
    }
    @android.annotation.TargetApi(18) private static final class Focus18 {
        static Object attach(View view,Runnable changed){ViewTreeObserver.OnWindowFocusChangeListener listener=value->changed.run();view.getViewTreeObserver().addOnWindowFocusChangeListener(listener);return listener;}
        static void detach(View view,Object listener){if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnWindowFocusChangeListener((ViewTreeObserver.OnWindowFocusChangeListener)listener);}
    }
    public void onActivityCreated(Activity activity,Bundle state){if(own(activity))states.put(activity,new State());}
    public void onActivityStarted(Activity activity){}
    public void onActivityResumed(Activity activity) {
        if(!own(activity))return;
        State state=states.get(activity);if(state==null){state=new State();states.put(activity,state);}state.resumed=true;state.order=++resumeSerial;external=false;
        if(activity.getClass().getName().equals(destination))launching=false;
        refresh();
    }
    public void onActivityPaused(Activity activity){State state=states.get(activity);if(state!=null)state.resumed=false;refresh();}
    public void onActivityStopped(Activity activity){refresh();}
    public void onActivitySaveInstanceState(Activity activity,Bundle state){}
    public void onActivityDestroyed(Activity activity){states.remove(activity);if(activity instanceof MainActivity)detachMain(activity);refresh();}
}
