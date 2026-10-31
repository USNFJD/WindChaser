package cn.hydrogen.launcher;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.io.*;
import java.util.*;

/** Process-wide admission and live-interface checks. Probes never launch an Activity or Service. */
final class LinkedAppCoordinator {
    static final String PROBE="cn.windchaser.startup.PROBE";
    static final int PROBE_REPLY=6;
    static final String PROBE_AVAILABLE="cn.windchaser.startup.PROBE_AVAILABLE";
    private static final long PROBE_TIMEOUT=500;
    private static final int STARTUP_WAIT_MS=900;
    private static final long STARTUP_PROBE_TIMEOUT=1200;
    private static LinkedAppCoordinator instance;
    interface Result { void ready(boolean ready); }
    interface Sender { void send(Intent intent); }
    interface Capability { boolean supports(String pkg); }
    private final Context app;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Sender sender;private final Capability capability;private final boolean testTransport;
    private final Map<String,Entry> entries=new HashMap<>();
    private final Map<String,Boolean> supported=new HashMap<>();
    private final Set<Runnable> listeners=new LinkedHashSet<>();
    private boolean admitted,hostVisible,sequenceActive;
    private int recoveryAttempt;private long subscribedAt=-10000;
    private final Runnable recovery=this::refresh;
    private final Runnable musicSubscription=this::sendMusicSubscription;
    private final Messenger replies=new Messenger(new Handler(Looper.getMainLooper()){
        @Override public void handleMessage(Message message){
            if(message.what!=PROBE_REPLY)return;
            Bundle data=message.getData();Entry entry=entries.get(data.getString("package"));
            if(entry==null||entry.nonce==null||!entry.nonce.equals(data.getString("nonce"))||data.getLong("issued")!=entry.issued)return;
            if(Build.VERSION.SDK_INT>=21&&message.sendingUid!=entry.uid)return;
            boolean live=message.replyTo!=null&&message.replyTo.getBinder().isBinderAlive();
            boolean ready=data.getBoolean("ready")&&live;
            entry.proofIssued=entry.issued;entry.foreground=live&&data.getBoolean("foreground");entry.firstSetup=live&&data.getBoolean("first_setup");
            finishProbe(entry,ready,message.replyTo);
        }
    });
    private static final class Entry {
        final String pkg;final List<Result> callbacks=new ArrayList<>();
        boolean ready,starting,lost,foreground,firstSetup;String nonce;long issued,proofIssued,lastWarmAt=-30000;int uid,waitMs;
        Messenger endpoint;IBinder.DeathRecipient death;Runnable timeout;
        Entry(String name){pkg=name;}
    }
    static LinkedAppCoordinator get(Context context){
        if(instance==null)instance=new LinkedAppCoordinator(context.getApplicationContext(),null,null);
        return instance;
    }
    LinkedAppCoordinator(Context context,Sender transport,Capability features){
        app=context;testTransport=transport!=null;sender=transport==null?context::sendBroadcast:transport;
        capability=features==null?this::readCapability:features;
    }
    private Entry entry(String pkg){Entry item=entries.get(pkg);if(item==null){item=new Entry(pkg);entries.put(pkg,item);}return item;}
    private boolean supports(String pkg){Boolean value=supported.get(pkg);if(value==null){value=capability.supports(pkg);supported.put(pkg,value);}return value;}
    private boolean definitelyCold(String pkg){
        // Absence on KitKat can avoid a pointless probe timeout. Presence is NEVER
        // treated as readiness; modern restricted task/process lists are not used.
        if(Build.VERSION.SDK_INT>20||testTransport)return false;
        try{
            android.app.ActivityManager manager=(android.app.ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
            if(processes==null||processes.isEmpty())return false;
            int uid=app.getPackageManager().getApplicationInfo(pkg,0).uid;
            for(android.app.ActivityManager.RunningAppProcessInfo process:processes)if(process.uid==uid)return false;
            return true;
        }catch(Exception unavailable){return false;}
    }
    private boolean readCapability(String pkg){
        try{
            if(app.getPackageManager().checkSignatures(app.getPackageName(),pkg)!=PackageManager.SIGNATURE_MATCH)return false;
            Context remote=app.createPackageContext(pkg,0);
            try(InputStream in=remote.getAssets().open("windchaser-startup-ready.json")){
                ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[256];int count;
                while((count=in.read(bytes))!=-1){if(out.size()+count>2048)return false;out.write(bytes,0,count);}
                org.json.JSONObject metadata=new org.json.JSONObject(out.toString("UTF-8"));
                return metadata.optInt("interface_probe_version")==1&&pkg.equals(metadata.optString("package"));
            }
        }catch(Exception unavailable){return false;}
    }
    void listen(Runnable listener){listeners.add(listener);}
    void unlisten(Runnable listener){listeners.remove(listener);}
    private void changed(){for(Runnable listener:new ArrayList<>(listeners))listener.run();if(admitted&&!sequenceActive&&(canBind(new Config(app).pkg(true))||canBind(new Config(app).pkg(false))))CompanionService.sync(app);}
    void hostReady(){admitted=true;boolean entering=!hostVisible;if(entering){for(Entry item:entries.values())item.ready=false;}hostVisible=true;if(entering)changed();}
    void hostPaused(){hostVisible=false;handler.removeCallbacks(recovery);handler.removeCallbacks(musicSubscription);}
    void beginSequence(){admitted=true;sequenceActive=true;handler.removeCallbacks(recovery);handler.removeCallbacks(musicSubscription);changed();}
    void endSequence(){sequenceActive=false;for(Entry item:entries.values())item.starting=false;changed();if(hostVisible)refreshSoon();}
    void starting(String pkg){Entry item=entry(pkg);item.starting=true;clearEndpoint(item);item.ready=false;item.lost=true;changed();}
    void startupFinished(String pkg){entry(pkg).starting=false;probe(pkg,ready->{});}
    boolean busy(){return sequenceActive;}
    // Reuse a previously admitted session, never a process-name heuristic or a
    // persisted "ready" flag. A dead/lost endpoint requires normal admission.
    boolean restorable(String pkg){
        return !sequenceActive&&liveEndpoint(pkg);
    }
    // A remembered live endpoint avoids repeating passive background probes.
    // It never substitutes the fresh authenticated proofs required for admission.
    boolean liveEndpoint(String pkg){
        Entry item=entry(pkg);
        return !item.starting&&!item.lost&&item.ready
            &&item.endpoint!=null&&item.endpoint.getBinder().isBinderAlive();
    }
    // The real card/control handshake may recover a warm app even if a separate
    // read-only probe times out. Only a confirmed probe can skip automatic startup.
    boolean canRequest(String pkg){return admitted&&hostVisible&&!sequenceActive&&!entry(pkg).starting;}
    boolean interfaceLost(String pkg){return entry(pkg).lost;}
    void controlResponded(String pkg){entry(pkg).lost=false;}
    boolean canConnect(String pkg){
        if(!canRequest(pkg))return false;
        // Older paired APKs retain their existing live card/control handshake, but
        // can never skip the automatic startup based on a process-name heuristic.
        if(!supports(pkg))return true;
        Entry item=entry(pkg);return item.ready&&item.endpoint!=null&&item.endpoint.getBinder().isBinderAlive();
    }
    boolean canBind(String pkg){Entry item=entry(pkg);return admitted&&!sequenceActive&&!item.starting&&item.ready&&item.endpoint!=null&&item.endpoint.getBinder().isBinderAlive();}
    void probeInitializing(String pkg,Result callback){entry(pkg).starting=false;probe(pkg,callback);}
    // Only an already launched module gets a bounded native wait. Cold checks
    // keep their short timeout; every ready proof still expires after 1500ms.
    void awaitInitializing(String pkg,Result callback){entry(pkg).starting=false;probe(pkg,callback,STARTUP_WAIT_MS);}
    boolean confirmed(String pkg){
        Entry e=entry(pkg);long age=SystemClock.elapsedRealtime()-e.proofIssued;
        return e.ready&&age>=0&&age<1500&&e.endpoint!=null&&e.endpoint.getBinder().isBinderAlive();
    }
    boolean confirmedRecently(String pkg,long maxAge){return confirmed(pkg)&&SystemClock.elapsedRealtime()-entry(pkg).proofIssued<=maxAge;}
    boolean firstSetupWindow(String pkg){Entry e=entry(pkg);return e.firstSetup&&foregroundWindow(pkg);}
    boolean foregroundWindow(String pkg){Entry e=entry(pkg);long age=SystemClock.elapsedRealtime()-e.proofIssued;return e.foreground&&age>=0&&age<1500;}
    boolean foreground(String pkg){return entry(pkg).ready&&foregroundWindow(pkg);}
    void probe(String pkg,Result callback){probe(pkg,callback,0);}
    private void probe(String pkg,Result callback,int waitMs){
        Entry item=entry(pkg);
        if(item.starting){callback.ready(false);return;}
        if(!supports(pkg)||definitelyCold(pkg)){callback.ready(false);return;}
        item.callbacks.add(callback);
        if(item.nonce!=null)return;
        sendProbe(item,waitMs);
    }
    void probeAvailableInitializing(String pkg){
        Entry item=entry(pkg);
        if(!sequenceActive||item.nonce==null)return;
        // A request sent before a dynamic receiver existed cannot be delivered
        // later. Preserve its callbacks and replace only that pending request.
        handler.removeCallbacks(item.timeout);item.timeout=null;item.nonce=null;
        sendProbe(item,item.waitMs);
    }
    private void sendProbe(Entry item,int waitMs){
        String pkg=item.pkg;item.waitMs=waitMs;
        item.nonce=UUID.randomUUID().toString();item.issued=SystemClock.elapsedRealtime();
        try{item.uid=app.getPackageManager().getApplicationInfo(pkg,0).uid;}
        catch(PackageManager.NameNotFoundException removed){finishProbe(item,false,null);return;}
        item.timeout=()->finishProbe(item,false,null);handler.postDelayed(item.timeout,waitMs>0?STARTUP_PROBE_TIMEOUT:PROBE_TIMEOUT);
        Intent request=new Intent(PROBE).setPackage(pkg).addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            .putExtra("protocol",1).putExtra("wait_ms",waitMs).putExtra("nonce",item.nonce).putExtra("issued",item.issued).putExtra("client",replies);
        try{sender.send(request);}catch(RuntimeException error){finishProbe(item,false,null);}
    }
    private void clearEndpoint(Entry item){
        if(item.endpoint!=null&&item.death!=null)try{item.endpoint.getBinder().unlinkToDeath(item.death,0);}catch(RuntimeException ignored){}
        item.endpoint=null;item.death=null;
    }
    private void finishProbe(Entry item,boolean ready,Messenger endpoint){
        if(item.nonce==null)return;
        handler.removeCallbacks(item.timeout);item.timeout=null;item.nonce=null;
        item.ready=ready;if(!ready&&endpoint==null){item.foreground=false;item.firstSetup=false;}
        if(!ready&&item.endpoint!=null&&!item.endpoint.getBinder().isBinderAlive()){
            clearEndpoint(item);item.lost=true;
        }
        if(ready){
            clearEndpoint(item);
            item.endpoint=endpoint;item.lost=false;final IBinder binder=endpoint.getBinder();
            item.death=()->handler.post(()->{if(item.endpoint!=null&&item.endpoint.getBinder().equals(binder)){clearEndpoint(item);item.ready=false;item.lost=true;changed();if(hostVisible&&!sequenceActive)refreshSoon();}});
            try{binder.linkToDeath(item.death,0);}catch(RemoteException dead){item.ready=false;item.lost=true;clearEndpoint(item);}
        }
        boolean accepted=item.ready;
        List<Result> callbacks=new ArrayList<>(item.callbacks);item.callbacks.clear();
        changed();for(Result callback:callbacks)callback.ready(accepted);
    }
    void interruptInitializing(){
        List<Result> cancelled=new ArrayList<>();
        for(Entry item:entries.values()){
            if(item.timeout!=null)handler.removeCallbacks(item.timeout);
            item.timeout=null;item.nonce=null;cancelled.addAll(item.callbacks);item.callbacks.clear();
            item.proofIssued=-1501;item.foreground=false;item.firstSetup=false;
        }
        // Dialog/player callers must also release their in-flight flags. They
        // receive failure, never a stale ready reply; service tickets are invalid.
        for(Result callback:cancelled)callback.ready(false);
    }
    void refreshSoon(){recoveryAttempt=0;handler.removeCallbacks(recovery);handler.post(recovery);}
    private void refresh(){
        if(!admitted||!hostVisible||sequenceActive)return;
        Config cfg=new Config(app);int[] backoff={200,500,1000,2000,3500,6000};
        if(recoveryAttempt<backoff.length)handler.postDelayed(recovery,backoff[recoveryAttempt++]);
        probe(cfg.pkg(true),ready->recovered(cfg,true,ready));probe(cfg.pkg(false),ready->recovered(cfg,false,ready));changed();
    }
    private void recovered(Config cfg,boolean music,boolean ready){
        if(entry(cfg.pkg(true)).ready&&entry(cfg.pkg(false)).ready)handler.removeCallbacks(recovery);
        if(ready)return;
        // Queue behind any first-frame automatic sequence already awaiting admission.
        // A public service may prewarm a missing interface, but never foreground its UI.
        handler.post(()->{
            String pkg=cfg.pkg(music);Entry item=entry(pkg);long now=SystemClock.elapsedRealtime();
            if(!admitted||!hostVisible||sequenceActive||item.starting||item.ready||!supports(pkg)||!cfg.b("autoRecoverApps",true)||now-item.lastWarmAt<30000)return;
            item.lastWarmAt=now;CompanionService.prewarm(app,cfg,music);
        });
    }
    void requestMusicSubscription(){
        String pkg=new Config(app).pkg(true);
        if(!canRequest(pkg)||definitelyCold(pkg))return;
        long delay=200-(SystemClock.elapsedRealtime()-subscribedAt);
        handler.removeCallbacks(musicSubscription);
        if(delay>0)handler.postDelayed(musicSubscription,delay);else sendMusicSubscription();
    }
    private void sendMusicSubscription(){
        String pkg=new Config(app).pkg(true);if(!canRequest(pkg)||definitelyCold(pkg))return;
        subscribedAt=SystemClock.elapsedRealtime();
        try{sender.send(QqMusicBridge.subscriptionIntent());}catch(RuntimeException error){LauncherApp.record("QQ音乐状态订阅受限",error);}
    }
    void packagesChanged(){
        supported.clear();List<Result> cancelled=new ArrayList<>();
        for(Entry item:entries.values()){
            if(item.timeout!=null)handler.removeCallbacks(item.timeout);
            item.timeout=null;item.nonce=null;cancelled.addAll(item.callbacks);item.callbacks.clear();
            clearEndpoint(item);item.ready=false;item.lost=true;
        }
        changed();for(Result callback:cancelled)callback.ready(false);
        if(hostVisible&&!sequenceActive)refreshSoon();
    }
}
