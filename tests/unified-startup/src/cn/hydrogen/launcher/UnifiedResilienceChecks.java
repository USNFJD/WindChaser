package cn.hydrogen.launcher;
import android.content.*;import android.os.*;import fixture.Env;
import static cn.hydrogen.launcher.UnifiedAdmissionChecks.*;
/** Real admission/controller decisions with replaceable OS and engine boundaries. */
public final class UnifiedResilienceChecks {
 public static void main(String[] args)throws Exception{
  String name=args[0];Build.VERSION.SDK_INT=19;
  if(name.equals("queued-service-dispatch")){
   UnifiedInitService.request(context);
   check(UnifiedInitService.active(),"accepted service dispatch already owns the startup while Android queues onCreate");
   UnifiedInitService.request(context);
   check(Env.serviceStarts.size()==1,"queued initialization is not dispatched twice");
   mapReady=musicReady=true;initialize(false);Handler.runFor(1000);
   check(homes()==1&&!UnifiedInitService.active(),"queued owner transfers to the created service and completes only through real interfaces");
  }else if(name.equals("rejected-service-dispatch")){
   boolean rejected=false;
   try{UnifiedInitService.request(new Context(){@Override public ComponentName startService(Intent i){throw new SecurityException("injected service rejection");}});}
   catch(SecurityException expected){rejected=true;}
   check(rejected&&!UnifiedInitService.active(),"a rejected OS service start releases the queued owner for a real retry");
   UnifiedInitService.request(context);
   check(UnifiedInitService.active()&&Env.serviceStarts.size()==1,"a failed dispatch cannot permanently block the next authorized entry");
  }else if(name.equals("sequential-map")){
   initialize(false);Handler.runFor(3000);
   check(launches(Config.MAP)==1&&launches(Config.MUSIC)==0,"unready map keeps its native foreground; QQ Activity cannot suspend its initialization");
   mapReady=true;Handler.runFor(1000);check(launches(Config.MUSIC)==1,"music launches promptly after genuine map readiness");
   musicReady=true;Handler.runFor(1000);check(homes()==1,"both genuine offline interfaces admit desktop");
  }else if(name.equals("resume-lost-window")){
   initialize(false);Env.top="external.reverse.camera";Handler.runFor(1000);
   Env.top="cn.windchaser.unified";com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(3000);
   check(launches(Config.MAP)==2,"return to bootstrap reattaches unfinished map instead of querying a permanently parked window");
   mapReady=musicReady=true;Handler.runFor(1500);check(homes()==1,"recovered window still requires fresh real endpoints");
  }else if(name.equals("slow-cpu")){
   mapReady=musicReady=true;mapReplyDelay=musicReplyDelay=1600;initialize(false);Handler.runFor(15000);
   check(homes()==1&&UnifiedInitService.admitted(),"slow but real 1600ms responses enter once without a probe starvation loop");
   check(launches(Config.MAP)==0&&launches(Config.MUSIC)==0,"slow live interfaces are reused rather than launched again");
  }else if(name.equals("asymmetric-slow-cpu")){
   mapReady=musicReady=true;mapReplyDelay=1600;musicReplyDelay=3000;initialize(false);Handler.runFor(14000);
   check(homes()==1&&UnifiedInitService.admitted(),"unequal slow real interfaces complete without alternating proof-expiry starvation");
   check(launches(Config.MAP)==0&&launches(Config.MUSIC)==0,"slow ready native modules retain their existing instances");
  }else if(name.equals("slow-map-fast-music")){
   mapReady=musicReady=true;mapReplyDelay=3000;musicReplyDelay=50;initialize(false);Handler.runFor(12000);
   check(homes()==1,"a fast music reply and slow map reply are both retained until genuine final verification completes");
  }else if(name.equals("first-setup-handoff")){
   initialize(true);Handler.runFor(4000);
   check(Config.MAP.equals(Env.top)&&Env.launches.stream().noneMatch(i->i.hasExtra("first_map_setup")),
    "a focused first-use window and an unready engine keep native consent in front");
   Env.top="external.permission.controller";mapReady=true;Handler.runFor(3000);
   check(Env.launches.stream().noneMatch(i->i.hasExtra("first_map_setup")),"ready engine cannot displace an external permission dialog");
   Env.top=Config.MAP;com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(2000);
   long offers=Env.launches.stream().filter(i->i.hasExtra("first_map_setup")).count();
   check(offers==1,"fresh ready and foreground proofs offer the host dialog once (offers="+offers+", top="+Env.top+", map probes="+mapProbes+")");
   check(UnifiedInitService.firstMapPending()&&launches(Config.MUSIC)==0&&homes()==0,"handoff does not replace user confirmation or start music");
  }else if(name.equals("first-setup-lost-window")){
   initialize(true);Env.top="external.reverse.camera";Handler.runFor(2000);Env.top="cn.windchaser.unified";
   com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(2500);
   check(launches(Config.MAP)==2&&homes()==0,"lost first-use native window is reopened without completing consent or entering desktop");
  }else if(name.equals("first-setup-dialog-retained")){
   initialize(true);Env.top="external.transition";Handler.runFor(500);
   UnifiedBootstrapActivity.mapSetupWindow=true;Env.top="cn.windchaser.unified";
   com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(15000);
   check(launches(Config.MAP)==1&&homes()==0,"owned first-use map dialog retains its original native surfaces without a relaunch loop");
   mapReady=true;Handler.runFor(1000);check(UnifiedInitService.firstMapPending(),"native readiness still requires the user's setup confirmation");
   UnifiedInitService.completeFirstMap();musicReady=true;Handler.runFor(2000);
   check(homes()==1,"retained first-use surfaces finish through normal real readiness admission");
  }else if(name.equals("screen-off-resume")){
   initialize(false);for(BroadcastReceiver r:new java.util.ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));
   mapReady=musicReady=true;Handler.runFor(2000);
   check(UnifiedInitService.active()&&homes()==0,"screen-off retains the existing startup owner without admitting desktop");
   check(!UnifiedInitService.startupWindowState().getBoolean("allowed"),"sleep also blocks deferred native Activity transitions even before focus callbacks arrive");
   check(!UnifiedInitService.consumeInterruptedEntry(),"retained owner does not create a competing startup sequence");
   for(BroadcastReceiver r:new java.util.ArrayList<>(Env.receivers))r.onReceive(service,new Intent("android.intent.action.SCREEN_ON"));
   Handler.runFor(2000);check(homes()==1&&!UnifiedInitService.active(),"owned native foreground automatically resumes after wake and confirms both real interfaces");
  }else if(name.equals("screen-on-external")){
   initialize(false);for(BroadcastReceiver r:new java.util.ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));
   Env.top="external.reverse.camera";mapReady=musicReady=true;Handler.runFor(2000);long before=Env.launches.size();
   for(BroadcastReceiver r:new java.util.ArrayList<>(Env.receivers))r.onReceive(service,new Intent("android.intent.action.SCREEN_ON"));
   Handler.runFor(60000);check(homes()==0&&Env.launches.size()==before,"screen-on never opens a window while camera still owns foreground");
   Env.top=Config.MAP;com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(2000);
   check(homes()==1,"camera release after wake refreshes both proofs and completes the same startup");
  }else if(name.equals("screen-on-unready")){
   initialize(false);for(BroadcastReceiver r:new java.util.ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SCREEN_OFF));
   Handler.runFor(1000);for(BroadcastReceiver r:new java.util.ArrayList<>(Env.receivers))r.onReceive(service,new Intent("android.intent.action.SCREEN_ON"));
   Handler.runFor(10000);check(homes()==0&&UnifiedInitService.active(),"waking up cannot substitute for actual engine readiness");
   mapReady=musicReady=true;Handler.runFor(2000);check(homes()==1,"late native readiness after wake permits one normal desktop admission");
  }else if(name.equals("shutdown-cancels")){
   initialize(false);for(BroadcastReceiver r:new java.util.ArrayList<>(Env.receivers))r.onReceive(service,new Intent(Intent.ACTION_SHUTDOWN));
   mapReady=musicReady=true;Handler.runFor(60000);check(!UnifiedInitService.active()&&homes()==0,"actual shutdown releases startup and prevents all late desktop returns");
  }else if(name.equals("late-local-ready")){
   initialize(false);Handler.runFor(52000);check(UnifiedInitService.active(),"slow offline initialization keeps recoverable owner beyond original 45 seconds");
   mapReady=musicReady=true;Handler.runFor(5000);check(homes()==1,"late actual native completion automatically enters without a retry dialog");
  }else if(name.equals("map-loss-during-music")){
   mapReady=true;initialize(false);Handler.runFor(500);mapReady=false;map.kill();
   Env.top="external.reverse.camera";Handler.runFor(1500);Env.top="cn.windchaser.unified";
   com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();Handler.runFor(3500);
   check(launches(Config.MAP)==1,"a lost already-passed map stage is repaired when returning, without waiting forever in final verification");
   map=new Messenger(new Handler());mapReady=musicReady=true;Handler.runFor(2500);check(homes()==1,"replacement map endpoint must prove actual readiness before entry");
  }else if(name.equals("no-foreground-retry")){
   initialize(false);Env.top="external.reverse.camera";long before=Env.launches.size();Handler.runFor(180000);
   check(Env.launches.size()==before&&homes()==0,"long camera interruption cannot trigger a recovery Activity or desktop");
   Env.top="cn.windchaser.unified";com.morgoo.droidplugin.unified.StartupWindowState.focusChanged();mapReady=musicReady=true;Handler.runFor(2500);
   check(homes()==1,"camera release rechecks live interfaces and completes automatically");
  }else if(name.equals("no-fake-ready")){
   initialize(false);Handler.runFor(180000);check(homes()==0&&!UnifiedInitService.admitted(),"recovery and elapsed time never fake unavailable engines");
   check(launches(Config.MAP)<=4&&launches(Config.MUSIC)==0,"unready foreground engine is not opened in a busy restart loop");
   int before=mapProbes+qqProbes;Handler.runFor(30000);check(mapProbes+qqProbes-before<80,"long unresolved native failures use bounded observation backoff");
  }else if(name.equals("consent-not-bypassed")){
   mapReady=musicReady=true;initialize(true);Handler.runFor(10000);
   check(homes()==0&&UnifiedInitService.firstMapPending(),"offline recovery cannot bypass original first-use consent");
   UnifiedInitService.completeFirstMap();Handler.runFor(2000);check(homes()==1,"user-completed setup and genuine interfaces enter normally");
  }else throw new IllegalArgumentException(name);
  System.out.println("RESULT resilience-"+name+" checks="+checks+" PASS (production service/coordinator; simulated OS/native boundaries)");
 }
}
