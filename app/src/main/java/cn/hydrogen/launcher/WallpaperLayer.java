package cn.hydrogen.launcher;

import android.content.*;
import android.graphics.*;
import android.media.MediaPlayer;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.util.concurrent.*;

final class WallpaperLayer extends FrameLayout implements TextureView.SurfaceTextureListener, SurfaceHolder.Callback {
    private static final ThreadPoolExecutor IMAGES=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,new LinkedBlockingQueue<>());
    private final Config cfg;
    private MediaPlayer player;
    private TextureView texture;
    private SurfaceView legacyVideo;
    private ImageView image,defaultImage;
    private Bitmap bitmap,backgroundBitmap;
    private boolean active, disposed,prepared,videoFailed,memorySuspended;
    private final android.os.Handler videoHandler=new android.os.Handler(android.os.Looper.getMainLooper());
    private final VideoLoopGuard loopGuard=new VideoLoopGuard();
    private long videoFrames;
    private int videoRebuildAttempts;
    private static final long MEMORY_RETRY_MS=10000;
    private final Runnable videoCheck=this::checkVideo;
    private final Runnable memoryRetry=this::retryAfterMemory;
    private int decodeGeneration,decodeWidth,decodeHeight;
    private Future<?> decoding;
    static WallpaperLayer defaultBackground(Context context,WallpaperLayer previous){
        WallpaperLayer layer=previous==null||previous.disposed?new WallpaperLayer(context,true):previous;
        if(layer.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)layer.getParent()).removeView(layer);
        return layer;
    }
    WallpaperLayer(Context c) {this(c,false);}
    private WallpaperLayer(Context c,boolean defaultsOnly) {
        super(c);cfg=new Config(c);
        defaultImage=new ImageView(c);defaultImage.setContentDescription("默认静态背景");defaultImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        defaultImage.setImageDrawable(new android.graphics.drawable.ColorDrawable(0xff0b1620));addView(defaultImage,new LayoutParams(-1,-1));
        String kind=defaultsOnly?"":cfg.s("wallpaperKind","");File file=new File(c.getFilesDir(),"wallpaper");
        if(file.isFile()&&kind.equals("image")){
            image=new ImageView(c);int fit=cfg.n("fit",0);image.setScaleType(fit==0?ImageView.ScaleType.CENTER_CROP:fit==1?ImageView.ScaleType.FIT_CENTER:ImageView.ScaleType.CENTER);addView(image,new LayoutParams(-1,-1));
        }else if(file.isFile()&&kind.equals("video")){
            if(android.os.Build.VERSION.SDK_INT<19){legacyVideo=new SurfaceView(c);legacyVideo.getHolder().addCallback(this);addView(legacyVideo,new LayoutParams(-1,-1,Gravity.CENTER));}
            else{texture=new TextureView(c);texture.setOpaque(false);texture.setSurfaceTextureListener(this);addView(texture,new LayoutParams(-1,-1));}
        }
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);decode(w,h);}
    private void decode(int width,int height){
        if(disposed||width<1||height<1||width==decodeWidth&&height==decodeHeight)return;
        decodeWidth=width;decodeHeight=height;int generation=++decodeGeneration;cancelDecode();
        Context app=getContext().getApplicationContext();File file=new File(app.getFilesDir(),"wallpaper");boolean custom=image!=null;int fit=custom?cfg.n("fit",0):0;
        WeakReference<WallpaperLayer> target=new WeakReference<>(this);
        decoding=IMAGES.submit(()->{
            Bitmap decoded=null,background=null;boolean original=custom;
            try{decoded=custom?DisplayBitmap.decode(()->new FileInputStream(file),width,height,fit):SharedDefaultWallpaper.decode(app,width,height);}
            catch(Exception|OutOfMemoryError failure){LauncherApp.record("壁纸解码失败，恢复默认背景",failure);original=false;
                if(custom)try{decoded=SharedDefaultWallpaper.decode(app,width,height);}catch(Exception|OutOfMemoryError ignored){}
            }
            if(original&&fit!=0)try{background=SharedDefaultWallpaper.decode(app,width,height);}catch(Exception|OutOfMemoryError ignored){}
            final Bitmap result=decoded,backdrop=background;final boolean selected=original;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(()->{
                WallpaperLayer layer=target.get();if(layer==null||layer.disposed||layer.decodeGeneration!=generation){if(result!=null&&selected)result.recycle();return;}
                layer.bitmap=result;layer.backgroundBitmap=backdrop;
                if(selected&&result!=null){layer.image.setImageBitmap(result);if(backdrop==null)layer.defaultImage.setImageDrawable(null);else layer.defaultImage.setImageBitmap(backdrop);}
                else{if(layer.image!=null)layer.image.setImageDrawable(null);if(result!=null)layer.defaultImage.setImageBitmap(result);}
            });
        });
    }
    private void cancelDecode(){if(decoding!=null){decoding.cancel(false);if(decoding instanceof Runnable)IMAGES.remove((Runnable)decoding);decoding=null;}}
    void resume() {
        if(disposed)return;
        active=true;
        if(memorySuspended){videoHandler.removeCallbacks(memoryRetry);videoHandler.post(memoryRetry);return;}
        resumeVideo();
    }
    private void resumeVideo() {
        if(!active||disposed||videoFailed||memorySuspended)return;
        if(texture!=null&&texture.isAvailable()&&player==null)prepare(texture.getSurfaceTexture());
        else if(legacyVideo!=null&&legacyVideo.getHolder().getSurface().isValid()&&player==null)prepareSurface(legacyVideo.getHolder().getSurface());
        else if(player!=null&&prepared)try{player.start();armVideoCheck();}catch(IllegalStateException error){rebuildStalledVideo();}
    }
    void pause() {
        active=false;
        videoHandler.removeCallbacks(memoryRetry);
        releaseVideo();
    }
    void dispose() {
        if(disposed)return;
        disposed=true;decodeGeneration++;cancelDecode();pause();
        if(texture!=null)texture.setSurfaceTextureListener(null);
        if(legacyVideo!=null)legacyVideo.getHolder().removeCallback(this);
        defaultImage.setImageDrawable(null);if(image!=null)image.setImageDrawable(null);bitmap=backgroundBitmap=null;
    }
    void releaseVideo() {
        videoHandler.removeCallbacks(videoCheck);
        prepared=false;videoFrames=0;loopGuard.reset(android.os.SystemClock.uptimeMillis());
        MediaPlayer previous=player;player=null;
        if(previous!=null){
            previous.setOnPreparedListener(null);previous.setOnErrorListener(null);previous.setOnVideoSizeChangedListener(null);
            previous.setOnCompletionListener(null);previous.setOnSeekCompleteListener(null);previous.release();
        }
    }
    private void fallback(){
        videoFailed=true;memorySuspended=false;videoHandler.removeCallbacks(memoryRetry);releaseVideo();
        if(texture!=null)texture.setVisibility(GONE);if(legacyVideo!=null)legacyVideo.setVisibility(GONE);
    }
    private void prepare(SurfaceTexture s) {
        Surface surface=new Surface(s);
        try{prepareSurface(surface);}finally{surface.release();}
    }
    private void prepareSurface(Surface surface) {
        if(!active||disposed||player!=null||videoFailed||memorySuspended)return;
        try {
            player=new MediaPlayer();player.setDataSource(new File(getContext().getFilesDir(),"wallpaper").toString());
            player.setSurface(surface);player.setVolume(0f,0f);player.setLooping(true);
            // Silent visual background: never request audio focus from music or navigation.
            player.setOnVideoSizeChangedListener((p,w,h)->{if(p==player&&!disposed)transform(w,h);});
            player.setOnPreparedListener(p->{
                if(p!=player||disposed)return;
                prepared=true;loopGuard.reset(android.os.SystemClock.uptimeMillis());
                try{p.setLooping(true);if(active){p.start();armVideoCheck();}}
                catch(IllegalStateException error){rebuildStalledVideo();}
            });
            player.setOnCompletionListener(this::restartCompletedVideo);
            player.setOnSeekCompleteListener(p->{
                if(p!=player||disposed||!active||!prepared||!loopGuard.recovering())return;
                try{p.setLooping(true);p.start();armVideoCheck();}
                catch(IllegalStateException error){rebuildStalledVideo();}
            });
            player.setOnErrorListener((p,what,extra)->{
                if(p!=player||disposed)return true;
                LauncherApp.record("视频壁纸解码失败 "+what+"/"+extra,null);
                fallback();Ui.toast(getContext(),"视频格式或硬件解码不支持，已恢复静态背景");return true;
            });
            player.prepareAsync();
        }catch(Exception error){LauncherApp.record("视频壁纸加载失败",error);fallback();Ui.toast(getContext(),"视频壁纸无法播放，已使用默认背景");}
    }
    private void restartCompletedVideo(MediaPlayer completed) {
        if(completed!=player||disposed||!active||!prepared||loopGuard.recovering())return;
        loopGuard.restarting(android.os.SystemClock.uptimeMillis());
        try{
            completed.setLooping(true);completed.seekTo(0);
            // PlaybackCompleted supports start(). Do not depend on a seek callback
            // which some head-unit decoders omit. The watchdog verifies actual progress.
            completed.start();armVideoCheck();
        }catch(IllegalStateException error){LauncherApp.record("视频壁纸循环恢复失败",error);rebuildStalledVideo();}
    }
    private void armVideoCheck() {
        videoHandler.removeCallbacks(videoCheck);
        if(active&&!disposed&&prepared&&player!=null&&!memorySuspended)videoHandler.postDelayed(videoCheck,VideoLoopGuard.CHECK_MS);
    }
    private void checkVideo() {
        if(!active||disposed||!prepared||player==null||memorySuspended)return;
        try{
            int action=loopGuard.inspect(android.os.SystemClock.uptimeMillis(),player.getCurrentPosition(),player.getDuration(),player.isPlaying(),videoFrames);
            if(loopGuard.madeProgress())videoRebuildAttempts=0;
            if(action==VideoLoopGuard.RESTART)restartCompletedVideo(player);
            else if(action==VideoLoopGuard.RECREATE)rebuildStalledVideo();
        }catch(IllegalStateException error){rebuildStalledVideo();}
        armVideoCheck();
    }
    private void rebuildStalledVideo() {
        if(!active||disposed||memorySuspended)return;
        if(++videoRebuildAttempts>2){LauncherApp.record("视频壁纸循环无法恢复，使用静态背景",null);fallback();return;}
        LauncherApp.record("视频壁纸循环重建解码器 attempt="+videoRebuildAttempts,null);
        releaseVideo();resumeVideo();
    }
    void suspendForMemory() {
        if(disposed||videoFailed||(texture==null&&legacyVideo==null))return;
        memorySuspended=true;releaseVideo();videoHandler.removeCallbacks(memoryRetry);
        if(active)videoHandler.postDelayed(memoryRetry,MEMORY_RETRY_MS);
    }
    private void retryAfterMemory() {
        if(!active||disposed||!memorySuspended)return;
        android.app.ActivityManager manager=(android.app.ActivityManager)getContext().getSystemService(Context.ACTIVITY_SERVICE);
        android.app.ActivityManager.MemoryInfo info=new android.app.ActivityManager.MemoryInfo();
        if(manager!=null){manager.getMemoryInfo(info);if(info.lowMemory){videoHandler.postDelayed(memoryRetry,MEMORY_RETRY_MS);return;}}
        memorySuspended=false;resumeVideo();
    }
    private void transform(int w,int h) {
        if(legacyVideo!=null && w>0 && h>0 && getWidth()>0 && getHeight()>0){int fit=cfg.n("fit",0);float factor=fit==0?Math.max((float)getWidth()/w,(float)getHeight()/h):fit==1?Math.min((float)getWidth()/w,(float)getHeight()/h):1;legacyVideo.setLayoutParams(new LayoutParams(Math.round(w*factor),Math.round(h*factor),Gravity.CENTER));return;}
        if(texture==null || w==0 || h==0 || texture.getWidth()==0 || texture.getHeight()==0) return;
        float vw=texture.getWidth(),vh=texture.getHeight(),factor;
        int fit=cfg.n("fit",0); factor=fit==0?Math.max(vw/w,vh/h):fit==1?Math.min(vw/w,vh/h):1;
        Matrix matrix=new Matrix(); matrix.setScale(w*factor/vw,h*factor/vh,vw/2,vh/2); texture.setTransform(matrix);
    }
    @Override public void onSurfaceTextureAvailable(SurfaceTexture s,int w,int h) { prepare(s); }
    @Override public void onSurfaceTextureSizeChanged(SurfaceTexture s,int w,int h) { if(player!=null) try { transform(player.getVideoWidth(),player.getVideoHeight()); } catch(IllegalStateException ignored) {} }
    @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture s) { releaseVideo(); return true; }
    @Override public void onSurfaceTextureUpdated(SurfaceTexture s) {videoFrames++;}
    @Override public void surfaceCreated(SurfaceHolder holder){prepareSurface(holder.getSurface());}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int w,int h){}
    @Override public void surfaceDestroyed(SurfaceHolder holder){releaseVideo();}
}
