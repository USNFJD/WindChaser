package cn.windchaser.startup;
import android.app.*;import android.os.*;import java.lang.reflect.*;import java.lang.ref.*;import java.util.*;
/** Production curtain expiry with replaceable owner IPC and Android view boundary. */
public final class CurtainRecoveryChecks {
 static int checks;
 static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;System.out.println("PASS "+why);}
 static Field field(String name)throws Exception{Field f=StartupCurtain.class.getDeclaredField(name);f.setAccessible(true);return f;}
 static void prepare()throws Exception{
  Handler.reset();field("session").set(null,"active-owner");field("until").setLong(null,SystemClock.elapsedRealtime()+45000);
  ((ArrayList)field("activities").get(null)).add(new WeakReference<Activity>(new Activity()));
  fixture.Env.state=new Bundle();fixture.Env.state.putString("session","active-owner");fixture.Env.state.putLong("remaining_ms",45000);
  Handler timer=(Handler)field("timer").get(null);timer.postDelayed((Runnable)field("expire").get(null),45000);
 }
 public static void main(String[]args)throws Exception{
  prepare();String scenario=args[0];
  if(scenario.equals("slow-owner")){
   Handler.runFor(120000);check(StartupCurtain.active(),"startup artwork remains while the actual initialization owner is active beyond old expiry");
   check(fixture.Env.calls<=7,"owner extension uses a bounded fifteen-second observation cadence");
   fixture.Env.state=new Bundle();Handler.runFor(15000);check(!StartupCurtain.active(),"released owner removes obsolete startup artwork");
  }else if(scenario.equals("camera-owner")){
   fixture.Env.state.putBoolean("allowed",false);Handler.runFor(180000);
   check(StartupCurtain.active(),"external foreground does not expire the owner's shared startup artwork");
   check(fixture.Env.launches.isEmpty()&&fixture.Env.broadcasts.isEmpty(),"curtain renewal never starts a window or displaces the external foreground");
  }else if(scenario.equals("screen-owner")){
   Method power=StartupCurtain.class.getDeclaredMethod("powerChanged",android.content.Intent.class);power.setAccessible(true);
   power.invoke(null,new android.content.Intent(android.content.Intent.ACTION_SCREEN_OFF));Handler.runFor(120000);
   check(StartupCurtain.active(),"sleep preserves the existing shared artwork while its actual owner remains active");
   check(fixture.Env.launches.isEmpty()&&fixture.Env.broadcasts.isEmpty(),"retaining sleeping artwork creates no Activity or overlay");
   power.invoke(null,new android.content.Intent(android.content.Intent.ACTION_SHUTDOWN));
   check(!StartupCurtain.active(),"real shutdown removes the retained artwork");
  }else if(scenario.equals("replaced-owner")){
   fixture.Env.state.putString("session","replacement");Handler.runFor(45000);check(!StartupCurtain.active(),"another initialization session cannot retain an old curtain");
  }else if(scenario.equals("unavailable-owner")){
   fixture.Env.unavailable=true;Handler.runFor(45000);check(!StartupCurtain.active(),"unavailable owner releases the expired curtain instead of covering the app forever");
  }else throw new IllegalArgumentException(scenario);
  System.out.println("RESULT curtain-"+scenario+" checks="+checks+" PASS (production curtain; simulated owner IPC)");
 }
}
