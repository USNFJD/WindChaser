package cn.windchaser.mapbridge;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import cn.windchaser.motion.CardTimeline;
import java.lang.ref.WeakReference;
import java.util.*;

/** Small, source-available adapter injected into the user's own 6.5 APK.
 * Relocates the existing native surfaces; no second engine, screenshots or input injection.
 */
public final class MapCardBridge implements Application.ActivityLifecycleCallbacks {
    public static final String SHOW="com.autonavi.plus.showmap",CLOSE="com.autonavi.plus.closemap";
    private static final String CONTROL_PERMISSION="cn.windchaser.unified.permission.STARTUP_READY";
    private static volatile MapCardBridge instance;
    private final Context app;private final Handler handler=new Handler(Looper.getMainLooper());
    private WeakReference<Activity> owner=new WeakReference<>(null);
    private final ArrayList<Slot> slots=new ArrayList<>();
    private WindowManager windows;private FrameLayout overlay;private Rect desired;
    private Rect attachedBounds;private Messenger client,endpoint;private Handler worker;
    private volatile long session=-1,sequence=-1,lastHostSignal;private IBinder.DeathRecipient hostDeath;
    private final HostWindowGuard guard=new HostWindowGuard();
    private volatile boolean awaitingFocus;
    private final Runnable watchdog=new Runnable(){public void run(){
        synchronized(guard){
        if(!added&&!awaitingFocus)return;
        long guardedSession=session,guardedSequence=sequence,now=SystemClock.elapsedRealtime();
        int task=foregroundTask();String reason=guard.blocked(now,task);
        // A slow internal Activity may block its main looper during creation.
        // Only an already OFFSCREEN, non-touchable window can use this bounded
        // handoff allowance; visible overlays retain the original focus deadlines.
        boolean internalHandoff=parked&&task==HostWindowGuard.HOST&&now-parkedAt<10000;
        if(reason!=null&&!internalHandoff){block(guardedSession,guardedSequence,reason);return;}
        if(guard.needsProbe(now,task)){
            long nonce=guard.probe(now);
            try{Bundle data=new Bundle();data.putLong("session",guardedSession);data.putLong("sequence",guardedSequence);data.putLong("probe",nonce);
                Message probe=Message.obtain(null,4);probe.setData(data);probe.replyTo=endpoint;
                if(client!=null)client.send(probe);
            }catch(RemoteException dead){block(guardedSession,guardedSequence,"host_disconnected");return;}
        }
        worker.postDelayed(this,120);
        }
    }};
    private void block(long expectedSession,long expectedSequence,String reason){
        handler.postAtFrontOfQueue(()->{if((added||awaitingFocus)&&session==expectedSession&&sequence==expectedSequence){
            Log.i("WindChaserMap","Overlay withdrawn: "+reason);hide(true);blocked(reason);
        }});
    }
    private volatile boolean added;private volatile boolean parked;private volatile long parkedAt;private boolean motionUntouchable,desiredMoving;
    private Bundle pendingMove;private boolean movePosted;
    private boolean layoutReady;private long layoutGeneration,layoutConfirmedAt;
    private FrameLayout layoutRoot;private ViewTreeObserver.OnPreDrawListener layoutListener;private Runnable layoutCompletion;
    private final CardTimeline windowTimeline=new CardTimeline();
    private long timelineId,timelineStart,timelineSequence,lastTimelineId;
    private int timelinePlans,timelineFrames,moveCommands;
    private long timelineLayoutConfirmedAt;
    private void clearLayoutProof(){
        layoutReady=false;layoutGeneration++;
        if(layoutRoot!=null&&layoutListener!=null&&layoutRoot.getViewTreeObserver().isAlive())layoutRoot.getViewTreeObserver().removeOnPreDrawListener(layoutListener);
        if(layoutCompletion!=null)handler.removeCallbacks(layoutCompletion);
        layoutListener=null;layoutRoot=null;layoutCompletion=null;
    }
    private void confirmWindowLayout(){
        clearLayoutProof();if(!added||parked||overlay==null||desired==null)return;
        final FrameLayout root=overlay;final Rect expected=new Rect(desired);
        final long generation=layoutGeneration,ownerSequence=sequence,deadline=SystemClock.uptimeMillis()+1000;
        layoutRoot=root;
        layoutListener=()->{
            if(generation!=layoutGeneration||overlay!=root||!added||parked)return true;
            int[] point=new int[2];root.getLocationOnScreen(point);
            boolean valid=expected.equals(desired)&&root.getWidth()==expected.width()&&root.getHeight()==expected.height()
                    &&point[0]==expected.left&&point[1]==expected.top&&!root.isLayoutRequested()
                    &&mapSurface!=null&&uiSurface!=null&&mapSurface.getHolder().getSurface().isValid()&&uiSurface.getHolder().getSurface().isValid();
            if(valid){
                root.getViewTreeObserver().removeOnPreDrawListener(layoutListener);layoutListener=null;
                // Posted after this traversal; never acknowledge updateViewLayout itself as a drawn layout.
                layoutCompletion=()->{layoutCompletion=null;if(generation==layoutGeneration&&ownerSequence==sequence&&root==overlay&&added&&!parked&&expected.equals(desired)){
                    layoutReady=true;layoutConfirmedAt=SystemClock.uptimeMillis();layoutRoot=null;ack(null);
                }};handler.post(layoutCompletion);
            }else if(SystemClock.uptimeMillis()<deadline)root.postInvalidateOnAnimation();
            else clearLayoutProof();
            return true;
        };
        root.getViewTreeObserver().addOnPreDrawListener(layoutListener);root.postInvalidateOnAnimation();
    }
    private void cancelTimeline(){windowTimeline.stop();timelineId=0;timelineStart=0;}
    private void startTimeline(Bundle data){
        long now=SystemClock.elapsedRealtime(),uptime=SystemClock.uptimeMillis(),issued=data.getLong("issued");
        if(data.getLong("session")!=session||data.getLong("sequence")!=sequence||issued>now||now-issued>500)return;
        long id=data.getLong("timeline"),start=data.getLong("start_uptime"),duration=data.getLong("duration");
        if(id==timelineId&&windowTimeline.running()){ack(null);return;}
        if(id<=lastTimelineId||!added||parked||!layoutReady||desired==null||!guard.focused(now)||start<uptime+8||start>uptime+250||duration<80||duration>400){ack(null);return;}
        Rect from=new Rect(data.getInt("from_x"),data.getInt("top"),data.getInt("from_x")+data.getInt("width"),data.getInt("top")+data.getInt("height"));
        final Rect to=new Rect(from);to.offset(data.getInt("to_x")-from.left,0);
        Point display=new Point();windows.getDefaultDisplay().getRealSize(display);
        if(!from.equals(desired)||to.left<0||to.top<0||to.right>display.x||to.bottom>display.y){ack(null);return;}
        cancelTimeline();timelineId=lastTimelineId=id;timelineStart=start;timelineSequence=sequence;timelinePlans++;timelineFrames=0;timelineLayoutConfirmedAt=layoutConfirmedAt;
        motionUntouchable=desiredMoving=true;
        try{windows.updateViewLayout(overlay,windowParams(from,false,true));}
        catch(RuntimeException error){hide(true);return;}
        windowTimeline.start(start,duration,(progress,finished)->{
            if(!added||parked||timelineId!=id||sequence!=timelineSequence||!guard.focused(SystemClock.elapsedRealtime())){cancelTimeline();return;}
            Rect area=new Rect(from);area.offset(Math.round((to.left-from.left)*progress),0);
            timelineFrames++;
            try{
                if(!area.equals(attachedBounds)||finished){windows.updateViewLayout(overlay,windowParams(area,false,!finished));attachedBounds=new Rect(area);}
                desired=area;motionUntouchable=desiredMoving=!finished;
                if(finished){confirmWindowLayout();ack(null);}
            }catch(RuntimeException error){Log.e("WindChaserMap","Shared window timeline failed",error);hide(true);}
        });ack(null);
    }
    private final Runnable moveFrame=()->{Bundle data;synchronized(this){data=pendingMove;pendingMove=null;movePosted=false;}if(data!=null)move(data);};private boolean paused,stopped;private int retries;private SurfaceView mapSurface,uiSurface;
    private SurfaceView observedMap,observedUi;
    private final Amap65Runtime runtime=new Amap65Runtime();
    private final Runnable retry=new Runnable(){public void run(){show();}};
    private final Runnable recover=new Runnable(){public void run(){if(!added)return;Activity a=owner.get();if(a==null)return;try{runtime.recover(a,mapSurface,uiSurface);}catch(Exception error){Log.e("WindChaserMap","Native card recovery failed",error);hide(true);}}};
    private final Runnable viewport=new Runnable(){public void run(){if(!added)return;try{runtime.syncViewport(mapSurface,uiSurface);}catch(Exception error){Log.e("WindChaserMap","Card viewport sync failed",error);hide(true);}}};
    // Reparenting destroys and recreates native surfaces. Replay visibility only
    // after the original surfaceCreated callbacks have queued native creation.
    private final SurfaceHolder.Callback surfaces=new SurfaceHolder.Callback(){
        public void surfaceCreated(SurfaceHolder holder){runtime.invalidateRecovery();if(added){handler.removeCallbacks(recover);handler.post(recover);}}
        public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){
            if(observedUi!=null&&holder==observedUi.getHolder()){runtime.uiBufferSize(width,height);runtime.uiBufferFormat(format);}
            if(added&&desired!=null){
                // Resize the actual render target, not a stretched old buffer.
                // Late callbacks from the fullscreen surface may carry its old size.
                if(width!=desired.width()||height!=desired.height())holder.setFixedSize(desired.width(),desired.height());
                handler.removeCallbacks(viewport);handler.post(viewport);
            }
        }
        public void surfaceDestroyed(SurfaceHolder holder){if(observedUi!=null&&holder==observedUi.getHolder())runtime.uiBufferSize(0,0);}
    };
    private final BroadcastReceiver receiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
        if(SHOW.equals(intent.getAction())||CLOSE.equals(intent.getAction())){
            Bundle data=intent.getExtras();
            // Independent cleanup has no old host Binder/sequence. Only accept it
            // after host death or lease expiry, so a late sweep cannot hide a new card.
            if(CLOSE.equals(intent.getAction())&&(data==null||!data.containsKey("protocol"))){
                boolean alive=client!=null&&client.getBinder().isBinderAlive();
                if(guard.allowFallbackClose(SystemClock.elapsedRealtime(),alive)){hide(true);unlinkHost();}
            }else accept(data);
        }
        else if(Intent.ACTION_SCREEN_OFF.equals(intent.getAction())||Intent.ACTION_SHUTDOWN.equals(intent.getAction()))hide(true);
    }};
    private void accept(Bundle data){
        if(data==null||data.getInt("protocol")!=8)return;
        long incomingSession=data.getLong("session"),incomingSequence=data.getLong("sequence");
        long age=SystemClock.elapsedRealtime()-data.getLong("issued");
        if(age<0||age>1500||incomingSession<session)return;
        Messenger reply=data.getParcelable("client");
        if(reply==null||!reply.getBinder().isBinderAlive())return;
        if(incomingSession!=session){
            hide(true);unlinkHost();session=incomingSession;sequence=-1;lastTimelineId=0;client=reply;
            final long linked=session;
            hostDeath=()->handler.postAtFrontOfQueue(()->{if(session==linked){hide(true);unlinkHost();}});
            try{client.getBinder().linkToDeath(hostDeath,0);}catch(RemoteException dead){unlinkHost();return;}
        }else if(client==null||!client.getBinder().equals(reply.getBinder()))return;
        if(incomingSequence<sequence)return;
        boolean show=data.getBoolean("show"),park=data.getBoolean("park");
        // Duplicate show/close delivery is idempotent. An unsequenced legacy
        // close can never remove a newer host-owned window.
        if(incomingSequence==sequence&&!show){ack(null);return;}
        boolean newer=incomingSequence>sequence;
        if(newer)cancelTimeline();
        if(!newer&&added){ack(null);return;}
        synchronized(guard){
            sequence=incomingSequence;lastHostSignal=data.getLong("issued");
            // A new show/park sequence must also retire the old focus challenge.
            // Motion uses message 6 and never resets this guard or the native surfaces.
            if(show||park){if(newer)guard.begin(lastHostSignal);else guard.signal(lastHostSignal);awaitingFocus=true;}
        }
        if(show){
            Rect area=new Rect(data.getInt("x",-1),data.getInt("y",-1),data.getInt("w",-1),data.getInt("h",-1));
            Point dm=new Point();windows.getDefaultDisplay().getRealSize(dm);
            if(area.left<0||area.top<0||area.width()<48||area.height()<48||area.right>dm.x||area.bottom>dm.y){ack("无效卡片尺寸");return;}
            if(desired!=null&&!desired.equals(area))runtime.cancelTouch();desiredMoving=data.getBoolean("moving");desired=area;retries=0;handler.removeCallbacks(retry);show();
        }else if(park){
            handler.removeCallbacks(retry);awaitingFocus=false;park();
            if(added){worker.removeCallbacks(watchdog);worker.post(watchdog);}ack(null);
        }else{hide(true);if(data.getBoolean("release"))unlinkHost();}
    }
    private void ack(String error){if(client==null)return;try{Bundle data=new Bundle();data.putLong("session",session);data.putLong("sequence",sequence);data.putBoolean("visible",added&&!parked);data.putBoolean("retention",true);data.putBoolean("retained",added);data.putBoolean("parked",parked);data.putInt("timeline_protocol",CardTimeline.VERSION);data.putBoolean("layout_ready",layoutReady&&added&&!parked);data.putLong("timeline",timelineId);data.putLong("start_uptime",timelineStart);data.putBoolean("timeline_armed",windowTimeline.running());if(error!=null)data.putString("error",error);Message message=Message.obtain(null,2);message.setData(data);message.replyTo=endpoint;client.send(message);}catch(RemoteException dead){Log.w("WindChaserMap","Host acknowledgement unavailable");}}
    private void blocked(String reason){if(client==null)return;try{Bundle data=new Bundle();data.putLong("session",session);data.putLong("sequence",sequence);data.putBoolean("visible",false);data.putBoolean("blocked",true);data.putString("reason",reason);Message message=Message.obtain(null,2);message.setData(data);message.replyTo=endpoint;client.send(message);}catch(RemoteException ignored){}}
    private void unlinkHost(){if(client!=null&&hostDeath!=null)try{client.getBinder().unlinkToDeath(hostDeath,0);}catch(Exception ignored){}hostDeath=null;client=null;}
    @SuppressWarnings("deprecation") private int foregroundTask(){
        try{ActivityManager manager=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);List<ActivityManager.RunningTaskInfo> tasks=manager.getRunningTasks(1);if(tasks==null||tasks.isEmpty())return HostWindowGuard.UNKNOWN;ComponentName top=tasks.get(0).topActivity;if(top==null)return HostWindowGuard.UNKNOWN;
            return "cn.windchaser.unified".equals(top.getPackageName())?HostWindowGuard.HOST:HostWindowGuard.OTHER;}
        catch(RuntimeException unavailable){return HostWindowGuard.UNKNOWN;}
    }
    private static final class Slot {
        final SurfaceView view;final ViewGroup parent;final int index;final ViewGroup.LayoutParams params;
        Slot(SurfaceView v){view=v;parent=(ViewGroup)v.getParent();index=parent.indexOfChild(v);params=v.getLayoutParams();}
    }
    public static synchronized void install(Application application){
        // Only the map UI process owns the native SurfaceViews. Push/location
        // processes must not register a competing control endpoint or ACK a
        // card request whose surfaces they can never provide.
        if(instance!=null||!cn.windchaser.startup.RuntimeProbe.mainProcess(application))return;
        DefaultMapView.install(application);
        try{instance=new MapCardBridge(application);application.registerActivityLifecycleCallbacks(instance);Log.i("WindChaserMap","6.5 card bridge installed v12 (layout-confirmed shared timeline; signature-protected system overlay + verified window focus + recoverable guard)");}
        catch(RuntimeException error){Log.e("WindChaserMap","Bridge unavailable; original application continues",error);}
    }
    /** No SHOW, reparent, lifecycle replay, or native initialization during a readiness query. */
    public static boolean interfaceReady(){
        MapCardBridge bridge=instance;
        return bridge!=null&&MapInterfaceReadiness.ready(bridge.owner.get(),bridge.mapSurface,bridge.uiSurface);
    }
    /** Called at the end of the original native Activity methods, after their cleanup. */
    public static void afterLifecycle(Activity activity,int phase){
        MapCardBridge b=instance;if(b==null||b.owner.get()!=activity)return;
        if(phase==1)b.paused=true;if(phase==2)b.stopped=true;
        b.runtime.invalidateRecovery();b.handler.removeCallbacks(b.recover);if(b.added)b.handler.post(b.recover);
        else if(b.desired!=null){b.handler.removeCallbacks(b.retry);b.handler.post(b.retry);}
    }
    /** rd.g() can run after navigation/orientation changes using fullscreen metrics. */
    public static void afterLayout(Activity activity){
        MapCardBridge b=instance;if(b==null||!b.added||b.owner.get()!=activity||b.desired==null)return;
        try{
            for(Slot slot:b.slots){ViewGroup.LayoutParams p=slot.view.getLayoutParams();if(p.width!=b.desired.width()||p.height!=b.desired.height()){b.runtime.cancelTouch();slot.view.setLayoutParams(b.cardLayout());slot.view.getHolder().setFixedSize(b.desired.width(),b.desired.height());}}
            b.runtime.pinActivitySize(activity,b.desired.width(),b.desired.height());b.handler.removeCallbacks(b.viewport);b.handler.post(b.viewport);
        }catch(Exception error){Log.e("WindChaserMap","Cannot preserve card layout",error);b.hide(true);}
    }
    /** Original HMI plugin callbacks may arrive after Activity lifecycle recovery. */
    public static String hmiValue(Object config,String value){
        MapCardBridge bridge=instance;
        return bridge!=null&&bridge.added&&bridge.runtime.ownsHmi(config)?"1":value;
    }
    private MapCardBridge(Application application){
        app=application.getApplicationContext();cn.windchaser.startup.RuntimeProbe.install(app);windows=(WindowManager)app.getSystemService(Context.WINDOW_SERVICE);
        HandlerThread commands=new HandlerThread("WindChaserCardCommands");commands.start();
        worker=new Handler(commands.getLooper());
        endpoint=new Messenger(new Handler(commands.getLooper()){@Override public void handleMessage(Message message){
            Bundle data=new Bundle(message.getData());
            if(message.what==3||message.what==5){
                synchronized(guard){
                if(data.getLong("session")!=session||data.getLong("sequence")!=sequence)return;
                long issued=data.getLong("issued"),now=SystemClock.elapsedRealtime();
                if(issued>now||now-issued>1500)return;
                if(message.what==3){lastHostSignal=Math.max(lastHostSignal,issued);guard.signal(issued);return;}
                int result=guard.reply(data.getLong("probe"),issued,now,data.getBoolean("focused"));
                if(result==0)block(session,sequence,"host_not_focused");
                else if(result==1&&awaitingFocus){long s=session,q=sequence;handler.post(()->{if(session==s&&sequence==q&&awaitingFocus)show();});}
                return;
                }
            }
            if(message.what==7){handler.post(()->startTimeline(data));return;}
            if(message.what==8){handler.post(()->{if(data.getLong("session")==session&&data.getLong("sequence")==sequence&&data.getLong("timeline")==timelineId)cancelTimeline();});return;}
            if(message.what==6){synchronized(MapCardBridge.this){pendingMove=data;if(!movePosted){movePosted=true;handler.post(moveFrame);}}return;}
            if(message.what!=1)return;
            Runnable action=()->accept(data);if(data.getBoolean("show"))handler.post(action);else handler.postAtFrontOfQueue(action);
        }});
        IntentFilter controls=new IntentFilter(SHOW);controls.addAction(CLOSE);
        // The launcher already owns/requests this signature permission. Protect
        // Binder handoff at the broadcast boundary on KitKat as well as newer ROMs.
        app.registerReceiver(receiver,controls,CONTROL_PERMISSION,null);
        IntentFilter power=new IntentFilter(Intent.ACTION_SCREEN_OFF);power.addAction(Intent.ACTION_SHUTDOWN);
        app.registerReceiver(receiver,power);
    }
    private boolean mapActivity(Activity activity){return activity.getClass().getName().equals("com.autonavi.amapauto.MainMapActivity");}
    private boolean discover(){
        Activity activity=owner.get();if(activity==null||activity.isFinishing())return false;
        if(!slots.isEmpty())return true;
        int mapId=activity.getResources().getIdentifier("map_surface","id",app.getPackageName());
        int uiId=activity.getResources().getIdentifier("ui_surface","id",app.getPackageName());
        View map=activity.findViewById(mapId),ui=activity.findViewById(uiId);
        if(!(map instanceof SurfaceView)||!(ui instanceof SurfaceView)||map==ui||!(map.getParent() instanceof ViewGroup)||!(ui.getParent() instanceof ViewGroup))return false;
        mapSurface=(SurfaceView)map;uiSurface=(SurfaceView)ui;
        if(observedMap!=mapSurface||observedUi!=uiSurface){unwatch();observedMap=mapSurface;observedUi=uiSurface;observedMap.getHolder().addCallback(surfaces);observedUi.getHolder().addCallback(surfaces);}
        slots.add(new Slot(mapSurface));slots.add(new Slot(uiSurface));return true;
    }
    private void unwatch(){if(observedMap!=null)observedMap.getHolder().removeCallback(surfaces);if(observedUi!=null)observedUi.getHolder().removeCallback(surfaces);observedMap=observedUi=null;}
    private void show(){
        if(desired==null||client==null||!client.getBinder().isBinderAlive())return;
        if(SystemClock.elapsedRealtime()-lastHostSignal>HostWindowGuard.LEASE_MS){hide(true);blocked("heartbeat_timeout");return;}
        if(!guard.focused(SystemClock.elapsedRealtime())){
            awaitingFocus=true;worker.removeCallbacks(watchdog);worker.post(watchdog);return;
        }
        if(!discover()||!paused){
            if(retries++<20){handler.removeCallbacks(retry);handler.postDelayed(retry,retries<5?120:350);}
            else{Log.w("WindChaserMap","Map surfaces not ready; open the map and complete setup first");desired=null;ack("请先打开地图完成初始化");}
            return;
        }
        try{
            WindowManager.LayoutParams params=windowParams(desired,false,desiredMoving);
            if(!added){
                overlay=new FrameLayout(app);overlay.setBackgroundColor(Color.BLACK);
                for(Slot slot:slots){ViewParent current=slot.view.getParent();if(current instanceof ViewGroup)((ViewGroup)current).removeView(slot.view);slot.view.getHolder().setFixedSize(desired.width(),desired.height());overlay.addView(slot.view,cardLayout());}
                windows.addView(overlay,params);added=true;
                runtime.enter(owner.get(),mapSurface,uiSurface);
                Log.i("WindChaserMap","Native surfaces attached to "+desired);
            }else{
                boolean resized=attachedBounds==null||desired.width()!=attachedBounds.width()||desired.height()!=attachedBounds.height();
                if(resized){runtime.cancelTouch();for(Slot slot:slots){slot.view.getHolder().setFixedSize(desired.width(),desired.height());slot.view.setLayoutParams(cardLayout());}}
                if(parked||motionUntouchable||!desired.equals(attachedBounds))windows.updateViewLayout(overlay,params);
            }
            parked=false;motionUntouchable=desiredMoving;
            awaitingFocus=false;attachedBounds=new Rect(desired);confirmWindowLayout();worker.removeCallbacks(watchdog);worker.postDelayed(watchdog,120);ack(null);
        }catch(Exception error){Log.e("WindChaserMap","Cannot attach system overlay; restoring original surfaces",error);hide(true);ack("系统拒绝地图悬浮窗，请检查悬浮窗权限");}
    }
    private WindowManager.LayoutParams windowParams(Rect area,boolean hidden,boolean moving){
        int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if(hidden||moving)flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        WindowManager.LayoutParams p=new WindowManager.LayoutParams(area.width(),area.height(),Build.VERSION.SDK_INT>=26?2038:2002,flags,PixelFormat.TRANSLUCENT);
        p.gravity=Gravity.TOP|Gravity.LEFT;p.x=hidden?-Math.max(8192,area.width()*3):area.left;p.y=area.top;p.setTitle("WindChaser Map 6.5 card");return p;
    }
    /** Keep both native SurfaceViews attached, visible and at their current size.
     * GONE/INVISIBLE would destroy their Surface on KitKat. No input may reach a parked window. */
    private void park(){
        cancelTimeline();clearLayoutProof();runtime.cancelTouch();motionUntouchable=false;
        if(!added||desired==null){ack(null);return;}
        try{windows.updateViewLayout(overlay,windowParams(desired,true,false));parkedAt=SystemClock.elapsedRealtime();parked=true;}
        catch(RuntimeException error){Log.e("WindChaserMap","Cannot park window",error);hide(true);}
    }
    /** Pure position updates: never reparent, resize buffers, enter JNI or reset the focus lease. */
    private void move(Bundle data){
        moveCommands++;if(windowTimeline.running())return;
        long now=SystemClock.elapsedRealtime(),issued=data.getLong("issued");
        if(!added||parked||desired==null||data.getLong("session")!=session||data.getLong("sequence")!=sequence||issued>now||now-issued>500||!guard.focused(now))return;
        Rect area=new Rect(data.getInt("x"),data.getInt("y"),data.getInt("w"),data.getInt("h"));
        Point display=new Point();windows.getDefaultDisplay().getRealSize(display);
        if(area.width()!=desired.width()||area.height()!=desired.height()||area.left<0||area.top<0||area.right>display.x||area.bottom>display.y)return;
        boolean moving=data.getBoolean("moving");
        if(area.equals(attachedBounds)&&moving==motionUntouchable)return;
        try{if(moving&&!motionUntouchable)runtime.cancelTouch();windows.updateViewLayout(overlay,windowParams(area,false,moving));desired=area;attachedBounds=new Rect(area);motionUntouchable=desiredMoving=moving;if(!moving)confirmWindowLayout();}
        catch(RuntimeException error){Log.e("WindChaserMap","Cannot move card",error);hide(true);}
    }
    private FrameLayout.LayoutParams cardLayout(){
        // Native de.B / GAdaAndroid.nativeOnLayoutChange receives these bounds.
        // Both view and buffer have the card aspect, allowing HMI to relayout.
        return new FrameLayout.LayoutParams(desired.width(),desired.height(),Gravity.TOP|Gravity.LEFT);
    }
    private void hide(boolean clearRequest){
        hide(clearRequest,false);
    }
    private void hide(boolean clearRequest,boolean resuming){
        cancelTimeline();clearLayoutProof();
        awaitingFocus=false;handler.removeCallbacks(retry);handler.removeCallbacks(recover);handler.removeCallbacks(viewport);if(clearRequest)desired=null;
        worker.removeCallbacks(watchdog);
        // Withdraw the visible/touchable system overlay BEFORE native cleanup.
        if(overlay!=null){if(added)try{windows.removeViewImmediate(overlay);}catch(RuntimeException error){
            // A failed remove must not leave a black/touchable root above camera.
            final FrameLayout retired=overlay;retired.setVisibility(View.GONE);
            handler.post(()->{try{windows.removeViewImmediate(retired);}catch(RuntimeException ignored){}});
        }overlay.removeAllViews();}
        added=false;parked=false;motionUntouchable=false;overlay=null;attachedBounds=null;synchronized(this){pendingMove=null;}handler.removeCallbacks(moveFrame);movePosted=false;ack(null);
        Activity a=owner.get();if(a!=null)try{runtime.leave(a,mapSurface,uiSurface,resuming,paused,stopped);}catch(Exception error){Log.e("WindChaserMap","Native card cleanup failed",error);}
        // Restore original z order, parent and layout, including when addView failed midway.
        ArrayList<Slot> restore=new ArrayList<>(slots);Collections.sort(restore,new Comparator<Slot>(){public int compare(Slot a,Slot b){return a.index-b.index;}});
        // This exact original APK never calls setFixedSize: its buffers follow
        // layout. Release our temporary card buffer size when restoring it.
        for(Slot slot:restore){try{slot.view.getHolder().setSizeFromLayout();if(slot.view.getParent()==slot.parent)continue;ViewParent parent=slot.view.getParent();if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(slot.view);slot.parent.addView(slot.view,Math.min(slot.index,slot.parent.getChildCount()),slot.params);}catch(RuntimeException error){Log.e("WindChaserMap","Surface restore failed",error);}}
    }
    @Override public void onActivityCreated(Activity a,Bundle state){if(mapActivity(a)){hide(true,true);slots.clear();runtime.uiBufferSize(0,0);owner=new WeakReference<>(a);paused=stopped=false;}}
    @Override public void onActivityStarted(Activity a){if(mapActivity(a)){hide(true,true);stopped=false;}}
    @Override public void onActivityResumed(Activity a){if(mapActivity(a)){hide(true,true);paused=stopped=false;owner=new WeakReference<>(a);slots.clear();discover();}}
    @Override public void onActivityPaused(Activity a){if(mapActivity(a)){discover();afterLifecycle(a,1);}}
    @Override public void onActivityStopped(Activity a){if(mapActivity(a))afterLifecycle(a,2);}
    @Override public void onActivitySaveInstanceState(Activity a,Bundle state){}
    @Override public void onActivityDestroyed(Activity a){if(owner.get()==a){hide(true);unwatch();slots.clear();mapSurface=uiSurface=null;owner.clear();}}
}
