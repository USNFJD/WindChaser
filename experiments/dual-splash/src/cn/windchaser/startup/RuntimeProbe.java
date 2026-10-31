package cn.windchaser.startup;

import android.content.*;
import android.os.*;
import org.json.*;

/** Read-only live checks in an already running companion. No manifest receiver or Activity launch. */
public final class RuntimeProbe {
    public static final String ACTION="cn.windchaser.startup.PROBE";
    private static final String EVENTS="com.tencent.qqmusiccar.action.PLAY_COMMAND_SEND_FOR_THIRD";
    private static final String EXTRA="com.tencent.qqmusiccar.EXTRA_COMMAND_DATA";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final Messenger ENDPOINT=new Messenger(new Handler(Looper.getMainLooper()));
    private static Context app;private static final java.util.ArrayList<Ticket> pending=new java.util.ArrayList<>();
    private static final Runnable drive=RuntimeProbe::checkPending;
    private static boolean controlRunning,subscriptionSent;
    private static final java.util.concurrent.Executor CONTROL=new java.util.concurrent.ThreadPoolExecutor(0,1,15,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.SynchronousQueue<Runnable>(),r->{Thread t=new Thread(r,"QQ-live-control-probe");t.setDaemon(true);return t;});
    private static java.lang.reflect.Method mapReady;private static Boolean lastReady;
    private static synchronized java.lang.reflect.Method mapMethod()throws Exception{
        if(mapReady==null)mapReady=Class.forName("cn.windchaser.mapbridge.MapCardBridge").getMethod("interfaceReady");return mapReady;
    }
    private static final class Ticket {
        final String nonce;final long issued,deadline;final Messenger client;
        Ticket(String n,long t,Messenger c,long until){nonce=n;issued=t;client=c;deadline=until;}
    }
    private RuntimeProbe(){}
    public static void install(Context context){
        if(app!=null||!mainProcess(context))return;app=context.getApplicationContext();
        app.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent request){
            if(!ACTION.equals(request.getAction())||request.getIntExtra("protocol",0)!=1)return;
            String nonce=request.getStringExtra("nonce");long issued=request.getLongExtra("issued",0),age=SystemClock.elapsedRealtime()-issued;
            Messenger client=request.getParcelableExtra("client");
            if(nonce==null||nonce.length()>128||age<0||age>1500||client==null||!client.getBinder().isBinderAlive())return;
            int wait=Math.max(0,Math.min(900,request.getIntExtra("wait_ms",0)));
            Ticket ticket=new Ticket(nonce,issued,client,wait>0?issued+wait:SystemClock.elapsedRealtime()+350);
            boolean isMap="com.autonavi.amapauto".equals(app.getPackageName());
            if(isMap&&wait==0){reply(ticket,mapInterfaceReady());return;}
            if(!isMap&&(!"com.tencent.qqmusiccar".equals(app.getPackageName())||wait==0&&!QqHome.contentReady())){reply(ticket,false);return;}
            for(Ticket old:pending)if(old.nonce.equals(nonce)&&old.issued==issued&&old.client.getBinder().equals(client.getBinder()))return;
            if(pending.size()>=64){reply(ticket,false);return;}
            pending.add(ticket);checkPending();
        }},new IntentFilter(ACTION),DualSplash.PERMISSION,null);
        if("com.tencent.qqmusiccar".equals(app.getPackageName()))app.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent event){
            if(pending.isEmpty()||!EVENTS.equals(event.getAction()))return;
            try{
                String raw=event.getStringExtra(EXTRA);if(raw==null||raw.length()>512*1024)return;
                JSONObject response=new JSONObject(raw);if(!"play".equals(response.optString("module")))return;
                JSONObject command=response.optJSONObject("command");if(command==null)return;
                String method=command.optString("method");JSONObject data=command.optJSONObject("data");
                if(data==null||!("update_state".equals(method)&&data.has("state")||"update_song".equals(method)&&data.has("key_title")))return;
                if(QqHome.contentReady()&&QqControlReadiness.available(app.getClassLoader()))completeReady();
            }catch(JSONException malformed){}
        }},new IntentFilter(EVENTS));
        // A bounded initialization query can precede this dynamic receiver.
        // This signed hint only asks the host to re-probe; it never says ready.
        try{app.sendBroadcast(new Intent("cn.windchaser.startup.PROBE_AVAILABLE")
            .setPackage("cn.windchaser.unified").putExtra("package",app.getPackageName()),DualSplash.PERMISSION);}
        catch(RuntimeException unavailable){}
    }
    private static boolean mapInterfaceReady(){
        try{return Boolean.TRUE.equals(mapMethod().invoke(null));}
        catch(Exception|LinkageError unavailable){return false;}
    }
    private static void checkPending(){
        MAIN.removeCallbacks(drive);
        long now=SystemClock.elapsedRealtime();
        // Expiry belongs to each nonce; a new request cannot extend an older
        // request or accept a late result. No initialization is performed here.
        for(int i=pending.size()-1;i>=0;i--){Ticket ticket=pending.get(i);
            if(now>=ticket.deadline||!ticket.client.getBinder().isBinderAlive()){
                pending.remove(i);reply(ticket,false);
            }
        }
        if(pending.isEmpty()){subscriptionSent=false;return;}
        if("com.autonavi.amapauto".equals(app.getPackageName())){
            if(mapInterfaceReady()){completeReady();return;}
        }else if(QqHome.contentReady()){
            // Share a single in-flight read-only player IPC, including across
            // host retry requests. Slow hardware never queues more IPC workers.
            if(!controlRunning){
                controlRunning=true;
                try{CONTROL.execute(()->{
                    boolean ready=QqControlReadiness.ready(app.getClassLoader());
                    MAIN.post(()->{controlRunning=false;
                        if(ready&&QqHome.contentReady())completeReady();else scheduleCheck();
                    });
                });}catch(java.util.concurrent.RejectedExecutionException busy){controlRunning=false;}
            }
            if(!subscriptionSent){
                subscriptionSent=true;
                try{app.sendBroadcast(new Intent("com.tencent.qqmusiccar.action")
                    .setComponent(new ComponentName(app.getPackageName(),"com.tencent.qqmusiccar.app.reciver.BroadcastReceiverCenterForThird"))
                    .putExtra("action",100).addFlags(Intent.FLAG_RECEIVER_FOREGROUND));}
                catch(RuntimeException unavailable){} // The independent read-only IPC can still prove readiness.
            }
        }
        scheduleCheck();
    }
    private static void scheduleCheck(){
        MAIN.removeCallbacks(drive);
        if(pending.isEmpty()){subscriptionSent=false;return;}
        long delay=150,now=SystemClock.elapsedRealtime();
        for(Ticket ticket:pending)delay=Math.min(delay,Math.max(0,ticket.deadline-now));
        MAIN.postDelayed(drive,delay);
    }
    private static void completeReady(){
        MAIN.removeCallbacks(drive);subscriptionSent=false;
        java.util.ArrayList<Ticket> completed=new java.util.ArrayList<>(pending);pending.clear();
        long now=SystemClock.elapsedRealtime();
        for(Ticket ticket:completed)reply(ticket,now<ticket.deadline);
    }
    public static boolean mainProcess(Context context){
        try(java.io.InputStream in=new java.io.FileInputStream("/proc/self/cmdline")){
            byte[] bytes=new byte[256];int count=in.read(bytes),end=0;
            while(end<count&&bytes[end]!=0)end++;
            if(end>0)return context.getPackageName().equals(new String(bytes,0,end,"UTF-8"));
        }catch(java.io.IOException unavailable){}
        try{
            android.app.ActivityManager manager=(android.app.ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<android.app.ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
            if(processes!=null)for(android.app.ActivityManager.RunningAppProcessInfo process:processes)
                if(process.pid==android.os.Process.myPid())return context.getPackageName().equals(process.processName);
        }catch(RuntimeException unavailable){}
        return false;
    }
    private static void reply(Ticket ticket,boolean ready){
        if(lastReady==null||lastReady!=ready){lastReady=ready;android.util.Log.i("WindChaserProbe",app.getPackageName()+" live interface ready="+ready);}
        try{Bundle data=new Bundle();data.putInt("protocol",1);data.putString("package",app.getPackageName());data.putString("nonce",ticket.nonce);data.putLong("issued",ticket.issued);data.putBoolean("ready",ready);data.putBoolean("foreground",DualSplash.interfaceForeground());data.putBoolean("first_setup",StartupCurtain.needsMapSetup()&&DualSplash.contentInitialized());
            Message message=Message.obtain(null,6);message.setData(data);message.replyTo=ENDPOINT;ticket.client.send(message);
        }catch(RemoteException|RuntimeException disconnected){}
    }
}
