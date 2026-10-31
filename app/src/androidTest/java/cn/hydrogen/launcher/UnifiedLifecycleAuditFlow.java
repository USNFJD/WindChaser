package cn.hydrogen.launcher;
import android.app.*;import android.content.*;import android.graphics.Rect;import android.os.*;import android.view.*;import java.lang.reflect.*;
/** Real embedded Amap window lifecycle, without a fake renderer or synthetic ACK. */
final class UnifiedLifecycleAuditFlow {
 private final Instrumentation test;private int passed;private final StringBuilder report=new StringBuilder("Production unified map lifecycle; actual native ACK only\n");private MainActivity home;private AmapFloatingController map;private AlertDialog popup;
 UnifiedLifecycleAuditFlow(Instrumentation value){test=value;}
 interface Condition{boolean get()throws Exception;}
 private Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 private void main(Runnable run){Throwable[] error={null};test.runOnMainSync(()->{try{run.run();}catch(Throwable problem){error[0]=problem;}});if(error[0]!=null)throw new AssertionError(error[0]);}
 private void waitFor(String name,Condition check,long limit)throws Exception{long end=SystemClock.elapsedRealtime()+limit;while(!check.get()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);require(check.get(),name);}
 private void require(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;report.append("PASS ").append(name).append('\n');}
 private View find(View view,String label){if(view.getContentDescription()!=null&&label.contentEquals(view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){View got=find(((ViewGroup)view).getChildAt(i),label);if(got!=null&&got.isShown())return got;}return null;}
 private void cards()throws Exception{main(()->home.showPage(MainActivity.CARDS));waitFor("second page has live native card",()->map.connected()&&(Boolean)field(map,"windowVisible"),15000);}
 void run(){Bundle result=new Bundle();boolean success=false;TestPreferences saved=new TestPreferences(test.getTargetContext());try{
  Context ctx=test.getTargetContext();Config config=new Config(ctx);config.put("amapFloating",true);config.put("quickSlot0","");
  home=(MainActivity)test.startActivitySync(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitFor("desktop owns focused window",()->home.hasWindowFocus(),12000);map=(AmapFloatingController)field(home,"floatingMap");cards();Object workspace=MapWorkspace.get(ctx);Rect original=new Rect((Rect)field(map,"lastBounds"));require(original.width()>100&&original.height()>100,"actual map card has nonempty bounds");
  for(int page:new int[]{MainActivity.MINIMAL,MainActivity.APPS,MainActivity.MINIMAL,MainActivity.APPS}){
   long before=(Long)field(map,"lastAckAt");main(()->home.showPage(page));Thread.sleep(750);require(!(Boolean)field(map,"windowVisible"),"other desktop page hides map and touch window: "+page);require((Boolean)field(map,"parked"),"internal page retains parked native map: "+page);require(MapWorkspace.get(ctx)==workspace,"internal page reuses same workspace: "+page);cards();waitFor("return receives fresh native ACK",()->(Long)field(map,"lastAckAt")>before,8000);require(original.equals((Rect)field(map,"lastBounds")),"return preserves card dimensions");
  }
  main(()->{View slot=find(home.getWindow().getDecorView(),"快捷区自定义应用1");if(slot==null)throw new AssertionError("missing actual shortcut slot");slot.performClick();});Thread.sleep(900);
  require(map.connected()&&(Boolean)field(map,"windowVisible"),"actual add-app picker keeps native map visible");long ack=(Long)field(map,"lastAckAt");waitFor("native map remains alive while app picker is open",()->(Long)field(map,"lastAckAt")>ack,6000);test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);Thread.sleep(400);
  main(()->ctx.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));waitFor("external full-screen activity removes map visibility",()->!(Boolean)field(map,"windowVisible"),8000);
  main(()->ctx.startActivity(new Intent(ctx,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)));waitFor("desktop regains focus",()->home.hasWindowFocus(),8000);cards();require(MapWorkspace.get(ctx)==workspace,"external app return reuses workspace");
  main(()->{popup=new ThemeDialog.Builder(home).setTitle("胎压与地图窗口回归").setItems(new String[]{"测试完成"},null).show();});Thread.sleep(400);require(map.connected()&&(Boolean)field(map,"windowVisible"),"second-page themed popup retains real map");main(popup::dismiss);popup=null;
  main(home::finish);waitFor("finishing desktop removes native map visibility",()->!(Boolean)field(map,"windowVisible"),8000);success=true;
 }catch(Throwable failure){report.append("FAIL ").append(failure).append('\n');}finally{if(popup!=null)main(popup::dismiss);saved.close();}
 report.append("UNIFIED_LIFECYCLE_RESULT checks=").append(passed).append(success?" PASS":" FAIL").append('\n');result.putString("stream",report.toString());test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
}
