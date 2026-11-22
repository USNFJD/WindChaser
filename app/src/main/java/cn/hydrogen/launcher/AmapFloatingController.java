package cn.hydrogen.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Rect;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.View;
import android.view.ViewTreeObserver;
import cn.windchaser.motion.CardTimeline;

/** Controls the map's system overlay with sequenced commands and a foreground lease. */
final class AmapFloatingController {
    static final String PACKAGE = "com.autonavi.amapauto";
    static final String VERSION = "6.5.0.601323";
    static final String SHOW = "com.autonavi.plus.showmap";
    static final String CLOSE = "com.autonavi.plus.closemap";
    interface Transport { void send(Intent intent); }
    interface Availability { boolean available(); }

    private final Config config;
    private final Transport transport;
    private final Availability availability;
    private final boolean firstSetupWindow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Rect lastBounds = new Rect();
    private final ViewTreeObserver.OnGlobalLayoutListener layout = this::sync;
    private final ViewTreeObserver.OnPreDrawListener drawn = ()->{sync();return true;};
    private final View.OnAttachStateChangeListener attachment = new View.OnAttachStateChangeListener() {
        @Override public void onViewAttachedToWindow(View view) { sync(); }
        @Override public void onViewDetachedFromWindow(View view) { sync(); }
    };
    private static long latestInstance;
    private final long instance = ++latestInstance;
    private View anchor;
    private boolean active, mapPage, focused, screenOn = true, requested, released, connected;
    private int attempt;
    private Runnable changed;
    private LinkedAppCoordinator coordinator;
    private java.util.concurrent.Callable<Boolean> dialogFocus=()->false;
    void setDialogFocus(java.util.concurrent.Callable<Boolean> focus){dialogFocus=focus;}
    private boolean ownDialogFocused(){try{return dialogFocus.call();}catch(Exception ignored){return false;}}
    private final Runnable interfaceChanged=this::sync;
    private String status = "正在恢复地图；首次使用需打开高德完成授权";
    private final Runnable retry = ()->{if(this.parked)sendPark();else sendShow();};
    private final Runnable closeRetry = this::sendClose;
    private int closeAttempt;
    private boolean closing;
    private String requestedPackage=PACKAGE;
    
    private final long session=SystemClock.elapsedRealtimeNanos();
    private long sequence;
    private boolean safetyBlocked,parked,retentionCapable,moving,lastMoving,windowVisible;
    private boolean timelineCapable,windowLayoutReady,timelineArmed;
    private long timelineSerial,timelineId,timelineStart,timelineSequence;
    private int timelineCommands;
    private java.util.concurrent.Callable<Boolean> workspaceFocus=()->false;
    void setWorkspaceFocus(java.util.concurrent.Callable<Boolean> proof){workspaceFocus=proof;}
    boolean retainsSurfaces(){return retentionCapable;}
    boolean windowVisible(){return windowVisible;}
    boolean timelineSupported(){return timelineCapable;}
    boolean layoutConfirmed(){return windowVisible&&windowLayoutReady;}
    boolean timelineArmed(){return timelineId!=0&&timelineArmed;}
    long prepareTimeline(int offset,long duration){
        if(!timelineCapable||!layoutConfirmed()||!requested||parked||remote==null||duration<80||duration>400)return 0;
        cancelTimeline();timelineId=++timelineSerial;timelineSequence=sequence;timelineArmed=false;
        timelineStart=SystemClock.uptimeMillis()+CardTimeline.LEAD_MS;
        try{Bundle data=new Bundle();data.putLong("session",session);data.putLong("sequence",sequence);data.putLong("issued",SystemClock.elapsedRealtime());
            data.putLong("timeline",timelineId);data.putLong("start_uptime",timelineStart);data.putLong("duration",duration);
            data.putInt("from_x",lastBounds.left);data.putInt("to_x",lastBounds.left-offset);data.putInt("top",lastBounds.top);data.putInt("width",lastBounds.width());data.putInt("height",lastBounds.height());
            Message message=Message.obtain(null,7);message.setData(data);remote.send(message);timelineCommands++;return timelineStart;
        }catch(RemoteException dead){timelineId=0;timelineArmed=false;return 0;}
    }
    void cancelTimeline(){
        long id=timelineId;timelineId=0;timelineArmed=false;timelineStart=0;
        if(id==0||remote==null)return;
        try{Bundle data=new Bundle();data.putLong("session",session);data.putLong("sequence",timelineSequence);data.putLong("timeline",id);
            Message message=Message.obtain(null,8);message.setData(data);remote.send(message);
        }catch(RemoteException ignored){}
    }
    void setMotion(boolean value){if(!value)cancelTimeline();moving=value;sync();}
    private boolean ownWorkspace(){try{return workspaceFocus.call();}catch(Exception ignored){return false;}}
    private boolean canPark(){return !firstSetupWindow&&!released&&screenOn&&selected(config)&&config.b("amapFloating",true)&&retentionCapable&&ownWorkspace();}
    void refreshWorkspace(){sync();}
    void parkForNavigation(){boolean alreadyParked=parked;setHostState(false,false,false);if(alreadyParked&&requested&&canPark()){sequence++;awaitingReply=false;sendPark();}}
    private Messenger remote;
    private long sentAt,lastAckAt;private boolean awaitingReply;
    private final Messenger replies=new Messenger(new Handler(Looper.getMainLooper()){
        @Override public void handleMessage(Message message){
            if((message.what!=2&&message.what!=4)||released)return;
            Bundle data=message.getData();
            if(data.getLong("session")!=session||data.getLong("sequence")!=sequence)return;
            if(message.what==4){
                try{
                    Bundle proof=new Bundle();proof.putLong("session",session);proof.putLong("sequence",sequence);
                    proof.putLong("probe",data.getLong("probe"));proof.putLong("issued",SystemClock.elapsedRealtime());
                    proof.putBoolean("focused",requested&&!safetyBlocked&&(parked?canPark():bounds()!=null));
                    Message answer=Message.obtain(null,5);answer.setData(proof);
                    if(message.replyTo!=null)message.replyTo.send(answer);
                }catch(RemoteException ignored){}
                return;
            }
            lastAckAt=SystemClock.elapsedRealtime();boolean previouslyVisible=windowVisible;
            windowVisible=requested&&data.getBoolean("visible");
            timelineCapable=data.getInt("timeline_protocol")==CardTimeline.VERSION;
            windowLayoutReady=windowVisible&&data.getBoolean("layout_ready");
            if(timelineId!=0&&data.getLong("timeline")==timelineId&&data.getLong("start_uptime")==timelineStart)timelineArmed=data.getBoolean("timeline_armed");
            awaitingReply=false;remote=message.replyTo;retentionCapable=data.getBoolean("retention",false);connected=requested&&(data.getBoolean("visible")||data.getBoolean("retained"));
            if(requested&&(parked?data.getBoolean("retained"):data.getBoolean("visible"))){
                if(!parked&&retentionCapable&&(!previouslyVisible||lastMoving!=moving))sendMove();
                handler.removeCallbacks(retry);
                updateStatus("地图卡片已连接");
            }else if(!requested&&!data.getBoolean("visible")){
                handler.removeCallbacks(closeRetry);closing=false;
            }else if(data.getBoolean("blocked")){
                safetyBlocked=true;requested=false;connected=false;parked=false;lastBounds.setEmpty();
                handler.removeCallbacks(retry);handler.removeCallbacks(heartbeat);
                LauncherApp.record("高德卡片让位："+data.getString("reason"),null);
                updateStatus("正在确认桌面状态，地图将自动恢复");
                handler.removeCallbacks(recoverBlocked);handler.postDelayed(recoverBlocked,500);
            }else if(requested){
                if(data.containsKey("error"))updateStatus("地图卡片："+data.getString("error"));
                else{
                    // Native lifecycle cleanup can withdraw a previously connected
                    // surface. Its negative ACK re-arms the real card handshake.
                    updateStatus("正在恢复地图卡片");handler.removeCallbacks(retry);handler.postDelayed(retry,250);
                }
            }
        }
    });
    private final Runnable recoverBlocked=new Runnable(){public void run(){
        if(released||!safetyBlocked||!active||!mapPage||!screenOn||anchor==null)return;
        // Re-sample the actual ViewRoot focus, not just an old lifecycle flag.
        if(bounds()!=null){safetyBlocked=false;sync();}
        else handler.postDelayed(this,500);
    }};
    private long availableAt=-2000;private boolean available;
    private final Runnable heartbeat=new Runnable(){public void run(){
        if(!requested||released)return;
        Rect current=bounds();if(parked){if(!canPark()){hide();return;}}else{if(current==null){sync();return;}if(!lastBounds.equals(current)){sync();if(!requested)return;}}
        if(remote!=null)try{Bundle data=new Bundle();data.putLong("session",session);data.putLong("sequence",sequence);data.putLong("issued",SystemClock.elapsedRealtime());Message message=Message.obtain(null,3);message.setData(data);remote.send(message);}catch(RemoteException gone){remote=null;requested=false;connected=false;awaitingReply=false;sync();return;}
        // A live Binder can outlive a withdrawn native surface. Revalidate the
        // existing sequence; the adapter ACK is idempotent and does not reparent.
        long now=SystemClock.elapsedRealtime();
        if(connected&&((!awaitingReply&&now-lastAckAt>=2000)||(awaitingReply&&now-sentAt>=750)))try{command(!parked);}
        catch(RemoteException gone){remote=null;awaitingReply=false;}
        handler.postDelayed(this,250);
    }};

    AmapFloatingController(Activity activity, Config config) {
        this(config, activity.getApplicationContext()::sendBroadcast, availability(activity.getApplicationContext(),config));
        coordinator=LinkedAppCoordinator.get(activity);coordinator.listen(interfaceChanged);
        // The existing system overlay is retained. The companion independently
        // requests fresh window-focus proof and watches our reply Binder lifetime.
    }
    private static Availability availability(Context context,Config config) { return () -> supported(context,config.pkg(false)); }

    // The transport boundary allows lifecycle/coordinate tests without executing third-party code.
    AmapFloatingController(Config config, Transport transport, Availability availability) {
        this(config,transport,availability,false);
    }
    static AmapFloatingController firstSetup(Activity activity,Config config){
        return new AmapFloatingController(config,activity.getApplicationContext()::sendBroadcast,
                availability(activity.getApplicationContext(),config),true);
    }
    private AmapFloatingController(Config config,Transport transport,Availability availability,boolean firstSetupWindow){
        this.firstSetupWindow=firstSetupWindow;
        this.config = config;
        requestedPackage=config.pkg(false);
        this.transport = transport;
        this.availability = availability;
    }

    static boolean supported(Context context){return supported(context,PACKAGE);}
    static boolean supported(Context context,String pkg){
        if(!PACKAGE.equals(pkg))return false;
        try{
            PackageInfo info=context.getPackageManager().getPackageInfo(pkg,0);
            if(!info.applicationInfo.enabled||!VERSION.equals(info.versionName))return false;
            Context map=context.createPackageContext(pkg,0);
            try(java.io.InputStream in=map.getAssets().open("windchaser-card-bridge.json")){
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[256];int count;
                while((count=in.read(bytes))!=-1){if(out.size()+count>2048)return false;out.write(bytes,0,count);}
                return validMarker(new org.json.JSONObject(out.toString("UTF-8")));
            }
        }catch(Exception ignored){return false;}
    }
    static boolean validMarker(org.json.JSONObject m){return m.optInt("bridge_version")==1&&m.optInt("bridge_revision")>=8&&m.optBoolean("sequenced_window_commands")&&m.optBoolean("foreground_host_guard")&&VERSION.equals(m.optString("base_version"))
        &&"7daa40731d4f1bf2b1e521280bc031e5892c48f9092d918ded12da963b7c3950".equals(m.optString("base_sha256"))
        &&m.optBoolean("hmi_foreground_guard")&&m.optBoolean("card_relayout")&&m.optBoolean("surface_buffer_matches_card")&&m.optBoolean("buffer_touch_coordinates");}
    static boolean selected(Config config){return PACKAGE.equals(config.pkg(false));}
    static void autoSelect(Context context,Config config){if(!PACKAGE.equals(config.s("mapPackage",PACKAGE)))config.put("mapPackage",PACKAGE);}
    static void select(Config config){config.put("mapPackage",PACKAGE);config.put("amapFloating",true);}

    static Intent showIntent(Rect bounds) {
        return showIntent(bounds,PACKAGE);
    }
    static Intent showIntent(Rect bounds,String pkg) {
        if (bounds.left < 0 || bounds.top < 0 || bounds.width() <= 0 || bounds.height() <= 0)
            throw new IllegalArgumentException("Invalid floating map bounds: " + bounds);
        // This particular plugin assigns w/h directly to Rect.right/bottom.
        return new Intent(SHOW).setPackage(pkg).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                .putExtra("x", bounds.left).putExtra("y", bounds.top)
                .putExtra("w", bounds.right).putExtra("h", bounds.bottom);
    }

    static Intent closeIntent() { return new Intent(CLOSE).setPackage(PACKAGE).addFlags(Intent.FLAG_RECEIVER_FOREGROUND); }
    static Intent closeIntent(String pkg){return new Intent(CLOSE).setPackage(pkg).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);}
    static void closeAll(Context context){context.sendBroadcast(closeIntent());}

    static void openPermissions(Activity activity) {
        String pkg=new Config(activity).pkg(false);
        String action = Build.VERSION.SDK_INT >= 23
                ? "android.settings.action.MANAGE_OVERLAY_PERMISSION" : Settings.ACTION_APPLICATION_DETAILS_SETTINGS;
        Intent intent = new Intent(action, Uri.parse("package:" + pkg));
        if (activity.getPackageManager().resolveActivity(intent, 0) == null)
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg));
        Ui.open(activity, intent);
        Ui.toast(activity, "请允许高德地图显示悬浮窗；部分车机在权限管理或自启动管理中设置");
    }

    void setChanged(Runnable callback) { changed = callback; }
    String status() { return status; }
    boolean connected(){return connected;}

    void bind(View view) {
        if (anchor == view) return;
        if (anchor != null && anchor.getViewTreeObserver().isAlive()){
            anchor.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
            anchor.getViewTreeObserver().removeOnPreDrawListener(drawn);
        }
        if (anchor != null) anchor.removeOnAttachStateChangeListener(attachment);
        anchor = view;
        if (anchor != null) { anchor.getViewTreeObserver().addOnGlobalLayoutListener(layout);anchor.getViewTreeObserver().addOnPreDrawListener(drawn); anchor.addOnAttachStateChangeListener(attachment); }
        sync();
    }

    void setHostState(boolean active, boolean mapPage, boolean focused) {
        boolean entering=active&&mapPage&&focused&&(!this.active||!this.mapPage||!this.focused);
        if(active&&focused&&(!this.active||!this.focused))safetyBlocked=false;
        this.active = active;
        this.mapPage = mapPage;
        this.focused = focused;
        sync();
        if(entering)refreshInterface();
    }

    private void refreshInterface(){
        if(coordinator!=null&&!coordinator.canConnect(config.pkg(false)))coordinator.refreshSoon();
    }

    void setScreenOn(boolean on) { boolean waking=on&&!screenOn;screenOn = on;if(on)safetyBlocked=false;sync();if(waking&&active&&mapPage&&focused)refreshInterface(); }

    private Rect bounds() {
        if (released || !active || !mapPage || !focused || !screenOn || anchor == null
                || !selected(config) || (!firstSetupWindow&&!config.b("amapFloating", true)) || !available()
                || (!anchor.hasWindowFocus()&&!ownDialogFocused()) || !anchor.isShown() || anchor.getWindowVisibility() != View.VISIBLE || anchor.getWindowToken() == null || anchor.getWidth() < 2 || anchor.getHeight() < 2)
            return null;
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        Rect bounds = new Rect(location[0], location[1], location[0] + anchor.getWidth(), location[1] + anchor.getHeight());
        android.graphics.Point display=new android.graphics.Point();
        if(anchor.getDisplay()!=null)anchor.getDisplay().getRealSize(display);
        int width=display.x>0?display.x:anchor.getResources().getDisplayMetrics().widthPixels;
        int height=display.y>0?display.y:anchor.getResources().getDisplayMetrics().heightPixels;
        // Refuse a partial/offscreen window rather than covering adjacent controls.
        if (bounds.left < 0 || bounds.top < 0 || bounds.right > width || bounds.bottom > height) return null;
        return bounds;
    }

    private void sync() {
        if(safetyBlocked){handler.removeCallbacks(recoverBlocked);handler.postDelayed(recoverBlocked,500);return;}
        Rect bounds = bounds();
        if (bounds == null) { temporaryHide(); return; }
        if(coordinator!=null&&!coordinator.canRequest(config.pkg(false))){temporaryHide();return;}
        if(timelineId!=0){
            if(requested&&!parked&&sequence==timelineSequence&&bounds.width()==lastBounds.width()&&bounds.height()==lastBounds.height())return;
            cancelTimeline();
        }
        if(requested&&!parked&&connected&&retentionCapable&&remote!=null&&bounds.width()==lastBounds.width()&&bounds.height()==lastBounds.height()){
            if(!bounds.equals(lastBounds)||lastMoving!=moving){lastBounds.set(bounds);sendMove();}
            return;
        }
        if (!requested || parked || !lastBounds.equals(bounds)) {
            handler.removeCallbacks(retry);handler.removeCallbacks(closeRetry);closing=false;
            lastBounds.set(bounds);requested=true;parked=false;lastMoving=moving;windowLayoutReady=false;
            sequence++;handler.removeCallbacks(heartbeat);handler.postDelayed(heartbeat,250);attempt=0;
            handler.post(retry);
        }
    }
    private void temporaryHide(){
        if(!requested)return;
        if(!canPark()){hide();return;}
        if(parked)return;
        cancelTimeline();parked=true;windowVisible=false;windowLayoutReady=false;sequence++;awaitingReply=false;
        handler.removeCallbacks(retry);handler.removeCallbacks(heartbeat);
        sendPark();handler.postDelayed(heartbeat,250);
    }
    private void sendPark(){
        if(!requested||!parked)return;
        if(!canPark()){hide();return;}
        try{command(false);handler.removeCallbacks(retry);handler.postDelayed(retry,500);}
        catch(Exception gone){hide();}
    }
    private void sendMove(){
        if(remote==null||!requested||parked||timelineId!=0)return;
        try{Bundle data=new Bundle();data.putLong("session",session);data.putLong("sequence",sequence);data.putLong("issued",SystemClock.elapsedRealtime());
            data.putInt("x",lastBounds.left);data.putInt("y",lastBounds.top);data.putInt("w",lastBounds.right);data.putInt("h",lastBounds.bottom);data.putBoolean("moving",moving);
            Message message=Message.obtain(null,6);message.setData(data);remote.send(message);lastMoving=moving;
        }catch(RemoteException dead){remote=null;connected=false;requested=false;sync();}
    }

    private boolean available(){long now=SystemClock.elapsedRealtime();if(now-availableAt>=1000){available=availability.available();availableAt=now;}return available;}

    private void command(boolean show) throws RemoteException {
        Bundle data=new Bundle();data.putInt("protocol",8);data.putLong("session",session);data.putLong("sequence",sequence);
        data.putLong("issued",SystemClock.elapsedRealtime());data.putBoolean("show",show);data.putBoolean("moving",moving);
        data.putBoolean("release",released);data.putBoolean("park",parked&&!show);
        data.putParcelable("client",replies);
        if(show){data.putInt("x",lastBounds.left);data.putInt("y",lastBounds.top);data.putInt("w",lastBounds.right);data.putInt("h",lastBounds.bottom);}
        // A live Binder is not proof that the old endpoint still answers.
        // Rediscover via the signature-protected receiver after one ACK deadline.
        if(remote!=null&&awaitingReply&&SystemClock.elapsedRealtime()-sentAt>=750)remote=null;
        if(!awaitingReply)sentAt=SystemClock.elapsedRealtime();awaitingReply=true;
        if(remote!=null){try{Message message=Message.obtain(null,1);message.setData(data);message.replyTo=replies;remote.send(message);return;}catch(RemoteException gone){remote=null;}}
        Intent intent=show?showIntent(lastBounds,requestedPackage):closeIntent(requestedPackage);intent.putExtras(data);transport.send(intent);
    }

    void reconnect() {
        safetyBlocked=false;
        hide();
        sync();
        refreshInterface();
        if (!requested) updateStatus("等待地图卡片显示；首次须先启动高德并加载地图");
    }

    void closeWindow() {
        hide();
        // Also clean up a window left behind by a previous launcher instance.
        if (!closing && selected(config) && available()) beginClose();
    }

    private void sendShow() {
        Rect bounds = bounds();
        if (bounds == null) { hide(); return; }
        if (!bounds.equals(lastBounds)) { sync(); return; }
        for(View parent=anchor;parent!=null;){
            if(parent.isLayoutRequested()){handler.removeCallbacks(retry);handler.postDelayed(retry,16);return;}
            android.view.ViewParent next=parent.getParent();parent=next instanceof View?(View)next:null;
        }
        try {
            requestedPackage=config.pkg(false);command(true);
            if (attempt == 0) LauncherApp.record("高德系统悬浮卡片显示请求 " + bounds, null);
            updateStatus("正在请求恢复地图。若未显示，可点打开地图；首次使用需完成高德授权");
        } catch (Exception error) {
            LauncherApp.record("高德悬浮显示请求失败", error);
            hide();
            updateStatus("发送失败：" + error.getClass().getSimpleName());
            return;
        }
        int[] delay = {120, 250, 500, 1000, 2000, 3500, 5000};
        if(!connected){
            // Keep a visible pending card recoverable when native initialization
            // finishes after the short retry series. Pausing/hiding cancels this.
            handler.removeCallbacks(retry);
            handler.postDelayed(retry,attempt<delay.length?delay[attempt++]:5000);
        }
    }

    private void hide() {
        cancelTimeline();windowLayoutReady=false;
        handler.removeCallbacks(recoverBlocked);
        handler.removeCallbacks(retry);
        handler.removeCallbacks(heartbeat);
        if (!requested) return;
        requested = false;connected=false;parked=false;windowVisible=false;awaitingReply=false;
        sequence++;
        lastBounds.setEmpty();
        beginClose();
    }

    private void beginClose() {
        handler.removeCallbacks(closeRetry);
        closing = true;
        closeAttempt = 0;
        sendClose();
    }

    private void sendClose() {
        // A replacement Activity owns its own cleanup. Never let an old destroyed
        // instance hide the map after the new card has become visible.
        if (released && instance != latestInstance) { closing = false; return; }
        if (requested || !closing) return;
        try {
            command(false);
            if (closeAttempt == 0) LauncherApp.record("高德系统悬浮卡片隐藏请求", null);
        } catch (Exception error) { LauncherApp.record("高德悬浮关闭请求失败", error); }
        // Delivery and native window creation are asynchronous. Close again after a
        // late show, but never poll indefinitely or interfere after returning to the card.
        int[] delay = {40, 100, 220, 500};
        if (closeAttempt < delay.length)
            handler.postDelayed(closeRetry, delay[closeAttempt++]);
    }

    private void updateStatus(String value) {
        if (status.equals(value)) return;
        status = value;
        if (changed != null) changed.run();
    }

    void release() {
        if(coordinator!=null)coordinator.unlisten(interfaceChanged);
        if (released) return;
        changed = null;
        dialogFocus=()->false;workspaceFocus=()->false;
        released = true;
        bind(null);
        hide();
        handler.removeCallbacks(retry);
        // Keep a short, finite close sweep after destruction: a show already in
        // the other process's queue can arrive after the first close broadcast.
        if (closing || (selected(config) && availability.available())) beginClose();
    }
}
