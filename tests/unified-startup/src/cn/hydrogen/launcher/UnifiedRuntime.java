package cn.hydrogen.launcher;
import android.content.*;
public final class UnifiedRuntime {
 static long lastUntil;static boolean coldMap;static boolean definitelyNotRunning(String pkg){return coldMap&&Config.MAP.equals(pkg);}static boolean ready(){return true;}
 static boolean launch(Context c,String pkg,String session,long until){
  lastUntil=until;c.startActivity(new Intent().setComponent(new ComponentName(pkg,"fixture.Starter")));return true;
 }
}
