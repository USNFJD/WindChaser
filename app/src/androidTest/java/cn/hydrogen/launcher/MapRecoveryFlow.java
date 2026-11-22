package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Cross-UID bridge, lifecycle and actual shortcut-picker integration on API19.
 * Java surfaces stand in for the commercial map engine. */
final class MapRecoveryFlow {
    private final Instrumentation test;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final String fixture="cn.hydrogen.launcher.test";
    private final StringBuilder report=new StringBuilder("Real bridge and real launcher callbacks; Java test surfaces, not commercial Amap\n");
    private MainActivity activity;private AmapFloatingController controller;private Config cfg;private String backup;
    private final boolean returnOnly;private int passed,failed,shows;private volatile boolean watch,withdrawn;private int nativeStarts;
    MapRecoveryFlow(Instrumentation test){this(test,false);}
    MapRecoveryFlow(Instrumentation test,boolean returnOnly){this.test=test;this.returnOnly=returnOnly;}
    private Context context(){return test.getTargetContext();}
    private interface Step{void run()throws Exception;}
    private interface Condition{boolean ready()throws Exception;}
    private void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void check(String name,Step step){if(returnOnly&&!(name.startsWith("already")||name.startsWith("full-screen")||name.startsWith("warm")))return;android.util.Log.i("MapRecoveryTest","START "+name);try{step.run();passed++;report.append("PASS ");}catch(Throwable error){failed++;report.append("FAIL ").append(error).append(" ");}report.append(name).append('\n');}
    private void onMain(Runnable step){test.runOnMainSync(step);}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void set(Object owner,String name,Object value)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);f.set(owner,value);}
    private void await(String name,Condition condition)throws Exception{long end=SystemClock.elapsedRealtime()+12000;while(SystemClock.elapsedRealtime()<end){try{if(condition.ready())return;}catch(AssertionError notYet){}Thread.sleep(60);}throw new AssertionError(name+" state="+state()+" controller="+(controller==null?"none":controller.status()+" active="+field(controller,"active")+" requested="+field(controller,"requested")+" focus="+field(controller,"focused")+" blocked="+field(controller,"safetyBlocked")+" seq="+field(controller,"sequence")+" activity="+activity.hasWindowFocus()+" destroyed="+activity.isDestroyed()+" canRequest="+LinkedAppCoordinator.get(context()).canRequest(cfg.pkg(false))+" page="+activity.visiblePage()+" mapPage="+field(controller,"mapPage")+" anchor="+((View)field(controller,"anchor")).isShown()+" anchorFocus="+((View)field(controller,"anchor")).hasWindowFocus()+" bounds="+boundsDebug()+" startup="+startupDebug()));}
    private JSONObject state()throws Exception{
        CountDownLatch latch=new CountDownLatch(1);String[] json={null};
        context().sendOrderedBroadcast(new Intent("cn.windchaser.fixture.STATE").setPackage(fixture),null,new BroadcastReceiver(){public void onReceive(Context c,Intent i){json[0]=getResultData();latch.countDown();}},main,0,null,null);
        require(latch.await(3,TimeUnit.SECONDS)&&json[0]!=null,"fixture unavailable");return new JSONObject(json[0]);
    }
    private void awaitCard()throws Exception{await("card not ready",()->{JSONObject s=state();return controller.connected()&&s.optBoolean("paused")&&s.optBoolean("native_resumed")&&s.optInt("ui_width")>48&&s.optInt("ui_width")<1000;});}
    private String startupDebug()throws Exception{Object owner=field(activity,"startupApps");LinkedAppCoordinator c=LinkedAppCoordinator.get(context());return "drawn:"+field(owner,"drawn")+",scheduled:"+field(owner,"scheduled")+",closed:"+field(owner,"closed")+",decor:"+((View)field(owner,"decor")).hasWindowFocus()+",visible:"+field(c,"hostVisible")+",busy:"+field(c,"sequenceActive");}
    private Object boundsDebug(){try{Method m=AmapFloatingController.class.getDeclaredMethod("bounds");m.setAccessible(true);return m.invoke(controller);}catch(Exception e){return e.toString();}}
    private View findText(View v,String text,boolean last){View found=null;if(v instanceof android.widget.TextView&&text.contentEquals(((android.widget.TextView)v).getText()))found=v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View child=findText(((ViewGroup)v).getChildAt(n),text,last);if(child!=null){found=child;if(!last)break;}}return found;}
    private static final class RectTest {
        static void assertSeparate(MainActivity a,AlertDialog dialog){View decor=dialog.getWindow().getDecorView();int[] xy=new int[2];decor.getLocationOnScreen(xy);android.graphics.Rect r=new android.graphics.Rect(xy[0],xy[1],xy[0]+decor.getWidth(),xy[1]+decor.getHeight());android.graphics.Rect area=a.mapDialogArea();if(area==null||r.left<area.left||r.right>area.right||r.top<area.top||r.bottom>area.bottom)throw new AssertionError("picker outside safe area: "+r+" vs "+area);}
    }
    private View find(View v,String name){if(name.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){View found=find(((ViewGroup)v).getChildAt(n),name);if(found!=null)return found;}return null;}
    private Dialog songDialog()throws Exception{
        Object host=field(activity,"mapDialogs");for(Object owned:(Collection<?>)field(host,"windows")){Dialog dialog=(Dialog)field(owned,"dialog");if(dialog instanceof SongInfoDialog&&dialog.isShowing())return dialog;}return null;
    }
    private void captureDialog(Dialog dialog,String name){onMain(()->{View decor=dialog.getWindow().getDecorView();android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(decor.getWidth(),decor.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);try{decor.draw(new android.graphics.Canvas(image));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context().getExternalFilesDir(null),name))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}}catch(java.io.IOException e){throw new RuntimeException(e);}finally{image.recycle();}});}
    private android.graphics.Rect screenBounds(Dialog dialog){View decor=dialog.getWindow().getDecorView();int[] xy=new int[2];decor.getLocationOnScreen(xy);return new android.graphics.Rect(xy[0],xy[1],xy[0]+decor.getWidth(),xy[1]+decor.getHeight());}
    private AlertDialog ownedDialog()throws Exception{
        Object host=field(activity,"mapDialogs");for(Object owned:(Collection<?>)field(host,"windows")){AlertDialog dialog=(AlertDialog)field(owned,"dialog");if(dialog.isShowing())return dialog;}return null;
    }
    private final Runnable monitor=new Runnable(){public void run(){if(!watch)return;if(!controller.connected())withdrawn=true;main.postDelayed(this,20);}};
    private void installTransport()throws Exception{
        controller=(AmapFloatingController)field(activity,"floatingMap");
        set(controller,"transport",(AmapFloatingController.Transport)i->{if(AmapFloatingController.SHOW.equals(i.getAction()))shows++;context().sendBroadcast(new Intent(i).setPackage(fixture));});
        set(controller,"availability",(AmapFloatingController.Availability)()->true);set(controller,"availableAt",-2000L);
        // Keep production changed callback, dialog focus, coordinator and layout gates.
        onMain(()->{controller.reconnect();});
    }
    void run(){
        try{
            cfg=new Config(context());backup=cfg.exportJson();cfg.put("autoRecoverApps",false);cfg.put("homePage",MainActivity.CARDS);cfg.put("quickSlot0","");cfg.put("quickSlot1","");for(int n=0;n<4;n++)cfg.put("slot"+n,"");cfg.put("pageAnimation",false);
            context().startActivity(new Intent().setComponent(new ComponentName(fixture,"com.autonavi.amapauto.MainMapActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            await("warm map activity",()->!state().optBoolean("paused")&&state().optInt("ui_width")>0);
            Thread.sleep(900);
            activity=(MainActivity)test.startActivitySync(new Intent(context(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Thread.sleep(700);installTransport();
            check("already-running map connects with production geometry and status callback",this::awaitCard);
            check("real Add Application picker retains overlay and native engine",()->{
                awaitCard();nativeStarts=state().getInt("starts");withdrawn=false;watch=true;main.post(monitor);
                onMain(()->{View add=find(activity.getWindow().getDecorView(),"快捷区自定义应用1");require(add!=null,"actual shortcut missing");add.performClick();});
                await("actual picker missing",()->ownedDialog()!=null&&ownedDialog().getListView()!=null);
                AlertDialog dialog=ownedDialog();await("picker focus",()->dialog.getWindow().getDecorView().hasWindowFocus());
                Thread.sleep(3200);watch=false;
                require(controller.connected()&&state().optBoolean("native_resumed"),"map hidden while own picker showing");
                require(!withdrawn,"map withdrawn during picker transition");require(state().getInt("starts")==nativeStarts,"native engine restarted for own picker");
                onMain(dialog::dismiss);Thread.sleep(400);awaitCard();
            });
            check("actual bottom dock app picker retains map with ordinary application dialog",()->{
                awaitCard();int starts=state().getInt("starts");withdrawn=false;watch=true;main.post(monitor);
                onMain(()->{View add=findText(activity.getWindow().getDecorView(),"添加应用",true);require(add!=null,"bottom Add Application entry missing");while(add!=null&&!add.isClickable())add=add.getParent() instanceof View?(View)add.getParent():null;require(add!=null,"dock button not clickable");add.performClick();});
                await("dock picker missing",()->ownedDialog()!=null);AlertDialog dialog=ownedDialog();
                await("ordinary app picker has no real focus",()->dialog.getWindow().getDecorView().hasWindowFocus());
                require(dialog.getWindow().getAttributes().type==WindowManager.LayoutParams.TYPE_APPLICATION,"requires launcher overlay permission");
                Thread.sleep(2400);watch=false;require(!withdrawn&&controller.connected(),"bottom picker withdrew map");require(state().getInt("starts")==starts&&state().optBoolean("native_resumed"),"map engine restarted or paused");
                RectTest.assertSeparate(activity,dialog);
                android.graphics.Rect initial=screenBounds(dialog);require(initial.height()<=Math.ceil(activity.getResources().getDisplayMetrics().heightPixels*.62f),"picker too tall");
                require(dialog.getListView().getCount()>dialog.getListView().getChildCount(),"long-list scrolling fixture missing");
                onMain(()->dialog.getListView().setSelection(dialog.getListView().getCount()-1));
                for(int n=0;n<8;n++){Thread.sleep(100);require(initial.equals(screenBounds(dialog)),"picker moved or resized while scrolling");}
                onMain(()->{View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);android.graphics.Rect button=new android.graphics.Rect();require(cancel.getGlobalVisibleRect(button),"cancel clipped");require(button.height()>=cancel.getHeight()-2,"cancel partly clipped");});
                captureDialog(dialog,"dialog-app-picker.png");onMain(dialog::dismiss);awaitCard();
            });
            check("song information retains map with stable bounded translucent window",()->{
                awaitCard();int starts=state().getInt("starts");withdrawn=false;watch=true;main.post(monitor);
                onMain(()->{View button=find(activity.getWindow().getDecorView(),"歌曲信息");require(button!=null,"actual song info button missing");button.performClick();});
                await("song info missing",()->songDialog()!=null);Dialog info=songDialog();await("song info focus",()->info.getWindow().getDecorView().hasWindowFocus());
                android.graphics.Rect bounds=screenBounds(info);require(bounds.height()<=Math.ceil(activity.getResources().getDisplayMetrics().heightPixels*.62f),"song info too tall");
                for(int n=0;n<10;n++){Thread.sleep(100);require(bounds.equals(screenBounds(info)),"song info moved");}
                watch=false;require(!withdrawn&&controller.connected()&&state().optBoolean("native_resumed"),"song info hid map");require(state().getInt("starts")==starts,"song info restarted engine");
                captureDialog(info,"dialog-song-info.png");onMain(info::dismiss);awaitCard();
            });
            check("200 long app names scroll inside a fixed window with reachable cancel",()->{
                awaitCard();String[] names=new String[200];for(int n=0;n<names.length;n++)names[n]="应用 "+(n+1)+" · 这是一个用于检查窗口长度和文字截断的很长应用名称";
                AlertDialog[] holder={null};onMain(()->holder[0]=new ThemeDialog.Builder(activity).setTitle("选择已安装应用 · 长名称窗口检查").setItems(names,null).setNegativeButton("取消",null).show());AlertDialog dialog=holder[0];
                await("long list focus",()->dialog.getWindow().getDecorView().hasWindowFocus());Thread.sleep(200);android.graphics.Rect initial=screenBounds(dialog);
                require(initial.height()<=Math.ceil(activity.getResources().getDisplayMetrics().heightPixels*.62f),"long list enlarged window");require(dialog.getWindow().getAttributes().windowAnimations==0,"window movement animation enabled");RectTest.assertSeparate(activity,dialog);
                onMain(()->dialog.getListView().setSelection(199));Thread.sleep(300);
                onMain(()->{require(dialog.getListView().getLastVisiblePosition()==199,"last app unreachable");View row=dialog.getListView().getChildAt(0);android.widget.TextView text=(android.widget.TextView)row.findViewById(android.R.id.text1);require(text.getMaxLines()==1&&text.getEllipsize()==android.text.TextUtils.TruncateAt.END,"long app names wrap");android.graphics.Rect cancel=new android.graphics.Rect();View button=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);require(button.getGlobalVisibleRect(cancel)&&cancel.height()>=button.getHeight()-2,"cancel clipped");});
                for(int n=0;n<5;n++){Thread.sleep(100);require(initial.equals(screenBounds(dialog)),"long list moved window");}
                captureDialog(dialog,"dialog-long-names.png");require(controller.connected()&&state().optBoolean("native_resumed"),"long list hid map");onMain(dialog::dismiss);awaitCard();
            });
            check("native negative ACK re-arms SHOW after successful connection",()->{
                awaitCard();Messenger real=(Messenger)field(controller,"remote");Messenger replies=(Messenger)field(controller,"replies");int[] commands={0};
                Messenger observer=new Messenger(new Handler(Looper.getMainLooper()){@Override public void handleMessage(Message m){try{if(m.what==1&&m.getData().getBoolean("show"))commands[0]++;real.send(Message.obtain(m));}catch(RemoteException e){throw new RuntimeException(e);}}});
                Bundle data=new Bundle();data.putLong("session",(Long)field(controller,"session"));data.putLong("sequence",(Long)field(controller,"sequence"));data.putBoolean("visible",false);
                Message ack=Message.obtain(null,2);ack.setData(data);ack.replyTo=observer;replies.send(ack);
                try{await("negative ACK left map disconnected",()->commands[0]>0&&controller.connected());}
                finally{set(controller,"remote",real);onMain(controller::reconnect);awaitCard();}
            });
            check("long-press shortcut and nested selection dialogs retain native map",()->{
                awaitCard();int starts=state().getInt("starts");withdrawn=false;watch=true;main.post(monitor);
                onMain(()->find(activity.getWindow().getDecorView(),"快捷区自定义应用1").performLongClick());await("long press dialog",()->ownedDialog()!=null);
                AlertDialog menu=ownedDialog();onMain(()->menu.getListView().performItemClick(menu.getListView().getChildAt(1),1,menu.getListView().getAdapter().getItemId(1)));
                await("nested picker",()->ownedDialog()!=null&&ownedDialog()!=menu);Thread.sleep(1800);watch=false;
                require(!withdrawn&&controller.connected()&&state().getInt("starts")==starts,"nested selection withdrew map");AlertDialog dialog=ownedDialog();onMain(dialog::dismiss);awaitCard();
            });
            check("silent but live Messenger is rediscovered through broadcast",()->{
                AlertDialog dialog=ownedDialog();if(dialog!=null)onMain(dialog::dismiss);awaitCard();
                int before=shows;Messenger sink=new Messenger(new Handler(Looper.getMainLooper()){@Override public void handleMessage(Message m){}});
                set(controller,"remote",sink);onMain(controller::reconnect);
                try{await("silent endpoint never rediscovered",()->shows>before&&controller.connected());}
                finally{set(controller,"remote",null);onMain(controller::reconnect);awaitCard();}
            });
            check("full-screen reverse camera withdraws map even with own picker open",()->{
                awaitCard();onMain(()->Ui.pick(activity,app->{}));await("picker missing",()->ownedDialog()!=null);
                context().startActivity(new Intent().setComponent(new ComponentName(fixture,ReverseCameraActivity.class.getName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Thread.sleep(1200);await("overlay above camera",()->!state().optBoolean("native_resumed"));require(ownedDialog()==null,"own dialog survived genuine leave");
                context().startActivity(new Intent(context(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));awaitCard();
            });
            check("warm Activity return restores map without opening original map again",()->{
                int before=state().getInt("starts");context().startActivity(new Intent().setComponent(new ComponentName(fixture,TestInsetsActivity.class.getName())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Thread.sleep(1200);await("map not hidden on leave",()->!state().optBoolean("native_resumed"));
                context().startActivity(new Intent(context(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));awaitCard();int after=state().getInt("starts");require(after<=before+2,"excess engine restarts before="+before+" after="+after+" shows="+shows);
            });
        }catch(Throwable e){failed++;report.append("FAIL setup ").append(e).append('\n');}
        finally{watch=false;main.removeCallbacks(monitor);if(activity!=null)onMain(activity::finish);if(backup!=null)try{cfg.restore(backup);}catch(Exception ignored){}}
        report.append("MAP_RECOVERY_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
