package cn.hydrogen.launcher;
import android.app.*;import android.content.*;import android.graphics.*;import android.os.*;import android.view.*;import android.widget.*;
/** A real API19 SurfaceView experiment, independent of commercial Amap. */
final class MapParkProbe {
 final Instrumentation test; MapParkProbe(Instrumentation t){test=t;}
 volatile int created,destroyed,changed;SurfaceView surface;FrameLayout root;WindowManager wm;WindowManager.LayoutParams p;Activity activity;
 void main(Runnable r){test.runOnMainSync(r);test.waitForIdleSync();}
 void run(){String result;try{
 activity=test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
 main(()->{wm=(WindowManager)test.getTargetContext().getSystemService(Context.WINDOW_SERVICE);root=new FrameLayout(test.getTargetContext());surface=new SurfaceView(test.getTargetContext());surface.getHolder().addCallback(new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder h){created++;}public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){changed++;Canvas c=h.lockCanvas();if(c!=null){c.drawColor(Color.MAGENTA);h.unlockCanvasAndPost(c);}}public void surfaceDestroyed(SurfaceHolder h){destroyed++;}});root.addView(surface,new FrameLayout.LayoutParams(240,180));p=new WindowManager.LayoutParams(240,180,2002,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.x=70;p.y=70;wm.addView(root,p);});
 Thread.sleep(500);int c=created,d=destroyed,ch=changed;if(c<1||!surface.getHolder().getSurface().isValid())throw new AssertionError("initial surface invalid");
 for(int i=0;i<6;i++){main(()->{p.x=-2048;p.flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;wm.updateViewLayout(root,p);});Thread.sleep(120);if(!surface.getHolder().getSurface().isValid())throw new AssertionError("park invalid");main(()->{p.x=70;p.flags&=~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;wm.updateViewLayout(root,p);});Thread.sleep(120);}
 if(created!=c||destroyed!=d)throw new AssertionError("surface lifecycle changed "+c+"/"+d+" -> "+created+"/"+destroyed);result="PARK_PROBE PASS created="+created+" destroyed="+destroyed+" changed="+ch+"->"+changed+" valid="+surface.getHolder().getSurface().isValid();
 }catch(Throwable e){result="PARK_PROBE FAIL "+e;}finally{main(()->{if(root!=null&&root.getParent()!=null)wm.removeViewImmediate(root);if(activity!=null)activity.finish();});}Bundle out=new Bundle();out.putString("stream",result+"\n");test.finish(result.contains("PASS")?-1:0,out);}
}
