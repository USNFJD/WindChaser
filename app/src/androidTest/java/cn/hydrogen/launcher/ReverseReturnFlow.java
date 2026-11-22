package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import com.morgoo.droidplugin.unified.StartupWindowState;
import java.io.*;
import java.lang.reflect.Field;

/** Test-only: suppress a real desktop delivery, then interrupt its retained owner. */
final class ReverseReturnFlow {
    private final Instrumentation test;private final Context context;
    private int checks;
    ReverseReturnFlow(Instrumentation value){test=value;context=test.getTargetContext();}
    private void require(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
    private Object state(String name)throws Exception{
        Field f=UnifiedInitService.class.getDeclaredField("owner");f.setAccessible(true);Object owner=f.get(null);
        if(owner==null)return null;Field member=UnifiedInitService.class.getDeclaredField(name);member.setAccessible(true);return member.get(owner);
    }
    private void mark(String value)throws Exception{
        try(FileOutputStream out=new FileOutputStream(new File(context.getFilesDir(),"reverse-return-stage"))){out.write(value.getBytes("UTF-8"));}
    }
    private void await(java.util.concurrent.Callable<Boolean> condition,long ms)throws Exception{
        long end=SystemClock.elapsedRealtime()+ms;while(SystemClock.elapsedRealtime()<end){if(condition.call())return;Thread.sleep(50);}throw new AssertionError("timed out waiting for reverse return stage");
    }
    void run(){
        Instrumentation.ActivityMonitor blocked=null;Bundle result=new Bundle();
        try{
            await(()->UnifiedInitService.admitted()&&!UnifiedInitService.active(),15000);
            require(StartupWindowState.foreground(context.getPackageName()),"actual desktop window owns focus");
            blocked=test.addMonitor(MainActivity.class.getName(),new Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null),true);
            test.runOnMainSync(()->{UnifiedInitService.newEntry();UnifiedInitService.request(context);});
            await(()->Boolean.TRUE.equals(state("awaitingDesktop")),15000);
            require(UnifiedInitService.active(),"suppressed real startActivity retains initialization owner");
            require(((Integer)state("desktopAttempts"))>=1,"real verified desktop return was attempted");
            mark("desktop-delivery-blocked");
            await(()->!StartupWindowState.allowed(),20000);
            Thread.sleep(2500);
            require(UnifiedInitService.active(),"camera focus loss retains pending return");
            require(!StartupWindowState.foreground(context.getPackageName()),"external camera never counts as desktop focus");
            int attempts=(Integer)state("desktopAttempts");Thread.sleep(1500);
            require(((Integer)state("desktopAttempts"))==attempts,"pending return never retries over camera");
            test.removeMonitor(blocked);blocked=null;mark("camera-priority-confirmed");
            await(()->UnifiedInitService.admitted()&&!UnifiedInitService.active(),25000);
            require(StartupWindowState.foreground(context.getPackageName()),"desktop focus confirmed after camera ends");
            Thread.sleep(1500);require(!UnifiedInitService.active(),"acknowledged return owner is released once");
            mark("complete");result.putString("stream","RESULT reverse-return checks="+checks+" PASS\n");test.finish(Activity.RESULT_OK,result);
        }catch(Throwable error){
            try{mark("failed: "+error);}catch(Exception ignored){}
            result.putString("stream","FAIL reverse-return after "+checks+" checks: "+error);test.finish(Activity.RESULT_CANCELED,result);
        }finally{if(blocked!=null)test.removeMonitor(blocked);}
    }
}
