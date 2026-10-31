package cn.hydrogen.launcher;

import android.app.*;import android.content.*;import android.os.*;import android.view.*;
import cn.windchaser.startup.RuntimeProbe;import cn.windchaser.mapbridge.MapInterfaceReadiness;
import com.autonavi.amapauto.jni.protocol.AndroidProtocolExe;
import fixture.Env;import java.util.*;

public final class CoordinatorChecks {
 static final String QQ="com.tencent.qqmusiccar",MAP="com.autonavi.amapauto";
 static Context context;static LinkedAppCoordinator coordinator;static List<Intent> probes=new ArrayList<>();
 static Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper()));static int checks;
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message+" time="+SystemClock.now+" launches="+Env.launches.size());checks++;System.out.println("PASS "+message);}
 static void install(LinkedAppCoordinator value)throws Exception{java.lang.reflect.Field f=LinkedAppCoordinator.class.getDeclaredField("instance");f.setAccessible(true);f.set(null,value);coordinator=value;}
 static Intent lastProbe(){return probes.get(probes.size()-1);}
 static void reply(Intent probe,boolean ready)throws Exception{reply(probe,ready,probe.getStringExtra("nonce"),probe.getPackage(),probe.getLongExtra("issued",0),endpoint);}
 static void reply(Intent probe,boolean ready,String nonce,String pkg,long issued,Messenger remote)throws Exception{
  Bundle data=new Bundle();data.putInt("protocol",1);data.putString("nonce",nonce);data.putString("package",pkg);data.putLong("issued",issued);data.putBoolean("ready",ready);
  Message message=Message.obtain(null,6);message.setData(data);message.replyTo=remote;message.sendingUid=context.getPackageManager().getApplicationInfo(pkg,0).uid;
  ((Messenger)probe.getParcelableExtra("client")).send(message);
 }
 static void automatic(Set<String> ready)throws Exception{install(new LinkedAppCoordinator(context,i->{if(LinkedAppCoordinator.PROBE.equals(i.getAction())){probes.add(i);try{reply(i,ready.contains(i.getPackage()));}catch(Exception e){throw new RuntimeException(e);}}},pkg->true));coordinator.hostReady();}
 static void sequence(){Env.dispatch(new Intent(context,StartupSequenceService.class).putExtra("task",7));}
 static void deliver(Context target,Intent event){for(BroadcastReceiver r:new ArrayList<>(Env.receivers))r.onReceive(target,event);}
 public static void main(String[] args)throws Exception{
  Env.reset();context=new Context();String scenario=args[0];
  install(new LinkedAppCoordinator(context,i->{if(LinkedAppCoordinator.PROBE.equals(i.getAction()))probes.add(i);},pkg->true));coordinator.hostReady();
  if(scenario.equals("coalesce")){
   int[] results={0};coordinator.probe(QQ,r->{if(r)results[0]++;});coordinator.probe(QQ,r->{if(r)results[0]++;});
   check(probes.size()==1,"concurrent QQ users share one live request");check(!coordinator.canConnect(QQ),"process existence cannot grant readiness");reply(lastProbe(),true);Handler.runFor(0);
   check(results[0]==2&&coordinator.canConnect(QQ),"one valid interface proof completes both users");coordinator.probe(QQ,r->{});check(probes.size()==2,"explicit recheck requires a new nonce");
  }else if(scenario.equals("timeout")){
   int[] falseResults={0};coordinator.probe(MAP,r->{if(!r)falseResults[0]++;});coordinator.probe(MAP,r->{if(!r)falseResults[0]++;});Handler.runFor(499);check(falseResults[0]==0,"no premature missing-interface result");Handler.runFor(1);
   check(falseResults[0]==2&&!coordinator.canConnect(MAP),"missing interface expires once for all waiters");check(Env.launches.isEmpty()&&Env.serviceStarts.isEmpty(),"a probe never creates a companion UI or service");
  }else if(scenario.equals("invalid-replies")){
   int[] results={0};coordinator.probe(QQ,r->results[0]++);Intent p=lastProbe();reply(p,true,"wrong",QQ,p.getLongExtra("issued",0),endpoint);Handler.runFor(0);check(results[0]==0,"wrong nonce ignored");
   reply(p,true,p.getStringExtra("nonce"),MAP,p.getLongExtra("issued",0),endpoint);Handler.runFor(0);check(results[0]==0,"wrong package ignored");reply(p,true,p.getStringExtra("nonce"),QQ,p.getLongExtra("issued",0)+1,endpoint);Handler.runFor(0);check(results[0]==0,"wrong issuance ignored");
   reply(p,true,p.getStringExtra("nonce"),QQ,p.getLongExtra("issued",0),null);Handler.runFor(0);check(results[0]==1&&!coordinator.canConnect(QQ),"a response without a live Binder is not ready");
  }else if(scenario.equals("binder-death")){
   coordinator.probe(MAP,r->{});reply(lastProbe(),true);Handler.runFor(0);check(coordinator.canConnect(MAP),"verified native interface admitted");endpoint.kill();Handler.runFor(0);check(!coordinator.canConnect(MAP),"Binder death revokes cached proof");check(Env.launches.isEmpty(),"Binder death does not force an Activity into foreground");
  }else if(scenario.equals("sequence-gates")){
   coordinator.probe(QQ,r->{});reply(lastProbe(),true);Handler.runFor(0);check(coordinator.canBind(QQ),"proven interface can be protected");coordinator.beginSequence();check(!coordinator.canBind(QQ)&&!coordinator.canConnect(QQ),"automatic initialization owns all card and binding admission");
   int before=Env.broadcasts.size();coordinator.requestMusicSubscription();check(Env.broadcasts.size()==before,"card subscriptions do not race startup");coordinator.starting(MAP);int[] callbacks={0};coordinator.probe(MAP,r->{if(!r)callbacks[0]++;});check(callbacks[0]==1&&probes.size()==1,"already starting target receives no second probe or launch");
  }else if(scenario.equals("all-ready")){
   automatic(new HashSet<>(Arrays.asList(QQ,MAP)));sequence();Handler.runFor(0);check(Env.launches.isEmpty(),"both live interfaces skip all startup screens");check(!StartupSequenceService.active()&&!coordinator.busy(),"all-ready path completes and releases admission");check(!StartupSequenceService.hasPending(context),"all-ready path removes its watchdog ticket");
  }else if(scenario.equals("qq-ready-map-cold")){
   automatic(new HashSet<>(Arrays.asList(QQ)));sequence();Handler.runFor(0);check(Env.launches.size()==1&&MAP.equals(Env.top),"only unready map opens");sequence();Handler.runFor(0);check(Env.launches.size()==1,"repeated service start cannot launch an in-flight target again");check(!Env.launches.get(0).getBooleanExtra("windchaser.startup.return_home",false),"map cannot directly return HOME before remaining QQ readiness check");((PendingIntent)Env.launches.get(0).getParcelableExtra(StartupSequenceService.READY_CALLBACK)).send();Handler.runFor(0);check("cn.hydrogen.launcher".equals(Env.top)&&Env.launches.stream().noneMatch(i->QQ.equals(i.getComponent().getPackageName())),"ready QQ is skipped and service returns HOME after map");
  }else if(scenario.equals("qq-cold-map-ready")){
   Set<String> ready=new HashSet<>(Arrays.asList(MAP));automatic(ready);sequence();Handler.runFor(0);check(Env.launches.size()==1&&QQ.equals(Env.top),"only unready QQ opens first");ready.add(QQ);((PendingIntent)Env.launches.get(0).getParcelableExtra(StartupSequenceService.READY_CALLBACK)).send();Handler.runFor(0);check(Env.launches.stream().noneMatch(i->MAP.equals(i.getComponent().getPackageName())),"ready map is not relaunched after QQ finishes");check("cn.hydrogen.launcher".equals(Env.top),"service returns home when remaining target was skipped");
  }else if(scenario.equals("probe-foreign")||scenario.equals("probe-screen-off")||scenario.equals("probe-disabled")){
   sequence();check(Env.launches.isEmpty(),"wait for real interface probe before launch decision");
   if(scenario.equals("probe-foreign"))Env.top="other.foreground";
   if(scenario.equals("probe-screen-off"))((PowerManager)Env.services.get(Context.POWER_SERVICE)).on=false;
   if(scenario.equals("probe-disabled"))Env.enabled=false;
   reply(lastProbe(),false);Handler.runFor(0);check(Env.launches.isEmpty()&&!StartupSequenceService.active(),"changed power, foreground or switch cancels pending automatic launch");
  }else if(scenario.equals("first-frame")){
   automatic(new HashSet<>());Activity home=new MainActivity();home.pkg=context.getPackageName();StartupApps apps=new StartupApps(home,new Config(home));apps.resume();Handler.runFor(0);check(Env.serviceStarts.isEmpty(),"focus alone does not start before first draw");long before=SystemClock.now;Handler.runFor(1);check(Env.serviceStarts.size()==1&&Env.launches.size()==1,"first completed draw plus focus starts one sequence");check(SystemClock.now-before==1,"no fixed 800ms wait");apps.resume();Handler.runFor(0);check(Env.serviceStarts.size()==1,"repeat focus/resume cannot duplicate startup");
  }else if(scenario.equals("frame-reattach")){
   automatic(new HashSet<>());Activity home=new MainActivity();home.pkg=context.getPackageName();View decor=home.getWindow().getDecorView();decor.token=null;StartupApps apps=new StartupApps(home,new Config(home));apps.resume();Handler.runFor(5);check(Env.serviceStarts.isEmpty(),"unattached decor never admits startup");decor.token=new Object();decor.replaceObserver();apps.resume();Handler.runFor(1);check(Env.serviceStarts.size()==1,"attached live observer delivers first frame after floating observer replacement");coordinator.endSequence();coordinator.hostPaused();Env.top=home.pkg;apps.resume();check(coordinator.canRequest(MAP),"warm return restores coordinator host visibility");apps.close();
  }else if(scenario.equals("frame-focus")){
   automatic(new HashSet<>());Activity home=new MainActivity();home.pkg=context.getPackageName();home.focus=false;StartupApps apps=new StartupApps(home,new Config(home));apps.resume();Handler.runFor(5);check(Env.serviceStarts.isEmpty(),"draw without focus cannot start companions");home.focus=true;apps.resume();home.focus=false;Handler.runFor(0);check(Env.serviceStarts.isEmpty(),"lost focus before posted admission is respected");home.focus=true;apps.resume();Handler.runFor(0);check(Env.serviceStarts.size()==1,"focus after draw triggers immediately once");
  }else if(scenario.equals("frame-closed")||scenario.equals("frame-disabled")){
   Activity home=new MainActivity();home.pkg=context.getPackageName();StartupApps apps=new StartupApps(home,new Config(home));if(scenario.equals("frame-disabled"))Env.enabled=false;apps.resume();if(scenario.equals("frame-closed"))apps.close();Handler.runFor(2000);check(Env.serviceStarts.isEmpty()&&Env.launches.isEmpty(),"closed host or disabled automatic startup never launches companions");
  }else if(scenario.equals("resume-recheck")){
   coordinator.probe(QQ,r->{});reply(lastProbe(),true);Handler.runFor(0);check(coordinator.canConnect(QQ),"initial live proof accepted");coordinator.hostPaused();coordinator.hostReady();check(!coordinator.canConnect(QQ),"resumed host does not reuse an old readiness flag");coordinator.probe(QQ,r->{});reply(lastProbe(),true);Handler.runFor(0);check(coordinator.canConnect(QQ),"fresh real reply restores warm control connection");
  }else if(scenario.equals("late-proof")){
   coordinator.probe(MAP,r->{});Intent old=lastProbe();Handler.runFor(500);coordinator.probe(MAP,r->{});Intent fresh=lastProbe();reply(old,true);Handler.runFor(0);check(!coordinator.canConnect(MAP),"expired nonce cannot resurrect stale native readiness");reply(fresh,true);Handler.runFor(0);check(coordinator.canConnect(MAP),"current nonce restores readiness");
  }else if(scenario.equals("qq-runtime-state")||scenario.equals("qq-runtime-timeout")){
   Context qq=new Context();qq.pkg=QQ;android.os.Process.name=QQ;RuntimeProbe.install(qq);List<Message> answers=new ArrayList<>();Messenger client=new Messenger(new Handler(Looper.getMainLooper()){@Override public void handleMessage(Message m){answers.add(m);}});
   Intent request=new Intent(LinkedAppCoordinator.PROBE).putExtra("protocol",1).putExtra("nonce","fresh-query").putExtra("issued",SystemClock.elapsedRealtime()).putExtra("client",client);deliver(qq,request);
   check(Env.broadcasts.size()==1&&Env.broadcasts.get(0).getIntExtra("action",0)==100,"running QQ is queried through its real public state interface");
   deliver(qq,new Intent("com.tencent.qqmusiccar.action.PLAY_COMMAND_SEND_FOR_THIRD").putExtra("com.tencent.qqmusiccar.EXTRA_COMMAND_DATA","{\"module\":\"play\",\"command\":{\"method\":\"update_lyric\",\"data\":{}}}"));Handler.runFor(0);check(answers.isEmpty(),"lyrics or cached metadata alone do not prove control readiness");
   if(scenario.equals("qq-runtime-state")){deliver(qq,new Intent("com.tencent.qqmusiccar.action.PLAY_COMMAND_SEND_FOR_THIRD").putExtra("com.tencent.qqmusiccar.EXTRA_COMMAND_DATA","{\"module\":\"play\",\"command\":{\"method\":\"update_state\",\"data\":{\"state\":1}}}"));Handler.runFor(0);check(answers.size()==1&&answers.get(0).getData().getBoolean("ready"),"fresh playback state produces a positive native interface proof");Handler.runFor(500);check(answers.size()==1,"successful query cancels its timeout");}
   else{Handler.runFor(350);check(answers.size()==1&&!answers.get(0).getData().getBoolean("ready"),"alive QQ without a state reply is not marked ready");}
   check(Env.launches.isEmpty()&&Env.serviceStarts.isEmpty(),"readiness helper never opens an application");
  }else if(scenario.equals("map-native-readiness")){
   Activity map=new Activity();map.pkg=MAP;SurfaceView surface=new SurfaceView(),ui=new SurfaceView();surface.parent=new ViewGroup();ui.parent=new ViewGroup();AndroidProtocolExe.ready=false;
   check(!MapInterfaceReadiness.ready(map,surface,ui),"surface objects without native HMI readiness are insufficient");AndroidProtocolExe.ready=true;
   check(MapInterfaceReadiness.ready(map,surface,ui),"native HMI and two attached original surfaces are ready");check(!MapInterfaceReadiness.ready(map,surface,surface),"one surface cannot stand in for both render channels");ui.parent=null;check(!MapInterfaceReadiness.ready(map,surface,ui),"unrecoverable detached UI is not ready");map.finishing=true;check(!MapInterfaceReadiness.ready(map,surface,surface),"destroying activity cannot be reused");
  }else if(scenario.equals("kitkat-cold-negative")){
   ActivityManager manager=new ActivityManager(){@Override public List<RunningAppProcessInfo> getRunningAppProcesses(){RunningAppProcessInfo p=new RunningAppProcessInfo();p.uid=999;return Arrays.asList(p);}};Env.services.put(Context.ACTIVITY_SERVICE,manager);
   install(new LinkedAppCoordinator(context,null,pkg->true));int[] results={0};coordinator.probe(QQ,r->{if(!r)results[0]++;});check(results[0]==1&&Env.broadcasts.isEmpty(),"proven absent KitKat process skips a pointless timeout");Build.VERSION.SDK_INT=21;coordinator.probe(QQ,r->{});check(Env.broadcasts.size()==1,"restricted modern process list cannot prove a cold app");check(!coordinator.canConnect(QQ),"process information never grants positive interface readiness");
  }else if(scenario.equals("package-change-pending")){
   int[] cancelled={0};coordinator.probe(MAP,r->{if(!r)cancelled[0]++;});Intent stale=lastProbe();coordinator.packagesChanged();check(cancelled[0]==1,"package change cancels each old pending request exactly once");reply(stale,true);Handler.runFor(0);check(!coordinator.canConnect(MAP),"late old-package proof cannot restore a replaced interface");Intent fresh=lastProbe();check(!fresh.getStringExtra("nonce").equals(stale.getStringExtra("nonce")),"package change creates a fresh live probe");reply(fresh,true);Handler.runFor(0);check(coordinator.canConnect(MAP),"new package proof restores map connection");
  }else if(scenario.equals("service-destroy-owner")){
   automatic(new HashSet<>());sequence();Handler.runFor(0);check(coordinator.busy(),"startup service owns exclusive initialization");String pending=context.getSharedPreferences("startup-return",0).getString("token",null);Env.kill();check(!coordinator.busy(),"unexpected service destruction releases exclusive admission");check(pending!=null&&pending.equals(context.getSharedPreferences("startup-return",0).getString("token",null)),"service destruction preserves independent return watchdog");check(Env.launches.size()==1,"cleanup never repeats the current application launch");
  }else if(scenario.equals("recovery-after-timeout")){
   coordinator.refreshSoon();Handler.runFor(15000);int before=probes.size();Handler.runFor(15000);check(probes.size()==before,"automatic recovery stops after a finite retry window");check(!coordinator.canConnect(MAP),"missing native interface remains unavailable");coordinator.refreshSoon();Handler.runFor(0);check(probes.size()>before,"explicit card reentry can start a new readiness check");Intent fresh=lastProbe();check(MAP.equals(fresh.getPackage()),"map recovery request exists after retry exhaustion");reply(fresh,true);Handler.runFor(0);check(coordinator.canConnect(MAP),"late native readiness reconnects without restarting the app");check(Env.launches.isEmpty()&&Env.serviceStarts.isEmpty(),"readiness recovery never opens a companion UI");
  }else if(scenario.equals("qq-aux-process")){
   Context qq=new Context();qq.pkg=QQ;android.os.Process.name=QQ+":worker";RuntimeProbe.install(qq);check(Env.receivers.isEmpty(),"auxiliary process registers no competing readiness receiver");android.os.Process.name=QQ;RuntimeProbe.install(qq);check(Env.receivers.size()==2,"main process still installs both probe and native state listeners");check(Env.launches.isEmpty()&&Env.serviceStarts.isEmpty(),"process filtering does not start any application");
  }else if(scenario.equals("background-binding")){
   coordinator.probe(QQ,r->{});reply(lastProbe(),true);Handler.runFor(0);coordinator.hostPaused();check(!coordinator.canConnect(QQ),"hidden desktop cannot connect a card");check(coordinator.canBind(QQ),"verified background service protection survives leaving the desktop");coordinator.beginSequence();check(!coordinator.canBind(QQ),"automatic startup still owns background binding admission");
  }else if(scenario.equals("warm-recovery")){
   CompanionService.warmAllowed=true;coordinator.refreshSoon();Handler.runFor(15000);check(Env.serviceStarts.size()==2,"missing QQ and map interfaces each receive one public-service warmup");check(Env.launches.isEmpty(),"background recovery never opens a startup screen");coordinator.refreshSoon();Handler.runFor(1000);check(Env.serviceStarts.size()==2,"repeated page recovery cannot repeat an in-flight warmup within thirty seconds");coordinator.hostPaused();Handler.runFor(15000);check(Env.serviceStarts.size()==2,"paused host stops recovery warmups");
  }else if(scenario.equals("warm-ready-gates")){
   CompanionService.warmAllowed=true;automatic(new HashSet<>(Arrays.asList(QQ,MAP)));coordinator.refreshSoon();Handler.runFor(1000);check(Env.serviceStarts.isEmpty()&&Env.launches.isEmpty(),"already ready interfaces receive no prewarm or foreground launch");coordinator.beginSequence();coordinator.refreshSoon();Handler.runFor(1000);check(Env.serviceStarts.isEmpty(),"exclusive startup blocks independent recovery warmups");
  }else if(scenario.equals("warm-sequence-race")){
   CompanionService.warmAllowed=true;ActivityManager manager=new ActivityManager(){@Override public List<RunningAppProcessInfo> getRunningAppProcesses(){RunningAppProcessInfo p=new RunningAppProcessInfo();p.uid=999;return Arrays.asList(p);}};Env.services.put(Context.ACTIVITY_SERVICE,manager);install(new LinkedAppCoordinator(context,null,pkg->true));coordinator.hostReady();coordinator.refreshSoon();new Handler(Looper.getMainLooper()).post(()->coordinator.beginSequence());Handler.runFor(0);check(coordinator.busy(),"first-frame automatic sequence takes ownership");check(Env.serviceStarts.isEmpty(),"queued cold-interface warmup yields to automatic startup");
  }else if(scenario.equals("actual-handshake")){
   check(coordinator.canRequest(MAP)&&coordinator.canRequest(QQ),"warm interfaces can perform actual handshakes before a separate readiness proof");
   check(!coordinator.canConnect(MAP)&&!coordinator.canBind(QQ),"handshake admission never grants skip-startup or keepalive readiness");
   coordinator.probe(MAP,r->{});Handler.runFor(500);check(coordinator.canRequest(MAP),"probe timeout does not block a live map SHOW and ACK");
   coordinator.beginSequence();check(!coordinator.canRequest(QQ)&&!coordinator.canRequest(MAP),"startup owns both actual handshakes");coordinator.endSequence();coordinator.hostPaused();check(!coordinator.canRequest(QQ),"hidden host cannot subscribe");
  }else if(scenario.equals("qq-subscription-reentry")){
   List<Intent> sent=new ArrayList<>();install(new LinkedAppCoordinator(context,sent::add,pkg->true));int[] changes={0};coordinator.listen(()->changes[0]++);coordinator.hostReady();
   check(changes[0]==1,"first host admission notifies existing music clients");coordinator.requestMusicSubscription();check(sent.size()==1&&!coordinator.canConnect(QQ),"QQ subscription is sent without a separate probe proof");
   coordinator.requestMusicSubscription();Handler.runFor(199);check(sent.size()==1,"subscription burst is coalesced");Handler.runFor(1);check(sent.size()==2,"coalesced request is deferred rather than lost");
   coordinator.hostPaused();coordinator.requestMusicSubscription();Handler.runFor(200);check(sent.size()==2,"paused host sends no subscription");coordinator.hostReady();check(changes[0]==2,"returning host notifies music clients again");coordinator.requestMusicSubscription();check(sent.size()==3,"warm QQ can recover immediately on host return");
   coordinator.requestMusicSubscription();coordinator.beginSequence();Handler.runFor(200);check(sent.size()==3,"exclusive startup cancels deferred subscription");
  }else if(scenario.equals("qq-loss-proof")){
   coordinator.probe(QQ,r->{});reply(lastProbe(),true);Handler.runFor(0);check(!coordinator.interfaceLost(QQ),"live proof clears previous loss");coordinator.probe(QQ,r->{});Handler.runFor(500);check(!coordinator.interfaceLost(QQ),"probe timeout alone cannot disconnect actual playback events");
   endpoint.kill();Handler.runFor(0);check(coordinator.interfaceLost(QQ),"Binder death remains monitored even after a transient probe timeout");coordinator.controlResponded(QQ);check(!coordinator.interfaceLost(QQ)&&!coordinator.canConnect(QQ),"new playback event restores controls without granting independent startup readiness");
  }else if(scenario.equals("cold-subscription")){
   ActivityManager manager=new ActivityManager(){@Override public List<RunningAppProcessInfo> getRunningAppProcesses(){RunningAppProcessInfo p=new RunningAppProcessInfo();p.uid=999;return Arrays.asList(p);}};Env.services.put(Context.ACTIVITY_SERVICE,manager);install(new LinkedAppCoordinator(context,null,pkg->true));coordinator.hostReady();coordinator.requestMusicSubscription();Handler.runFor(1000);check(Env.broadcasts.isEmpty()&&Env.launches.isEmpty(),"auto-start-disabled cold QQ is not created by state subscription");
  }else throw new AssertionError(scenario);
  System.out.println("RESULT "+scenario+" checks="+checks+" PASS (production coordinator/helpers, JVM stubs)");
 }
}
