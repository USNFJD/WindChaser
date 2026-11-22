package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.*;

/** Actual Android dialogs and PSS sampling; synthetic TPMS is an explicit adapter fixture. */
final class RefinementFlow {
    private final Instrumentation test;private int passed;private final StringBuilder report=new StringBuilder("Refinements: actual Android windows/PSS; sensor fixture only\n");
    RefinementFlow(Instrumentation test){this.test=test;}
    private void require(boolean ok,String name){if(!ok)throw new AssertionError(name);passed++;report.append("PASS ").append(name).append('\n');}
    private Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private TextView find(View v,String text){if(v instanceof TextView&&text.contentEquals(((TextView)v).getText()))return (TextView)v;if(v instanceof ViewGroup)for(int n=0;n<((ViewGroup)v).getChildCount();n++){TextView found=find(((ViewGroup)v).getChildAt(n),text);if(found!=null)return found;}return null;}
    void run(){MainActivity activity=null;AlertDialog[] window={null};Config cfg=new Config(test.getTargetContext());String backup="";TestPreferences preferences=new TestPreferences(test.getTargetContext());Bundle result=new Bundle();boolean success=false;
        try{
            backup=cfg.exportJson();long now=SystemClock.elapsedRealtime();
            TirePressureData.Snapshot absent=TirePressureData.snapshot();require(!absent.installed&&!absent.fresh(0,now),"missing hardware has no usable reading");
            TirePressureData.Wheel live=new TirePressureData.Wheel(2.35f,28f,now);
            TirePressureData.Wheel zero=new TirePressureData.Wheel(0f,0f,now);
            TirePressureData.Wheel old=new TirePressureData.Wheel(2.1f,25f,now-11000);
            TirePressureData.Wheel invalid=new TirePressureData.Wheel(Float.NaN,Float.POSITIVE_INFINITY,now);
            TirePressureData.Snapshot measured=new TirePressureData.Snapshot(true,true,"sensor fixture",10000,new TirePressureData.Wheel[]{live,zero,old,invalid});
            require(measured.fresh(0,now)&&measured.fresh(1,now)&&zero.bar==0f,"fresh real values include zero pressure without inventing normality");
            require(!measured.fresh(2,now)&&invalid.bar==null&&invalid.celsius==null,"old and non-finite data cannot appear current");
            require(!measured.fresh(0,now-100)&&!TirePressureData.Snapshot.disconnected("fixture").fresh(0,now),"future timestamps and disconnected sources are rejected");
            TirePressureData.attach(()->{throw new IllegalStateException("offline");});require(!TirePressureData.snapshot().connected,"adapter failure produces disconnected status");TirePressureData.attach(null);
            AppMemory memory=AppMemory.capture(test.getTargetContext());long total=0;boolean self=false;for(AppMemory.Item item:memory.items){total+=item.kb;self|=item.pid==android.os.Process.myPid();}
            require(self&&memory.kb>0&&total==memory.kb,"actual same-UID process PSS is measured and summed");report.append("PSS sample=").append(memory.total()).append(" processes=").append(memory.items.size()).append('\n');
            cfg.put("dialogAlpha",55);cfg.put("dialogDim",9);cfg.put("dialogTextSize",120);cfg.put("dialogSurface","#123456");String saved=cfg.exportJson();cfg.put("dialogAlpha",90);cfg.restore(saved);require(cfg.n("dialogAlpha",0)==55&&cfg.n("dialogTextSize",0)==120,"popup appearance survives config export and restore");
            boolean rejected=false;try{cfg.restore("{\"format\":\"hydrogen-config\",\"version\":1,\"settings\":{\"dialogAlpha\":101}}");}catch(Exception expected){rejected=true;}require(rejected&&cfg.n("dialogAlpha",0)==55,"invalid popup opacity cannot modify saved settings");
            activity=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));final MainActivity host=activity;
            test.runOnMainSync(()->host.showPage(MainActivity.CARDS));Thread.sleep(800);
            test.runOnMainSync(()->window[0]=new ThemeDialog.Builder(host).setTitle("弹窗外观测试").setItems(new String[]{"第一项","第二项"},null).show());Thread.sleep(700);
            AlertDialog dialog=window[0];View decor=dialog.getWindow().getDecorView();require(dialog.getWindow().getAttributes().dimAmount==0&&(dialog.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_DIM_BEHIND)==0,"map-side dialog preserves map visibility without global dim");
            int[] color={0};test.runOnMainSync(()->{Bitmap pixel=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);Drawable background=decor.getBackground();Rect bounds=new Rect(background.getBounds());background.setBounds(0,0,80,80);background.draw(new Canvas(pixel));color[0]=pixel.getPixel(40,40);background.setBounds(bounds);pixel.recycle();});int ink=color[0];report.append("menu pixel=").append(Integer.toHexString(ink)).append('\n');require(Math.abs(Color.alpha(ink)-140)<=1&&Math.abs(Color.red(ink)-0x12)<=2&&Math.abs(Color.green(ink)-0x34)<=2&&Math.abs(Color.blue(ink)-0x56)<=2,"actual menu surface uses selected opacity and color");
            TextView item=find(decor,"第一项");require(item!=null&&item.getTextSize()>18*host.getResources().getDisplayMetrics().scaledDensity,"menu labels follow the popup font setting");
            AmapFloatingController map=(AmapFloatingController)field(host,"floatingMap");long ackBefore=(Long)field(map,"lastAckAt"),sequenceBefore=(Long)field(map,"sequence");
            Rect first=new Rect();decor.getGlobalVisibleRect(first);Thread.sleep(2300);Rect second=new Rect();decor.getGlobalVisibleRect(second);require(first.equals(second),"popup geometry stays fixed through map liveness checks");
            require((Long)field(map,"lastAckAt")>ackBefore&&(Long)field(map,"sequence")==sequenceBefore,"map liveness reuses the same window sequence and receives an actual native ACK");
            require(decor.getHeight()<=host.getResources().getDisplayMetrics().heightPixels*.65f,"menu retains bounded height");
            test.runOnMainSync(dialog::dismiss);window[0]=null;
            final int[] reads={0};TirePressureData.attach(()->{reads[0]++;return new TirePressureData.Snapshot(true,true,"sensor fixture",10000,new TirePressureData.Wheel[]{live,zero,old,invalid});});
            test.runOnMainSync(()->window[0]=TirePressureDialog.show(host));Thread.sleep(350);
            require(find(window[0].getWindow().getDecorView(),"数据已过期")!=null,"TPMS visibly marks expired fixture data");
            AlertDialog tire=window[0];test.runOnMainSync(tire::dismiss);window[0]=null;int before=reads[0];Thread.sleep(1200);require(reads[0]==before,"TPMS polling stops when its window is dismissed");
            require(((AmapFloatingController)field(host,"floatingMap")).connected(),"map connection survives launcher-owned popup lifecycle");
            test.runOnMainSync(()->host.showPage(MainActivity.APPS));Thread.sleep(400);
            test.runOnMainSync(()->window[0]=new ThemeDialog.Builder(host).setTitle("弹窗暗度测试").setItems(new String[]{"第一项"},null).show());Thread.sleep(350);
            require(Math.abs(window[0].getWindow().getAttributes().dimAmount-.09f)<.001f,"non-map dialog uses configured surrounding dim");
            AlertDialog appsDialog=window[0];test.runOnMainSync(appsDialog::dismiss);window[0]=null;
            report.append("REFINEMENT_RESULT checks=").append(passed).append(" PASS\n");result.putString("stream",report.toString());success=true;
        }catch(Throwable error){report.append("FAIL ").append(error).append('\n');result.putString("stream",report.toString());}
        finally{TirePressureData.attach(null);if(window[0]!=null){AlertDialog last=window[0];test.runOnMainSync(last::dismiss);}preferences.close();}
        test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
