package cn.hydrogen.launcher;
import android.content.Context;import java.util.*;import java.lang.reflect.*;import java.lang.ref.WeakReference;
public final class MemoryChecks {
 static int checks;static void check(boolean v,String why){if(!v)throw new AssertionError(why);checks++;}
 static Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
 static Object shared()throws Exception{Field f=MusicSession.class.getDeclaredField("shared");f.setAccessible(true);return f.get(null);}
 public static void main(String[] args)throws Exception{
  Context app=new Context(),activity=new Context(app);String scenario=args[0];
  if(scenario.equals("sharing")){
   MusicSession.Lease a=MusicSession.acquire(activity,()->{}),b=MusicSession.acquire(activity,()->{});
   check(a.bridge()==b.bridge()&&a.bridge().cover==b.bridge().cover,"two clients must share state and one cover");check(a.bridge().context==app,"shared core must not keep Activity context");check(QqMusicBridge.created==1&&QqMusicBridge.registrations==1,"constructor-time callback and second owner create no duplicate receiver");a.close();check(b.bridge().registered,"closing one owner must not stop the other");b.close();check(shared()==null&&QqMusicBridge.stops==1,"last close releases shared registration");
  }else if(scenario.equals("clock")){
   MusicSession.Lease a=MusicSession.acquire(activity,()->{}),b=MusicSession.acquire(activity,()->{});a.resume();a.resume();b.resume();check(QqMusicBridge.starts==1,"all active owners share a single tick source");a.pause();check(QqMusicBridge.pauses==0,"one paused page cannot stop another active client");b.pause();check(QqMusicBridge.pauses==1&&b.bridge().registered,"all paused clients stop tick without discarding state registration");a.resume();check(QqMusicBridge.starts==2,"first resumed owner restarts clock exactly once");b.close();a.close();check(shared()==null,"clock has no surviving owner");
  }else if(scenario.equals("callbacks")){
   int[] updates={0,0};MusicSession.Lease a=MusicSession.acquire(activity,()->updates[0]++),b=MusicSession.acquire(activity,()->updates[1]++);a.resume();b.resume();updates[0]=updates[1]=0;a.pause();b.bridge().emit();check(updates[0]==0&&updates[1]==1,"only visible clients receive updates");a.close();b.bridge().emit();check(updates[0]==0&&updates[1]==2,"closed client callback is removed");b.close();check(shared()==null,"callbacks do not retain a singleton after last close");
  }else if(scenario.equals("reentrant-close")){
   MusicSession.Lease[] first={null};boolean[] close={false};int[] second={0};first[0]=MusicSession.acquire(activity,()->{if(close[0])first[0].close();});MusicSession.Lease b=MusicSession.acquire(activity,()->second[0]++);first[0].resume();b.resume();close[0]=true;second[0]=0;b.bridge().emit();check(second[0]==1&&b.bridge().registered,"closing during notification leaves other clients working");check(field(first[0],"callback")==null&&field(first[0],"session")==null,"closed lease releases callback and session references");b.close();check(QqMusicBridge.stops==1&&shared()==null,"reentrant close cannot double-stop or leak core");
  }else if(scenario.equals("weak-ownership")){
   MusicSession.Lease a=MusicSession.acquire(activity,()->{});Object session=shared();List<?> clients=(List<?>)field(session,"clients");check(clients.get(0) instanceof WeakReference,"singleton only weakly references UI leases");a.close();a.close();a.resume();a.pause();check(field(a,"callback")==null&&field(a,"session")==null,"closed owner keeps no UI callback");check(shared()==null&&QqMusicBridge.stops==1,"close is idempotent");MusicSession.Lease fresh=MusicSession.acquire(activity,()->{});check(QqMusicBridge.created==2,"new owner can create a clean core after release");fresh.close();
  }else if(scenario.equals("image-plans")){
   Random random=new Random(20261025);for(int i=0;i<10000;i++){int sw=1+random.nextInt(30000),sh=1+random.nextInt(30000),tw=1+random.nextInt(2000),th=1+random.nextInt(1500);ImageSizing.Plan p=ImageSizing.plan(sw,sh,tw,th,i%3);check(p.left>=0&&p.top>=0&&p.width>0&&p.height>0&&p.left+p.width<=sw&&p.top+p.height<=sh,"crop inside source");check(p.outWidth>0&&p.outHeight>0&&p.outWidth<=tw&&p.outHeight<=th,"output within measured viewport");check(p.sample>0&&(p.sample&(p.sample-1))==0,"decode uses valid power-of-two sampling");}
   ImageSizing.Plan p=ImageSizing.plan(1600,900,400,300,1);check(p.outWidth==400&&p.outHeight==225,"fit-center preserves aspect ratio");p=ImageSizing.plan(32000,1000,200,200,0);check(p.width==1000&&p.height==1000&&p.outWidth==200&&p.outHeight==200,"panorama is cropped before decoding");p=ImageSizing.plan(1600,900,320,180,2);check(p.width==320&&p.height==180&&p.sample==1,"center mode keeps original pixels");boolean invalid=false;try{ImageSizing.plan(0,2,1,1,0);}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"invalid image bounds rejected");
  }else throw new IllegalArgumentException(scenario);
  System.out.println("PASS "+scenario+" checks="+checks+" (production Java on JVM stubs; no native bitmap execution)");
 }
}
