package com.autonavi.amapauto;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.*;
import android.view.*;
import android.widget.FrameLayout;
import org.json.JSONObject;
import java.io.*;
import cn.windchaser.mapbridge.MapCardBridge;
import com.autonavi.eaglet.surfacemng.cfg.ConfigHmi;

/** Test APK only. Two drawn test surfaces, never a commercial map or navigation proof. */
public final class MainMapActivity extends Activity {
    private int surfaceCreated,surfaceDestroyed;private SurfaceView map,ui;private int touches,moves,cancels;private float x,y;private boolean paused;
    public int g=720,h=1280;public SurfaceView d,l;public View.OnTouchListener n;public boolean o,p,q;
    public static boolean nativeResumed;private boolean actuallyStopped,destroyed;private int frames;
    private String page="home",renderedPage="home";private int backgroundWrites,fixedBufferRequests,bufferWidth,bufferHeight,mapBufferWidth,mapBufferHeight;
    private int observedPlan=-1,actualMotionSamples,previousActualX;private long firstActualMotionAt;
    private final Choreographer.FrameCallback motionRecorder=new Choreographer.FrameCallback(){public void doFrame(long frameTime){
        if(destroyed)return;
        try{Object value=bridgeValue("overlay");Object count=bridgeValue("timelinePlans");
            if(value instanceof View&&count instanceof Number){View root=(View)value;int plans=((Number)count).intValue();int[] xy=new int[2];root.getLocationOnScreen(xy);
                if(plans!=observedPlan){observedPlan=plans;actualMotionSamples=0;firstActualMotionAt=0;previousActualX=xy[0];}
                long start=((Number)timelineValue("start")).longValue(),duration=((Number)timelineValue("duration")).longValue(),now=frameTime/1000000L;
                if(!Boolean.TRUE.equals(bridgeValue("parked"))&&now>=start&&now<start+duration&&xy[0]!=previousActualX){actualMotionSamples++;if(firstActualMotionAt==0)firstActualMotionAt=now;}
                previousActualX=xy[0];
            }
        }catch(Exception ignored){}Choreographer.getInstance().postFrameCallback(this);
    }};
    private boolean nativeStopCleanup;private final android.content.BroadcastReceiver stopFixture=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,android.content.Intent i){
        String action=i.getAction();
        if("cn.windchaser.fixture.BLOCK_ONCE".equals(action))try{
            java.lang.reflect.Field instance=MapCardBridge.class.getDeclaredField("instance");instance.setAccessible(true);Object bridge=instance.get(null);
            java.lang.reflect.Field session=MapCardBridge.class.getDeclaredField("session"),sequence=MapCardBridge.class.getDeclaredField("sequence");session.setAccessible(true);sequence.setAccessible(true);
            java.lang.reflect.Method block=MapCardBridge.class.getDeclaredMethod("block",long.class,long.class,String.class);block.setAccessible(true);block.invoke(bridge,session.getLong(bridge),sequence.getLong(bridge),"fixture_transient_task_failure");
        }catch(Exception error){throw new RuntimeException(error);}
        if("cn.windchaser.fixture.STATE".equals(action)){save();try{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(new File(getFilesDir(),"map-card-fixture.json"))){byte[] buffer=new byte[4096];int count;while((count=in.read(buffer))!=-1)bytes.write(buffer,0,count);}setResultData(bytes.toString("UTF-8"));}catch(Exception error){setResultData("{}");}}
        if("cn.windchaser.fixture.CLEANUP_STOP".equals(action)&&paused)clearNativeStop();
        if("cn.windchaser.fixture.HMI_BACKGROUND".equals(action)){backgroundWrites++;ConfigHmi.CONFIG_u_forebackState.a("0");ConfigHmi.CONFIG_u_wndFocusState.a("0");save();}
        if("cn.windchaser.fixture.RESET_LAYOUT".equals(action)){g=720;h=1280;ui.setLayoutParams(new FrameLayout.LayoutParams(1280,720));map.setLayoutParams(new FrameLayout.LayoutParams(1280,720));MapCardBridge.afterLayout(MainMapActivity.this);save();}
        if("cn.windchaser.fixture.UI_VIEWPORT_STALE".equals(action))try{Class.forName("vq").getMethod("i").invoke(null).getClass().getMethod("a",Surface.class,int.class,int.class,int.class).invoke(Class.forName("vq").getMethod("i").invoke(null),ui.getHolder().getSurface(),1,1280,720);save();}catch(Exception e){throw new RuntimeException(e);}
        if("cn.windchaser.fixture.FIX_BUFFER".equals(action)){fixedBufferRequests++;map.getHolder().setFixedSize(1280,720);ui.getHolder().setFixedSize(1280,720);save();}
    }};
    private final android.os.Handler ticker=new android.os.Handler();
    private boolean hmiVisible(){return "1".equals(ConfigHmi.CONFIG_u_forebackState.a())&&"1".equals(ConfigHmi.CONFIG_u_wndFocusState.a());}
    private final Runnable frame=new Runnable(){public void run(){if(destroyed)return;if(nativeResumed&&hmiVisible()){frames++;if(!Boolean.TRUE.equals(bridgeValue("parked")))draw(ui.getHolder());save();}ticker.postDelayed(this,100);}};
    @Override public void onCreate(Bundle state){
        super.onCreate(state);MapCardBridge.install(getApplication());getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(5894);
        FrameLayout root=new FrameLayout(this);map=new SurfaceView(this);ui=new SurfaceView(this);
        map.setId(getResources().getIdentifier("map_surface","id",getPackageName()));ui.setId(getResources().getIdentifier("ui_surface","id",getPackageName()));
        ui.setZOrderMediaOverlay(true);ui.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        d=map;l=ui;root.addView(map,new FrameLayout.LayoutParams(-1,-1));root.addView(ui,new FrameLayout.LayoutParams(-1,-1));
        SurfaceHolder.Callback callback=new SurfaceHolder.Callback(){public void surfaceCreated(SurfaceHolder h){surfaceCreated++;draw(h);save();}public void surfaceChanged(SurfaceHolder h,int f,int w,int v){if(h==ui.getHolder()){bufferWidth=w;bufferHeight=v;}else{mapBufferWidth=w;mapBufferHeight=v;}draw(h);save();}public void surfaceDestroyed(SurfaceHolder h){surfaceDestroyed++;save();}};
        map.getHolder().addCallback(callback);ui.getHolder().addCallback(callback);
        n=(v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_CANCEL)cancels++;if(event.getActionMasked()==MotionEvent.ACTION_MOVE)moves++;if(event.getActionMasked()==MotionEvent.ACTION_DOWN){touches++;x=event.getX();y=event.getY();page=x<bufferWidth/2f?"search":"settings";}if(hmiVisible())draw(ui.getHolder());save();return true;};ui.setOnTouchListener(n);setContentView(root);ticker.postDelayed(frame,100);Choreographer.getInstance().postFrameCallback(motionRecorder);android.content.IntentFilter filter=new android.content.IntentFilter("cn.windchaser.fixture.CLEANUP_STOP");filter.addAction("cn.windchaser.fixture.HMI_BACKGROUND");filter.addAction("cn.windchaser.fixture.FIX_BUFFER");filter.addAction("cn.windchaser.fixture.RESET_LAYOUT");filter.addAction("cn.windchaser.fixture.UI_VIEWPORT_STALE");filter.addAction("cn.windchaser.fixture.STATE");filter.addAction("cn.windchaser.fixture.BLOCK_ONCE");registerReceiver(stopFixture,filter);
    }
    private void draw(SurfaceHolder holder){Canvas c=null;try{c=holder.lockCanvas();if(c==null)return;Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);if(holder==map.getHolder())c.drawColor(0xff164b66);else{c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);p.setColor(Color.WHITE);p.setTextSize(24);c.drawText("TEST SURFACES - NOT AMAP",20,45,p);c.drawText("Touch count: "+touches+"   Page: "+page,20,80,p);p.setStyle(Paint.Style.STROKE);c.drawCircle(c.getWidth()/2f,c.getHeight()/2f,Math.min(c.getWidth(),c.getHeight())*.25f,p);renderedPage=page;}}catch(RuntimeException ignored){}finally{if(c!=null)holder.unlockCanvasAndPost(c);}}
    private Object bridgeValue(String name){try{java.lang.reflect.Field instance=MapCardBridge.class.getDeclaredField("instance");instance.setAccessible(true);Object bridge=instance.get(null);java.lang.reflect.Field f=MapCardBridge.class.getDeclaredField(name);f.setAccessible(true);return f.get(bridge);}catch(Exception e){return "unavailable";}}
    private Object timelineValue(String name){try{Object timeline=bridgeValue("windowTimeline");java.lang.reflect.Field f=timeline.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(timeline);}catch(Exception ignored){return 0;}}
    private int windowValue(String name){try{Object v=bridgeValue("overlay");return WindowManager.LayoutParams.class.getField(name).getInt(((View)v).getLayoutParams());}catch(Exception e){return 0;}}
    private void save(){try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(getFilesDir(),"map-card-fixture.json")),"UTF-8")){
        int[] location=new int[2];ui.getLocationOnScreen(location);Rect frame=ui.getHolder().getSurfaceFrame();
        out.write(new JSONObject().put("surface_created",surfaceCreated).put("surface_destroyed",surfaceDestroyed).put("surface_valid",ui.getHolder().getSurface().isValid()).put("overlay_identity",System.identityHashCode(bridgeValue("overlay"))).put("parked",bridgeValue("parked")).put("moving",bridgeValue("motionUntouchable")).put("layout_ready",bridgeValue("layoutReady")).put("layout_ready_at",bridgeValue("layoutConfirmedAt")).put("timeline_layout_at",bridgeValue("timelineLayoutConfirmedAt")).put("timeline_plans",bridgeValue("timelinePlans")).put("timeline_frames",bridgeValue("timelineFrames")).put("actual_motion_samples",actualMotionSamples).put("first_actual_motion_at",firstActualMotionAt).put("timeline_start",timelineValue("start")).put("timeline_duration",timelineValue("duration")).put("move_commands",bridgeValue("moveCommands")).put("window_x",windowValue("x")).put("window_flags",windowValue("flags")).put("bridge_added",bridgeValue("added")).put("bridge_sequence",bridgeValue("sequence")).put("bridge_desired",String.valueOf(bridgeValue("desired"))).put("ui_native_width",Class.forName("vq").getField("uiWidth").getInt(null)).put("ui_native_height",Class.forName("vq").getField("uiHeight").getInt(null)).put("ui_syncs",Class.forName("vq").getField("uiSyncs").getInt(null)).put("activity_width",h).put("activity_height",g).put("map_width",map.getWidth()).put("map_height",map.getHeight()).put("ui_width",ui.getWidth()).put("ui_height",ui.getHeight())
                .put("ui_left",location[0]).put("ui_top",location[1]).put("buffer_width",bufferWidth).put("buffer_height",bufferHeight).put("map_buffer_width",mapBufferWidth).put("map_buffer_height",mapBufferHeight).put("holder_frame_width",frame.width()).put("holder_frame_height",frame.height())
                .put("hmi_foreground",ConfigHmi.CONFIG_u_forebackState.a()).put("hmi_focus",ConfigHmi.CONFIG_u_wndFocusState.a()).put("page",page).put("rendered_page",renderedPage).put("background_writes",backgroundWrites).put("fixed_buffer_requests",fixedBufferRequests)
                .put("cancels",cancels).put("touches",touches).put("moves",moves).put("x",x).put("y",y).put("paused",paused).put("actually_stopped",actuallyStopped)
                .put("native_stop_cleanup",nativeStopCleanup).put("native_resumed",nativeResumed).put("frames",frames).put("native_paused_flag",o).put("native_stopped_flag",p)
                .put("pause_render",com.autonavi.eaglet.surfacemng.cfg.ConfigEaglet.enablePauseRender.d).put("starts",Class.forName("vq").getField("starts").getInt(null))
                .put("resumes",Class.forName("vq").getField("resumes").getInt(null)).put("native_width",Class.forName("de").getField("lastWidth").getInt(null)).put("native_height",Class.forName("de").getField("lastHeight").getInt(null)).toString());
    }catch(Exception ignored){}}
    @Override public void onStart(){super.onStart();actuallyStopped=p=false;ui.setOnTouchListener(n);}
    @Override public void onPause(){super.onPause();paused=o=true;q=false;nativeResumed=false;ConfigHmi.CONFIG_u_forebackState.a("0");ConfigHmi.CONFIG_u_wndFocusState.a("0");com.autonavi.eaglet.surfacemng.cfg.ConfigEaglet.enablePauseRender.b(1);MapCardBridge.afterLifecycle(this,1);save();}
    private void clearNativeStop(){nativeStopCleanup=p=true;ui.setOnTouchListener(null);nativeResumed=false;MapCardBridge.afterLifecycle(this,2);save();}
    @Override public void onStop(){super.onStop();actuallyStopped=true;clearNativeStop();}
    @Override public void onResume(){super.onResume();paused=o=false;q=true;nativeResumed=true;ConfigHmi.CONFIG_u_forebackState.a("1");ConfigHmi.CONFIG_u_wndFocusState.a("1");save();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);MapCardBridge.afterLifecycle(this,3);}
    @Override public void onDestroy(){destroyed=true;ticker.removeCallbacks(frame);unregisterReceiver(stopFixture);super.onDestroy();}
}
