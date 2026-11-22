package cn.hydrogen.launcher;
import android.app.*;import android.os.*;import android.content.*;
/** Exercises actual signed guest probes after production admission; never sets ready flags. */
final class StartupReadinessFlow {
 private final Instrumentation test;private final Context app;
 StartupReadinessFlow(Instrumentation value){test=value;app=value.getTargetContext();}
 void run(){Bundle result=new Bundle();try{
  long until=SystemClock.elapsedRealtime()+15000;boolean[] accepted={false};
  while(SystemClock.elapsedRealtime()<until){test.runOnMainSync(()->accepted[0]=UnifiedInitService.admitted()&&!UnifiedInitService.active());if(accepted[0])break;Thread.sleep(100);}
  if(!accepted[0])throw new AssertionError("focused desktop acknowledgment missing: "+UnifiedInitService.status());
  boolean[] answered={false,false},ready={false,false};
  test.runOnMainSync(()->{LinkedAppCoordinator c=LinkedAppCoordinator.get(app);
   c.awaitInitializing(Config.MAP,value->{ready[0]=value;answered[0]=true;});
   c.awaitInitializing(Config.MUSIC,value->{ready[1]=value;answered[1]=true;});});
  until=SystemClock.elapsedRealtime()+6000;
  while(SystemClock.elapsedRealtime()<until){boolean[] done={false};test.runOnMainSync(()->done[0]=answered[0]&&answered[1]);if(done[0])break;Thread.sleep(50);}
  final boolean[] live={false};test.runOnMainSync(()->{LinkedAppCoordinator c=LinkedAppCoordinator.get(app);live[0]=c.liveEndpoint(Config.MAP)&&c.liveEndpoint(Config.MUSIC);});
  if(!answered[0]||!answered[1]||!ready[0]||!ready[1]||!live[0])throw new AssertionError("actual map/music IPC not ready: map="+ready[0]+", music="+ready[1]+", live="+live[0]);
  result.putString("stream","RESULT startup-readiness checks=5 PASS (production admission, actual bundled native IPC)\n");test.finish(Activity.RESULT_OK,result);
 }catch(Throwable error){result.putString("stream","FAIL startup-readiness: "+error);test.finish(Activity.RESULT_CANCELED,result);}}
}
