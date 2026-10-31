package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** A bounded foreground operation survives destruction of the HOME Activity. */
public final class StartupSequenceService extends Service {
    static final long MUSIC_DELAY=650,MAP_DELAY=2350;
    private static final long READY_TIMEOUT=20000;
    private static final long MAX_SEQUENCE_MS=45000;
    private static final String RECOVER="windchaser.startup.recover";
    static final String READY_CALLBACK="windchaser.startup.ready_callback",READY_SIGNAL="windchaser.startup.READY";
    static final String CALLBACK="windchaser.startup.callback",STEP="windchaser.startup.step";
    private static final String CHANNEL="startup_sequence";
    private static final long[] RETRIES={0,450,1000,1800,3000,4500,6000};
    private static StartupSequenceService running;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<Target> targets=new ArrayList<>();
    private final ArrayList<PendingIntent> readyTickets=new ArrayList<>();
    private String token;private int index,taskId=-1,page,attempts;
    private boolean returning,finished,registered;
    private Target waiting,checking;
    private int probeGeneration;private boolean launchedAny;
    private Runnable prepareReady;
    private void stopPreparing(){if(prepareReady!=null)handler.removeCallbacks(prepareReady);prepareReady=null;}
    private final Runnable startupTimeout=()->{if(waiting!=null){LauncherApp.record("关联应用未回报启动完成，超时继续："+waiting.pkg,null);waiting=null;stopPreparing();advance();}};
    private final Messenger completion=new Messenger(new Handler(Looper.getMainLooper()){
        @Override public void handleMessage(Message message){
            if(message.what!=1||finished||returning||waiting==null)return;
            Bundle data=message.getData();
            if(!token.equals(data.getString(StartupApps.TOKEN))||data.getInt(STEP)!=index||!waiting.pkg.equals(data.getString("package")))return;
            if(Build.VERSION.SDK_INT>=21&&message.sendingUid!=waiting.uid)return;
            acceptReady(token,index,waiting.pkg,"Messenger");
        }
    });
    private long returnStarted;
    private final BroadcastReceiver power=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){finishSequence("screen-off-or-shutdown");}};
    private static final class Target {final Intent intent;final long delay;final boolean reportsReady;final String pkg;final int uid;Target(Intent i,long d,boolean r,int u){intent=i;delay=d;reportsReady=r;pkg=i.getComponent().getPackageName();uid=u;}}
    static boolean active(){return running!=null&&!running.finished;}
    static void acknowledge(Intent intent){
        StartupSequenceService service=running;
        if(service!=null&&(service.returning||(service.waiting!=null&&service.index==service.targets.size()))&&intent!=null&&intent.getBooleanExtra(StartupApps.RETURN,false)
                &&service.token.equals(intent.getStringExtra(StartupApps.TOKEN)))service.finishSequence("home-visible");
    }
    static void userReturned(){if(active())running.finishSequence("user-returned");}
    static boolean hasPending(Context context){return context.getSharedPreferences("startup-return",0).getString("token",null)!=null;}
    static void acknowledge(Context context,Intent intent){
        acknowledge(intent);
        if(intent!=null&&intent.getBooleanExtra(StartupApps.RETURN,false))clearPending(context,intent.getStringExtra(StartupApps.TOKEN));
    }
    static void userReturned(Context context){userReturned();clearPending(context,context.getSharedPreferences("startup-return",0).getString("token",null));}
    private static void clearPending(Context context,String value){
        android.content.SharedPreferences prefs=context.getSharedPreferences("startup-return",0);
        if(value==null||!value.equals(prefs.getString("token",null)))return;
        prefs.edit().clear().commit();
        try{AlarmManager alarms=(AlarmManager)context.getSystemService(ALARM_SERVICE);if(alarms!=null)alarms.cancel(watchdog(context,value));}catch(RuntimeException ignored){}
    }
    @Override public void onCreate(){
        super.onCreate();running=this;
        IntentFilter filter=new IntentFilter(Intent.ACTION_SCREEN_OFF);filter.addAction(Intent.ACTION_SHUTDOWN);registerReceiver(power,filter);registered=true;
    }
    @Override public int onStartCommand(Intent request,int flags,int startId){
        if(request!=null&&READY_SIGNAL.equals(request.getAction()))return receiveReady(request);
        if(request!=null&&request.hasExtra(RECOVER))return recover(request);
        if(token!=null)return START_NOT_STICKY;
        token=UUID.randomUUID().toString();
        foreground();
        if(request==null||!new Config(this).b("autoRecoverApps",true)){finishSequence("disabled");return START_NOT_STICKY;}
        taskId=request.getIntExtra("task",-1);page=request.getIntExtra(StartupApps.PAGE,0);
        Config config=new Config(this);Set<String> selected=new HashSet<>();
        addTarget(config.pkg(false),MAP_DELAY,selected);addTarget(config.pkg(true),MUSIC_DELAY,selected);
        LinkedAppCoordinator.get(this).beginSequence();armWatchdog();advance();return START_NOT_STICKY;
    }
    private void foreground(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"关联应用启动",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        startForeground(59,builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle("追风桌面")
                .setContentText("正在启动关联应用，完成后自动返回桌面").setOngoing(true).build());
    }
    private android.content.SharedPreferences pending(){return getSharedPreferences("startup-return",0);}
    private static PendingIntent watchdog(Context context,String value){
        Intent intent=new Intent(context,StartupSequenceService.class).putExtra(RECOVER,value);
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        return Build.VERSION.SDK_INT>=26?PendingIntent.getForegroundService(context,93,intent,flags):PendingIntent.getService(context,93,intent,flags);
    }
    private void armWatchdog(){
        long due=SystemClock.elapsedRealtime()+MAX_SEQUENCE_MS;
        android.content.SharedPreferences.Editor saved=pending().edit().putString("token",token).putLong("due",due).putInt("page",page).putInt("task",taskId).putInt("targets",targets.size());
        for(int i=0;i<targets.size();i++)saved.putString("target"+i,targets.get(i).pkg).putLong("delay"+i,targets.get(i).delay);
        saved.commit();
        try{AlarmManager alarms=(AlarmManager)getSystemService(ALARM_SERVICE);if(alarms!=null){if(Build.VERSION.SDK_INT>=19)alarms.setExact(AlarmManager.ELAPSED_REALTIME,due,watchdog(this,token));else alarms.set(AlarmManager.ELAPSED_REALTIME,due,watchdog(this,token));}}
        catch(RuntimeException unavailable){LauncherApp.record("关联启动独立返回计时器不可用，保留服务内回退",unavailable);}
    }
    private void cancelWatchdog(){clearPending(this,token);}
    private int recover(Intent request){
        foreground();String expected=pending().getString("token",null);long due=pending().getLong("due",0),now=SystemClock.elapsedRealtime();
        if(expected==null||!expected.equals(request.getStringExtra(RECOVER))){if(token==null){stopForeground(true);stopSelf();}return START_NOT_STICKY;}
        if(token==null){token=expected;page=pending().getInt("page",0);}
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        if(now<due||now-due>15000||power==null||!power.isScreenOn()||!new Config(this).b("autoRecoverApps",true)){finishSequence("expired-or-screen-off-recovery");return START_NOT_STICKY;}
        LauncherApp.record("关联启动触发独立返回兜底，不重复启动关联应用",null);beginReturn();return START_NOT_STICKY;
    }
    private void addTarget(String pkg,long delay,Set<String> selected){
        if(pkg==null||pkg.equals(getPackageName())||!selected.add(pkg))return;
        Intent launch=getPackageManager().getLaunchIntentForPackage(pkg);
        if(launch!=null)try{targets.add(new Target(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION),delay,reportsReady(pkg),getPackageManager().getApplicationInfo(pkg,0).uid));}catch(android.content.pm.PackageManager.NameNotFoundException removed){}
    }
    private PendingIntent readyTicket(Target target){
        Intent signal=new Intent(this,StartupSequenceService.class).setAction(READY_SIGNAL)
                .setData(android.net.Uri.parse("windchaser-startup://ready/"+token+"/"+index))
                .putExtra(StartupApps.TOKEN,token).putExtra(STEP,index).putExtra("package",target.pkg);
        int flags=Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0;
        PendingIntent ticket=Build.VERSION.SDK_INT>=26?PendingIntent.getForegroundService(this,94,signal,flags):PendingIntent.getService(this,94,signal,flags);
        readyTickets.add(ticket);return ticket;
    }
    private int receiveReady(Intent request){
        if(token==null){
            foreground();android.content.SharedPreferences saved=pending();long remaining=saved.getLong("due",0)-SystemClock.elapsedRealtime();
            String expected=saved.getString("token",null),pkg=saved.getString("awaitingPackage",null);int expectedStep=saved.getInt("awaitingStep",0),count=saved.getInt("targets",0);
            if(expected==null||!expected.equals(request.getStringExtra(StartupApps.TOKEN))||pkg==null||!pkg.equals(request.getStringExtra("package"))
                    ||expectedStep!=request.getIntExtra(STEP,0)||expectedStep<=0||count<expectedStep||count>2||remaining<=0||remaining>MAX_SEQUENCE_MS){stopForeground(true);stopSelf();return START_NOT_STICKY;}
            token=expected;page=saved.getInt("page",0);taskId=saved.getInt("task",-1);Set<String> selected=new HashSet<>();
            for(int i=0;i<count;i++)addTarget(saved.getString("target"+i,null),saved.getLong("delay"+i,i==0?MAP_DELAY:MUSIC_DELAY),selected);
            if(targets.size()!=count){finishSequence("targets-changed-during-startup");return START_NOT_STICKY;}
            index=expectedStep;waiting=targets.get(index-1);launchedAny=true;LinkedAppCoordinator.get(this).beginSequence();
            if(!waiting.pkg.equals(pkg)){finishSequence("ready-target-mismatch");return START_NOT_STICKY;}
            LauncherApp.record("启动完成通知恢复返回流程："+pkg,null);
        }
        acceptReady(request.getStringExtra(StartupApps.TOKEN),request.getIntExtra(STEP,0),request.getStringExtra("package"),"PendingIntent");
        return START_NOT_STICKY;
    }
    private void acceptReady(String expected,int expectedStep,String pkg,String source){
        if(finished||returning||waiting==null||!token.equals(expected)||index!=expectedStep||!waiting.pkg.equals(pkg))return;
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        if(power==null||!power.isScreenOn()||foreignForeground()||!new Config(this).b("autoRecoverApps",true)){finishSequence("ready-screen-off-disabled-or-other-app");return;}
        LauncherApp.record("关联应用启动画面结束："+pkg+" source="+source,null);
        LinkedAppCoordinator.get(this).startupFinished(pkg);waiting=null;stopPreparing();handler.removeCallbacks(startupTimeout);advance();
    }
    private boolean reportsReady(String pkg){
        try{
            if(getPackageManager().checkSignatures(getPackageName(),pkg)!=android.content.pm.PackageManager.SIGNATURE_MATCH)return false;
            Context app=createPackageContext(pkg,0);
            try(java.io.InputStream in=app.getAssets().open("windchaser-startup-ready.json")){
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[128];int count;
                while((count=in.read(bytes))!=-1){if(out.size()+count>512)return false;out.write(bytes,0,count);}
                org.json.JSONObject marker=new org.json.JSONObject(out.toString("UTF-8"));
                return marker.optInt("protocol")==1&&pkg.equals(marker.optString("package"));
            }
        }catch(Exception unavailable){return false;}
    }
    private void advance(){
        if(finished||returning||checking!=null)return;
        if(!new Config(this).b("autoRecoverApps",true)){beginReturn();return;}
        if(index>=targets.size()){
            if(!launchedAny){finishSequence("interfaces-already-ready");return;}
            beginReturn();return;
        }
        final Target target=targets.get(index);checking=target;final int generation=++probeGeneration;
        LinkedAppCoordinator.get(this).probe(target.pkg,ready->{
            if(finished||returning||checking!=target||generation!=probeGeneration)return;
            checking=null;
            PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
            if(power==null||!power.isScreenOn()||foreignForeground()||!new Config(this).b("autoRecoverApps",true)){finishSequence("probe-screen-off-disabled-or-other-app");return;}
            if(ready){index++;LauncherApp.record("关联接口已就绪，直接复用："+target.pkg,null);advance();return;}
            launch(target);
        });
    }
    private void launch(Target target){
        index++;
        try{
            waiting=target;LinkedAppCoordinator.get(this).starting(target.pkg);
            Intent intent=automaticIntent(target.intent);
            if(target.reportsReady)intent.putExtra(READY_CALLBACK,readyTicket(target)).putExtra(CALLBACK,completion).putExtra(StartupApps.TOKEN,token).putExtra(STEP,index)
                    .putExtra("windchaser.startup.return_home",index==targets.size()).putExtra(StartupApps.PAGE,page);
            pending().edit().putInt("awaitingStep",index).putString("awaitingPackage",target.pkg).commit();
            startActivity(intent);launchedAny=true;
            LauncherApp.record("关联启动 "+target.intent.getComponent()+(target.reportsReady?" 等待启动页完成回执":" 旧安装包兼容等待="+target.delay),null);
            if(target.reportsReady){prepare(target,intent);handler.postDelayed(startupTimeout,READY_TIMEOUT);}
            else handler.postDelayed(()->{LinkedAppCoordinator.get(this).startupFinished(target.pkg);waiting=null;advance();},target.delay);
        }catch(RuntimeException error){waiting=null;LinkedAppCoordinator.get(this).startupFinished(target.pkg);LauncherApp.record("关联应用启动失败，继续返回桌面",error);advance();}
    }
    private void prepare(Target target,Intent launch){
        stopPreparing();
        Intent request=new Intent("cn.windchaser.startup.PREPARE").setPackage(target.pkg).addFlags(Intent.FLAG_RECEIVER_FOREGROUND).putExtras(launch);
        prepareReady=new Runnable(){int attempt;final long[] delays={120,400,1000,2200,4500,8000,12000};public void run(){
            if(finished||returning||waiting!=target)return;
            sendBroadcast(request);
            if(attempt<delays.length)handler.postDelayed(this,delays[attempt++]);
        }};
        handler.post(prepareReady);
    }
    // A short-lived marker is attached only here, never to ordinary app icons.
    static Intent automaticIntent(Intent original){return new Intent(original).putExtra("windchaser.startup.custom_splash",true).putExtra("windchaser.startup.splash_deadline",SystemClock.elapsedRealtime()+60000);}
    private void beginReturn(){if(finished||returning)return;waiting=null;stopPreparing();handler.removeCallbacks(startupTimeout);checking=null;probeGeneration++;returning=true;returnStarted=SystemClock.uptimeMillis();returnHome();}
    private void returnHome(){
        if(finished)return;
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        if(power==null||!power.isScreenOn()||foreignForeground()){finishSequence("screen-off-or-other-app");return;}
        attempts++;
        try{
            ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
            if(taskId>=0)try{manager.moveTaskToFront(taskId,ActivityManager.MOVE_TASK_NO_USER_ACTION);}catch(RuntimeException ignored){}
            startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)
                    .putExtra(StartupApps.RETURN,true).putExtra(StartupApps.TOKEN,token).putExtra(StartupApps.PAGE,page));
        }catch(RuntimeException error){LauncherApp.record("返回桌面尝试 "+attempts+" 受限",error);}
        if(attempts<RETRIES.length)handler.postAtTime(this::returnHome,returnStarted+RETRIES[attempts]);
        else handler.postDelayed(()->finishSequence("return-timeout"),700);
    }
    @SuppressWarnings("deprecation") private boolean foreignForeground(){
        if(Build.VERSION.SDK_INT>=21)return false; // Restricted task lists cannot prove a foreign app.
        try{ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);List<ActivityManager.RunningTaskInfo> tasks=manager.getRunningTasks(1);
            if(tasks==null||tasks.isEmpty()||tasks.get(0).topActivity==null)return false;
            String top=tasks.get(0).topActivity.getPackageName();Config cfg=new Config(this);
            return !top.equals(getPackageName())&&!top.equals(cfg.pkg(true))&&!top.equals(cfg.pkg(false));
        }catch(RuntimeException unavailable){return false;}
    }
    private void finishSequence(String reason){
        if(finished)return;finished=true;checking=null;probeGeneration++;handler.removeCallbacksAndMessages(null);
        LinkedAppCoordinator.get(this).endSequence();
        cancelWatchdog();for(PendingIntent ticket:readyTickets)ticket.cancel();readyTickets.clear();
        LauncherApp.record("关联启动结束 reason="+reason+" returnAttempts="+attempts,null);
        stopForeground(true);stopSelf();
    }
    @Override public void onTaskRemoved(Intent root){LauncherApp.record("桌面任务被移除，保留本次关联启动的有限返回流程",null);super.onTaskRemoved(root);}
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);if(running==this){running=null;if(!finished)LinkedAppCoordinator.get(this).endSequence();}if(registered)unregisterReceiver(power);stopForeground(true);super.onDestroy();}
}
