package cn.windchaser.startup;import java.util.concurrent.*;public class QqControlReadiness {
 static volatile boolean result=true,available=true;static volatile int calls;static final CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
 static boolean ready(ClassLoader l){calls++;entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new AssertionError("fixture IPC release missing");}catch(InterruptedException e){throw new RuntimeException(e);}return result;}
 static boolean available(ClassLoader l){return available;}
}