package cn.windchaser.startup;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.os.*;
import android.view.*;
import java.io.*;

/** Select startup artwork and acknowledge its completion for launcher-initiated sessions. */
public final class DualSplash {
    // These helper classes are rebuilt only into the single-APK experiment.
    // Keep original initialization, privacy dialogs and completion callbacks;
    // suppress startup artwork without shortcutting any ready signal.
    private static final boolean NO_ARTWORK=true;
    public static final String FLAG="windchaser.startup.custom_splash";
    public static final String DEADLINE="windchaser.startup.splash_deadline";
    public static final String READY_CALLBACK="windchaser.startup.ready_callback";
    public static final String CALLBACK="windchaser.startup.callback",TOKEN="windchaser.startup.token",STEP="windchaser.startup.step";
    public static final String PREPARE="cn.windchaser.startup.PREPARE",PERMISSION="cn.windchaser.unified.permission.STARTUP_READY";
    private static volatile long automaticUntil;
    private static PendingIntent readyCallback;private static Messenger callback;private static String token;private static int step;
    private static PendingDraw pending;private static CompletionRetry completionRetry;
    private static final StartupReadyPolicy mapPolicy=new StartupReadyPolicy();
    private static java.lang.ref.WeakReference<View> mapSplashView,mapCoverView;
    private static boolean returnHome;private static int homePage;
    private static boolean registered;private static String completedToken;private static int completedStep;
    private static java.lang.ref.WeakReference<Activity> currentActivity;
    private static Bitmap mark;
    private DualSplash(){}
    public static void select(Intent intent){
        long now=SystemClock.elapsedRealtime(),deadline=0;
        try{if(intent!=null&&intent.getBooleanExtra(FLAG,false))deadline=intent.getLongExtra(DEADLINE,0);}catch(RuntimeException invalid){}
        if(pending!=null)pending.cancel();if(completionRetry!=null)completionRetry.cancel();
        automaticUntil=deadline>now&&deadline<=now+60000?deadline:0;
        readyCallback=null;callback=null;token=null;step=0;returnHome=false;homePage=0;
        if(automatic())try{readyCallback=intent.getParcelableExtra(READY_CALLBACK);callback=intent.getParcelableExtra(CALLBACK);token=intent.getStringExtra(TOKEN);step=intent.getIntExtra(STEP,0);returnHome=intent.getBooleanExtra("windchaser.startup.return_home",false);homePage=intent.getIntExtra("windchaser.startup.page",0);}catch(RuntimeException invalid){callback=null;}
    }
    public static boolean automatic(){return automaticUntil>SystemClock.elapsedRealtime();}
    private static void install(Activity activity){
        if(registered)return;Context app=activity.getApplicationContext();
        RuntimeProbe.install(app);
        app.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context context,Intent request){
            if(request==null||!request.getBooleanExtra(FLAG,false))return;
            String incoming=request.getStringExtra(TOKEN);int next=request.getIntExtra(STEP,0);
            if(incoming==null||(incoming.equals(completedToken)&&next==completedStep))return;
            if(!incoming.equals(token)||next!=step||!automatic())select(request);
            Activity active=currentActivity==null?null:currentActivity.get();
            if(active!=null){if(context.getPackageName().equals("com.autonavi.amapauto"))mapResumed(active);else qqReady(active);}
        }},new IntentFilter(PREPARE),PERMISSION,null);
        IntentFilter power=new IntentFilter(Intent.ACTION_SCREEN_OFF);power.addAction(Intent.ACTION_SHUTDOWN);
        app.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(completionRetry!=null)completionRetry.cancel();if(pending!=null)pending.cancel();automaticUntil=0;readyCallback=null;callback=null;token=null;
        }},power);registered=true;
    }
    public static void begin(Activity activity){install(activity);StartupCurtain.select(activity,activity.getIntent());if(activity.getPackageName().equals("com.autonavi.amapauto")){currentActivity=new java.lang.ref.WeakReference<>(activity);mapPolicy.created();mapSplashView=mapCoverView=null;}select(activity.getIntent());window(activity);}
    public static void incoming(Activity activity,Intent intent){install(activity);StartupCurtain.select(activity,intent);select(intent);window(activity);}
    public static void forward(Intent intent){StartupCurtain.forward(intent);if(automatic())intent.putExtra(FLAG,true).putExtra(DEADLINE,automaticUntil).putExtra(READY_CALLBACK,readyCallback).putExtra(CALLBACK,callback).putExtra(TOKEN,token).putExtra(STEP,step).putExtra("windchaser.startup.return_home",returnHome).putExtra("windchaser.startup.page",homePage);}
    public static void mainBegin(Activity activity){install(activity);StartupCurtain.select(activity,activity.getIntent());
        // AppStarter may build a new internal Intent without copying extras.
        // Keep its bounded automatic session across that internal transition.
        Intent request=activity.getIntent();
        if(!automatic()||(request!=null&&request.getBooleanExtra(FLAG,false)))select(request);
    }
    public static void qqReady(Activity activity){currentActivity=new java.lang.ref.WeakReference<>(activity);afterDraw(activity);}
    public static void mapSplashAdded(Activity activity){
        currentActivity=new java.lang.ref.WeakReference<>(activity);mapPolicy.added();
        // An artwork window can be added after onResume. Any warm-start guess
        // must yield to the real animation and start fresh when it ends.
        if(completionRetry!=null)completionRetry.cancel();if(pending!=null)pending.cancel();
    }
    public static void mapSplashState(Activity activity,View view){if(NO_ARTWORK)view.setAlpha(0f);mapSplashAdded(activity);mapSplashView=new java.lang.ref.WeakReference<>(view);afterDraw(activity);}
    public static void mapFirstFrame(){Activity a=currentActivity==null?null:currentActivity.get();if(a!=null)afterDraw(a);}
    public static void mapSplashRemoved(Activity activity){currentActivity=new java.lang.ref.WeakReference<>(activity);mapPolicy.removed();mapSplashView=null;afterDraw(activity);}
    private static void clearMapCoverArtwork(Activity activity,View cover){
        if(!NO_ARTWORK||cover==null)return;
        int id=activity.getResources().getIdentifier("cover","id",activity.getPackageName());
        // main_map_view uses this one dedicated ImageView for startup artwork.
        // Keep its lifecycle/visibility and both SurfaceViews untouched.
        if(id!=0&&cover.getId()==id&&cover instanceof android.widget.ImageView){
            cover.setBackgroundColor(0xff091827);
            ((android.widget.ImageView)cover).setImageDrawable(null);
        }
    }
    public static void mapCoverState(Activity activity,View cover){clearMapCoverArtwork(activity,cover);currentActivity=new java.lang.ref.WeakReference<>(activity);mapCoverView=new java.lang.ref.WeakReference<>(cover);afterDraw(activity);}
    public static void mapCover(Activity activity,boolean visible){if(visible)mapSplashAdded(activity);else mapSplashRemoved(activity);}
    public static void mapResumed(Activity activity){
        if(NO_ARTWORK){int id=activity.getResources().getIdentifier("cover","id",activity.getPackageName());if(id!=0)clearMapCoverArtwork(activity,activity.findViewById(id));}
        currentActivity=new java.lang.ref.WeakReference<>(activity);mapPolicy.resumed();afterDraw(activity);
    }
    private static boolean visible(java.lang.ref.WeakReference<View> ref){View view=ref==null?null:ref.get();return view!=null&&view.getVisibility()==View.VISIBLE&&view.getWindowToken()!=null&&view.isShown();}
    private static boolean mapReady(Activity activity){
        if(!activity.getPackageName().equals("com.autonavi.amapauto"))return true;
        // UsbFillActivity is only the bootstrap/router. PREPARE may reach it
        // before it opens MainMapActivity; acknowledging it returns too early,
        // then the real map steals the foreground again a moment later.
        if(!activity.getClass().getName().equals("com.autonavi.amapauto.MainMapActivity"))return false;
        boolean splash=mapSplashView==null?mapPolicy.visible():visible(mapSplashView);
        // Returning follows the original artwork. A native rendering cover can
        // remain visible or relayout after it ends, and must not block return.
        return mapPolicy.ready(splash,visible(mapCoverView));
    }
    private static boolean returnToHome(Activity current,String completed,boolean shouldReturn,int returnPage){
        if(!shouldReturn)return false;
        try{if(current.getPackageManager().checkSignatures(current.getPackageName(),"cn.windchaser.unified")!=android.content.pm.PackageManager.SIGNATURE_MATCH)return false;
            Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setComponent(new ComponentName("cn.windchaser.unified","cn.hydrogen.launcher.MainActivity"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("windchaser.startup.return",true).putExtra(TOKEN,completed).putExtra("windchaser.startup.page",returnPage);
            current.startActivity(home);return true;
        }catch(RuntimeException unavailable){android.util.Log.w("WindChaserStartup","direct HOME return unavailable; launcher watchdog remains",unavailable);return false;}
    }
    private static void completeSession(String value,int valueStep){
        if(value==null||!value.equals(token)||valueStep!=step)return;
        completedToken=value;completedStep=valueStep;automaticUntil=0;readyCallback=null;callback=null;token=null;
        if(pending!=null)pending.cancel();
    }
    private static boolean dispatchCompletion(Activity current,PendingIntent ticket,Messenger messenger,String expected,int expectedStep,boolean shouldReturn,int returnPage,String source){
        boolean delivered=false;
        if(ticket!=null)try{ticket.send();delivered=true;android.util.Log.i("WindChaserStartup","startup finished via PendingIntent, source="+source+": "+current.getPackageName());}
        catch(PendingIntent.CanceledException cancelled){if(completionRetry!=null)completionRetry.cancel();android.util.Log.i("WindChaserStartup","startup session ticket cancelled; skipping return");return false;}
        catch(RuntimeException failed){android.util.Log.w("WindChaserStartup","ready ticket unavailable; trying Messenger",failed);}
        if(messenger!=null)try{Message ready=Message.obtain(null,1);Bundle data=new Bundle();data.putString(TOKEN,expected);data.putInt(STEP,expectedStep);data.putString("package",current.getPackageName());data.putString("event","splash-finished");ready.setData(data);messenger.send(ready);delivered=true;}
        catch(RemoteException|RuntimeException failed){android.util.Log.w("WindChaserStartup","Messenger ready delivery unavailable",failed);}
        return returnToHome(current,expected,shouldReturn,returnPage)||delivered;
    }
    /** This clock starts only after the original artwork has ended. */
    private static final class CompletionRetry implements Runnable {
        final java.lang.ref.WeakReference<Activity> activity;final String expected;final int expectedStep,returnPage;
        final PendingIntent ticket;final Messenger messenger;final boolean shouldReturn;final long expires,primaryAt,artworkEndedAt;final long[] fallbackOffsets;
        final Handler timer=new Handler(Looper.getMainLooper());boolean cancelled;int retries;
        CompletionRetry(Activity current,String value,int step,PendingIntent ticket,Messenger messenger,boolean home,int page,long expires){
            activity=new java.lang.ref.WeakReference<>(current);expected=value;expectedStep=step;this.ticket=ticket;this.messenger=messenger;shouldReturn=home;returnPage=page;this.expires=expires;
            // All retries use the same artwork-end origin, never app launch or
            // the previous retry. A new real splash cancels a warm-resume guess.
            artworkEndedAt=SystemClock.uptimeMillis();
            boolean music=current.getPackageName().equals("com.tencent.qqmusiccar");
            primaryAt=artworkEndedAt;
            fallbackOffsets=music?new long[]{650,3000}:new long[]{500,2350,5000};
            long delay=fallbackOffsets[0];
            timer.postDelayed(this,delay);
            android.util.Log.i("WindChaserStartup","post-artwork fallback armed, delay="+delay+" package="+current.getPackageName());
        }
        void cancel(){cancelled=true;timer.removeCallbacks(this);if(completionRetry==this)completionRetry=null;}
        @Override public void run(){
            Activity current=activity.get();
            if(cancelled||current==null||current.isFinishing()||SystemClock.elapsedRealtime()>=expires||!completionForeground(current)){cancel();return;}
            if(dispatchCompletion(current,ticket,messenger,expected,expectedStep,shouldReturn,returnPage,"post-artwork-timer"))completeSession(expected,expectedStep);
            if(!cancelled&&++retries<fallbackOffsets.length)timer.postAtTime(this,artworkEndedAt+fallbackOffsets[retries]);
            else{completeSession(expected,expectedStep);cancel();}
        }
    }
    public static boolean contentInitialized(){
        Activity a=currentActivity==null?null:currentActivity.get();
        if(a==null||a.isFinishing())return false;
        if("com.tencent.qqmusiccar".equals(a.getPackageName())&&!"com.tencent.qqmusiccar.v2.activity.home.MainActivity".equals(a.getClass().getName()))return false;
        View root=a.getWindow().getDecorView();return root.getWindowToken()!=null&&root.getWidth()>0&&root.getHeight()>0;
    }
    public static boolean interfaceForeground(){Activity a=currentActivity==null?null:currentActivity.get();return a!=null&&!a.isFinishing()&&completionForeground(a);}
    private static boolean completionForeground(Activity activity){
        PowerManager power=(PowerManager)activity.getSystemService(Context.POWER_SERVICE);
        if(power!=null&&!power.isScreenOn())return false;
        // On KitKat prefer the actual top task, including when a focus callback
        // is stale after another app (such as the reverse camera) becomes top.
        if(Build.VERSION.SDK_INT<=20)try{
            ActivityManager manager=(ActivityManager)activity.getSystemService(Context.ACTIVITY_SERVICE);
            java.util.List<ActivityManager.RunningTaskInfo> tasks=manager==null?null:manager.getRunningTasks(1);
            if(tasks!=null&&!tasks.isEmpty()&&tasks.get(0).topActivity!=null)
                return activity.getPackageName().equals(tasks.get(0).topActivity.getPackageName());
        }catch(RuntimeException unavailable){}
        return activity.hasWindowFocus();
    }
    private static void afterDraw(Activity activity){
        if(!automatic()||(callback==null&&readyCallback==null&&!returnHome)||token==null||step<=0||activity.isFinishing())return;
        if(activity.getPackageName().equals("com.autonavi.amapauto")&&mapReady(activity)&&completionRetry==null)completionRetry=new CompletionRetry(activity,token,step,readyCallback,callback,returnHome,homePage,automaticUntil);
        if(pending!=null&&pending.activity.get()==activity&&token.equals(pending.expected)){pending.view.removeCallbacks(pending);pending.view.post(pending);return;}
        if(pending!=null)pending.cancel();
        pending=new PendingDraw(activity);pending.attach();
    }
    private static final class PendingDraw implements ViewTreeObserver.OnPreDrawListener,Runnable {
        final java.lang.ref.WeakReference<Activity> activity;final View view;final String expected;boolean posted,cancelled;
        PendingDraw(Activity a){activity=new java.lang.ref.WeakReference<>(a);view=a.getWindow().getDecorView();expected=token;}
        void attach(){view.getViewTreeObserver().addOnPreDrawListener(this);view.post(this);view.postInvalidateOnAnimation();}
        void cancel(){cancelled=true;if(view.getViewTreeObserver().isAlive())view.getViewTreeObserver().removeOnPreDrawListener(this);view.removeCallbacks(this);if(pending==this)pending=null;}
        @Override public boolean onPreDraw(){
            Activity a=activity.get();
            if(!automatic()||a==null||a.isFinishing()||!expected.equals(token)){cancel();return true;}
            if(!posted&&(!a.getPackageName().equals("com.autonavi.amapauto")||mapPolicy.artworkEnded())&&mapReady(a)&&completionForeground(a)&&view.isShown()&&view.getWidth()>0&&view.getHeight()>0){
                if(completionRetry==null)completionRetry=new CompletionRetry(a,token,step,readyCallback,callback,returnHome,homePage,automaticUntil);
                long delay=Math.max(0,completionRetry.primaryAt-SystemClock.uptimeMillis());
                posted=true;view.postDelayed(()->{Activity current=activity.get();if(!cancelled&&current!=null&&completionForeground(current)&&!current.isFinishing()&&mapReady(current)&&automatic()&&expected.equals(token)){
                    boolean delivered=dispatchCompletion(current,readyCallback,callback,token,step,returnHome,homePage,"artwork-end");
                    if(!delivered)return;
                    completeSession(token,step);
                }else{posted=false;view.postInvalidateOnAnimation();}},delay);
            }
            return true;
        }
        @Override public void run(){if(cancelled||!automatic()){cancel();return;}onPreDraw();if(!cancelled)view.postDelayed(this,80);}
    }
    private static int official(Context context){String name=context.getPackageName().equals("com.tencent.qqmusiccar")?"spalshstatic":"starting_bg";return context.getResources().getIdentifier(name,"drawable",context.getPackageName());}
    public static void window(Activity activity){try{if(StartupCurtain.window(activity))return;if(NO_ARTWORK){activity.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff091827));activity.getWindow().setWindowAnimations(0);activity.overridePendingTransition(0,0);return;}int resource=official(activity);if(resource!=0)activity.getWindow().setBackgroundDrawable(drawable(activity,resource));}catch(RuntimeException ignored){}}
    public static Drawable drawable(Context context,int original){
        if(NO_ARTWORK)return new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
        if(automatic())try{return new Artwork(context);}catch(IOException|RuntimeException missing){}
        return context.getResources().getDrawable(original);
    }
    public static void qqStarter(Activity activity){
        if(NO_ARTWORK){
            // AppCompat inserts its own content wrapper; the actual activity_start
            // FrameLayout keeps a separate splash background. Clear only that
            // background, retaining its privacy dialogs and all child views.
            int id=activity.getResources().getIdentifier("root_view","id",activity.getPackageName());
            View frame=id==0?null:activity.findViewById(id);
            if(frame instanceof android.widget.FrameLayout)frame.setBackgroundColor(0xff091827);
        }
        qqContent(activity,false);
    }
    public static void qqSplash(Activity activity){qqContent(activity,true);}
    public static void qqContent(Activity activity,boolean splashLayout){
        if(splashLayout&&!automatic()&&!NO_ARTWORK)return;
        try{ViewGroup content=(ViewGroup)activity.findViewById(android.R.id.content);if(content==null||content.getChildCount()==0)return;View root=content.getChildAt(0);Drawable art=NO_ARTWORK?new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT):(automatic()?new Artwork(activity):drawable(activity,official(activity)));root.setBackgroundDrawable(art);
            // This separate optional activity contains one static logo child.
            // AppStarter's real FrameLayout, privacy dialog and ad views stay intact.
            if(splashLayout&&root instanceof ViewGroup){ViewGroup group=(ViewGroup)root;for(int i=0;i<group.getChildCount();i++)group.getChildAt(i).setVisibility(View.INVISIBLE);}
            if(NO_ARTWORK&&splashLayout)root.setAlpha(0f);
            window(activity);
        }catch(IOException|RuntimeException missing){}
    }
    private static synchronized Bitmap bitmap(Context context)throws IOException{
        if(mark==null){try(InputStream in=context.getAssets().open("windchaser-startup-mark.png")){mark=BitmapFactory.decodeStream(in);}if(mark==null)throw new IOException("startup artwork unavailable");}return mark;
    }
    private static final class Artwork extends Drawable {
        private final Bitmap image;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);private final float density;
        private Shader gradient;private final RectF target=new RectF();
        Artwork(Context context)throws IOException{image=bitmap(context);density=context.getResources().getDisplayMetrics().density;}
        @Override protected void onBoundsChange(Rect b){
            gradient=b.isEmpty()?null:new LinearGradient(b.left,b.top,b.right,b.bottom,0xff102b42,0xff071321,Shader.TileMode.CLAMP);
            float width=Math.min(b.width()*.50f,360*density),height=width*image.getHeight()/image.getWidth();
            if(height>b.height()*.62f){height=b.height()*.62f;width=height*image.getWidth()/image.getHeight();}
            target.set(b.exactCenterX()-width/2,b.exactCenterY()-height/2,b.exactCenterX()+width/2,b.exactCenterY()+height/2);
        }
        @Override public void draw(Canvas canvas){Rect b=getBounds();if(b.isEmpty())return;paint.setShader(gradient);canvas.drawRect(b,paint);paint.setShader(null);canvas.drawBitmap(image,null,target,paint);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
