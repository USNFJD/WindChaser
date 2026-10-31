package cn.windchaser.mapbridge;

import android.app.Activity;
import android.os.PowerManager;
import android.content.Context;
import android.graphics.Matrix;
import android.view.*;
import java.lang.reflect.*;

/** Bindings verified against the exact 6.5.0.601323 Java classes in the input APK.
 * Only the native view's state is restored. Android Activity lifecycle is never forged.
 */
final class Amap65Runtime {
    private long recoveryRevision,recoveredRevision=-1;
    void invalidateRecovery(){recoveryRevision++;}
    private Object dispatcher,jni,renderConfig;private Method start,resume,pause,stop,focus,attach,detach,setRender;
    private Field paused,stopped,running;private View.OnTouchListener touch;
    private int previousPauseRender;private volatile boolean entered;
    private Object hmiForeground,hmiFocus;private Method setHmi,getHmi;
    private String previousForeground,previousFocus;
    private View.OnLayoutChangeListener nativeLayout;
    private int lastWidth,lastHeight,lastUiWidth,lastUiHeight,lastUiGeneration;private Method uiChanged;private Field screenWidth,screenHeight;private int oldScreenWidth,oldScreenHeight;
    private View.OnTouchListener cardTouch;private boolean gestureActive,dropGesture;private MotionEvent lastTouch;
    private volatile int uiBufferWidth,uiBufferHeight,uiFormat=1;private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());private SurfaceView currentMap,currentUi;
    private final Runnable touchRefresh=()->{if(entered)try{syncViewport(currentMap,currentUi);refreshUiViewport(currentUi,true);refreshHmi();}catch(Exception e){android.util.Log.e("WindChaserMap","Post-touch viewport refresh failed",e);}};
    boolean ownsHmi(Object config){return entered&&(config==hmiForeground||config==hmiFocus);}
    void uiBufferSize(int width,int height){if(uiBufferWidth>0&&uiBufferHeight>0&&(uiBufferWidth!=width||uiBufferHeight!=height))cancelTouch();uiBufferWidth=width;uiBufferHeight=height;if(width==0||height==0)lastUiWidth=lastUiHeight=0;}
    void uiBufferFormat(int format){uiFormat=format;}

    private static Field field(Class<?> type,String name)throws Exception{
        for(Class<?> next=type;next!=null;next=next.getSuperclass())try{Field f=next.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    void enter(Activity activity,SurfaceView map,SurfaceView ui)throws Exception{
        if(entered){recover(activity,map,ui);return;}
        ClassLoader loader=activity.getClassLoader();Class<?> base=activity.getClass();
        if(field(base,"d").get(activity)!=map||field(base,"l").get(activity)!=ui)throw new IllegalStateException("6.5 native surface fields do not match");
        touch=(View.OnTouchListener)field(base,"n").get(activity);if(touch==null)throw new IllegalStateException("6.5 original touch listener not initialized");
        paused=field(base,"o");stopped=field(base,"p");running=field(base,"q");
        Class<?> events=Class.forName("vq",true,loader);dispatcher=events.getMethod("i").invoke(null);uiChanged=events.getMethod("a",android.view.Surface.class,int.class,int.class,int.class);
        try{screenHeight=field(base,"g");screenWidth=field(base,"h");oldScreenWidth=screenWidth.getInt(activity);oldScreenHeight=screenHeight.getInt(activity);}catch(NoSuchFieldException ignored){screenWidth=screenHeight=null;}currentMap=map;currentUi=ui;
        start=events.getMethod("e");resume=events.getMethod("d");pause=events.getMethod("b",boolean.class);stop=events.getMethod("c",boolean.class);focus=events.getMethod("e",boolean.class);
        Class<?> context=Class.forName("de",true,loader);jni=context.getMethod("A").invoke(null);attach=context.getMethod("a",View.class);detach=context.getMethod("b",View.class);
        nativeLayout=(View.OnLayoutChangeListener)field(context,"B").get(jni);
        Class<?> config=Class.forName("com.autonavi.eaglet.surfacemng.cfg.ConfigEaglet",true,loader);
        renderConfig=config.getField("enablePauseRender").get(null);previousPauseRender=field(renderConfig.getClass(),"d").getInt(renderConfig);setRender=renderConfig.getClass().getMethod("b",int.class);
        // PluginDummy.onActivityPaused independently sets u_forebackState=0.
        // Native start/resume alone does not undo that HMI configuration.
        Class<?> hmi=Class.forName("com.autonavi.eaglet.surfacemng.cfg.ConfigHmi",true,loader);
        hmiForeground=hmi.getField("CONFIG_u_forebackState").get(null);hmiFocus=hmi.getField("CONFIG_u_wndFocusState").get(null);
        setHmi=hmiForeground.getClass().getMethod("a",String.class);getHmi=hmiForeground.getClass().getMethod("a");
        previousForeground=(String)getHmi.invoke(hmiForeground);previousFocus=(String)getHmi.invoke(hmiFocus);
        lastWidth=lastHeight=lastUiWidth=lastUiHeight=lastUiGeneration=0;
        cardTouch=new View.OnTouchListener(){public boolean onTouch(View view,MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_DOWN)dropGesture=false;else if(dropGesture)return true;
            try{if(event.getActionMasked()==MotionEvent.ACTION_DOWN){syncViewport(map,ui);refreshUiViewport(ui,true);refreshHmi();}if(event.getActionMasked()==MotionEvent.ACTION_UP||event.getActionMasked()==MotionEvent.ACTION_CANCEL){handler.removeCallbacks(touchRefresh);handler.postDelayed(touchRefresh,120);}}catch(Exception e){android.util.Log.e("WindChaserMap","Cannot refresh card HMI state",e);}
            int width=uiBufferWidth,height=uiBufferHeight;
            if(view.getWidth()<=0||view.getHeight()<=0||width<=0||height<=0)return true;
            // API19 getSurfaceFrame() describes the window frame even when the
            // buffer is fixed at another size. surfaceChanged carries its true size.
            float sx=(float)width/view.getWidth(),sy=(float)height/view.getHeight();
            if(Math.abs(sx-1)<.0001f&&Math.abs(sy-1)<.0001f)return forwardTouch(view,event);
            // Fixed native buffers may survive moving/resizing the SurfaceView.
            // Transform ALL pointers, including history, into buffer coordinates.
            MotionEvent mapped=MotionEvent.obtain(event);
            try{Matrix matrix=new Matrix();matrix.setScale(sx,sy);mapped.transform(matrix);return forwardTouch(view,mapped);}finally{mapped.recycle();}
        }};
        entered=true;recover(activity,map,ui);
    }
    private boolean forwardTouch(View view,MotionEvent event){
        int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN)gestureActive=true;
        if(lastTouch!=null){lastTouch.recycle();lastTouch=null;}
        if(gestureActive)lastTouch=MotionEvent.obtain(event);
        boolean handled=touch.onTouch(view,event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){gestureActive=false;if(lastTouch!=null){lastTouch.recycle();lastTouch=null;}}
        return handled;
    }
    void cancelTouch(){
        if(gestureActive&&lastTouch!=null&&touch!=null&&currentUi!=null){MotionEvent cancel=MotionEvent.obtain(lastTouch);try{cancel.setAction(MotionEvent.ACTION_CANCEL);touch.onTouch(currentUi,cancel);}finally{cancel.recycle();}}
        gestureActive=false;dropGesture=true;if(lastTouch!=null){lastTouch.recycle();lastTouch=null;}
    }
    private void refreshHmi()throws Exception{
        if(!entered)return;
        setHmi.invoke(hmiForeground,"1");setHmi.invoke(hmiFocus,"1");
    }
    void syncViewport(SurfaceView map,SurfaceView ui)throws Exception{
        if(!entered)return;
        refreshHmi();
        int w=map.getWidth(),h=map.getHeight();
        if(w>0&&h>0&&nativeLayout!=null&&(w!=lastWidth||h!=lastHeight)){
            nativeLayout.onLayoutChange(map,map.getLeft(),map.getTop(),map.getRight(),map.getBottom(),0,0,lastWidth,lastHeight);
            lastWidth=w;lastHeight=h;
        }
        refreshUiViewport(ui,false);
    }
    private void refreshUiViewport(SurfaceView ui,boolean force)throws Exception{
        if(!entered||ui==null||!ui.getHolder().getSurface().isValid())return;
        int w=uiBufferWidth,h=uiBufferHeight,generation=System.identityHashCode(ui.getHolder().getSurface());
        // Map and Eaglet UI have separate native size notifications. Replaying only
        // de.B updates map proportions while UI hit-testing can retain old bounds.
        if(w>0&&h>0&&(force||w!=lastUiWidth||h!=lastUiHeight||generation!=lastUiGeneration)){
            uiChanged.invoke(dispatcher,ui.getHolder().getSurface(),uiFormat,w,h);
            lastUiWidth=w;lastUiHeight=h;lastUiGeneration=generation;
        }
    }
    void pinActivitySize(Activity a,int width,int height)throws Exception{if(entered&&screenWidth!=null){screenWidth.setInt(a,width);screenHeight.setInt(a,height);}}
    void recover(Activity activity,SurfaceView map,SurfaceView ui)throws Exception{
        if(!entered)return;
        // rd.onStop() clears these even if a SurfaceView is still visible in a card.
        currentMap=map;currentUi=ui;ui.setOnTouchListener(cardTouch);pinActivitySize(activity,map.getWidth(),map.getHeight());
        // addView may run before surfaceCreated. Wait for the original native
        // callbacks rather than starting once with an invalid surface and again
        // immediately afterwards. A stable surface/lifecycle revision recovers once.
        if(!map.getHolder().getSurface().isValid()||!ui.getHolder().getSurface().isValid())return;
        boolean interrupted=paused.getBoolean(activity)||stopped.getBoolean(activity)||!running.getBoolean(activity);
        if(recoveredRevision!=recoveryRevision||interrupted){
            detach.invoke(jni,map);attach.invoke(jni,map);
            paused.setBoolean(activity,false);stopped.setBoolean(activity,false);running.setBoolean(activity,true);
            setRender.invoke(renderConfig,0);start.invoke(dispatcher);resume.invoke(dispatcher);focus.invoke(dispatcher,true);
            recoveredRevision=recoveryRevision;
            android.util.Log.i("WindChaserMap","Native card recovered, revision="+recoveryRevision+", lifecycle="+interrupted);
        }
        syncViewport(map,ui);
    }
    void leave(Activity activity,SurfaceView map,SurfaceView ui,boolean resuming,boolean actualPaused,boolean actualStopped)throws Exception{
        if(!entered)return;cancelTouch();entered=false;recoveredRevision=-1;dropGesture=false;handler.removeCallbacks(touchRefresh);
        try{
            if(!resuming){
                focus.invoke(dispatcher,false);
                if(actualPaused)pause.invoke(dispatcher,false);
                if(actualStopped)stop.invoke(dispatcher,false);
                paused.setBoolean(activity,actualPaused);stopped.setBoolean(activity,actualStopped);running.setBoolean(activity,!actualPaused);
                if(actualStopped){ui.setOnTouchListener(null);detach.invoke(jni,map);}
            }
        }finally{
            try{
                // The Activity is genuinely in the background after closing a card.
                // A resumed Activity owns its current foreground state again.
                setHmi.invoke(hmiForeground,resuming?"1":actualPaused?"0":previousForeground);
                setHmi.invoke(hmiFocus,resuming?"1":"0");
            }finally{
            PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);
            setRender.invoke(renderConfig,power!=null&&!power.isScreenOn()?1:previousPauseRender);
            ui.setOnTouchListener(actualStopped&&!resuming?null:touch);
            if(screenWidth!=null){screenWidth.setInt(activity,oldScreenWidth);screenHeight.setInt(activity,oldScreenHeight);}screenWidth=screenHeight=null;currentMap=currentUi=null;uiChanged=null;touch=null;cardTouch=null;dispatcher=null;jni=null;renderConfig=null;nativeLayout=null;hmiForeground=hmiFocus=null;
            }
        }
    }
}
