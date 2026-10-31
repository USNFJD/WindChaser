package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real API19 bitmap/cache/Handler execution with deterministic network failures. */
final class AlbumArtFlow {
    private final Instrumentation test;private final Context context;
    private final StringBuilder report=new StringBuilder();private int passed,failed;
    private final ArrayList<AlbumArt> opened=new ArrayList<>();
    private static final String A="https://y.gtimg.cn/windchaser-cover-test-a.jpg",B="https://y.gtimg.cn/windchaser-cover-test-b.jpg";
    private byte[] red,blue;
    private interface Check {void run()throws Exception;}
    private interface Condition {boolean getAsBoolean();}
    AlbumArtFlow(Instrumentation test){this.test=test;context=test.getTargetContext();}
    private void main(Runnable r){test.runOnMainSync(r);}
    private void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    private void check(String name,Check body){try{body.run();passed++;report.append("PASS ");}catch(Throwable error){failed++;report.append("FAIL ").append(error).append(" · ");}report.append(name).append('\n');}
    private void await(String name,Condition predicate)throws Exception{long end=SystemClock.elapsedRealtime()+10000;while(SystemClock.elapsedRealtime()<end){boolean[] yes={false};main(()->yes[0]=predicate.getAsBoolean());if(yes[0])return;Thread.sleep(30);}throw new AssertionError("timeout: "+name);}
    private static byte[] png(int color){Bitmap b=Bitmap.createBitmap(640,480,Bitmap.Config.RGB_565);b.eraseColor(color);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();}
    private AlbumArt art(String suffix,AlbumArt.Fetcher fetcher,long... retries){AlbumArt[] value={null};main(()->{value[0]=new AlbumArt(context,()->{},"-test-"+suffix,fetcher,retries);opened.add(value[0]);});return value[0];}
    private void begin(AlbumArt art,Object owner,String url){main(()->{art.target(owner,96,80);art.load(url);});}
    private void close(AlbumArt art){main(art::close);}
    private static Object field(Object owner,String name)throws Exception{Field f=owner.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(owner);}
    private void seed(String suffix,String url,byte[] bytes)throws Exception{try(OutputStream out=new FileOutputStream(new File(context.getCacheDir(),"qq-cover-test-"+suffix+".jpg"))){out.write(bytes);}context.getSharedPreferences("cover-cache-test-"+suffix,0).edit().putString("url",url).commit();}
    void runLive(){
        check("production KitKat HTTPS fetch decodes a real QQ CDN image",()->{
            context.getSharedPreferences("cover-cache-test-live",0).edit().clear().commit();new File(context.getCacheDir(),"qq-cover-test-live.jpg").delete();
            AlbumArt[] art={null};main(()->{art[0]=new AlbumArt(context,()->{},"-test-live");opened.add(art[0]);});Object owner=new Object();begin(art[0],owner,"https://y.gtimg.cn/music/photo_new/T002R300x300M000003DFRzD192KKD.jpg");
            long end=SystemClock.elapsedRealtime()+45000;boolean[] ready={false};while(SystemClock.elapsedRealtime()<end){main(()->ready[0]=art[0].bitmap!=null);if(ready[0])break;Thread.sleep(100);}require(ready[0],"live CDN cover unavailable; inspect runtime log");main(()->require(art[0].bitmap.getWidth()==96&&art[0].bitmap.getHeight()==80,"unbounded live decode"));close(art[0]);
        });
        main(()->{for(AlbumArt art:opened)art.close();});report.append("LIVE_ALBUM_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    void run(){
        Config cfg=new Config(context);String backup="";
        try{
            backup=cfg.exportJson();red=png(Color.RED);blue=png(Color.BLUE);
            // Unique suffixes let the suite run repeatedly without a stale fixture.
            for(String suffix:new String[]{"retry","bounded","network","cache","remote","stale","close","hidden","size","bad-path","bridge"}){context.getSharedPreferences("cover-cache-test-"+suffix,0).edit().clear().commit();new File(context.getCacheDir(),"qq-cover-test-"+suffix+".jpg").delete();}
            check("transient failure retries the same URL once and coalesces repeated album events",()->{
                AtomicInteger calls=new AtomicInteger();AlbumArt art=art("retry",url->{if(calls.incrementAndGet()==1)throw new IOException("offline");return red;},300,600);Object owner=new Object();begin(art,owner,A);
                await("first failure",()->{try{return (Integer)field(art,"failures")==1;}catch(Exception e){throw new RuntimeException(e);}});
                main(()->{for(int i=0;i<50;i++)art.load(A);});require(calls.get()==1,"same URL bypassed backoff");
                await("cover retry",()->art.bitmap!=null);main(()->require(art.bitmap.getWidth()==96&&art.bitmap.getHeight()==80,"cover was not decoded to target"));require(calls.get()==2,"duplicate fetches: "+calls);close(art);
            });
            check("automatic retries stop after the bounded series",()->{
                AtomicInteger calls=new AtomicInteger();AlbumArt art=art("bounded",url->{calls.incrementAndGet();throw new IOException("offline");},100,180);begin(art,new Object(),A);await("bounded attempts",()->{try{return (Integer)field(art,"failures")==3;}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(450);main(()->{for(int i=0;i<20;i++)art.load(A);});require(calls.get()==3,"retry storm");close(art);
            });
            check("network restoration retries an exhausted URL without a request storm",()->{
                AtomicInteger calls=new AtomicInteger();AlbumArt art=art("network",url->{if(calls.incrementAndGet()==1)throw new IOException("offline");return blue;});begin(art,new Object(),B);await("offline exhausted",()->{try{return (Integer)field(art,"failures")==1;}catch(Exception e){throw new RuntimeException(e);}});main(()->{for(int i=0;i<30;i++)art.networkAvailable();});await("online recovery",()->art.bitmap!=null);require(calls.get()==2,"network return did not recover/coalesce");close(art);
            });
            check("a corrupt matching cache is discarded and fetched again",()->{
                seed("cache",A,new byte[]{1,2,3});AtomicInteger calls=new AtomicInteger();AlbumArt art=art("cache",url->{calls.incrementAndGet();return red;},100);begin(art,new Object(),A);await("cache repaired",()->art.bitmap!=null);require(calls.get()==1,"corrupt cache blocked recovery");close(art);
                AlbumArt restored=art("cache",url->{throw new IOException("cache should serve offline");},100);begin(restored,new Object(),A);await("offline cached image",()->restored.bitmap!=null);close(restored);
            });
            check("invalid downloaded bytes are not cached and a later response can recover",()->{
                AtomicInteger calls=new AtomicInteger();AlbumArt art=art("remote",url->calls.incrementAndGet()==1?new byte[]{7,8}:blue,120);begin(art,new Object(),B);await("valid retry",()->art.bitmap!=null);require(calls.get()==2,"invalid response was kept");close(art);
            });
            check("an old track finishing after a track change cannot overwrite the new cover",()->{
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();AlbumArt art=art("stale",url->{calls.incrementAndGet();if(A.equals(url)){entered.countDown();release.await(5,TimeUnit.SECONDS);return red;}return blue;},100);begin(art,new Object(),A);require(entered.await(5,TimeUnit.SECONDS),"request did not start");main(()->art.load(B));release.countDown();await("new track cover",()->art.bitmap!=null);main(()->require(art.bitmap.getPixel(0,0)==Color.BLUE,"old image displayed"));require(calls.get()==2,"new track request lost");require(B.equals(context.getSharedPreferences("cover-cache-test-stale",0).getString("url","")),"old cache overwrote new track");close(art);
            });
            check("closing during a request drops its result and cancels retries",()->{
                CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicInteger calls=new AtomicInteger();AlbumArt art=art("close",url->{calls.incrementAndGet();entered.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}return red;},100);begin(art,new Object(),A);require(entered.await(5,TimeUnit.SECONDS),"request did not start");close(art);release.countDown();Thread.sleep(350);main(()->require(art.bitmap==null,"closed loader published an image"));require(calls.get()==1,"closed loader retried");
            });
            check("hidden pages stop retries and a visible target recovers",()->{
                AtomicInteger calls=new AtomicInteger();AlbumArt art=art("hidden",url->{if(calls.incrementAndGet()==1)throw new IOException("offline");return red;},150);Object owner=new Object();main(()->art.load(A));Thread.sleep(220);require(calls.get()==0,"download without a target");main(()->art.target(owner,96,80));await("first hidden failure",()->{try{return (Integer)field(art,"failures")==1;}catch(Exception e){throw new RuntimeException(e);}});main(()->art.releaseTarget(owner));Thread.sleep(350);require(calls.get()==1,"invisible page kept retrying");main(()->art.target(owner,96,80));await("visible again",()->art.bitmap!=null);require(calls.get()==2,"visible page did not recover");close(art);
            });
            check("several targets share one download and resize from the disk cache",()->{
                AtomicInteger calls=new AtomicInteger();AlbumArt art=art("size",url->{calls.incrementAndGet();return red;},100);Object first=new Object(),second=new Object();main(()->{art.target(first,96,80);art.target(second,128,120);art.load(A);});await("large target",()->art.bitmap!=null&&art.bitmap.getWidth()==128);main(()->art.releaseTarget(second));await("small target",()->art.bitmap!=null&&art.bitmap.getWidth()==96);require(calls.get()==1,"size change downloaded again");close(art);
            });
            check("cache write failure still displays a successfully decoded cover",()->{
                File blocker=new File(context.getCacheDir(),"cover-test-not-directory");try(OutputStream out=new FileOutputStream(blocker)){out.write(1);}Context blocked=new ContextWrapper(context){@Override public File getCacheDir(){return blocker;}};AlbumArt[] art={null};main(()->{art[0]=new AlbumArt(blocked,()->{},"-test-bad-path",url->red,new long[]{100});opened.add(art[0]);});begin(art[0],new Object(),A);await("cover despite cache failure",()->art[0].bitmap!=null);close(art[0]);blocker.delete();
            });
            check("untrusted or local URLs never reach the downloader",()->{
                AtomicInteger calls=new AtomicInteger();AlbumArt art=art("unsafe",url->{calls.incrementAndGet();return red;},100);begin(art,new Object(),"file:///sdcard/cover.jpg");main(()->{art.load("https://127.0.0.1/a.jpg");art.load("https://y.gtimg.cn.evil.test/a.jpg");});Thread.sleep(250);require(calls.get()==0,"unsafe URL reached network");close(art);
            });
            final QqMusicBridge[] bridge={null};
            check("QQ temporary album misses preserve the current image and saved URL",()->{
                main(()->{bridge[0]=new QqMusicBridge(context,()->{},"-test-bridge");try{bridge[0].accept(event("update_song","\"key_title\":\"封面测试\",\"key_artist\":\"歌手\""));}catch(Exception e){throw new RuntimeException(e);}});seed("bridge",A,red);
                main(()->{try{bridge[0].cover.target(this,96,80);bridge[0].accept(event("update_album","\"code\":0,\"album_url\":\""+A+"\""));}catch(Exception e){throw new RuntimeException(e);}});await("bridge cover",()->bridge[0].cover.bitmap!=null);
                main(()->{try{Bitmap before=bridge[0].cover.bitmap;bridge[0].accept(event("update_album","\"code\":-1"));require(bridge[0].cover.bitmap==before,"miss erased a valid cover");require(A.equals(cfg.p.getString("qqCoverUrl","")),"miss erased saved URL");}catch(Exception e){throw new RuntimeException(e);}});
            });
            check("a real track change clears prior artwork and cancels old album retries",()->main(()->{
                try{Field ticking=QqMusicBridge.class.getDeclaredField("ticking");ticking.setAccessible(true);ticking.setBoolean(bridge[0],true);bridge[0].accept(event("update_album","\"code\":-1"));require((Boolean)field(bridge[0],"albumRetryPending"),"retry not scheduled");bridge[0].accept(event("update_song","\"key_title\":\"下一首\",\"key_artist\":\"另一歌手\""));require(bridge[0].cover.bitmap==null,"old track cover retained");require(!(Boolean)field(bridge[0],"albumRetryPending"),"old album retry retained");bridge[0].accept(event("update_album","\"code\":-1"));bridge[0].pause();require(!(Boolean)field(bridge[0],"albumRetryPending"),"pause retained retry");bridge[0].stop();}catch(Exception e){throw new RuntimeException(e);}
            }));
        }catch(Throwable error){failed++;report.append("FAIL setup/runtime ").append(error).append('\n');}
        finally{main(()->{for(AlbumArt art:opened)art.close();});if(!backup.isEmpty())try{cfg.restore(backup);}catch(Exception ignored){}}
        report.append("ALBUM_ART_RESULT passed=").append(passed).append(" failed=").append(failed).append('\n');Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private static String event(String method,String data){return "{\"module\":\"play\",\"command\":{\"method\":\""+method+"\",\"data\":{"+data+"}}}";}
}
