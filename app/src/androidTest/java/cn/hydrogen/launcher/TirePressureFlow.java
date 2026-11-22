package cn.hydrogen.launcher;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import java.lang.reflect.*;import java.io.*;
/** Uses the actual unified bootstrap, actual dock and launcher-owned dialog. No TPMS telemetry is fabricated. */
final class TirePressureFlow {
 final Instrumentation test;MainActivity activity;int passed;StringBuilder report=new StringBuilder();
 TirePressureFlow(Instrumentation value){test=value;}
 void require(boolean value,String message){if(!value)throw new AssertionError(message);passed++;report.append("PASS ").append(message).append('\n');}
 Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 TextView find(View view,String text){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))return (TextView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){TextView found=find(group.getChildAt(n),text);if(found!=null)return found;}}return null;}
 void findAll(View view,String text,java.util.List<View> result){if(view instanceof TextView&&text.contentEquals(((TextView)view).getText()))result.add(view);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++)findAll(group.getChildAt(n),text,result);}}
 View description(View view,String text){if(text.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){View found=description(group.getChildAt(n),text);if(found!=null)return found;}}return null;}
 android.graphics.Rect rectangle(View view){int[] xy=new int[2];view.getLocationOnScreen(xy);return new android.graphics.Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight());}
 ScrollView scrolling(View view){if(view instanceof ScrollView)return (ScrollView)view;if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){ScrollView found=scrolling(group.getChildAt(n));if(found!=null)return found;}}return null;}
 AlertDialog dialog()throws Exception{return (AlertDialog)field(activity,"tirePressureDialog");}
 void shot(String name)throws Exception{if(Build.VERSION.SDK_INT>=18){android.graphics.Bitmap bitmap=test.getUiAutomation().takeScreenshot();if(bitmap!=null)try(FileOutputStream out=new FileOutputStream(new File(activity.getExternalFilesDir(null),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();}}}
 void run(){Bundle result=new Bundle();TestPreferences saved=new TestPreferences(test.getTargetContext());try{
  long until=SystemClock.elapsedRealtime()+15000;long focusedSince=0;
  while(SystemClock.elapsedRealtime()<until){
   test.runOnMainSync(()->{try{activity=(MainActivity)((java.lang.ref.WeakReference<?>)field(MapWorkspace.get(test.getTargetContext()),"main")).get();}catch(Exception e){throw new RuntimeException(e);}});
   if(activity!=null&&activity.hasWindowFocus()&&!activity.isFinishing()){if(focusedSince==0)focusedSince=SystemClock.elapsedRealtime();if(SystemClock.elapsedRealtime()-focusedSince>=800)break;}else focusedSince=0;Thread.sleep(50);
  }
  require("追风桌面".contentEquals(activity.getPackageManager().getApplicationLabel(activity.getApplicationInfo())),"installed app display name is exactly 追风桌面");
  require(activity!=null&&!activity.isFinishing()&&activity.hasWindowFocus()&&focusedSince>0,"reuse the admitted focused desktop without launching another Activity");
  test.runOnMainSync(()->{new Config(activity).put("amapFloating",true);activity.render();activity.showPage(MainActivity.CARDS);});
  long mapUntil=SystemClock.elapsedRealtime()+10000;boolean[] baseMap={false};
  do{test.runOnMainSync(()->baseMap[0]=activity.mapCardConnected());if(baseMap[0])break;Thread.sleep(100);}while(SystemClock.elapsedRealtime()<mapUntil);
  if(!baseMap[0]){report.append("before state page=").append(activity.visiblePage()).append(" eligible=").append(activity.mapDialogEligible()).append(" focus=").append(activity.hasWindowFocus()).append("\n");shot("final34-tpms-baseline-failure.png");}require(baseMap[0],"native map card is connected before opening the window");
  View[] buttons=new View[2];test.runOnMainSync(()->{try{LinearLayout dock=(LinearLayout)field(activity,"dock");buttons[0]=dock.getChildAt(4);buttons[1]=dock.getChildAt(5);require(find(buttons[0],"胎压")!=null&&find(buttons[1],"主题设置")!=null,"tire pressure precedes theme settings on page two");buttons[0].performClick();}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(700);
  AlertDialog first=dialog();require(first!=null&&first.isShowing(),"tire pressure opens a launcher-owned window");View decor=first.getWindow().getDecorView();
  require(find(decor,"左前")!=null&&find(decor,"右前")!=null&&find(decor,"左后")!=null&&find(decor,"右后")!=null,"four wheel positions are visible");
  View car=description(decor,"汽车俯视图，车头朝上；左前、右前、左后、右后");
  android.graphics.Rect carArea=new android.graphics.Rect();
  require(car!=null&&car.getGlobalVisibleRect(carArea)&&carArea.width()>=40&&carArea.height()>=120,"top-down car is visible at actual screen size");
  android.graphics.Rect leftFront=rectangle(find(decor,"左前")),rightFront=rectangle(find(decor,"右前")),leftRear=rectangle(find(decor,"左后")),rightRear=rectangle(find(decor,"右后"));
  carArea=rectangle(car);
  report.append("wheel rectangles car=").append(carArea).append(" LF=").append(leftFront).append(" RF=").append(rightFront).append(" LR=").append(leftRear).append(" RR=").append(rightRear).append("\n");shot("final34-tpms-layout.png");
  require(leftFront.right<=carArea.left&&rightFront.left>=carArea.right&&leftRear.top>leftFront.top&&rightRear.top>rightFront.top,"wheel readouts match the car corners without overlapping the vehicle");
  require(find(decor,"尚未接入车机胎压数据")==null&&find(decor,"接入胎压模块后显示实时胎压与温度")==null&&find(decor,"— bar")!=null,"requested hints are absent and missing sensors keep placeholder values");
  android.util.DisplayMetrics dm=activity.getResources().getDisplayMetrics();require(decor.getWidth()>=Math.min(220*dm.density,dm.widthPixels*.25f)&&decor.getWidth()<=dm.widthPixels*.85f&&decor.getHeight()<=dm.heightPixels*.9f,"small tire window fits current display");
  android.graphics.Rect actual=new android.graphics.Rect();require(find(decor,"左前").getGlobalVisibleRect(actual)&&actual.width()>20&&actual.left>=0&&actual.right<=dm.widthPixels,"wheel labels have a real visible rectangle on screen");
  java.util.List<View> temperatures=new java.util.ArrayList<>();findAll(decor,"— ℃",temperatures);boolean visibleTemperatures=temperatures.size()==4;for(View temperature:temperatures){android.graphics.Rect rectangle=new android.graphics.Rect();visibleTemperatures&=temperature.getGlobalVisibleRect(rectangle)&&rectangle.height()>=temperature.getHeight();}require(visibleTemperatures,"all four temperature readouts fit without scrolling at default text size");
  ScrollView compactScroll=scrolling(decor);android.graphics.Rect viewport=rectangle(compactScroll),content=rectangle((View)car.getParent());int topGap=content.top-viewport.top,bottomGap=viewport.bottom-content.bottom;
  report.append("center gaps top=").append(topGap).append(" bottom=").append(bottomGap).append(" panelHeight=").append(decor.getHeight()).append("\n");
  require(topGap>=0&&bottomGap>=0&&Math.abs(topGap-bottomGap)<=Ui.dp(activity,12),"wheel content is vertically centered with balanced top and bottom spacing");
  boolean[] map={false};test.runOnMainSync(()->{try{Object controller=field(activity,"floatingMap");map[0]=activity.mapCardConnected()&&(Boolean)field(controller,"windowVisible")&&!(Boolean)field(controller,"parked");}catch(Exception e){throw new RuntimeException(e);}});report.append("state page=").append(activity.visiblePage()).append(" eligible=").append(activity.mapDialogEligible()).append(" focus=").append(decor.hasWindowFocus()).append(" type=").append(first.getWindow().getAttributes().type).append(" area=").append(activity.mapDialogArea()).append("\n");shot("final34-tpms-before-assert.png");require(map[0],"native Amap card remains connected while tire window is open");shot("final34-tpms-cards.png");
  test.runOnMainSync(()->buttons[0].performClick());require(dialog()==first,"repeated taps reuse the same window");
  View close=description(decor,"关闭胎压窗口");android.graphics.Rect closeArea=new android.graphics.Rect();
  require(close!=null&&close.getGlobalVisibleRect(closeArea)&&closeArea.width()>=43*dm.density&&closeArea.height()>=43*dm.density,"corner close button has a visible 44dp touch target");
  int heightCap=Ui.dp(activity,dm.heightPixels/dm.density<540?244:288);require(decor.getHeight()<=heightCap+2,"native alert does not expand the compact panel height after showing");
  android.graphics.Rect before=rectangle(decor);Thread.sleep(1100);require(before.equals(rectangle(decor)),"tire panel does not move after focus and map heartbeat settle");
  test.runOnMainSync(close::performClick);Thread.sleep(200);require(dialog()==null,"corner close dismisses and releases the dialog reference");
  test.runOnMainSync(()->activity.showPage(MainActivity.APPS));Thread.sleep(500);test.runOnMainSync(()->buttons[0].performClick());Thread.sleep(300);
  require(dialog()!=null&&dialog().isShowing(),"page three uses the same tire-pressure entry");require(!(Boolean)field(field(activity,"floatingMap"),"windowVisible"),"tire dialog on page three does not expose the parked map");shot("final34-tpms-apps.png");
  AlertDialog last=dialog();test.runOnMainSync(last::dismiss);
  Config config=(Config)field(activity,"cfg");int originalSize=config.n("dialogTextSize",100);
  try{test.runOnMainSync(()->{config.put("dialogTextSize",150);buttons[0].performClick();});Thread.sleep(400);
   AlertDialog enlarged=dialog();View enlargedDecor=enlarged.getWindow().getDecorView();View enlargedClose=description(enlargedDecor,"关闭胎压窗口");android.graphics.Rect visibleClose=new android.graphics.Rect();
   require(enlargedClose!=null&&enlargedClose.getGlobalVisibleRect(visibleClose)&&visibleClose.height()>=43*dm.density,"larger text leaves the fixed close button accessible");
   ScrollView scroll=scrolling(enlargedDecor);test.runOnMainSync(()->scroll.fullScroll(View.FOCUS_DOWN));Thread.sleep(250);
   java.util.List<View> bottomReadings=new java.util.ArrayList<>();findAll(enlargedDecor,"— ℃",bottomReadings);android.graphics.Rect lastArea=new android.graphics.Rect();View lastReading=bottomReadings.get(bottomReadings.size()-1);
   require(lastReading.getGlobalVisibleRect(lastArea)&&lastArea.height()>=lastReading.getHeight(),"larger text can scroll to rear temperature readings without growing the window");
   require(find(enlargedDecor,"接入胎压模块后显示实时胎压与温度")==null,"removed footer does not return with large text");
   require(enlargedDecor.getHeight()<=dm.heightPixels*.9f,"large-text panel remains bounded by the screen");shot("final34-tpms-large-text.png");test.runOnMainSync(enlarged::dismiss);
  }finally{test.runOnMainSync(()->config.put("dialogTextSize",originalSize));}
  test.runOnMainSync(()->activity.showPage(MainActivity.MINIMAL));Thread.sleep(350);
  boolean[] dockHidden={false};test.runOnMainSync(()->{try{dockHidden[0]=!((View)field(activity,"dock")).isShown();}catch(Exception e){throw new RuntimeException(e);}});require(dockHidden[0],"minimal first page keeps its existing layout");
  report.append("RESULT tire-pressure checks=").append(passed).append(" PASS; native modules admitted; live sensor source unavailable\n");result.putString("stream",report.toString());test.finish(Activity.RESULT_OK,result);
 }catch(Throwable error){report.append("FAIL ").append(error).append('\n');result.putString("stream",report.toString());test.finish(Activity.RESULT_CANCELED,result);}finally{saved.close();if(activity!=null&&!activity.isFinishing())test.runOnMainSync(activity::render);}}
}
