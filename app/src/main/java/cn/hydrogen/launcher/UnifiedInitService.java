package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import com.morgoo.droidplugin.unified.StartupWindowState;

/** One bounded initialization owner. Desktop admission requires both live interfaces. */
public final class UnifiedInitService extends Service {
    private static final long LIMIT_MS=45000;
    private static UnifiedInitService owner;
    private static boolean admitted;
    private static String status="";
    private static String pendingVisual;
    private static volatile String gateSession;
    static Bundle startupWindowState(){
        Bundle state=new Bundle();state.putString("session",gateSession);state.putBoolean("allowed",StartupWindowState.allowed());
        UnifiedInitService active=owner;long remaining=active==null?1:Math.max(1,(active.returning?LIMIT_MS:300000)-active.activeElapsed);
        state.putLong("remaining_ms",remaining);return state;
    }
    private String visualSession;private boolean returning,awaitingMapSetup,mapSetupOffered;private long lastMapSetupUi;
    static boolean firstMapPending(){return owner!=null&&owner.awaitingMapSetup&&!owner.finished;}
    static boolean mapSetupFor(String value){return firstMapPending()&&owner.visualSession.equals(value);}
    static void completeFirstMap(){
        if(owner==null||owner.finished||!owner.awaitingMapSetup)return;
        // Called only by the setup dialog after a fresh native probe and an
        // established card connection. This is not desktop admission.
        owner.getSharedPreferences(INIT_PREFS,0).edit().putBoolean(Config.MAP,true).apply();
        owner.awaitingMapSetup=false;owner.handler.post(owner::checkStep);
    }
    static void cancelFirstMap(){if(owner!=null)owner.cancel("首次设置已取消，请重新进入追风桌面继续");}
    private final BroadcastReceiver recovery=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(finished||visualSession==null||!visualSession.equals(i.getStringExtra("windchaser.init.session")))return;
        String pkg=i.getStringExtra("package");if(!Config.MAP.equals(pkg)&&!Config.MUSIC.equals(pkg))return;
        coordinator.probeInitializing(pkg,ready->{if(!finished&&windowAllowed()&&StartupWindowState.foreground(pkg))fail("准备已暂停，请重试或检查应用设置；不会跳过接口检查。");});
    }};
    private final BroadcastReceiver firstSetup=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(!"cn.windchaser.startup.FIRST_MAP_SETUP".equals(i.getAction())||finished||!awaitingMapSetup||!visualSession.equals(i.getStringExtra("windchaser.init.session")))return;
        coordinator.probeInitializing(Config.MAP,ready->{
            if(!finished&&windowAllowed()&&awaitingMapSetup&&StartupWindowState.foreground(Config.MAP))offerMapSetup();
        });
    }};
    private final BroadcastReceiver probeAvailable=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(finished)return;String pkg=i.getStringExtra("package");
        if(Config.MAP.equals(pkg)||Config.MUSIC.equals(pkg))coordinator.probeAvailableInitializing(pkg);
    }};
    private static final String INIT_PREFS="unified-native-initialization";
    static void desktopVisible(final android.app.Activity a){
        final String expected=pendingVisual;
        if(expected==null||!a.hasWindowFocus()||!expected.equals(a.getIntent().getStringExtra("windchaser.init.session")))return;
        a.getWindow().getDecorView().post(()->{
            UnifiedInitService active=owner;
            if(active!=null&&!a.isFinishing()&&a.hasWindowFocus()&&expected.equals(pendingVisual)){active.desktopActivity=new java.lang.ref.WeakReference<>(a);active.acceptDesktop(expected);}
        });
    }
    private void acceptDesktop(String expected){
        if(finished||!awaitingDesktop||!expected.equals(visualSession)||!windowAllowed())return;
        if(!coordinator.confirmed(Config.MAP)||!coordinator.confirmed(Config.MUSIC)){
            handler.post(this::verifyAll);return;
        }
        pendingVisual=null;awaitingDesktop=false;
        getSharedPreferences(INIT_PREFS,0).edit().putBoolean(Config.MAP,true).putBoolean(Config.MUSIC,true).apply();
        status="地图和音乐初始化完成";
        LauncherApp.record("进入桌面前初始化：桌面已取得焦点，完成返回确认，耗时="+(SystemClock.elapsedRealtime()-started)+"ms",null);
        close();endVisual(this,expected);UnifiedBootstrapActivity.desktopAccepted();
    }
    private static void endVisual(Context c,String value){
        if(value==null)return;
        for(String pkg:new String[]{Config.MAP,Config.MUSIC})c.sendBroadcast(new Intent("cn.windchaser.startup.END_INIT_VISUAL").setPackage(pkg)
            .putExtra("windchaser.init.session",value),"cn.windchaser.unified.permission.STARTUP_READY");
    }
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final String[] packages={Config.MAP,Config.MUSIC};
    private final boolean[] launched={false,false};
    private int step,generation,focusRetries;
    private boolean finished,registered,verifying,awaitingDesktop;
    private long nextDesktopAttemptAt,lastDesktopAttemptAt;private int desktopAttempts;
    private java.lang.ref.WeakReference<android.app.Activity> desktopActivity;
    private long started,activeElapsed,lastBudgetAt,windowEpoch=-1;
    private boolean startupPaused,backgroundChecking,musicWarmScheduled;
    private int backgroundRound;
    private long nextBackgroundAt;
    private final boolean[] backgroundReady={false,false};
    private final Runnable windowChanged=()->{if(started!=0&&!finished){checkStep();}};
    private final Runnable budgetTick=()->{if(finished)return;boolean wasPaused=startupPaused;boolean allowed=windowAllowed();if(allowed&&activeElapsed>=(returning?LIMIT_MS:300000)){fail(timeoutMessage());return;}if(allowed&&(wasPaused||awaitingDesktop&&SystemClock.elapsedRealtime()>=nextDesktopAttemptAt))handler.post(this::checkStep);if(!allowed)continueInBackground();handler.postDelayed(this.budgetTick,250);};
    private boolean windowAllowed(){
        long now=SystemClock.elapsedRealtime();boolean allowed=StartupWindowState.allowed();
        if(lastBudgetAt!=0&&!startupPaused)activeElapsed+=Math.max(0,now-lastBudgetAt);
        lastBudgetAt=now;
        long epoch=StartupWindowState.epoch();
        if(windowEpoch!=epoch){
            boolean changed=windowEpoch!=-1;windowEpoch=epoch;
            if(changed)invalidateWindowProbes();
        }
        if(startupPaused!=!allowed){
            startupPaused=!allowed;
            // A background reply may describe a real ready engine, but it can
            // never prove foreground ownership. Resume always requests new proofs.
            if(allowed)invalidateWindowProbes();
            LauncherApp.record(allowed?"启动恢复：重新检查地图和音乐接口":"启动让行：保留后台加载，仅暂停界面切换与超时计时",null);
        }
        if(!allowed)status="后台继续准备地图和音乐，等待返回追风启动界面…";
        return allowed;
    }
    private void invalidateWindowProbes(){
        generation++;verifying=false;musicLaunchChecking=false;focusRetries=1;nextDesktopAttemptAt=0;
        backgroundChecking=false;backgroundRound=0;nextBackgroundAt=0;
        backgroundReady[0]=backgroundReady[1]=false;
        coordinator.interruptInitializing();
    }
    private void continueInBackground(){
        // No Activity, overlay, setup dialog or desktop launch is allowed here.
        // Existing native workers continue by themselves. These authenticated
        // probes only observe their readiness, with bounded retries and backoff.
        if(finished||!startupPaused||StartupWindowState.allowed()||backgroundChecking
            ||SystemClock.elapsedRealtime()<nextBackgroundAt)return;
        if(backgroundReady[0]&&backgroundReady[1]
            &&coordinator.liveEndpoint(Config.MAP)&&coordinator.liveEndpoint(Config.MUSIC))return;
        backgroundChecking=true;final int ticket=generation;final int[] remaining={2};
        for(int index=0;index<packages.length;index++){
            final int current=index;final String pkg=packages[index];
            LinkedAppCoordinator.Result result=ready->{
                if(finished||ticket!=generation||!startupPaused||StartupWindowState.allowed())return;
                boolean usable=ready&&coordinator.liveEndpoint(pkg);
                if(usable&&!backgroundReady[current])LauncherApp.record("后台准备："+(current==0?"高德地图":"QQ音乐")+"真实接口已就绪，保持外部界面前台",null);
                backgroundReady[current]=usable;
                if(!usable&&current==1)requestMusicBackend();
                if(--remaining[0]==0){
                    backgroundChecking=false;
                    int[] backoff={1000,2000,4000,8000};
                    nextBackgroundAt=SystemClock.elapsedRealtime()+backoff[Math.min(backgroundRound++,backoff.length-1)];
                }
            };
            if(backgroundReady[index]&&coordinator.liveEndpoint(pkg))result.ready(true);
            else coordinator.probeInitializing(pkg,result);
        }
    }
    private void requestMusicBackend(){
        // A service-only path already audited for later starts. First use and
        // user-disabled automatic startup never acquire permissions or show UI.
        if(finished||!returning||awaitingMapSetup||musicWarmRequested||!config.b("autoRecoverApps",true))return;
        musicWarmRequested=true;
        if(CompanionService.prepareMusicDuringStartup(this,config))LauncherApp.record("后台准备：请求QQ音乐原控制服务一次，不打开应用界面",null);
    }
    private LinkedAppCoordinator coordinator;private Config config;private boolean musicWarmRequested,musicLaunchChecking,musicReadyDuringMap;
    private final BroadcastReceiver power=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){cancel("初始化已暂停，请重新进入准备页重试");}};
    static boolean admitted(){return admitted;}
    static boolean canResumeDesktop(Context context){
        if(!admitted||active()||!UnifiedRuntime.ready())return false;
        LinkedAppCoordinator live=LinkedAppCoordinator.get(context);
        return live.restorable(Config.MAP)&&live.restorable(Config.MUSIC);
    }
    static void newEntry(){if(!active())admitted=false;}
    static boolean active(){return owner!=null&&!owner.finished;}
    static String status(){return status;}
    static boolean required(Context c){return !admitted;}
    static boolean recoverInterrupted(Context c){
        android.content.SharedPreferences p=c.getSharedPreferences("unified-startup-session",0);
        if(!p.getBoolean("active",false)||p.getInt("pid",-1)==android.os.Process.myPid())return false;
        endVisual(c,p.getString("session",null));p.edit().putBoolean("active",false).apply();admitted=false;
        status="上次初始化过程中断，请重试。地图和音乐均就绪后才会进入桌面。";return true;
    }
    static void recoveryRequested(){if(owner!=null)owner.cancel("准备已暂停，请重试或检查应用设置；不会跳过接口检查。");else{admitted=false;status="请选择重试或检查应用设置；地图和音乐均就绪后才会进入桌面。";}}

    static void request(Context c){
        if(active())return;
        status="正在准备高德地图与 QQ音乐…";
        Intent request=new Intent(c,UnifiedInitService.class);
        if(Build.VERSION.SDK_INT>=26)c.startForegroundService(request);else c.startService(request);
    }
    @Override public void onCreate(){
        super.onCreate();owner=this;config=new Config(this);coordinator=LinkedAppCoordinator.get(this);
        IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SHUTDOWN);
        registerReceiver(power,f);registerReceiver(firstSetup,new IntentFilter("cn.windchaser.startup.FIRST_MAP_SETUP"),"cn.windchaser.unified.permission.STARTUP_READY",null);registerReceiver(recovery,new IntentFilter("cn.windchaser.startup.RECOVER"),"cn.windchaser.unified.permission.STARTUP_READY",null);registerReceiver(probeAvailable,new IntentFilter(LinkedAppCoordinator.PROBE_AVAILABLE),"cn.windchaser.unified.permission.STARTUP_READY",null);registered=true;
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        foreground();
        if(started!=0)return START_NOT_STICKY;
        started=SystemClock.elapsedRealtime();admitted=false;coordinator.beginSequence();
        visualSession=java.util.UUID.randomUUID().toString();gateSession=visualSession;
        getSharedPreferences("unified-startup-session",0).edit().putBoolean("active",true).putInt("pid",android.os.Process.myPid()).putString("session",visualSession).apply();
        returning=getSharedPreferences(INIT_PREFS,0).getBoolean(Config.MAP,false)&&getSharedPreferences(INIT_PREFS,0).getBoolean(Config.MUSIC,false);
        awaitingMapSetup=!getSharedPreferences(INIT_PREFS,0).getBoolean(Config.MAP,false);
        lastBudgetAt=started;StartupWindowState.listen(windowChanged);handler.post(budgetTick);
        checkStep();return START_NOT_STICKY;
    }
    private String timeoutMessage(){
        if(awaitingDesktop)return "返回桌面尚未完成，请返回追风桌面重试；不会跳过地图和音乐接口检查。";
        if(awaitingMapSetup)return "高德首次设置尚未完成，请返回设置弹窗确认必要选项后重试。";
        String module=step==0?"高德地图引擎与卡片接口":step==1?"QQ音乐首页与播放控制接口":"地图或音乐连接复核";
        return module+"尚未就绪，请检查网络、权限或原应用设置后重试。已就绪的模块会重新确认后复用。";
    }
    private void foreground(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("unified_init","内置应用初始化",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"unified_init"):new Notification.Builder(this);
        Intent recover=new Intent(this,UnifiedBootstrapActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("recovery_requested",true);
        int piFlags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=23)piFlags|=PendingIntent.FLAG_IMMUTABLE;
        builder.setContentIntent(PendingIntent.getActivity(this,61,recover,piFlags));
        startForeground(61,builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle("追风桌面")
            .setContentText("正在初始化地图和音乐，准备好后进入桌面").setOngoing(true).build());
    }
    private void progress(String value){status=value;LauncherApp.record("进入桌面前初始化："+value,null);}
    private void checkStep(){
        if(finished)return;
        if(!windowAllowed())return;
        if(awaitingDesktop&&SystemClock.elapsedRealtime()<nextDesktopAttemptAt)return;
        if(step>=packages.length){verifyAll();return;}
        final int current=step,ticket=++generation;final String pkg=packages[current];
        final long requestedAt=SystemClock.elapsedRealtime();
        status=current==0?"正在初始化高德地图，等待地图引擎与卡片接口…":"正在初始化 QQ音乐，等待首页与播放控制接口…";
        LinkedAppCoordinator.Result result=ready->{
            if(finished||!windowAllowed()||generation!=ticket||current!=step)return;
            if(current==0&&awaitingMapSetup&&ready&&!launched[current]){launchStep(current);handler.postDelayed(this::checkStep,400);return;}
            if(current==0&&awaitingMapSetup&&launched[current]){
                status="请在启动弹窗中完成高德首次设置";
                if(coordinator.firstSetupWindow(pkg))offerMapSetup();
                handler.postDelayed(this::checkStep,400);return;
            }
            if(ready){progress((current==0?"高德地图":"QQ音乐")+"接口已就绪"+(launched[current]?"":"，复用已运行应用"));step++;checkStep();return;}
            if(!launched[current]){
                if(!config.b("autoRecoverApps",true)){fail("自动启动已关闭，且地图或音乐接口尚未就绪。请先完成应用设置，再重试。");return;}
                if(current>0){
                    // Reuse only a real proof issued within the last 100ms and
                    // matching the owned foreground. Otherwise refresh before
                    // advancing, including delayed permission/external windows.
                    LinkedAppCoordinator.Result advance=again->{
                        if(finished||!windowAllowed()||generation!=ticket||step!=current)return;
                        if(UnifiedBootstrapActivity.ownsInitializationWindow()||StartupWindowState.foreground(packages[current-1])){
                            launchStep(current);
                            // The native waiter already owns a bounded timeout;
                            // do not add a second fixed delay after launch.
                            if(!finished)handler.post(this::checkStep);
                        }else{
                            status="等待返回追风启动界面…";
                            if(!finished)handler.postDelayed(this::checkStep,300);
                        }
                    };
                    if(coordinator.confirmedRecently(packages[current-1],100)
                        &&(UnifiedBootstrapActivity.ownsInitializationWindow()||StartupWindowState.foreground(packages[current-1])))advance.ready(true);
                    else coordinator.probeInitializing(packages[current-1],advance);
                    return;
                }
                if(!UnifiedBootstrapActivity.ownsInitializationWindow()){
                    status="等待返回追风启动界面…";handler.postDelayed(this::checkStep,400);return;
                }
                launchStep(current);
            }
            if(current==0&&launched[0]){
                prepareMusicWindowDuringMap();
                if(launched[1])coordinator.awaitInitializing(Config.MUSIC,prepared->{});
            }
            // Native startup waits return immediately when ready. A negative
            // reply needs only a minimum request spacing, not another fixed nap.
            handler.postDelayed(this::checkStep,Math.max(0,250-(SystemClock.elapsedRealtime()-requestedAt)));
        };
        // Map readiness is a quick native flag/surface read; keep its loading
        // checks light. Music may need a slower real player IPC to finish.
        if(current==0&&!launched[0]&&UnifiedRuntime.definitelyNotRunning(pkg)){
            LauncherApp.record("启动加速：运行环境确认高德尚未创建进程，直接开始初始化",null);result.ready(false);
        }
        else if(launched[current]&&current!=0)coordinator.awaitInitializing(pkg,result);
        else coordinator.probeInitializing(pkg,result);
    }
    private void offerMapSetup(){
        if(finished||!windowAllowed()||!awaitingMapSetup||UnifiedBootstrapActivity.ownsInitializationWindow())return;
        long now=SystemClock.elapsedRealtime();if(mapSetupOffered&&now-lastMapSetupUi<1500)return;
        mapSetupOffered=true;lastMapSetupUi=now;
        // A native permission window can resume the map after its dialog was
        // offered. Present the existing host dialog again only after a fresh
        // map-window foreground proof, never while another app has focus.
        LauncherApp.record("首次地图设置：真实窗口回执通过，恢复启动弹窗",null);
        try{startActivity(new Intent(this,UnifiedBootstrapActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra("first_map_setup",visualSession));}
        catch(RuntimeException error){LauncherApp.record("打开首次地图设置弹窗失败",error);fail("无法显示地图首次设置，请返回准备页重试。");}
    }
    private void launchStep(int current){
        if(finished||!windowAllowed())return;
        String pkg=packages[current];launched[current]=true;coordinator.starting(pkg);
        if(!UnifiedRuntime.launch(this,pkg,visualSession,SystemClock.elapsedRealtime()+Math.max(1,(returning?LIMIT_MS:300000)-activeElapsed))){
            fail((current==0?"高德地图":"QQ音乐")+"启动失败，请重试。");return;
        }
        progress("已启动"+(current==0?"高德地图":"QQ音乐")+"，等待真实接口就绪");
        if(current==0)prepareMusicDuringMap();
    }
    private void prepareMusicDuringMap(){
        // First-use protocols/permissions retain the existing sequential UI.
        // Subsequent starts can prepare only QQ's public backend while the map
        // engine is loading. This is not a readiness proof or an Activity launch.
        if(finished||!returning||awaitingMapSetup||musicWarmRequested||musicWarmScheduled||!config.b("autoRecoverApps",true))return;
        musicWarmScheduled=true;
        coordinator.probeInitializing(Config.MUSIC,ready->{
            musicWarmScheduled=false;
            if(finished||!windowAllowed()||ready||step!=0||!launched[0]||launched[1]||!config.b("autoRecoverApps",true))return;
            requestMusicBackend();
        });
    }
    private void prepareMusicWindowDuringMap(){
        // On later starts, native map initialization can continue under the
        // shared visual while QQ measures its home and connects its player.
        // First-use setup and unowned/external windows remain sequential.
        // Runtime permission requests can arrive after map readiness on API23+.
        // Keep their native Activities sequential to prevent a delayed permission
        // handoff from bringing the map task back over the admitted desktop.
        if(Build.VERSION.SDK_INT>=23||finished||!returning||awaitingMapSetup||musicLaunchChecking||musicReadyDuringMap
            ||step!=0||!launched[0]||launched[1]||!config.b("autoRecoverApps",true)
            ||!StartupWindowState.foreground(Config.MAP))return;
        musicLaunchChecking=true;
        coordinator.probeInitializing(Config.MUSIC,ready->{
            musicLaunchChecking=false;
            if(finished||!windowAllowed()||step!=0||launched[1])return;
            if(ready){musicReadyDuringMap=true;return;}
            // A negative readiness reply alone never owns the foreground.
            if(!config.b("autoRecoverApps",true)||!StartupWindowState.foreground(Config.MAP))return;
            launchStep(1);
            LauncherApp.record("启动加速：高德窗口已确认，地图继续加载，同时准备QQ首页与控制接口",null);
        });
    }
    private void verifyAll(){
        if(finished||!windowAllowed()||verifying)return;
        verifying=true;final int ticket=++generation;
        final int[] remaining={packages.length};
        for(String pkg:packages){
            LinkedAppCoordinator.Result checked=ready->{
                if(finished||!windowAllowed()||ticket!=generation)return;
                // Post once so synchronous/reentrant test transports and cached
                // replies cannot enter the desktop before both requests start.
                if(--remaining[0]==0)handler.post(()->finishVerification(ticket));
            };
            if(focusRetries==0&&coordinator.confirmedRecently(pkg,250))checked.ready(true);
            else coordinator.awaitInitializing(pkg,checked);
        }
    }
    private void finishVerification(int ticket){
        if(finished||!windowAllowed()||ticket!=generation)return;
        verifying=false;
        // Both authenticated proofs must still be fresh and both Binder
        // endpoints alive. Parallel checks never relax the admission rule.
        if(!coordinator.confirmed(Config.MAP)||!coordinator.confirmed(Config.MUSIC)){
            status="正在再次确认地图与音乐连接…";handler.postDelayed(this::verifyAll,300);return;
        }
        android.app.Activity visibleDesktop=desktopActivity==null?null:desktopActivity.get();
        if(awaitingDesktop&&visibleDesktop!=null&&!visibleDesktop.isFinishing()&&visibleDesktop.hasWindowFocus()
            &&visualSession.equals(visibleDesktop.getIntent().getStringExtra("windchaser.init.session"))){acceptDesktop(visualSession);return;}
        if(!UnifiedBootstrapActivity.ownsInitializationWindow()
            &&!StartupWindowState.foreground(Config.MAP)&&!StartupWindowState.foreground(Config.MUSIC)
            &&!StartupWindowState.foreground(getPackageName())){
            status="地图和音乐已就绪，等待返回初始化界面…";
            // The native home can become ready just before its first focus
            // callback. Recheck fresh proofs promptly once, then back off when
            // an external app still owns the screen.
            int[] focusBackoff={80,100,150,500};
            handler.postDelayed(this::verifyAll,focusBackoff[Math.min(focusRetries++,focusBackoff.length-1)]);return;
        }
        enterDesktop();
    }
    private void enterDesktop(){
        if(finished||!windowAllowed())return;
        long now=SystemClock.elapsedRealtime();
        if(lastDesktopAttemptAt!=0&&now-lastDesktopAttemptAt<500){nextDesktopAttemptAt=lastDesktopAttemptAt+500;return;}
        try{
            // Do not start the old post-desktop warm-up sequence a second time.
            admitted=true;awaitingDesktop=true;StartupApps.initialized();pendingVisual=visualSession;
            lastDesktopAttemptAt=now;nextDesktopAttemptAt=now+(desktopAttempts++==0?750:1500);
            // Carry this session through the actual desktop visibility acknowledgment.
            Intent home=new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra(StartupApps.RETURN,true).putExtra(StartupApps.PAGE,config.n("homePage",0)).putExtra("windchaser.init.session",visualSession);
            startActivity(home);status="正在返回追风桌面…";
            // startActivity can succeed while a camera transition prevents the
            // requested window from appearing. Keep the owner and its bounded
            // retry alive until this session's desktop really gains focus.
            LauncherApp.record("进入桌面前初始化：两接口复核通过，请求返回桌面，次数="+desktopAttempts,null);
        }catch(RuntimeException error){admitted=false;LauncherApp.record("初始化完成后打开桌面失败",error);fail("打开桌面受限，请返回准备页重试。");}
    }
    private void fail(String reason){
        if(finished)return;status=reason;admitted=false;endVisual(this,visualSession);
        boolean visible=StartupWindowState.allowed()&&(UnifiedBootstrapActivity.ownsInitializationWindow()||StartupWindowState.foreground(Config.MAP)||StartupWindowState.foreground(Config.MUSIC)||StartupWindowState.foreground(getPackageName()));
        LauncherApp.record("进入桌面前初始化未完成："+reason,null);close();
        if(visible)try{startActivity(new Intent(this,UnifiedBootstrapActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra("diagnostics",true).putExtra("initialization_failed",true));}catch(RuntimeException error){LauncherApp.record("返回初始化提示页受限",error);}
    }
    private void cancel(String message){if(finished)return;endVisual(this,visualSession);admitted=false;status=message;close();}
    private void close(){
        if(finished)return;finished=true;gateSession=null;desktopActivity=null;if(visualSession!=null&&visualSession.equals(pendingVisual))pendingVisual=null;getSharedPreferences("unified-startup-session",0).edit().putBoolean("active",false).apply();generation++;StartupWindowState.listen(null);handler.removeCallbacksAndMessages(null);
        coordinator.endSequence();stopForeground(true);stopSelf();
    }
    @Override public void onTaskRemoved(Intent root){cancel("初始化已取消，请重新进入准备页重试");super.onTaskRemoved(root);}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onDestroy(){
        StartupWindowState.listen(null);handler.removeCallbacksAndMessages(null);
        if(!finished){getSharedPreferences("unified-startup-session",0).edit().putBoolean("active",false).apply();endVisual(this,visualSession);status="初始化已中断，请重试";admitted=false;coordinator.endSequence();}
        if(owner==this){gateSession=null;owner=null;}if(registered){unregisterReceiver(power);unregisterReceiver(firstSetup);unregisterReceiver(recovery);unregisterReceiver(probeAvailable);}stopForeground(true);super.onDestroy();
    }
}
