package com.morgoo.droidplugin.am;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import com.morgoo.droidplugin.unified.GuestProcessIdentity;
public final class ProcessSlotChecks{
 static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static void rejects(Runnable r,String s){try{r.run();}catch(IllegalStateException expected){checks++;return;}throw new AssertionError(s);}
 public static void main(String[]args)throws Exception{
  ProcessSlotReservations r=new ProcessSlotReservations();String m="com.autonavi.amapauto",q="com.tencent.qqmusiccar";
  check(r.canUse("P02",m,m+":push",0),"empty available");r.reserve("P02",m,m+":push",0);
  check(!r.canUse("P02",q,q,1),"QQ cannot take pending map-push slot before PID registration");check(r.canUse("P02",m,m+":push",1),"same pending native identity reuses");
  rejects(()->r.reserve("P02",q,q,1),"cross-package pending rejected");r.claim("P02",m,m+":push",200,2);
  check(!r.canUse("P02",q,q,60000),"registered live PID never expires");rejects(()->r.claim("P02",m,m+":push",201,60000),"cannot replace live PID");
  r.died(201);check(!r.canUse("P02",q,q,60001),"unrelated PID death retains reservation");r.died(200);check(r.canUse("P02",q,q,60002),"owner death releases slot");
  r.reserve("P03",m,null,10);check(r.canUse("P03",m,m,11),"default process normalized");check(!r.canUse("P03",m,m+":push",11),"main and auxiliary processes stay separate");
  check(r.canUse("P03",q,q,30010),"never-registered launch expires");r.clear();check(r.canUse("P02",q,q,60003),"clear releases reservations");
  ProcessSlotReservations state=new ProcessSlotReservations();
  check(!state.hasPackage(m,0),"empty runtime reports no pending package");
  state.reserve("P00",m,m,0);check(state.hasPackage(m,1)&&!state.hasPackage(q,1),"pending launch is never reported cold and is package-specific");
  check(state.hasPackage(m,29999)&&!state.hasPackage(m,30000),"unregistered launch retains the existing bounded reservation");
  state.claim("P00",q,q,123,30001);check(state.hasPackage(q,90000),"registered package never expires by elapsed time");
  state.died(124);check(state.hasPackage(q,90001),"unrelated process death cannot mark package cold");
  state.died(123);check(!state.hasPackage(q,90002),"registered process death releases cold-start state");
  for(int round=0;round<100;round++){
   ProcessSlotReservations pool=new ProcessSlotReservations();Object selection=new Object();CountDownLatch begin=new CountDownLatch(1);Set<String> chosen=ConcurrentHashMap.newKeySet();AtomicReference<Throwable> failure=new AtomicReference<>();List<Thread> threads=new ArrayList<>();
   for(String name:new String[]{m,m+":push",q,q+":QQPlayerProcess"}){final String pkg=name.startsWith(m)?m:q;Thread t=new Thread(()->{try{begin.await();synchronized(selection){for(int n=0;n<4;n++){String slot="P0"+n;if(pool.canUse(slot,pkg,name,0)){pool.reserve(slot,pkg,name,0);if(!chosen.add(slot))throw new AssertionError("duplicate slot "+slot);return;}}throw new AssertionError("no slot");}}catch(Throwable e){failure.set(e);}});threads.add(t);t.start();}
   begin.countDown();for(Thread t:threads)t.join();if(failure.get()!=null)throw new AssertionError(failure.get());check(chosen.size()==4,"parallel native identities receive separate slots");
  }
  GuestProcessIdentity.initialize(q,q);GuestProcessIdentity.initialize(q,q);check(q.equals(GuestProcessIdentity.packageName())&&q.equals(GuestProcessIdentity.processName()),"application identity stable and idempotent");
  rejects(()->GuestProcessIdentity.initialize(m,m),"live Application cannot switch package");rejects(()->GuestProcessIdentity.initialize(q,q+":QQPlayerProcess"),"live Application cannot switch process");
  System.out.println("RESULT process-slot checks="+checks+" PASS; 100 concurrent four-process selections");
 }
}
