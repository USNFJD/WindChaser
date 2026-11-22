package com.morgoo.droidplugin.unified;
import android.content.*;import android.os.*;import fixture.Env;
public final class StartupActivityGateChecks {
 static int checks;static Context host=new Context();
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
 static Intent home(boolean map){return new Intent().setComponent(new ComponentName(map?"com.autonavi.amapauto":"com.tencent.qqmusiccar",map?"com.autonavi.amapauto.MainMapActivity":"com.tencent.qqmusiccar.v2.activity.home.MainActivity")).putExtra("windchaser.init.session","test-session").putExtra("windchaser.init.until",SystemClock.now+45000);}
 static boolean start(Intent intent,int request){return StartupActivityGate.beforeStart(host,new Object[]{null,"guest",intent,null,null,null,request,0});}
 public static void main(String[] args){
  Handler.reset();Env.state.putString("session","test-session");Env.state.putBoolean("allowed",false);Env.state.putLong("remaining_ms",12345);
  String scenario=args[0];Intent intent=home(scenario.equals("map"));
  if(scenario.equals("music")||scenario.equals("map")){
   check(start(intent,-1),"native home transition is deferred behind external camera");Handler.runFor(8000);
   check(Env.launches.isEmpty(),"camera foreground never receives a native home launch");Env.state.putBoolean("allowed",true);Handler.runFor(300);
   check(Env.launches.size()==1,"owned focus resumes exactly one native home");Handler.runFor(3000);check(Env.launches.size()==1,"completed handoff removes retry polling");
  }else if(scenario.equals("deduplicate")){
   start(intent,-1);start(new Intent(intent),-1);Env.state.putBoolean("allowed",true);Handler.runFor(500);
   check(Env.launches.size()==1,"repeated same-home initialization requests are coalesced");
  }else if(scenario.equals("cancelled")||scenario.equals("new-session")){
   start(intent,-1);Env.state.putString("session",scenario.equals("cancelled")?null:"replacement");Handler.runFor(500);Env.state.putBoolean("allowed",true);Handler.runFor(5000);
   check(Env.launches.isEmpty(),"cancelled or replaced startup never replays an old native home");
  }else if(scenario.equals("contracts")){
   check(!start(new Intent().setComponent(intent.getComponent()),-1),"ordinary home navigation remains immediate");
   check(!start(intent,42),"start-for-result contract is not converted to a detached task");
   check(!start(new Intent(intent).setComponent(new ComponentName("com.tencent.qqmusiccar","guest.Settings")),-1),"settings and other native dialogs remain unchanged");
   Env.state.putBoolean("allowed",true);check(!start(intent,-1),"owned foreground startup has no extra handoff delay");
  }else if(scenario.equals("permission-result")||scenario.equals("permission-host-caller")){
   Object token=new Object();Intent request=new Intent("android.content.pm.action.REQUEST_PERMISSIONS").putExtra("native_payload","retained");
   Object[] original={null,scenario.equals("permission-host-caller")?"cn.windchaser.unified":"com.tencent.qqmusiccar",request,null,token,"result-who",7,0};
   Env.guestProcess=scenario.equals("permission-host-caller");final Object[][] replayed={null};
   check(StartupActivityGate.beforeStart(host,original,argsCopy->replayed[0]=argsCopy),"native permission dialog is held while camera owns foreground");
   Handler.runFor(3000);check(replayed[0]==null,"no permission dialog appears over camera");
   Env.state.putBoolean("allowed",true);Handler.runFor(300);
   check(replayed[0]!=null&&replayed[0][4]==token&&"result-who".equals(replayed[0][5])&&((Integer)replayed[0][6])==7,"permission replay retains original result token and request code");
   check(replayed[0][2]!=request&&"retained".equals(((Intent)replayed[0][2]).getStringExtra("native_payload")),"permission Intent payload is copied without mutating the caller");
  }else if(scenario.equals("late-replay-cancelled")){
   final int[] resumed={0};Object[] permissionArgs={null,"com.tencent.qqmusiccar",new Intent("android.content.pm.action.REQUEST_PERMISSIONS"),null,new Object(),null,3};
   StartupActivityGate.beforeStart(host,permissionArgs,copy->{Env.state.putString("session",null);if(!StartupActivityGate.beforeStart(host,copy,ignored->{}))resumed[0]++;});
   Env.state.putBoolean("allowed",true);Handler.runFor(500);
   check(resumed[0]==0,"permission session tag prevents a late queued replay after startup cancellation");
   check(start(intent,-1),"stale session-tagged home is consumed rather than foregrounded after cancellation");
  }else if(scenario.equals("permission-host-ordinary")){
   Intent request=new Intent("android.content.pm.action.REQUEST_PERMISSIONS");Object[] argsCopy={null,"cn.windchaser.unified",request,null,new Object(),null,9};
   check(!StartupActivityGate.beforeStart(host,argsCopy,copy->{}),"host first-use permission requests keep their original contract");
  }else if(scenario.equals("broker-unavailable")){
   Env.unavailable=true;check(start(intent,-1),"missing private broker cannot foreground a session-tagged home");Handler.runFor(3000);check(Env.launches.isEmpty(),"broker failure does not become permission to steal foreground");
   Env.unavailable=false;Env.state.putBoolean("allowed",true);Handler.runFor(300);check(Env.launches.size()==1,"recovered broker and owned focus resume pending native home");
  }else if(scenario.equals("long-camera")){
   long deadline=intent.getLongExtra("windchaser.init.until",0);start(intent,-1);Handler.runFor(90000);check(Env.launches.isEmpty(),"long reversing does not launch behind an expired visual timer");
   Env.state.putBoolean("allowed",true);Handler.runFor(300);check(Env.launches.size()==1,"native initialization continues after long camera interval");
   check(Env.launches.get(0).getLongExtra("windchaser.init.until",0)>SystemClock.now,"resumed home receives remaining active startup budget");
   check(intent.getLongExtra("windchaser.init.until",0)==deadline,"queued replay never mutates original native Intent");
  }else throw new IllegalArgumentException(scenario);
  System.out.println("RESULT startup-activity-gate "+scenario+" checks="+checks+" PASS");
 }
}
