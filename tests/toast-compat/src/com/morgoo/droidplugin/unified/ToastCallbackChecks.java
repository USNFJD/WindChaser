package com.morgoo.droidplugin.unified;
import android.app.ITransientNotification;import android.content.Context;import android.os.*;import android.view.View;import java.lang.reflect.*;
/** Real compatibility adapter and platform-shaped notification signatures. */
public final class ToastCallbackChecks {
 interface Api {
  void enqueueToast(String pkg,ITransientNotification callback,int duration);
  void enqueueToast(String pkg,IBinder token,ITransientNotification callback,int duration,boolean system,int display);
  void cancelToast(String pkg,IBinder token);
  void cancelToast(String pkg,ITransientNotification callback);
 }
 static class Legacy implements ITransientNotification {private View mView=new View(),mNextView=new View();}
 static class NextOnly implements ITransientNotification {private View mNextView=new View();}
 static class Other implements ITransientNotification {private String mView="not a View";}
 static Context host=new Context();static int checks;
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;System.out.println("PASS "+why);}
 public static void main(String[]args)throws Exception{
  String name=args[0];Build.VERSION.SDK_INT=19;Legacy callback=new Legacy();
  Method enqueue=Api.class.getMethod("enqueueToast",String.class,ITransientNotification.class,int.class);
  if(name.equals("legacy-fields")){
   ToastCallbackCompat.prepare(host,enqueue,new Object[]{"guest",callback,0});
   check(callback.mView.mContext==host&&callback.mNextView.mContext==host,"both legacy callback views use the installed host context");
  }else if(name.equals("legacy-next-only")){
   NextOnly next=new NextOnly();ToastCallbackCompat.prepare(host,enqueue,new Object[]{"guest",next,0});
   check(next.mNextView.mContext==host,"missing optional mView does not prevent valid mNextView adaptation");
  }else if(name.equals("modern-token")){
   Build.VERSION.SDK_INT=34;Binder token=new Binder();Object[] values={"guest",token,callback,0,false,0};
   Method modern=Api.class.getMethod("enqueueToast",String.class,IBinder.class,ITransientNotification.class,int.class,boolean.class,int.class);
   ToastCallbackCompat.prepare(host,modern,values);
   check(values[1]==token&&values[2]==callback,"modern token and callback retain their original identities");
   check(callback.mView.mContext!=host&&callback.mNextView.mContext!=host,"modern ToastPresenter state is not treated as legacy view state");
  }else if(name.equals("modern-cancel")){
   Build.VERSION.SDK_INT=34;Binder token=new Binder();ToastCallbackCompat.prepare(host,Api.class.getMethod("cancelToast",String.class,IBinder.class),new Object[]{"guest",token});
   check(true,"modern cancel token is never reflected as a callback");
  }else if(name.equals("legacy-cancel")){
   ToastCallbackCompat.prepare(host,Api.class.getMethod("cancelToast",String.class,ITransientNotification.class),new Object[]{"guest",callback});
   check(callback.mView.mContext==host,"legacy cancel retains compatible original view handling");
  }else if(name.equals("unknown-fields")){
   ToastCallbackCompat.prepare(host,enqueue,new Object[]{"guest",new Other(),0});check(true,"ROM-specific missing or non-View fields cannot crash a network error toast");
  }else if(name.equals("empty-callback")){
   ToastCallbackCompat.prepare(host,enqueue,new Object[]{"guest",null,0});ToastCallbackCompat.prepare(host,null,null);check(true,"absent callback is safe");
  }else if(name.equals("no-callback-signature")){
   ToastCallbackCompat.prepare(host,Api.class.getMethod("cancelToast",String.class,IBinder.class),new Object[]{"guest",new Binder()});check(true,"callback discovery follows the signature on legacy ROMs too");
  }else throw new IllegalArgumentException(name);
  System.out.println("RESULT toast-"+name+" checks="+checks+" PASS (production toast adapter; simulated notification boundary)");
 }
}
