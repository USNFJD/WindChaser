package cn.hydrogen.launcher;
import android.content.*;import android.app.*;import android.os.*;import fixture.Env;import java.util.*;
/** Executes production service and coordinator; only Android/process boundaries are test doubles. */
public final class UnifiedAdmissionChecks {
 static boolean mapReady,musicReady,cutMap;static int qqProbes,mapProbes,checks;static long musicReplyDelay,mapReplyDelay;static boolean slowFinal,dropHintProbe;static long firstInitMap=-1,firstInitMusic=-1;
 static Messenger map=new Messenger(new Handler()),music=new Messenger(new Handler());
 static UnifiedInitService service;static Context context=new Context();
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why+" @"+SystemClock.now);checks++;System.out.println("PASS "+why);}
 static long homes(){return Env.launches.stream().filter(i->i.getComponent().getClassName().equals(MainActivity.class.getName())).count();}
 static long launches(String pkg){return Env.launches.stream().filter(i->i.getComponent().getPackageName().equals(pkg)).count();}
 static void initialize(boolean first)throws Exception {
  Handler.reset();Env.activityStarted=(c,i)->{
   if(Env.acknowledgeDesktop&&i.getComponent().getClassName().equals(MainActivity.class.getName())){if(Env.clearDesktopProofOnArrival){LinkedAppCoordinator cdr=LinkedAppCoordinator.get(context);cdr.hostPaused();cdr.hostReady();}MainActivity a=new MainActivity();a.intent=new Intent(i);UnifiedInitService.desktopVisible(a);}
  };Env.services.put(Context.NOTIFICATION_SERVICE,new NotificationManager());
  if(!first)context.getSharedPreferences("unified-native-initialization",0).edit().putBoolean(Config.MAP,true).putBoolean(Config.MUSIC,true).apply();
  LinkedAppCoordinator coordinator=new LinkedAppCoordinator(context,i->{
   if(!LinkedAppCoordinator.PROBE.equals(i.getAction()))return;
   if(dropHintProbe&&"fixture.registering".equals(i.getPackage()))return;
   boolean isMap=Config.MAP.equals(i.getPackage());if(!isMap)qqProbes++;else mapProbes++;
   if(cutMap&&!isMap&&qqProbes==1){mapReady=false;map.kill();}
   Bundle data=new Bundle();data.putString("package",i.getPackage());data.putString("nonce",i.getStringExtra("nonce"));data.putLong("issued",i.getLongExtra("issued",0));
   data.putBoolean("ready",isMap?mapReady:musicReady);data.putBoolean("foreground",Env.reportForeground&&i.getPackage().equals(Env.top));data.putBoolean("first_setup",first&&isMap);
   Message reply=Message.obtain(null,6);reply.setData(data);reply.replyTo=isMap?map:music;reply.sendingUid=10001;
   long delay=isMap?mapReplyDelay:musicReplyDelay;
   if(slowFinal&&i.getIntExtra("wait_ms",0)>0){delay=isMap?700:850;if(isMap&&firstInitMap<0)firstInitMap=SystemClock.now;if(!isMap&&firstInitMusic<0)firstInitMusic=SystemClock.now;}
   Runnable respond=()->{try{((Messenger)i.getParcelableExtra("client")).send(reply);}catch(Exception e){throw new RuntimeException(e);}};if(delay>0)new Handler().postDelayed(respond,delay);else respond.run();
  },pkg->true);
  java.lang.reflect.Field f=LinkedAppCoordinator.class.getDeclaredField("instance");f.setAccessible(true);f.set(null,coordinator);
  service=new UnifiedInitService();service.onCreate();service.onStartCommand(new Intent(),0,1);Handler.runFor(0);
 }
 public static void main(String[] args)throws Exception {
  String s=args[0];
  if(s.equals("camera-qq-foreground-mismatch")){
   Build.VERSION.SDK_INT=19;mapReady=true;initialize(false);Handler.runFor(1000);
   check(launches(Config.MUSIC)==1,"music home starts once before reverse interruption");
   Env.top="external.reverse.camera";Handler.runFor(500);musicReady=true;Env.reportForeground=false;Handler.runFor(3000);
   check(homes()==0,"ready music behind camera never admits desktop");
   Env.top=Config.MUSIC;com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(2500);
   check(homes()==1&&UnifiedInitService.admitted(),"actual owned music focus restores desktop even when legacy guest foreground flag disagrees");
  }
  else if(s.equals("camera-desktop-proof-refresh")){
   mapReady=musicReady=true;Env.clearDesktopProofOnArrival=true;initialize(false);Handler.runFor(2500);
   check(homes()==1&&!UnifiedInitService.active(),"desktop lifecycle invalidation refreshes live proofs without relaunching the already visible desktop");
  }
  else if(s.equals("camera-desktop-unacknowledged")){
   mapReady=musicReady=true;Env.dropDesktop=1;initialize(false);
   check(homes()==1&&UnifiedInitService.active(),"accepted startActivity without visible desktop keeps return owner alive");
   Handler.runFor(3000);check(homes()==2&&!UnifiedInitService.active(),"missing desktop delivery retries and completes only after focused desktop acknowledgment");
  }
  else if(s.equals("camera-desktop-return-race")){
   mapReady=musicReady=true;Env.dropDesktop=1;initialize(false);Env.top="external.reverse.camera";Handler.runFor(61000);
   check(homes()==1&&UnifiedInitService.active(),"camera can interrupt final desktop delivery without losing return ownership or timing out");
   Env.top=Config.MUSIC;com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(3000);
   check(homes()==2&&!UnifiedInitService.active(),"restored QQ window rechecks endpoints then delivers and acknowledges desktop");
   check(launches(Config.MUSIC)==0&&launches(Config.MAP)==0,"interrupted delivery never repeats native initialization");
  }
  else if(s.equals("camera-desktop-ready-loss")){
   mapReady=musicReady=true;Env.dropDesktop=1;initialize(false);Env.top="external.reverse.camera";Handler.runFor(1000);
   mapReady=false;map.kill();Env.top=Config.MUSIC;com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(2000);
   check(homes()==1&&UnifiedInitService.active(),"pending desktop retry cannot reuse a map endpoint killed during reversing");
   map=new Messenger(new Handler());mapReady=true;Handler.runFor(3000);check(homes()==2&&!UnifiedInitService.active(),"new map proof and live music permit one acknowledged retry");
  }
  else if(s.equals("camera-desktop-late-ack")){
   mapReady=musicReady=true;Env.acknowledgeDesktop=false;initialize(false);MainActivity stale=new MainActivity();stale.intent.putExtra("windchaser.init.session","stale-session");
   UnifiedInitService.desktopVisible(stale);Handler.runFor(0);check(UnifiedInitService.active(),"old desktop instance cannot acknowledge another initialization session");
   Env.top="external.reverse.camera";MainActivity hidden=new MainActivity();hidden.intent=new Intent(Env.launches.get(0));UnifiedInitService.desktopVisible(hidden);Handler.runFor(2000);
   check(UnifiedInitService.active()&&homes()==1,"hidden desktop callback neither completes initialization nor displaces camera");
   Env.acknowledgeDesktop=true;Env.top=Config.MUSIC;com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(3000);
   check(homes()==2&&!UnifiedInitService.active(),"current focused desktop acknowledges recovered session exactly once");
  }
  else if(s.equals("camera-background-readiness")){
   Build.VERSION.SDK_INT=34;initialize(false);long before=Env.launches.size();Env.top="external.reverse.camera";Handler.runFor(1500);
   int queried=mapProbes+qqProbes;mapReady=musicReady=true;Handler.runFor(8500);
   LinkedAppCoordinator c=LinkedAppCoordinator.get(context);
   check(mapProbes+qqProbes>queried&&c.liveEndpoint(Config.MAP)&&c.liveEndpoint(Config.MUSIC),"native interfaces become ready and are collected while camera remains foreground");
   check(Env.launches.size()==before&&homes()==0&&!UnifiedInitService.admitted(),"background readiness never opens an Activity or admits desktop");
   int settled=mapProbes+qqProbes;Handler.runFor(10000);check(mapProbes+qqProbes==settled,"both live backends stop background polling");
   Env.top=Config.MAP;Handler.runFor(2000);check(homes()==1&&mapProbes+qqProbes>settled,"return refreshes proofs and admits once without relaunching ready modules");
  }
  else if(s.equals("camera-background-service-once")){
   Build.VERSION.SDK_INT=34;CompanionService.warmAllowed=true;musicReplyDelay=800;initialize(false);Env.top="external.reverse.camera";Handler.runFor(250);
   check(CompanionService.startupPrepares==0,"foreground preparation probe was interrupted before it could start service");
   Handler.runFor(35000);check(CompanionService.startupPrepares==1&&Env.serviceStarts.size()==1,"interrupted probe does not lose the once-only windowless music preparation");
   check(launches(Config.MAP)==1&&launches(Config.MUSIC)==0&&homes()==0,"music backend preparation keeps camera foreground and never opens music home");
   check(mapProbes<20&&qqProbes<20,"unready background engines use bounded backoff instead of busy polling");
  }
  else if(s.equals("camera-background-cold")){
   Build.VERSION.SDK_INT=34;CompanionService.warmAllowed=true;mapReplyDelay=400;initialize(false);Env.top="external.reverse.camera";Handler.runFor(10000);
   check(Env.launches.isEmpty()&&CompanionService.startupPrepares==1,"before map launch only the safe music service can be prepared behind camera");
   check(!UnifiedInitService.admitted()&&UnifiedInitService.active(),"a cold uncreated map is never declared ready or forced in front");
   mapReplyDelay=0;Env.top="cn.windchaser.unified";Handler.runFor(2000);check(launches(Config.MAP)==1&&homes()==0,"restoring owned window allows the deferred map Activity exactly once");
  }
  else if(s.equals("camera-background-first-use")){
   Build.VERSION.SDK_INT=34;CompanionService.warmAllowed=true;initialize(true);long before=Env.launches.size();Env.top="external.reverse.camera";Handler.runFor(15000);
   check(CompanionService.startupPrepares==0&&Env.serviceStarts.isEmpty(),"background services never bypass first-use consent or permission setup");
   check(Env.launches.size()==before&&homes()==0,"no first-use dialog or application window opens over camera");
  }
  else if(s.equals("camera-background-disabled")){
   Build.VERSION.SDK_INT=34;CompanionService.warmAllowed=true;musicReplyDelay=800;initialize(false);Env.enabled=false;Env.top="external.reverse.camera";Handler.runFor(15000);
   check(CompanionService.startupPrepares==0&&Env.serviceStarts.isEmpty(),"disabled automatic startup prevents all new background service requests");
   check(UnifiedInitService.active()&&homes()==0&&launches(Config.MUSIC)==0,"passive observation does not foreground music or desktop");
  }
  else if(s.equals("camera-background-death")){
   Build.VERSION.SDK_INT=34;initialize(false);Env.top="external.reverse.camera";mapReady=musicReady=true;Handler.runFor(10000);
   check(LinkedAppCoordinator.get(context).liveEndpoint(Config.MAP),"background engine was confirmed before process loss");
   mapReady=false;map.kill();Env.top=Config.MAP;Handler.runFor(2000);check(homes()==0&&!UnifiedInitService.admitted(),"dead background endpoint cannot pass foreground admission");
   map=new Messenger(new Handler());mapReady=true;Handler.runFor(2000);check(homes()==1,"replacement engine must answer a fresh probe before desktop admission");
  }
  else if(s.equals("camera-background-screen-off")){
   Build.VERSION.SDK_INT=34;CompanionService.warmAllowed=true;musicReplyDelay=800;initialize(false);Env.top="external.reverse.camera";Handler.runFor(300);
   for(BroadcastReceiver r:new ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));
   Handler.runFor(15000);check(CompanionService.startupPrepares==0&&Env.serviceStarts.isEmpty(),"cancelled background probe cannot start a late music service after screen-off");
   check(!UnifiedInitService.active()&&homes()==0,"screen-off cancels the observer without a delayed desktop return");
  }
  else if(s.equals("camera-pending-release")){
   Build.VERSION.SDK_INT=34;initialize(false);musicReplyDelay=800;List<Boolean> replies=new ArrayList<>();
   LinkedAppCoordinator.get(context).awaitInitializing("fixture.cancelled-dialog",replies::add);
   Env.top="external.reverse.camera";Handler.runFor(250);
   check(replies.size()==1&&!replies.get(0),"interrupted dialog probe releases its in-flight flag with failure");
   Handler.runFor(2000);check(replies.size()==1&&homes()==0,"late cancelled callback never fires twice or opens desktop");
  }
  else if(s.equals("camera-visual-deadline")){
   Build.VERSION.SDK_INT=34;initialize(false);Env.top="external.reverse.camera";Handler.runFor(61000);Env.top=Config.MAP;mapReady=true;Handler.runFor(2000);
   check(launches(Config.MUSIC)==1&&UnifiedRuntime.lastUntil>SystemClock.now,"music visual receives a future deadline after long reversing interruption");
   check(homes()==0,"fresh visual deadline never substitutes actual player readiness");musicReady=true;Handler.runFor(2000);check(homes()==1,"real music reply completes resumed startup");
  }
  else if(s.equals("camera-during-map")){
   Build.VERSION.SDK_INT=34;initialize(false);check(launches(Config.MAP)==1&&launches(Config.MUSIC)==0,"map starts first before camera interruption");
   Env.top="external.reverse.camera";Handler.runFor(250);mapReady=musicReady=true;Handler.runFor(60000);
   check(UnifiedInitService.active()&&homes()==0&&launches(Config.MUSIC)==0,"60-second reversing neither times out nor launches music or desktop");
   Env.top=Config.MAP;Handler.runFor(2000);check(homes()==1&&UnifiedInitService.admitted(),"return from camera freshly confirms both ready modules");
   check(launches(Config.MAP)==1&&launches(Config.MUSIC)==0,"ready modules survive interruption without duplicate initialization");
  }
  else if(s.equals("camera-before-first-launch")){
   mapReplyDelay=400;initialize(false);Env.top="external.reverse.camera";Handler.runFor(61000);
   check(UnifiedInitService.active()&&Env.launches.isEmpty(),"late first probe cannot launch anything over camera or spend its timeout");
   mapReplyDelay=0;mapReady=musicReady=true;Env.top="cn.windchaser.unified";Handler.runFor(2000);check(homes()==1,"resuming bootstrap restores the same unfinished sequence");
  }
  else if(s.equals("camera-final-reply")){
   mapReady=musicReady=true;musicReplyDelay=400;slowFinal=true;initialize(false);Env.top="external.reverse.camera";Handler.runFor(2000);
   check(homes()==0&&!UnifiedInitService.admitted(),"ready replies captured before reversing cannot admit desktop");
   int before=qqProbes+mapProbes;Env.top="cn.windchaser.unified";Handler.runFor(3500);
   check(homes()==1&&qqProbes+mapProbes>before,"resume discards old nonce proofs and really queries both endpoints");
  }
  else if(s.equals("camera-first-setup")){
   initialize(true);long before=Env.launches.size();Env.top="external.reverse.camera";Handler.runFor(310000);
   check(UnifiedInitService.active()&&Env.launches.size()==before,"reversing beyond first-use budget never opens setup dialogs or fails startup");
   Env.top=Config.MAP;Handler.runFor(2000);check(UnifiedInitService.active()&&homes()==0,"first-use still requires user setup after camera exits");
  }
  else if(s.equals("camera-repeated")){
   Build.VERSION.SDK_INT=34;initialize(false);
   for(int n=0;n<5;n++){Env.top="external.reverse.camera";Handler.runFor(12000);Env.top=Config.MAP;Handler.runFor(500);}
   check(UnifiedInitService.active()&&homes()==0&&launches(Config.MAP)==1,"repeated reverse gear interruptions retain the same startup without reopening map");
   mapReady=musicReady=true;Handler.runFor(2000);check(homes()==1,"one desktop admission after repeated camera interruption");
  }
  else if(s.equals("camera-screen-off")){
   Build.VERSION.SDK_INT=34;initialize(false);Env.top="external.reverse.camera";Handler.runFor(500);
   for(BroadcastReceiver r:new ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));
   mapReady=musicReady=true;Env.top="cn.windchaser.unified";Handler.runFor(60000);
   check(!UnifiedInitService.active()&&homes()==0&&launches(Config.MUSIC)==0,"screen-off while reversing cancels all delayed transitions");
  }
  else if(s.equals("camera-resume-one-dead")){
   Build.VERSION.SDK_INT=34;mapReady=true;initialize(false);Env.top="external.reverse.camera";Handler.runFor(60000);
   mapReady=false;map.kill();musicReady=true;Env.top="cn.windchaser.unified";Handler.runFor(2000);
   check(homes()==0&&!UnifiedInitService.admitted(),"camera return with dead map endpoint never enters desktop from cached readiness");
   map=new Messenger(new Handler());mapReady=true;Handler.runFor(2000);check(homes()==1,"fresh replacement endpoint permits safe continuation");
  }
  else if(s.equals("camera-timeout-budget")){
   Build.VERSION.SDK_INT=34;initialize(false);Handler.runFor(20000);Env.top="external.reverse.camera";Handler.runFor(90000);
   check(UnifiedInitService.active(),"camera time excluded from original 45-second limit");
   Env.top=Config.MAP;Handler.runFor(20000);check(UnifiedInitService.active(),"remaining initialization budget is retained after reverse");
   Handler.runFor(6000);check(!UnifiedInitService.active()&&homes()==0,"real initialization timeout still bounded after camera return");
  }
  else if(s.equals("both-ready")){mapReady=musicReady=true;initialize(false);check(UnifiedInitService.admitted()&&homes()==1,"both live replies admit exactly once");check(launches(Config.MAP)==0&&launches(Config.MUSIC)==0,"ready modules are reused without relaunch");check(qqProbes==1&&mapProbes==1,"fresh live replies avoid immediate duplicate probes");check(CompanionService.startupPrepares==0,"ready modules are not prepared again");}
  else if(s.equals("map-only")){mapReady=true;initialize(false);Handler.runFor(2000);check(!UnifiedInitService.admitted()&&homes()==0,"map readiness alone never opens desktop");musicReady=true;Handler.runFor(1000);check(UnifiedInitService.admitted()&&homes()==1,"music live response then admits both");}
  else if(s.equals("music-only")){musicReady=true;initialize(false);Handler.runFor(2000);check(!UnifiedInitService.admitted()&&homes()==0,"music readiness alone never opens desktop");mapReady=true;Handler.runFor(1000);check(UnifiedInitService.admitted()&&homes()==1,"map live response then admits both");}
  else if(s.equals("cold-sequence")){initialize(false);Handler.runFor(2000);check(launches(Config.MAP)==1&&launches(Config.MUSIC)==1&&homes()==0,"cold map launches first; later-start music preparation overlaps without desktop admission");mapReady=true;Handler.runFor(2000);check(launches(Config.MUSIC)==1&&homes()==0,"music launched once after map interface ready");musicReady=true;Handler.runFor(1000);check(UnifiedInitService.admitted()&&homes()==1,"cold sequence enters only after two interfaces");}
  else if(s.equals("death-before-entry")){mapReady=musicReady=cutMap=true;initialize(false);Handler.runFor(1000);check(!UnifiedInitService.admitted()&&homes()==0,"map Binder death between final replies blocks admission");map=new Messenger(new Handler());mapReady=true;Handler.runFor(1000);check(UnifiedInitService.admitted()&&homes()==1,"fresh replacement endpoint can recover admission");}
  else if(s.equals("timeout")){initialize(false);Handler.runFor(45000);check(!UnifiedInitService.active()&&!UnifiedInitService.admitted()&&homes()==0,"45 second timeout stops initialization without desktop admission");mapReady=musicReady=true;Handler.runFor(10000);check(homes()==0,"late readiness after timeout cannot revive cancelled callbacks");check(Env.launches.stream().anyMatch(i->i.getBooleanExtra("initialization_failed",false)),"timeout exposes recovery UI while own module is foreground");}
  else if(s.equals("first-timeout")){initialize(true);Handler.runFor(300000);check(!UnifiedInitService.active()&&!UnifiedInitService.admitted()&&homes()==0,"first-use timeout never counts as successful initialization");}
  else if(s.equals("screen-off")){initialize(false);for(BroadcastReceiver r:new ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));mapReady=musicReady=true;Handler.runFor(5000);check(!UnifiedInitService.active()&&!UnifiedInitService.admitted()&&homes()==0,"screen-off cancellation cannot open desktop later");}
  else if(s.equals("external-window")){mapReady=musicReady=true;Env.top="external.reverse.camera";initialize(false);Handler.runFor(2000);check(!UnifiedInitService.admitted()&&homes()==0,"ready modules never displace an external foreground window");Env.top="cn.windchaser.unified";Handler.runFor(1000);check(UnifiedInitService.admitted()&&homes()==1,"returning to startup rechecks and permits entry");}
  else if(s.equals("auto-disabled")){Env.enabled=false;mapReady=true;initialize(false);check(!UnifiedInitService.admitted()&&homes()==0&&launches(Config.MUSIC)==0,"disabled auto-start and missing music blocks desktop");check(CompanionService.startupPrepares==0,"auto-disabled never starts backend preparation");}
  else if(s.equals("interrupted")){context.getSharedPreferences("unified-startup-session",0).edit().putBoolean("active",true).putInt("pid",1233).putString("session","old").apply();check(UnifiedInitService.recoverInterrupted(context),"process interruption is detected");check(UnifiedInitService.required(context)&&homes()==0,"interruption offers retry without stale admission");check(!UnifiedInitService.recoverInterrupted(context),"interruption marker is consumed once");}
  else if(s.equals("map-setup-permission-return")){
   initialize(true);Handler.runFor(2000);long before=Env.launches.stream().filter(i->i.hasExtra("first_map_setup")).count();
   check(before==1,"first-use dialog is offered once");Env.top=Config.MAP;Handler.runFor(2000);
   check(Env.launches.stream().filter(i->i.hasExtra("first_map_setup")).count()==before+1,"return from native permission page restores existing setup dialog");
   Env.top="external.permission.controller";Handler.runFor(2000);long after=Env.launches.size();Handler.runFor(2000);
   check(Env.launches.size()==after&&homes()==0,"first-use recovery does not displace external permission windows");
  }
  else if(s.equals("map-setup")){mapReady=true;initialize(true);Handler.runFor(1000);check(UnifiedInitService.firstMapPending()&&homes()==0,"native map readiness does not bypass first-use user setup");UnifiedInitService.completeFirstMap();Handler.runFor(1000);SharedPreferences prefs=context.getSharedPreferences("unified-native-initialization",0);check(prefs.getBoolean(Config.MAP,false)&&!prefs.getBoolean(Config.MUSIC,false),"finished map setup is saved independently of music");check(!UnifiedInitService.admitted()&&homes()==0,"saved map setup never substitutes music readiness");musicReady=true;Handler.runFor(1000);check(UnifiedInitService.admitted()&&homes()==1,"user setup plus both live interfaces permits desktop");}
  else if(s.equals("warm-once")){CompanionService.warmAllowed=true;initialize(false);Handler.runFor(3000);check(CompanionService.startupPrepares==1&&Env.serviceStarts.size()==1,"map load prepares original music backend once per sequence");check(launches(Config.MAP)==1&&launches(Config.MUSIC)==1&&homes()==0,"confirmed map window permits one music preparation, without desktop admission");mapReady=true;Handler.runFor(1500);check(launches(Config.MUSIC)==1&&homes()==0,"music Activity is not duplicated when map becomes ready");musicReady=true;Handler.runFor(1000);check(homes()==1&&UnifiedInitService.admitted(),"both real interfaces still required after warm-up");}
  else if(s.equals("warm-unavailable")){initialize(false);Handler.runFor(2000);check(CompanionService.startupPrepares==1&&Env.serviceStarts.isEmpty(),"unavailable backend does not produce fake readiness or repeated retries");mapReady=true;Handler.runFor(1500);check(launches(Config.MUSIC)==1&&homes()==0,"backend failure preserves one normal music initialization");musicReady=true;Handler.runFor(1000);check(homes()==1,"normal Activity path can complete after backend failure");}
  else if(s.equals("warm-first-use")){CompanionService.warmAllowed=true;initialize(true);Handler.runFor(2000);check(CompanionService.startupPrepares==0&&Env.serviceStarts.isEmpty(),"first-use protocols and permissions keep sequential setup");check(launches(Config.MUSIC)==0&&homes()==0,"first-use map setup is not bypassed");}
  else if(s.equals("warm-ready-music")){CompanionService.warmAllowed=true;musicReady=true;initialize(false);Handler.runFor(2000);check(CompanionService.startupPrepares==0&&Env.serviceStarts.isEmpty(),"already live music backend is reused without service restart");mapReady=true;Handler.runFor(1500);check(homes()==1&&launches(Config.MUSIC)==0,"already ready music needs no Activity relaunch");}
  else if(s.equals("warm-cancelled")){CompanionService.warmAllowed=true;musicReplyDelay=200;initialize(false);for(BroadcastReceiver r:new ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));Handler.runFor(2000);check(CompanionService.startupPrepares==0&&Env.serviceStarts.isEmpty()&&homes()==0,"late warm probe after screen-off cannot start backend or desktop");}
  else if(s.equals("parallel-final")){
   mapReady=musicReady=true;musicReplyDelay=400;slowFinal=true;initialize(false);Handler.runFor(400);
   // Force final map proof renewal and require independent slow real replies.
   check(!UnifiedInitService.admitted(),"delayed final map verification cannot admit prematurely");
   Handler.runFor(900);check(UnifiedInitService.admitted()&&homes()==1,"slow final reply within native budget permits entry");
   check(launches(Config.MAP)==0&&launches(Config.MUSIC)==0,"parallel renewal never relaunches live modules");
  }
  else if(s.equals("await-slow")){
   initialize(false);LinkedAppCoordinator c=LinkedAppCoordinator.get(context);mapReady=true;mapReplyDelay=850;
   final boolean[] done={false},accepted={false};c.awaitInitializing("fixture.slow",r->{done[0]=true;accepted[0]=r;});
   // This fixture package uses the music endpoint; its response is delayed too.
   Handler.runFor(0);
   musicReady=true;musicReplyDelay=850;done[0]=false;accepted[0]=false;
   c.awaitInitializing("fixture.slow2",r->{done[0]=true;accepted[0]=r;});
   Handler.runFor(500);check(!done[0],"startup probe tolerates a response slower than 500ms");
   Handler.runFor(350);check(done[0]&&accepted[0],"authenticated 850ms response is accepted without reopening UI");
  }
  else if(s.equals("parallel-round")){
   mapReady=musicReady=true;initialize(false);long before=homes();
   // A new service instance models a return/retry without changing endpoints.
   service.onDestroy();musicReplyDelay=400;mapReplyDelay=400;slowFinal=true;
   service=new UnifiedInitService();service.onCreate();service.onStartCommand(new Intent(),0,2);Handler.runFor(800);
   check(!UnifiedInitService.admitted(),"two slow final probes still wait for both replies");
   check(firstInitMap>=0&&firstInitMusic>=0&&firstInitMap==firstInitMusic,"final map and music checks start in the same round");
   Handler.runFor(900);check(homes()==before+1&&UnifiedInitService.admitted(),"parallel final round enters once after the slower ready proof");
  }
  else if(s.equals("await-expired")){
   initialize(false);LinkedAppCoordinator c=LinkedAppCoordinator.get(context);musicReady=true;musicReplyDelay=1300;
   final java.util.List<Boolean> results=new java.util.ArrayList<>();c.awaitInitializing("fixture.late",results::add);
   Handler.runFor(1200);check(results.size()==1&&!results.get(0),"startup wait remains bounded and timeout is not success");
   Handler.runFor(100);check(results.size()==1&&!c.confirmed("fixture.late"),"late nonce reply cannot restore readiness after timeout");
  }
  else if(s.equals("slow-cancel")){
   mapReady=musicReady=true;musicReplyDelay=400;slowFinal=true;initialize(false);Handler.runFor(400);
   for(BroadcastReceiver r:new ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));
   Handler.runFor(2000);check(homes()==0&&!UnifiedInitService.admitted(),"cancellation invalidates parallel final callbacks");
  }
  else if(s.equals("focus-gap")){
   mapReady=musicReady=true;Env.top="external.permission.controller";initialize(false);
   check(homes()==0,"ready proofs do not bypass missing startup focus");
   Handler.runFor(79);Env.top="cn.windchaser.unified";com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(1);
   check(homes()==1&&UnifiedInitService.admitted(),"first focus gap is freshly rechecked after 80ms");
  }
  else if(s.equals("parallel-death")){
   mapReady=musicReady=true;musicReplyDelay=400;mapReplyDelay=400;slowFinal=true;initialize(false);Handler.runFor(1500);
   check(homes()==0,"parallel gate waits for the second proof");map.kill();mapReady=false;Handler.runFor(1000);
   check(homes()==0&&!UnifiedInitService.admitted(),"endpoint death between parallel replies blocks desktop");
  }
  else if(s.equals("receiver-installed")){
   initialize(false);LinkedAppCoordinator c=LinkedAppCoordinator.get(context);dropHintProbe=true;
   java.util.List<Boolean> results=new java.util.ArrayList<>();c.awaitInitializing("fixture.registering",results::add);
   Handler.runFor(100);check(results.isEmpty(),"query can precede native receiver registration");
   musicReady=true;dropHintProbe=false;c.probeAvailableInitializing("fixture.registering");Handler.runFor(0);
   check(results.size()==1&&results.get(0),"registered hint retries the existing query without waiting for timeout");
   check(c.confirmed("fixture.registering"),"only the replacement authenticated reply marks ready");
   Handler.runFor(1200);check(results.size()==1,"old timeout cannot complete replacement callbacks twice");
  }
  else if(s.equals("inflight-proof")){
   mapReady=musicReady=true;initialize(false);LinkedAppCoordinator c=LinkedAppCoordinator.get(context);
   c.probeInitializing("fixture.proof",ready->{});Handler.runFor(0);check(c.confirmed("fixture.proof"),"initial authenticated proof is valid");
   Handler.runFor(1500);musicReplyDelay=850;c.awaitInitializing("fixture.proof",ready->{});
   check(!c.confirmed("fixture.proof")&&!c.confirmedRecently("fixture.proof",250),"starting a new query cannot refresh the previous ready proof");
   Handler.runFor(850);check(c.confirmed("fixture.proof"),"only a new actual reply renews readiness");
  }
  else if(s.equals("overlap-external")){
   musicReplyDelay=100;initialize(false);Env.top="external.reverse.camera";Handler.runFor(1500);
   check(launches(Config.MAP)==1&&launches(Config.MUSIC)==0&&homes()==0,"overlap cannot start music over an external window");
   Env.top=Config.MAP;Handler.runFor(1000);
   check(launches(Config.MUSIC)==1&&homes()==0,"confirmed map window starts music once while map is still loading");
   musicReady=true;Handler.runFor(1000);check(homes()==0,"overlapped music readiness never substitutes map readiness");
   mapReady=true;Handler.runFor(1000);check(homes()==1&&UnifiedInitService.admitted(),"overlap admits desktop only after both actual interfaces");
  }
  else if(s.equals("overlap-runtime-permission")){
   android.os.Build.VERSION.SDK_INT=34;CompanionService.warmAllowed=true;initialize(false);Handler.runFor(2000);
   check(launches(Config.MAP)==1&&launches(Config.MUSIC)==0&&homes()==0,"runtime-permission platforms keep native windows sequential during map load");
   check(CompanionService.startupPrepares==1,"safe original music service preparation remains available");
   mapReady=true;Handler.runFor(1500);check(launches(Config.MUSIC)==1&&homes()==0,"map readiness starts music once without admitting early");
   musicReady=true;Handler.runFor(1000);check(homes()==1&&UnifiedInitService.admitted(),"both actual endpoints remain mandatory on modern Android");
  }
  else if(s.equals("resume-live")){
   mapReady=musicReady=true;initialize(false);Handler.runFor(3000);
   check(!LinkedAppCoordinator.get(context).confirmed(Config.MAP),"old startup proof expires normally");
   check(UnifiedInitService.canResumeDesktop(context),"admitted session with both live endpoints restores without replaying startup");
   check(homes()==1&&launches(Config.MAP)==0&&launches(Config.MUSIC)==0,"reuse does not relaunch modules or admit a new session");
  }
  else if(s.equals("resume-dead")){
   mapReady=musicReady=true;initialize(false);map.kill();Handler.runFor(0);
   check(!UnifiedInitService.canResumeDesktop(context),"dead native endpoint cannot use the warm return shortcut");
  }
  else if(s.equals("resume-lost")){
   mapReady=musicReady=true;initialize(false);musicReady=false;
   LinkedAppCoordinator.get(context).probeInitializing(Config.MUSIC,r->{});Handler.runFor(0);
   check(!UnifiedInitService.canResumeDesktop(context),"negative real readiness reply disables warm shortcut");
  }
  else if(s.equals("handoff-fresh")){
   mapReady=true;initialize(false);Handler.runFor(200);
   check(launches(Config.MUSIC)==1,"fresh foreground proof launches music once");
   check(mapProbes==1,"handoff reuses actual just-issued map proof without another probe");
   check(!UnifiedInitService.admitted(),"reused map proof never substitutes missing music readiness");
  }
  else if(s.equals("handoff-permission")){
   mapReady=true;musicReplyDelay=200;initialize(false);
   new Handler().postDelayed(()->Env.top="external.permission.controller",100);Handler.runFor(2000);
   check(launches(Config.MUSIC)==0&&homes()==0,"delayed handoff refreshes foreground and preserves external permission window");
  }
  else if(s.equals("cold-private-runtime")){
   android.os.Build.VERSION.SDK_INT=34;UnifiedRuntime.coldMap=true;mapReplyDelay=500;initialize(false);
   check(launches(Config.MAP)==1&&mapProbes==0,"private-runtime negative cold hint starts map without a nonexistent receiver timeout");
   check(homes()==0&&!UnifiedInitService.admitted(),"negative cold hint never counts as map readiness");
   UnifiedRuntime.coldMap=false;mapReplyDelay=0;mapReady=true;Handler.runFor(1500);musicReady=true;Handler.runFor(1500);
   check(homes()==1&&mapProbes>0,"launched module still passes real map and music interface checks");
  }
  else if(s.equals("cold-external")){
   UnifiedRuntime.coldMap=true;Env.top="external.reverse.camera";initialize(false);Handler.runFor(1000);
   check(launches(Config.MAP)==0&&homes()==0,"cold hint never displaces an external foreground");
   Env.top="cn.windchaser.unified";Handler.runFor(400);check(launches(Config.MAP)==1&&homes()==0,"returning foreground permits one cold launch without admission");
  }
  else if(s.equals("cold-auto-disabled")){
   UnifiedRuntime.coldMap=true;Env.enabled=false;initialize(false);Handler.runFor(2000);
   check(launches(Config.MAP)==0&&homes()==0&&!UnifiedInitService.admitted(),"cold hint still respects disabled automatic startup");
  }
  else throw new IllegalArgumentException(s);
  System.out.println("RESULT "+s+" checks="+checks+" PASS (production service/coordinator; JVM stubs)");
 }
}
