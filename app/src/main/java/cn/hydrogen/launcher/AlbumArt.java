package cn.hydrogen.launcher;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

/** One shared, downsampled cover for both pages, supplied by QQ's update_album event. */
final class AlbumArt {
    interface Fetcher { byte[] fetch(String url) throws Exception; }
    private static final int LIMIT=2*1024*1024;
    private static final Object CACHE_LOCK=new Object();
    private static final long[] RETRIES={1500,5000,15000};
    private final Context context;private final Runnable changed;private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor io=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.DiscardOldestPolicy());
    private volatile int generation;private volatile boolean closed;private volatile String requested="";Bitmap bitmap;private final String suffix;
    private final Fetcher fetcher;private final long[] retryDelays;
    private int failures;private long retryAt;
    private final Runnable retry=this::schedule;
    AlbumArt(Context c,Runnable changed){this(c,changed,"");}
    AlbumArt(Context c,Runnable changed,String suffix){this(c,changed,suffix,null,RETRIES);}
    AlbumArt(Context c,Runnable changed,String suffix,Fetcher fetcher,long[] retries){context=c;this.changed=changed;this.suffix=suffix;this.fetcher=fetcher;retryDelays=retries.clone();}
    static String safeUrl(String value){
        try{if(value==null||value.length()>4096)return "";Uri u=Uri.parse(value.trim());String host=u.getHost();if(host==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))return "";host=host.toLowerCase(java.util.Locale.US);
            boolean qq=host.endsWith(".gtimg.cn")||host.endsWith(".gtimg.com")||host.endsWith(".qq.com")||host.endsWith(".qpic.cn");
            if(!qq||!("https".equals(u.getScheme())||"http".equals(u.getScheme())))return "";return u.buildUpon().scheme("https").build().toString();
        }catch(Exception e){return "";}
    }
    private final java.util.WeakHashMap<Object,int[]> targets=new java.util.WeakHashMap<>();
    private int targetWidth,targetHeight,queuedGeneration=-1;
    private final Runnable sizeSettled=this::applySize;
    void target(Object owner,int width,int height){
        if(closed||owner==null||width<1||height<1)return;
        int[] old=targets.get(owner);if(old!=null&&old[0]==width&&old[1]==height)return;
        targets.put(owner,new int[]{width,height});resize();
    }
    void releaseTarget(Object owner){if(targets.remove(owner)!=null)resize();}
    private void resize(){
        int width=0,height=0;for(int[] size:targets.values()){width=Math.max(width,size[0]);height=Math.max(height,size[1]);}
        if(width==targetWidth&&height==targetHeight)return;
        boolean returning=targetWidth==0||targetHeight==0;targetWidth=width;targetHeight=height;++generation;io.getQueue().clear();
        if(returning||width==0||height==0){main.removeCallbacks(retry);failures=0;retryAt=0;}
        main.removeCallbacks(sizeSettled);main.postDelayed(sizeSettled,80);
    }
    private void applySize(){
        if(closed)return;if(targetWidth==0||targetHeight==0){bitmap=null;return;}
        if(bitmap!=null&&bitmap.getWidth()==targetWidth&&bitmap.getHeight()==targetHeight)return;
        schedule();
    }
    void load(String url){
        if(closed)return;final String next=safeUrl(url);
        // The same address must be retryable after a failed download, without
        // bypassing the backoff or creating concurrent requests on repeated events.
        if(next.equals(requested)&&(!next.isEmpty()||bitmap==null)){if(bitmap==null)schedule();return;}
        requested=next;++generation;io.getQueue().clear();main.removeCallbacks(retry);failures=0;retryAt=0;bitmap=null;changed.run();schedule();
    }
    void resume(){if(bitmap==null)schedule();}
    void networkAvailable(){
        if(closed||bitmap!=null||targetWidth<1||targetHeight<1||queuedGeneration==generation)return;
        main.removeCallbacks(retry);failures=0;retryAt=0;schedule();
    }
    private void schedule(){
        if(closed||requested.isEmpty()||targetWidth<1||targetHeight<1||queuedGeneration==generation||SystemClock.elapsedRealtime()<retryAt)return;
        main.removeCallbacks(retry);queuedGeneration=generation;
        final String next=requested;final int token=generation,width=targetWidth,height=targetHeight;
        io.execute(()->{Bitmap image=null;try{image=loadImage(next,width,height);}catch(Exception|OutOfMemoryError e){if(current(next))LauncherApp.record("QQ音乐封面未取得",e);}final Bitmap result=image;
            main.post(()->{if(!closed&&token==generation){
                queuedGeneration=-1;
                if(result!=null){failures=0;retryAt=0;bitmap=result;changed.run();}
                else{
                    failures++;long delay=failures<=retryDelays.length?retryDelays[failures-1]:60000;
                    retryAt=SystemClock.elapsedRealtime()+delay;
                    // No wakeups once the bounded retry series is exhausted.
                    // A later album event or foreground re-entry can try again.
                    if(failures<=retryDelays.length&&targetWidth>0&&targetHeight>0)main.postDelayed(retry,delay);
                }
            }else if(result!=null)result.recycle();});
        });
    }
    private boolean current(String url){return !closed&&url.equals(requested);}
    private Bitmap loadImage(String url,int width,int height)throws Exception{
        byte[] data=null;
        try{data=readCache(url);}catch(IOException e){discardCache(url);}
        if(data!=null){
            try{return decode(data,width,height);}catch(IOException|RuntimeException invalid){discardCache(url);}
        }
        if(!current(url))return null;
        data=fetcher==null?download(url):fetcher.fetch(url);
        if(data==null||data.length==0||data.length>LIMIT)throw new IOException("封面数据无效");
        if(!current(url))return null;
        Bitmap image=decode(data,width,height);
        try{saveCache(url,data);}catch(Exception e){LauncherApp.record("QQ音乐封面缓存未写入",e);}
        return image;
    }
    private static Bitmap decode(byte[] data,int width,int height)throws IOException{
        return DisplayBitmap.decode(()->new ByteArrayInputStream(data),width,height,0);
    }
    private AtomicFile cache(){return new AtomicFile(new File(context.getCacheDir(),"qq-cover"+suffix+".jpg"));}
    private android.content.SharedPreferences cacheKey(){return context.getSharedPreferences("cover-cache"+suffix,0);}
    private byte[] readCache(String url)throws IOException{
        synchronized(CACHE_LOCK){if(!url.equals(cacheKey().getString("url","")))return null;
            try(InputStream in=cache().openRead()){return read(in);}catch(FileNotFoundException absent){return null;}
        }
    }
    private void discardCache(String url){
        synchronized(CACHE_LOCK){if(url.equals(cacheKey().getString("url",""))){cacheKey().edit().remove("url").commit();cache().delete();}}
    }
    private void saveCache(String url,byte[] data)throws IOException{
        synchronized(CACHE_LOCK){
            if(!current(url))return;
            // Clear the URL before replacing bytes, so interruption cannot label
            // an old track's image as the new one. Disk work stays on the IO thread.
            if(!cacheKey().edit().remove("url").commit())return;
            AtomicFile file=cache();FileOutputStream out=null;
            try{out=file.startWrite();out.write(data);file.finishWrite(out);}catch(IOException failure){file.failWrite(out);throw failure;}
            if(current(url))cacheKey().edit().putString("url",url).commit();
        }
    }
    private byte[] download(String url)throws Exception{
        String address=url;
        for(int redirects=0;redirects<4;redirects++){
            if(!current(url))throw new InterruptedIOException("封面请求已过期");
            HttpsURLConnection c=(HttpsURLConnection)new URL(address).openConnection();if(Build.VERSION.SDK_INT<21)c.setSSLSocketFactory(WeatherHttp.legacyTls(context));c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);
            try{int code=c.getResponseCode();if(code>=300&&code<=399){address=safeUrl(new URL(new URL(address),c.getHeaderField("Location")).toString());if(address.isEmpty())throw new IOException("封面重定向地址不支持");continue;}if(code!=200)throw new IOException("封面 HTTP "+code);try(InputStream in=c.getInputStream()){return read(in);}}finally{c.disconnect();}
        }
        throw new IOException("封面跳转过多");
    }
    private static byte[] read(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>LIMIT)throw new IOException("封面超过 2 MB");out.write(b,0,n);}return out.toByteArray();}
    void close(){closed=true;generation++;io.shutdownNow();main.removeCallbacksAndMessages(null);targets.clear();targetWidth=targetHeight=0;bitmap=null;}
}
