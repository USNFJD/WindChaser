package cn.hydrogen.launcher;
import android.content.*;import android.os.*;import android.app.*;import android.view.*;
import cn.windchaser.startup.RuntimeProbe;import cn.windchaser.mapbridge.MapInterfaceReadiness;
import com.autonavi.amapauto.jni.protocol.AndroidProtocolExe;import fixture.Env;import java.util.*;
public final class ResourceReuseChecks {
 static int checks;static Context qq;static List<Message> answers=new ArrayList<>();
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static void deliver(Intent i){for(BroadcastReceiver r:new ArrayList<>(Env.receivers))r.onReceive(qq,i);}
 static Messenger client(){return new Messenger(new Handler(Looper.getMainLooper()){@Override public void handleMessage(Message m){answers.add(m);}});}
 static void request(String nonce,Messenger c){deliver(new Intent(RuntimeProbe.ACTION).putExtra("protocol",1).putExtra("nonce",nonce).putExtra("issued",SystemClock.elapsedRealtime()).putExtra("client",c));}
 static void state(){deliver(new Intent("com.tencent.qqmusiccar.action.PLAY_COMMAND_SEND_FOR_THIRD").putExtra("com.tencent.qqmusiccar.EXTRA_COMMAND_DATA","{\"module\":\"play\",\"command\":{\"method\":\"update_state\",\"data\":{\"state\":1}}}"));}
 public static void main(String[] args)throws Exception{
  Env.reset();String scenario=args[0];qq=new Context();qq.pkg="com.tencent.qqmusiccar";android.os.Process.name=qq.pkg;
  if(scenario.equals("map-method-reuse")){
   Activity map=new Activity();map.pkg="com.autonavi.amapauto";SurfaceView a=new SurfaceView(),b=new SurfaceView();a.parent=new ViewGroup();b.parent=new ViewGroup();
   AndroidProtocolExe.ready=true;check(MapInterfaceReadiness.ready(map,a,b),"ready native state accepted");
   java.lang.reflect.Field f=MapInterfaceReadiness.class.getDeclaredField("nativeReady");f.setAccessible(true);Object method=f.get(null);
   AndroidProtocolExe.ready=false;check(!MapInterfaceReadiness.ready(map,a,b),"method reuse must not cache readiness value");AndroidProtocolExe.ready=true;check(MapInterfaceReadiness.ready(map,a,b)&&f.get(null)==method,"same method reused while native state stays live");
  }else{
   RuntimeProbe.install(qq);RuntimeProbe.install(qq);check(Env.receivers.size()==2,"installation remains idempotent");Messenger a=client(),b=client();
   request("one",a);Handler.runFor(100);request("two",b);request("two",b);check(Env.broadcasts.size()==1,"concurrent native subscriptions coalesced and retry deduplicated");
   if(scenario.equals("probe-shared-success")){
    state();Handler.runFor(0);check(answers.size()==2,"both clients replied exactly once");check(answers.get(0).getData().getBoolean("ready")&&answers.get(1).getData().getBoolean("ready"),"same live native proof served both clients");
    check(!answers.get(0).getData().getString("nonce").equals(answers.get(1).getData().getString("nonce")),"independent correlation preserved");Handler.runFor(500);check(answers.size()==2,"successful batch cancels its timeout");request("three",a);check(Env.broadcasts.size()==2,"later request starts a fresh native query");Handler.runFor(350);check(answers.size()==3&&!answers.get(2).getData().getBoolean("ready"),"later timeout does not reuse stale proof");
   }else if(scenario.equals("probe-shared-timeout")){
    Handler.runFor(249);check(answers.isEmpty(),"no early timeout");Handler.runFor(1);check(answers.size()==2,"original 350ms deadline serves every waiter");check(!answers.get(0).getData().getBoolean("ready")&&!answers.get(1).getData().getBoolean("ready"),"timeout cannot manufacture ready state");state();Handler.runFor(0);check(answers.size()==2,"late native state cannot revive completed queries");
   }else if(scenario.equals("probe-bounded-waiters")){
    for(int n=0;n<100;n++)request("extra-"+n,a);Handler.runFor(0);check(Env.broadcasts.size()==1,"many waiters share one native subscription");state();Handler.runFor(0);check(answers.size()==102,"all accepted or overflow requests completed");Handler.runFor(500);check(answers.size()==102,"no retained callbacks after completion");
   }else throw new AssertionError("unknown scenario");
   check(Env.launches.isEmpty()&&Env.serviceStarts.isEmpty(),"reuse never starts an application or service");
  }
  System.out.println("PASS "+scenario+" checks="+checks+" (production helpers on JVM Android stubs)");
 }
}
