package cn.hydrogen.launcher;
import android.app.*;import android.content.*;import android.os.*;import java.lang.reflect.*;import java.util.concurrent.*;
/** Actual admitted endpoints and Android HOME/native-app returns. No synthetic readiness. */
final class StartupReturnAuditFlow {
 private final Instrumentation test;private MainActivity home;private int passed,visuals;private final StringBuilder report=new StringBuilder("Actual native interfaces and warm desktop return\n");
 StartupReturnAuditFlow(Instrumentation i){test=i;}
 interface Condition{boolean get()throws Exception;}
 private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private void main(Runnable run){Throwable[] error={null};test.runOnMainSync(()->{try{run.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);}
 private void require(boolean yes,String why){if(!yes)throw new AssertionError(why);passed++;report.append("PASS ").append(why).append('\n');}
 private void waitFor(String name,Condition c,long budget)throws Exception{long end=SystemClock.elapsedRealtime()+budget;while(!c.get()&&SystemClock.elapsedRealtime()<end)Thread.sleep(80);require(c.get(),name);}
 private boolean focused(){final boolean[] value={false};main(()->value[0]=home.hasWindowFocus());return value[0];}
 private void proof(String pkg)throws Exception{CountDownLatch done=new CountDownLatch(1);boolean[] live={false};main(()->LinkedAppCoordinator.get(home).awaitInitializing(pkg,r->{live[0]=r;done.countDown();}));require(done.await(3,TimeUnit.SECONDS)&&live[0],"actual ready endpoint: "+pkg);}
 private void ready()throws Exception{proof(Config.MAP);proof(Config.MUSIC);}
 private void launchEntry(Context c){main(()->c.startActivity(new Intent(c,UnifiedBootstrapActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION)));}
 void run(){Context c=test.getTargetContext();Bundle result=new Bundle();boolean success=false;TestPreferences saved=new TestPreferences(c);
 Application app=(Application)c.getApplicationContext();Application.ActivityLifecycleCallbacks observer=new Application.ActivityLifecycleCallbacks(){
  public void onActivityCreated(Activity a,Bundle b){}
  public void onActivityStarted(Activity a){if(a instanceof UnifiedBootstrapActivity)try{if(field(a,"progress")!=null)visuals++;}catch(Exception e){throw new AssertionError(e);}}public void onActivityResumed(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
 };
 try{Config cfg=new Config(c);cfg.put("amapFloating",true);cfg.put("pageAnimation",true);cfg.put("homePage",0);
  home=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitFor("desktop focus after actual admission",this::focused,12000);ready();
  com.morgoo.droidplugin.pm.PluginManager manager=com.morgoo.droidplugin.pm.PluginManager.getInstance();
  require(manager.getPackageProcessState(Config.MAP)==1,"private runtime retains running map state");
  require(manager.getPackageProcessState(Config.MUSIC)==1,"private runtime retains running music state");
  require(manager.getPackageProcessState("fixture.unknown.package")==-1,"unknown package is inconclusive, never reported cold");
  require(!UnifiedRuntime.definitelyNotRunning(Config.MAP),"running map cannot take cold-launch shortcut");
  app.registerActivityLifecycleCallbacks(observer);Object owner=MapWorkspace.get(c);Object map=field(home,"floatingMap");main(()->home.showPage(MainActivity.CARDS));Thread.sleep(300);
  waitFor("native map card connected before returns",()->((AmapFloatingController)map).connected(),15000);
  int animations=(Integer)field(home,"pageAnimationStarts");
  main(()->c.startActivity(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)));Thread.sleep(250);
  require((Integer)field(home,"pageAnimationStarts")==animations,"HOME/default-page restoration adds no page animation");
  require(home.visiblePage()==0,"explicit desktop return keeps configured home page");
  main(()->home.showPage(MainActivity.CARDS));Thread.sleep(250);require((Integer)field(home,"pageAnimationStarts")==animations+1,"intentional desktop swipe/page change retains animation");
  for(Class<?> screen:new Class<?>[]{SettingsActivity.class,FileCenterActivity.class}){
   ready();int before=visuals;Activity a=test.startActivitySync(new Intent(c,screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main(a::finish);waitFor("direct return from "+screen.getSimpleName(),this::focused,10000);require(visuals==before&&!UnifiedInitService.active(),"internal return reuses completed initialization");
  }
  ready();main(()->c.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));Thread.sleep(400);int before=visuals;launchEntry(c);waitFor("HOME returns from external settings",this::focused,10000);require(visuals==before,"HOME entry creates no startup artwork");require(!home.isFinishing()&&MapWorkspace.get(c)==owner,"HOME entry reuses desktop and map workspace");require(!UnifiedInitService.active(),"HOME entry does not rerun initialization service");require(home.getWindow().getAttributes().windowAnimations==0,"desktop window return animation is disabled");
  for(String pkg:new String[]{Config.MAP,Config.MUSIC}){
   ready();main(()->{if(!UnifiedRuntime.launch(home,pkg))throw new AssertionError("native launch failed");});Thread.sleep(1200);proof(pkg);before=visuals;launchEntry(c);waitFor("return from actual native app "+pkg,this::focused,12000);require(visuals==before&&!UnifiedInitService.active(),"native return does not replay shared startup: "+pkg);require(MapWorkspace.get(c)==owner,"native return preserves map workspace: "+pkg);
  }
  ready();main(()->home.showPage(MainActivity.CARDS));waitFor("map card reconnects after native returns",()->((AmapFloatingController)map).connected(),15000);success=true;
 }catch(Throwable error){report.append("FAIL ").append(error).append('\n');}finally{app.unregisterActivityLifecycleCallbacks(observer);saved.close();}
 report.append("STARTUP_RETURN_RESULT checks=").append(passed).append(success?" PASS":" FAIL").append('\n');result.putString("stream",report.toString());test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
