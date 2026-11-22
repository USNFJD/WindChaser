package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.concurrent.*;
import org.json.*;

/** Real cross-UID system-overlay tests. Uses Java test surfaces, not Amap. */
final class MapSwitchFlow {
    private final Instrumentation test;
    MapSwitchFlow(Instrumentation test){this.test=test;}
    private Context getTargetContext(){return test.getTargetContext();}
    private void runOnMainSync(Runnable task){test.runOnMainSync(task);}
    private void waitForIdleSync(){test.waitForIdleSync();}
    private Activity startActivitySync(Intent intent){return test.startActivitySync(intent);}
    private UiAutomation getUiAutomation(){return test.getUiAutomation();}
    private final String fixture="cn.hydrogen.launcher.test";
    private final StringBuilder report=new StringBuilder("API19 cross-UID SYSTEM OVERLAY; TEST SURFACES, not commercial Amap\n");
    private MainActivity activity;private Config config;private String backup;private int passed,failed;
    private final Handler main=new Handler(Looper.getMainLooper());
    private volatile Bundle reply;private Messenger remote;
    private boolean heartbeatEnabled=true,proofEnabled=true;private AmapFloatingController controller;
    private final Runnable heartbeat=new Runnable(){public void run(){if(heartbeatEnabled&&remote!=null)try{Bundle data=new Bundle();data.putLong("session",session);data.putLong("sequence",sequence);data.putLong("issued",SystemClock.elapsedRealtime());Message message=Message.obtain(null,3);message.setData(data);remote.send(message);}catch(RemoteException ignored){}main.postDelayed(this,250);}};
    private final long session=SystemClock.elapsedRealtimeNanos();private long sequence;private IBinder token;
    private final Messenger client=new Messenger(new Handler(Looper.getMainLooper()){@Override public void handleMessage(Message m){
        if(m.what==2){reply=new Bundle(m.getData());remote=m.replyTo;}
        if(m.what==4&&proofEnabled&&m.replyTo!=null)try{
            Bundle data=new Bundle(m.getData());data.putLong("issued",SystemClock.elapsedRealtime());
            data.putBoolean("focused",activity!=null&&activity.hasWindowFocus());
            Message answer=Message.obtain(null,5);answer.setData(data);m.replyTo.send(answer);
        }catch(RemoteException ignored){}
    }});
    private interface Check{void run()throws Exception;}
    private void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private void check(String title,Check task){android.util.Log.i("MapSwitchTest","START "+title);try{task.run();passed++;report.append("PASS ");}catch(Throwable e){failed++;report.append("FAIL ").append(e).append(" ");}report.append(title).append('\n');}
    private void onMain(Runnable task){runOnMainSync(task);waitForIdleSync();}
    private void command(boolean show,int x,int y,int right,int bottom)throws Exception{commandAt(++sequence,show,x,y,right,bottom);}
    private void commandAt(long seq,boolean show,int x,int y,int right,int bottom)throws Exception{
        Bundle data=new Bundle();data.putInt("protocol",8);data.putLong("session",session);data.putLong("sequence",seq);data.putLong("issued",SystemClock.elapsedRealtime());data.putBoolean("show",show);data.putParcelable("client",client);data.putInt("x",x);data.putInt("y",y);data.putInt("w",right);data.putInt("h",bottom);
        if(remote==null)getTargetContext().sendBroadcast(new Intent(show?AmapFloatingController.SHOW:AmapFloatingController.CLOSE).setPackage(fixture).putExtras(data));
        else{Message message=Message.obtain(null,1);message.setData(data);remote.send(message);}
    }
    private Bundle await(boolean visible)throws Exception{for(int n=0;n<100;n++){Bundle r=reply;if(r!=null&&r.getLong("sequence")==sequence&&r.getBoolean("visible")==visible)return r;Thread.sleep(30);}throw new AssertionError("Missing ACK seq="+sequence+" visible="+visible+" last="+reply);}
    private JSONObject state()throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] json={null};
        getTargetContext().sendOrderedBroadcast(new Intent("cn.windchaser.fixture.STATE").setPackage(fixture),null,new BroadcastReceiver(){public void onReceive(Context c,Intent i){json[0]=getResultData();latch.countDown();}},main,0,null,null);
        require(latch.await(3,TimeUnit.SECONDS),"state timeout");return new JSONObject(json[0]);
    }
    private JSONObject dimensions(int width,int height)throws Exception{JSONObject s=null;for(int n=0;n<70;n++){s=state();if(s.optInt("ui_width")==width&&s.optInt("ui_height")==height&&s.optInt("buffer_width")==width&&s.optInt("buffer_height")==height&&s.optBoolean("native_resumed"))return s;Thread.sleep(40);}throw new AssertionError("size "+s);}
    private int step;
    private void external(JSONObject task)throws Exception{
        int id=++step;task.put("step",id).put("session",session);
        File done=new File(getTargetContext().getFilesDir(),"map-switch-done-"+session+"-"+id);
        try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(getTargetContext().getFilesDir(),"map-switch-stage.json")),"UTF-8")){out.write(task.toString());}
        for(int n=0;n<200&&!done.exists();n++)Thread.sleep(50);
        require(done.exists(),"external test step timed out "+task);done.delete();
    }
    private void screenshot(String name)throws Exception{external(new JSONObject().put("action","screenshot").put("name",name));}
    private void tap(float x,float y)throws Exception{external(new JSONObject().put("action","tap").put("x",x).put("y",y));}
    void run(){
        try{
            config=new Config(getTargetContext());backup=config.exportJson();config.put("autoRecoverApps",false);config.put("homePage",MainActivity.CARDS);config.put("pageAnimation",true);main.post(heartbeat);
            getTargetContext().startActivity(new Intent().setComponent(new ComponentName(fixture,"com.autonavi.amapauto.MainMapActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1200);
            activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();Thread.sleep(500);onMain(()->{activity.showPage(MainActivity.CARDS);token=activity.getWindow().getAttributes().token;});
            check("cross-UID native map/UI surfaces retain TYPE_PHONE system overlay",()->{command(true,100,80,700,480);Bundle ack=await(true);require(!ack.containsKey("error"),"attach error "+ack);JSONObject s=dimensions(600,400);require(s.getInt("ui_left")==100&&s.getInt("ui_top")==80,"position "+s);screenshot("map-switch-visible.png");});
            check("touch reaches native test search/settings pages inside system-overlay card",()->{tap(250,280);Thread.sleep(160);require("search".equals(state().getString("rendered_page")),"search");tap(550,280);Thread.sleep(160);require("settings".equals(state().getString("rendered_page")),"settings");});
            check("foreground watchdog withdraws map for full-screen camera even without a hide command",()->{
                getTargetContext().startActivity(new Intent().setComponent(new ComponentName(fixture,ReverseCameraActivity.class.getName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(650);
                external(new JSONObject().put("action","camera").put("name","map-switch-camera-priority.png"));
                require(!state().getBoolean("native_resumed"),"map remains active above camera");
                getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(400);
                command(true,100,80,700,480);await(true);dimensions(600,400);
            });
            check("duplicate SHOW leaves buffers and native start count unchanged",()->{JSONObject before=state();commandAt(sequence,true,100,80,700,480);Thread.sleep(200);JSONObject after=state();require(before.getInt("starts")==after.getInt("starts"),"duplicate restarted engine");});
            check("resize synchronizes buffers and both hit-test coordinate spaces",()->{command(true,120,100,670,550);await(true);dimensions(550,450);tap(120+412,100+225);Thread.sleep(150);require("settings".equals(state().getString("rendered_page")),"resize touch");});
            check("old HIDE cannot close a newer SHOW",()->{long stale=sequence;command(true,100,80,700,480);await(true);commandAt(stale,false,0,0,0,0);Thread.sleep(200);require(state().getBoolean("native_resumed"),"stale close won");});
            check("HIDE acknowledgement arrives after window withdrawal",()->{long begin=SystemClock.elapsedRealtime();command(false,0,0,0,0);await(false);report.append("  hide command to ACK ms=").append(SystemClock.elapsedRealtime()-begin).append('\n');Thread.sleep(180);require(!state().getBoolean("native_resumed"),"native not paused");});
            check("late SHOW with old sequence cannot resurrect a hidden card",()->{commandAt(sequence-1,true,100,80,700,480);Thread.sleep(220);require(!state().getBoolean("native_resumed"),"old show resurrected overlay");});
            check("missing host heartbeat expires overlay without Activity callbacks",()->{command(true,100,80,700,480);await(true);dimensions(600,400);heartbeatEnabled=false;proofEnabled=false;Thread.sleep(2850);require(!state().getBoolean("native_resumed"),"lease never expired");heartbeatEnabled=true;proofEnabled=true;command(true,100,80,700,480);await(true);dimensions(600,400);});
            check("real page controller sends show after animation without extra 220ms",()->{
                command(false,0,0,0,0);await(false);heartbeatEnabled=false;
                onMain(()->{try{Field f=MainActivity.class.getDeclaredField("floatingMap");f.setAccessible(true);((AmapFloatingController)f.get(activity)).release();controller=new AmapFloatingController(config,i->getTargetContext().sendBroadcast(new Intent(i).setPackage(fixture)),()->true);f.set(activity,controller);config.put("amapFloating",true);controller.bind(find(activity.getWindow().getDecorView(),"高德悬浮地图区域"));activity.showPage(MainActivity.APPS);}catch(Exception e){throw new RuntimeException(e);}});
                Thread.sleep(200);long start=SystemClock.elapsedRealtime();onMain(()->activity.showPage(MainActivity.CARDS));awaitNative(true);report.append("  page switch to native active ms=").append(SystemClock.elapsedRealtime()-start).append('\n');
                Thread.sleep(400);int starts=state().getInt("starts");Thread.sleep(900);require(state().getInt("starts")==starts,"successful show keeps restarting native state");
            });
            check("temporary guard block recovers while launcher keeps focus without a lifecycle callback",()->{
                require(activity.hasWindowFocus(),"host not focused before block");int starts=state().getInt("starts");
                getTargetContext().sendBroadcast(new Intent("cn.windchaser.fixture.BLOCK_ONCE").setPackage(fixture));
                for(int n=0;n<100;n++){if(state().getInt("starts")>starts)break;Thread.sleep(40);}
                require(state().getInt("starts")>starts,"blocked state latched forever");awaitNative(true);
                require(activity.hasWindowFocus(),"test changed focus");
                require("地图卡片已连接".equals(controller.status()),"not acknowledged after recovery: "+controller.status());
            });
            check("old blocked acknowledgement cannot suppress recovered card",()->{
                onMain(()->{try{
                    Field sf=AmapFloatingController.class.getDeclaredField("session"),qf=AmapFloatingController.class.getDeclaredField("sequence"),rf=AmapFloatingController.class.getDeclaredField("replies");
                    sf.setAccessible(true);qf.setAccessible(true);rf.setAccessible(true);
                    Bundle b=new Bundle();b.putLong("session",sf.getLong(controller));b.putLong("sequence",qf.getLong(controller)-1);b.putBoolean("blocked",true);b.putBoolean("visible",false);
                    Message m=Message.obtain(null,2);m.setData(b);((Messenger)rf.get(controller)).send(m);
                }catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(200);
                require("地图卡片已连接".equals(controller.status()),"obsolete block won");require(state().getBoolean("native_resumed"),"old block withdrew window");
            });
            cn.windchaser.mapbridge.GuardPolicyChecks.run((name,task)->check(name,task::run));
            check("rapid page flips hide overlay and reject late state",()->{
                onMain(()->{activity.showPage(MainActivity.APPS);activity.showPage(MainActivity.CARDS);activity.showPage(MainActivity.MINIMAL);activity.showPage(MainActivity.CARDS);activity.showPage(MainActivity.APPS);});awaitNative(false);Thread.sleep(600);require(!state().getBoolean("native_resumed"),"late show exposed wrong page");onMain(()->activity.showPage(MainActivity.CARDS));awaitNative(true);
            });
            check("focus loss hides overlay and focus return restores it",()->{final AlertDialog[] dialog={null};onMain(()->dialog[0]=new AlertDialog.Builder(activity).setMessage("Focus priority test").setPositiveButton("OK",null).show());awaitNative(false);onMain(()->dialog[0].dismiss());awaitNative(true);});
            check("screen-off state closes map and screen-on can restore it",()->{onMain(()->controller.setScreenOn(false));awaitNative(false);onMain(()->controller.setScreenOn(true));awaitNative(true);});
            check("opening native map fullscreen restores original surfaces and returning restores card",()->{
                getTargetContext().startActivity(new Intent().setComponent(new ComponentName(fixture,"com.autonavi.amapauto.MainMapActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                JSONObject s=null;for(int n=0;n<80;n++){s=state();if(!s.optBoolean("paused")&&s.optInt("ui_width")==1280&&s.optInt("buffer_width")==1280)break;Thread.sleep(40);}
                require(s!=null&&!s.optBoolean("paused")&&s.optInt("ui_width")==1280&&s.optInt("buffer_width")==1280,"fullscreen not restored "+s);
                getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));awaitNative(true);
                for(int n=0;n<80;n++){s=state();if(s.optBoolean("paused")&&s.optInt("ui_width")<1000&&s.optBoolean("native_resumed"))break;Thread.sleep(40);}
                require(s.optBoolean("paused")&&s.optInt("ui_width")<1000&&s.optBoolean("native_resumed"),"card not restored "+s);
            });
            check("moving the card without a size/layout event updates absolute overlay coordinates",()->{
                final View anchor=find(activity.getWindow().getDecorView(),"高德悬浮地图区域");
                onMain(()->anchor.setTranslationX(18));awaitAligned();
                onMain(()->anchor.setTranslationX(0));awaitAligned();
            });
            check("return from ordinary app repairs shifted window, system bars and keyboard insets",()->{
                onMain(()->{WindowManager.LayoutParams p=activity.getWindow().getAttributes();p.width=1047;p.height=620;p.x=120;p.y=25;activity.getWindow().setAttributes(p);activity.getWindow().getDecorView().setSystemUiVisibility(0);});
                Thread.sleep(250);
                getTargetContext().startActivity(new Intent().setComponent(new ComponentName(fixture,TestInsetsActivity.class.getName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Thread.sleep(650);awaitNative(false);
                getTargetContext().startActivity(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                awaitNative(true);awaitAligned();
                onMain(()->{WindowManager.LayoutParams p=activity.getWindow().getAttributes();require(p.width==-1&&p.height==-1&&p.x==0&&p.y==0,"window still shifted");View content=activity.findViewById(android.R.id.content);int[] xy=new int[2];content.getLocationOnScreen(xy);require(xy[0]==0&&xy[1]==0&&content.getWidth()==1280&&content.getHeight()==720,"content clipped: "+xy[0]+","+xy[1]+" "+content.getWidth()+"x"+content.getHeight());});
                screenshot("return-layout-system-bars-and-ime.png");
            });
            check("Activity finish closes system overlay",()->{onMain(activity::finish);Thread.sleep(500);external(new JSONObject().put("action","no_overlay"));});
        }catch(Throwable error){failed++;report.append("FAIL fixture ").append(error).append('\n');}
        finally{main.removeCallbacks(heartbeat);try{if(controller!=null)onMain(controller::release);else if(token!=null)command(false,0,0,0,0);}catch(Exception ignored){}if(activity!=null)onMain(activity::finish);if(backup!=null)try{config.restore(backup);}catch(Exception ignored){}}
        report.append("MAP_SWITCH_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');
        try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(getTargetContext().getFilesDir(),"map-switch-report.txt")),"UTF-8")){out.write(report.toString());}catch(Exception ignored){}
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private View find(View v,String label){if(label.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View found=find(((ViewGroup)v).getChildAt(n),label);if(found!=null)return found;}return null;}
    private void awaitNative(boolean active)throws Exception{for(int n=0;n<80;n++){if(state().optBoolean("native_resumed")==active)return;Thread.sleep(40);}throw new AssertionError("native active != "+active+" "+state());}
    private void awaitAligned()throws Exception{
        final Rect expected=new Rect();onMain(()->{View anchor=find(activity.getWindow().getDecorView(),"高德悬浮地图区域");int[] xy=new int[2];anchor.getLocationOnScreen(xy);expected.set(xy[0],xy[1],xy[0]+anchor.getWidth(),xy[1]+anchor.getHeight());});
        JSONObject s=null;for(int n=0;n<80;n++){s=state();if(s.optInt("ui_left")==expected.left&&s.optInt("ui_top")==expected.top&&s.optInt("ui_width")==expected.width()&&s.optInt("ui_height")==expected.height())return;Thread.sleep(40);}throw new AssertionError("overlay differs from card "+expected+" "+s);
    }
}
