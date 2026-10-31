package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;

/** Captures protocol requests on an API 19 test device. It does not run Amap. */
final class AmapFloatingFlow {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder("Protocol/lifecycle capture on API 19; no third-party map execution or display proof\n");
    private final List<Intent> sent=Collections.synchronizedList(new ArrayList<>());
    private MainActivity activity;
    private AmapFloatingController controller;private LinkedAppCoordinator testCoordinator;
    private volatile boolean simulatedWindowVisible;
    private Config config;
    private int passed,failed;private final boolean dialogOnly;
    private interface Check{void run()throws Exception;}
    AmapFloatingFlow(Instrumentation test){this(test,false);}
    AmapFloatingFlow(Instrumentation test,boolean dialogOnly){this.test=test;this.dialogOnly=dialogOnly;}
    private void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private void check(String title,Check body){if(dialogOnly&&!title.startsWith("measured card")&&!title.startsWith("owned ")&&!title.startsWith("settings activity")&&!title.startsWith("actual Activity finish"))return;android.util.Log.i("AmapFlowTest","START "+title);try{body.run();passed++;report.append("PASS ");}catch(Throwable error){failed++;report.append("FAIL ").append(error).append(" · ");}report.append(title).append('\n');}
    private void main(Runnable task){Throwable[] error={null};test.runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});test.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private View find(View view,String label){if(label.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View found=find(((ViewGroup)view).getChildAt(n),label);if(found!=null)return found;}return null;}
    private void retainDialogFocus()throws Exception{Field f=MainActivity.class.getDeclaredField("mapDialogs");f.setAccessible(true);MapDialogHost dialogs=(MapDialogHost)f.get(activity);controller.setDialogFocus(dialogs::focused);}
    private View slot(){return find(activity.getWindow().getDecorView(),"高德悬浮地图区域");}
    private Intent last(){synchronized(sent){return sent.isEmpty()?null:sent.get(sent.size()-1);}}
    private void await(String action)throws Exception{for(int n=0;n<(dialogOnly?200:60);n++){Intent i=last();if(i!=null&&action.equals(i.getAction()))return;Thread.sleep(50);}StringBuilder state=new StringBuilder();main(()->{try{for(String name:new String[]{"resumed","leaving","hostFocused","layoutReady","transitioning","currentPage"}){Field f=MainActivity.class.getDeclaredField(name);f.setAccessible(true);state.append(name).append('=').append(f.get(activity)).append(' ');}state.append("actualFocus=").append(activity.hasWindowFocus());for(String name:new String[]{"active","mapPage","focused","requested","safetyBlocked"}){Field f=AmapFloatingController.class.getDeclaredField(name);f.setAccessible(true);state.append(' ').append(name).append('=').append(f.get(controller));}}catch(Exception e){state.append(e);}});throw new AssertionError("Missing action "+action+" "+state);}
    private int count(){synchronized(sent){return sent.size();}}
    private void noMoreRequests()throws Exception{int before=count();Thread.sleep(2700);synchronized(sent){for(int n=before;n<sent.size();n++)require(!AmapFloatingController.SHOW.equals(sent.get(n).getAction()),"show continued while hidden");}int settled=count();Thread.sleep(350);require(count()==settled,"cleanup did not stop");}
    private Rect measured(){Rect result=new Rect();main(()->{View view=slot();require(view!=null,"missing anchor");int[] xy=new int[2];view.getLocationOnScreen(xy);result.set(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());});return result;}
    private void requireBounds(Intent intent,Rect r){require(AmapFloatingController.PACKAGE.equals(intent.getPackage()),"unscoped broadcast");require(intent.getIntExtra("x",-1)==r.left&&intent.getIntExtra("y",-1)==r.top,"wrong origin");require(intent.getIntExtra("w",-1)==r.right&&intent.getIntExtra("h",-1)==r.bottom,"w/h must be right/bottom endpoints");}
    private void capture(String name)throws Exception{Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap==null&&dialogOnly){report.append("  Screenshot unavailable in software-rendered API19 lab; focus and real touch assertions remain active\n");return;}require(bitmap!=null,"no screenshot");try(OutputStream out=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),name))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}finally{bitmap.recycle();}}

    void run(){
        config=new Config(test.getTargetContext());String backup="";boolean hadDetected=config.p.contains("amapFloatDetected"),detected=config.b("amapFloatDetected",false);
        try{
            require(!Ui.installed(test.getTargetContext(),AmapFloatingController.PACKAGE),"Use isolated test device without the commercial map APK");
            backup=config.exportJson();
            check("offset rectangle uses endpoint protocol and explicit target",()->{
                Rect r=new Rect(100,80,700,480);Intent intent=AmapFloatingController.showIntent(r);requireBounds(intent,r);
                require(intent.getComponent()==null,"dynamic receiver must not be a manifest component");require(AmapFloatingController.PACKAGE.equals(AmapFloatingController.closeIntent().getPackage()),"close is unscoped");
                boolean rejected=false;try{AmapFloatingController.showIntent(new Rect(100,100,100,200));}catch(IllegalArgumentException expected){rejected=true;}require(rejected,"zero-width window accepted");
            });
            check("floating switch round-trip and invalid type rejection",()->{
                config.put("amapFloating",false);String data=config.exportJson();config.put("amapFloating",true);config.restore(data);require(!config.b("amapFloating",true),"disabled setting lost");
                boolean rejected=false;try{config.restore("{\"format\":\"hydrogen-config\",\"version\":1,\"settings\":{\"amapFloating\":1}}");}catch(Exception expected){rejected=true;}require(rejected&&!config.b("amapFloating",true),"invalid switch changed preferences");
            });
            check("6.5 card requests keep endpoint coordinates and target only the selected package",()->{
                Rect r=new Rect(40,60,540,360);Intent show=AmapFloatingController.showIntent(r,AmapFloatingController.PACKAGE);
                require(AmapFloatingController.PACKAGE.equals(show.getPackage())&&show.getIntExtra("w",0)==540&&show.getIntExtra("h",0)==360,"legacy coordinate contract");
                require(AmapFloatingController.PACKAGE.equals(AmapFloatingController.closeIntent(AmapFloatingController.PACKAGE).getPackage()),"wrong legacy close target");
            });
            check("6.5 selection persists separately and prewarms its own service without redirecting QQ",()->{
                String saved=config.exportJson();try{config.put("mapPackage",AmapFloatingController.PACKAGE);config.put("amap65Bridge",true);String selected=config.exportJson();config.put("amap65Bridge",false);config.restore(selected);
                    require(AmapFloatingController.selected(config),"legacy selection lost");require(AmapFloatingController.PACKAGE.equals(BackgroundRecovery.serviceIntent(false,config).getComponent().getPackageName()),"legacy service targets 7.5");require(Config.MUSIC.equals(BackgroundRecovery.serviceIntent(true,config).getComponent().getPackageName()),"QQ redirected");
                }finally{config.restore(saved);}
            });
            config.put("autoRecoverApps",false);AmapFloatingController.select(config);config.put("homePage",MainActivity.CARDS);config.put("mapShare",60);config.put("swap",false);
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));test.waitForIdleSync();
            main(()->{try{Field field=MainActivity.class.getDeclaredField("floatingMap");field.setAccessible(true);((AmapFloatingController)field.get(activity)).release();controller=new AmapFloatingController(config,i->{sent.add(new Intent(i));simulatedWindowVisible=AmapFloatingController.SHOW.equals(i.getAction());},()->true);field.set(activity,controller);if(dialogOnly){testCoordinator=new LinkedAppCoordinator(activity.getApplicationContext(),ignored->{},pkg->true);testCoordinator.hostReady();Field admission=AmapFloatingController.class.getDeclaredField("coordinator");admission.setAccessible(true);admission.set(controller,testCoordinator);require(!testCoordinator.canConnect(AmapFloatingController.PACKAGE),"fixture unexpectedly has separate readiness proof");}retainDialogFocus();controller.bind(slot());activity.showPage(MainActivity.CARDS);}catch(Exception error){throw new RuntimeException(error);}});
            check("measured card slot and escape controls remain outside external window",()->{
                await(AmapFloatingController.SHOW);Rect r=measured();require(r.width()>100&&r.height()>100,"slot not laid out");requireBounds(last(),r);
                main(()->{for(String label:new String[]{"切换高德悬浮地图","重连高德悬浮地图","打开高德地图","地图卡片菜单"}){View v=find(activity.getWindow().getDecorView(),label);require(v!=null&&v.isShown(),"missing escape control "+label);int[] xy=new int[2];v.getLocationOnScreen(xy);require(xy[1]+v.getHeight()<=r.top,"external window covers "+label);}});
                report.append("  measured slot ").append(r).append('\n');
                capture("v055-map-setup.png");
            });
            check("swapped/narrow card updates physical screen bounds",()->{
                Rect before=measured();main(()->{config.put("mapShare",35);config.put("swap",true);activity.render();});await(AmapFloatingController.SHOW);Rect after=measured();requireBounds(last(),after);require(after.width()<before.width()&&after.left>before.left,"layout change not reflected");
                report.append("  changed slot ").append(after).append('\n');
                capture("v055-map-swapped.png");
            });
            check("app and minimal pages close window and cancel retries",()->{
                main(()->activity.showPage(MainActivity.APPS));await(AmapFloatingController.CLOSE);noMoreRequests();main(()->activity.showPage(MainActivity.CARDS));await(AmapFloatingController.SHOW);main(()->activity.showPage(MainActivity.MINIMAL));await(AmapFloatingController.CLOSE);noMoreRequests();main(()->activity.showPage(MainActivity.CARDS));await(AmapFloatingController.SHOW);
            });
            check("late external show is cleared by bounded close retries",()->{
                main(()->activity.showPage(MainActivity.APPS));await(AmapFloatingController.CLOSE);int before=count();Thread.sleep(240);simulatedWindowVisible=true;Thread.sleep(500);require(!simulatedWindowVisible,"late external window was not closed");noMoreRequests();int closes=0;synchronized(sent){for(int n=before;n<sent.size();n++)if(AmapFloatingController.CLOSE.equals(sent.get(n).getAction()))closes++;}require(closes>=2&&closes<=4,"cleanup must be finite");main(()->activity.showPage(MainActivity.CARDS));await(AmapFloatingController.SHOW);
            });
            check("rapid flips cancel uncommitted show and returning cancels old closes",()->{
                main(()->{activity.showPage(MainActivity.APPS);activity.showPage(MainActivity.CARDS);activity.showPage(MainActivity.MINIMAL);activity.showPage(MainActivity.CARDS);activity.showPage(MainActivity.APPS);});await(AmapFloatingController.CLOSE);noMoreRequests();main(()->activity.showPage(MainActivity.CARDS));await(AmapFloatingController.SHOW);int start=count();Thread.sleep(2850);synchronized(sent){for(int n=start;n<sent.size();n++)require(!AmapFloatingController.CLOSE.equals(sent.get(n).getAction()),"stale close hid restored map");}require(simulatedWindowVisible,"restored map remains hidden");
            });
            check("settling destination page suppresses one-frame map exposure",()->{
                main(()->activity.showPage(MainActivity.APPS));int before=count();main(()->{activity.showPage(MainActivity.CARDS);activity.showPage(MainActivity.MINIMAL);});noMoreRequests();synchronized(sent){for(int n=before;n<sent.size();n++)require(!AmapFloatingController.SHOW.equals(sent.get(n).getAction()),"transient page exposed map");}capture("v055-minimal.png");main(()->activity.showPage(MainActivity.CARDS));await(AmapFloatingController.SHOW);
            });
            check("actual dialog focus loss closes and dismiss restores",()->{
                final AlertDialog[] dialog={null};main(()->dialog[0]=new AlertDialog.Builder(activity).setMessage("Lifecycle test dialog").setPositiveButton("OK",null).show());await(AmapFloatingController.CLOSE);
                main(()->dialog[0].dismiss());await(AmapFloatingController.SHOW);
            });
            check("settings activity pause closes and returning resumes",()->{
                Activity settings=test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();await(AmapFloatingController.CLOSE);main(settings::finish);await(AmapFloatingController.SHOW);
            });
            check("outgoing navigation closes before the destination is launched",()->{
                Instrumentation.ActivityMonitor monitor=test.addMonitor(FileCenterActivity.class.getName(),null,false);
                try{main(()->{activity.startActivity(new Intent(activity,FileCenterActivity.class));require(last()!=null&&AmapFloatingController.CLOSE.equals(last().getAction()),"navigation did not close synchronously");});Activity files=monitor.waitForActivityWithTimeout(4000);require(files!=null,"file center not launched");await(AmapFloatingController.CLOSE);main(files::finish);await(AmapFloatingController.SHOW);}finally{test.removeMonitor(monitor);}
            });
            check("leave hint remains latched across focus and layout callbacks",()->{
                main(()->{activity.onUserLeaveHint();activity.onWindowFocusChanged(true);activity.render();});await(AmapFloatingController.CLOSE);noMoreRequests();main(()->{activity.onNewIntent(new Intent(activity,MainActivity.class));activity.showPage(MainActivity.CARDS);});await(AmapFloatingController.SHOW);
            });
            check("owned application picker stays beside real TYPE_PHONE map and receives touches",()->{
                final AlertDialog[] dialog={null};final View[] overlay={null};final boolean[] picked={false};
                final WindowManager wm=(WindowManager)activity.getSystemService(Context.WINDOW_SERVICE);
                Rect bounds=measured();
                try{
                    main(()->{View map=new View(activity);map.setBackgroundColor(0xff236a76);WindowManager.LayoutParams lp=new WindowManager.LayoutParams(bounds.width(),bounds.height(),WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.OPAQUE);lp.gravity=Gravity.TOP|Gravity.LEFT;lp.x=bounds.left;lp.y=bounds.top;wm.addView(map,lp);overlay[0]=map;});
                    int before=count();
                    main(()->dialog[0]=new ThemeDialog.Builder(activity).setTitle("选择已安装应用").setItems(new String[]{"快捷应用测试"},(d,n)->picked[0]=true).setNegativeButton("取消",null).show());
                    Thread.sleep(900);test.waitForIdleSync();
                    main(()->{require(dialog[0].getWindow().getDecorView().hasWindowFocus(),"owned picker has no real focus");require(dialog[0].getWindow().getAttributes().type==WindowManager.LayoutParams.TYPE_APPLICATION,"picker must need no overlay permission");require(overlay[0].isShown(),"map overlay disappeared");});
                    synchronized(sent){for(int n=before;n<sent.size();n++)require(!AmapFloatingController.CLOSE.equals(sent.get(n).getAction()),"owned picker caused map CLOSE");}
                    Rect row=new Rect();main(()->{View item=dialog[0].getListView().getChildAt(0);int[] xy=new int[2];item.getLocationOnScreen(xy);row.set(xy[0],xy[1],xy[0]+item.getWidth(),xy[1]+item.getHeight());});require(!Rect.intersects(row,bounds),"ordinary picker must not overlap the higher map window");
                    capture("map-owned-picker.png");
                    long t=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(t,t,MotionEvent.ACTION_DOWN,row.centerX(),row.centerY(),0));test.sendPointerSync(MotionEvent.obtain(t,t+40,MotionEvent.ACTION_UP,row.centerX(),row.centerY(),0));for(int n=0;n<30&&(!picked[0]||dialog[0].isShowing());n++)Thread.sleep(100);test.waitForIdleSync();
                    require(picked[0]&&!dialog[0].isShowing(),"map intercepted picker touch");
                    synchronized(sent){for(int n=before;n<sent.size();n++)require(!AmapFloatingController.CLOSE.equals(sent.get(n).getAction()),"picker dismissal rebuilt map");}
                }finally{main(()->{if(dialog[0]!=null&&dialog[0].isShowing())dialog[0].dismiss();if(overlay[0]!=null)wm.removeViewImmediate(overlay[0]);});}
            });
            check("owned shortcut picker dismisses and hides map before leaving desktop",()->{
                final AlertDialog[] dialog={null};main(()->dialog[0]=new ThemeDialog.Builder(activity).setTitle("选择已安装应用").setItems(new String[]{"测试应用"},null).show());Thread.sleep(300);
                Activity settings=test.startActivitySync(new Intent(test.getTargetContext(),SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                try{await(AmapFloatingController.CLOSE);require(!dialog[0].isShowing(),"owned system dialog survived Activity pause");}
                finally{main(settings::finish);}
                await(AmapFloatingController.SHOW);
            });
            check("destroyed controller cleanup cannot hide its replacement",()->{
                main(()->{try{controller.release();controller=new AmapFloatingController(config,i->{sent.add(new Intent(i));simulatedWindowVisible=AmapFloatingController.SHOW.equals(i.getAction());},()->true);Field f=MainActivity.class.getDeclaredField("floatingMap");f.setAccessible(true);f.set(activity,controller);retainDialogFocus();controller.bind(slot());activity.showPage(MainActivity.CARDS);}catch(Exception e){throw new RuntimeException(e);}});await(AmapFloatingController.SHOW);int before=count();Thread.sleep(1200);synchronized(sent){for(int n=before;n<sent.size();n++)require(!AmapFloatingController.CLOSE.equals(sent.get(n).getAction()),"old destroy sweep hid replacement");}
            });
            check("screen state and disabled preference suppress requests",()->{
                main(()->controller.setScreenOn(false));await(AmapFloatingController.CLOSE);noMoreRequests();main(()->controller.setScreenOn(true));await(AmapFloatingController.SHOW);
                main(()->{config.put("amapFloating",false);activity.render();});await(AmapFloatingController.CLOSE);noMoreRequests();main(()->{config.put("amapFloating",true);activity.render();});await(AmapFloatingController.SHOW);
            });
            check("actual Activity finish cancels shows and cleans late windows after destroy",()->{main(activity::finish);await(AmapFloatingController.CLOSE);for(int n=0;n<160&&!activity.isDestroyed();n++)Thread.sleep(50);require(activity.isDestroyed(),"Activity destruction incomplete");Thread.sleep(200);simulatedWindowVisible=true;Thread.sleep(750);require(!simulatedWindowVisible,"late window survived Activity destruction");noMoreRequests();});
        }catch(Throwable error){failed++;report.append("FAIL fixture ").append(error).append('\n');}
        finally{
            if(testCoordinator!=null)main(testCoordinator::hostPaused);
            if(activity!=null)main(()->{if(controller!=null)controller.release();activity.finish();});
            if(!backup.isEmpty())try{config.restore(backup);}catch(Exception error){failed++;report.append("FAIL restore ").append(error).append('\n');}
            if(hadDetected)config.put("amapFloatDetected",detected);else config.p.edit().remove("amapFloatDetected").apply();
        }
        report.append("AMAP_FLOAT_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');
        try(Writer writer=new OutputStreamWriter(new FileOutputStream(new File(test.getTargetContext().getFilesDir(),"amap-floating-report.txt")),"UTF-8")){writer.write(report.toString());}catch(Exception ignored){}
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
