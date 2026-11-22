package cn.windchaser.startup;
import android.content.*;import android.os.*;import android.app.*;import fixture.Env;import java.util.*;import java.util.concurrent.*;
public class LiveProbeChecks {
 static Context app=new Context();static List<Message> replies=new ArrayList<>();static int checks;
 static Messenger client=new Messenger(new Handler(){public void handleMessage(Message m){replies.add(m);}});
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why+" @"+SystemClock.now);checks++;System.out.println("PASS "+why);}
 static void setup(String pkg){Handler.reset();app.pkg=pkg;android.os.Process.name=pkg;Env.services.put(Context.ACTIVITY_SERVICE,new ActivityManager());RuntimeProbe.install(app);check(!Env.receivers.isEmpty(),"main-process receiver installed");}
 static void send(String nonce,int wait){Intent i=new Intent(RuntimeProbe.ACTION).putExtra("protocol",1).putExtra("nonce",nonce).putExtra("issued",SystemClock.now).putExtra("client",client).putExtra("wait_ms",wait);Env.receivers.get(0).onReceive(app,i);Handler.runFor(0);}
 static boolean accepted(int n){return replies.get(n).getData().getBoolean("ready");}
 static void worker()throws Exception{check(QqControlReadiness.entered.await(1,TimeUnit.SECONDS),"original read-only control worker started");}
 static void release()throws Exception{QqControlReadiness.release.countDown();long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);int before=replies.size();do{Handler.runFor(0);if(replies.size()>before)return;Thread.sleep(1);}while(System.nanoTime()<end);Handler.runFor(0);}
 public static void main(String[]args)throws Exception{
  String name=args[0];boolean map=name.startsWith("map");setup(map?"com.autonavi.amapauto":"com.tencent.qqmusiccar");
  if(name.equals("map-wait")){send("map",900);Handler.runFor(449);check(replies.isEmpty(),"unready map is never accepted");cn.windchaser.mapbridge.MapCardBridge.ready=true;Handler.runFor(1);check(replies.size()==1&&accepted(0),"map readiness responds at the next local check without a full timeout");check(cn.windchaser.mapbridge.MapCardBridge.reads<=5,"bounded map checks avoid broadcast loops");}
  else if(name.equals("map-timeout")){send("map",900);Handler.runFor(900);check(replies.size()==1&&!accepted(0),"map expiry is an explicit negative reply");int reads=cn.windchaser.mapbridge.MapCardBridge.reads;Handler.runFor(10000);check(reads==cn.windchaser.mapbridge.MapCardBridge.reads,"no polling remains after expiry");}
  else if(name.equals("map-first-use")){
   StartupCurtain.firstUse=true;send("starting",0);
   check(!accepted(0)&&!replies.get(0).getData().getBoolean("first_setup"),"a measured first-use window alone cannot request handoff");
   cn.windchaser.mapbridge.MapCardBridge.ready=true;send("ready",0);
   check(accepted(1)&&replies.get(1).getData().getBoolean("first_setup"),"an initialized native map can request first-use handoff");
   StartupCurtain.firstUse=false;send("returning",0);
   check(accepted(2)&&!replies.get(2).getData().getBoolean("first_setup"),"returning starts never request first-use handoff");
  }
  else if(name.equals("map-delayed-delivery")){
   Handler.runFor(3000);long issued=SystemClock.now-2500;
   Intent i=new Intent(RuntimeProbe.ACTION).putExtra("protocol",1).putExtra("nonce","slow-delivery").putExtra("issued",issued).putExtra("client",client).putExtra("max_age_ms",4000).putExtra("wait_ms",900);
   Env.receivers.get(0).onReceive(app,i);Handler.runFor(450);check(replies.isEmpty(),"startup native wait starts on receipt after slow OS delivery");
   cn.windchaser.mapbridge.MapCardBridge.ready=true;Handler.runFor(150);
   check(replies.size()==1&&accepted(0),"genuine native readiness within the received request budget succeeds");
   check(replies.get(0).getData().getLong("observed")==SystemClock.now&&replies.get(0).getData().getLong("issued")==issued,"proof records actual native observation and retains original nonce issue time");
  }
  else if(name.equals("map-old-passive-request")){
   Handler.runFor(3000);cn.windchaser.mapbridge.MapCardBridge.ready=true;
   Intent i=new Intent(RuntimeProbe.ACTION).putExtra("protocol",1).putExtra("nonce","old-passive").putExtra("issued",SystemClock.now-2500).putExtra("client",client);
   Env.receivers.get(0).onReceive(app,i);Handler.runFor(1000);check(replies.isEmpty(),"ordinary passive request retains its stricter age limit");
  }
  else if(name.equals("map-bounded-request-age")){
   Handler.runFor(5000);cn.windchaser.mapbridge.MapCardBridge.ready=true;
   Intent i=new Intent(RuntimeProbe.ACTION).putExtra("protocol",1).putExtra("nonce","too-old").putExtra("issued",SystemClock.now-4001).putExtra("client",client).putExtra("max_age_ms",999999);
   Env.receivers.get(0).onReceive(app,i);Handler.runFor(1000);check(replies.isEmpty(),"request cannot extend the signed startup age beyond four seconds");
  }
  else if(name.equals("qq-slow")){send("qq",900);worker();Handler.runFor(700);check(replies.isEmpty(),"700ms player IPC is allowed to finish within startup budget");release();check(replies.size()==1&&accepted(0),"slow actual control result proves readiness");check(QqControlReadiness.calls==1&&Env.broadcasts.stream().filter(i->"com.tencent.qqmusiccar.action".equals(i.getAction())).count()==1,"one control worker and one subscription");}
  else if(name.equals("qq-content")){QqHome.ready=false;send("qq",900);Handler.runFor(300);check(QqControlReadiness.calls==0&&replies.isEmpty(),"unmeasured music home cannot become ready");QqHome.ready=true;Handler.runFor(150);worker();release();check(replies.size()==1&&accepted(0),"home measurement plus real control result permits readiness");}
  else if(name.equals("qq-coalesced")){send("old",900);worker();Handler.runFor(200);send("new",900);Handler.runFor(700);check(replies.size()==1&&!accepted(0),"each nonce expires independently");check(QqControlReadiness.calls==1&&Env.broadcasts.stream().filter(i->"com.tencent.qqmusiccar.action".equals(i.getAction())).count()==1,"overlapping requests share one original IPC and subscription");release();check(replies.size()==2&&accepted(1),"new unexpired ticket can accept the shared real response");}
  else if(name.equals("qq-expired")){send("qq",900);worker();Handler.runFor(1000);check(replies.size()==1&&!accepted(0),"slow IPC beyond deadline remains a failure");release();check(replies.size()==1,"late worker cannot produce a second or positive response");}
  else if(name.equals("qq-normal")){send("qq",0);worker();Handler.runFor(350);check(replies.size()==1&&!accepted(0),"passive non-startup probe retains the original 350ms budget");release();check(replies.size()==1,"passive timeout ignores late IPC too");}
  else if(name.equals("qq-invalid")){client.kill();send("qq",900);Handler.runFor(1000);check(replies.isEmpty()&&QqControlReadiness.calls==0,"dead client cannot schedule work");}
  else throw new IllegalArgumentException(name);
  System.out.println("RESULT "+name+" checks="+checks+" PASS (production RuntimeProbe, simulated native boundaries)");
 }
}
