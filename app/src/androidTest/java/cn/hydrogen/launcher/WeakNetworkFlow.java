package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.test.mock.MockPackageManager;
import java.lang.reflect.Field;
import java.util.*;

/** Controlled weak-network/partial-event checks. No traffic shaping or native playback claims. */
final class WeakNetworkFlow {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder("Weak-network fixtures: native protocol events are controlled; no native player or network traffic shaping.\n");
    private final List<Intent> sent=Collections.synchronizedList(new ArrayList<>());
    private QqMusicBridge bridge;private LinkedAppCoordinator coordinator;
    private int passed,failed;
    private interface Check {void run()throws Exception;}
    WeakNetworkFlow(Instrumentation value){test=value;}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void check(String name,Check check){try{check.run();passed++;report.append("PASS ");}catch(Throwable error){failed++;report.append("FAIL ").append(error).append(" ");}report.append(name).append('\n');}
    private void main(Runnable run){Throwable[] error={null};test.runOnMainSync(()->{try{run.run();}catch(Throwable e){error[0]=e;}});test.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void event(String method,String fields)throws Exception{main(()->{try{bridge.accept("{\"module\":\"play\",\"command\":{\"method\":\""+method+"\",\"data\":{"+fields+"}}}");}catch(Exception e){throw new RuntimeException(e);}});}
    private int subscriptions(){int count=0;synchronized(sent){for(Intent intent:sent)if(intent.getIntExtra("action",0)==100)count++;}return count;}
    private int controls(){int count=0;synchronized(sent){for(Intent intent:sent)if(intent.getData()!=null||QqMusicBridge.WEDRIVE_ACTION.equals(intent.getAction()))count++;}return count;}
    void run(){
        TestPreferences preferences=new TestPreferences(test.getTargetContext());Field singleton=null;Object previous=null;
        try{
            check("absent initial timing evidence cannot create a running clock",()->{PlaybackFreshness p=new PlaybackFreshness();p.state(true,1000);require(p.project(30000)==0&&p.waiting(30000),"invented an initial clock");});
            check("sparse playback evidence caps interpolation at six seconds",()->{PlaybackFreshness p=new PlaybackFreshness();p.sample(10000,true,1000);require(p.project(5000)==5000&&p.project(30000)==7000&&p.waiting(30000),"unbounded clock");});
            check("repeated stationary native samples stop interpolation",()->{PlaybackFreshness p=new PlaybackFreshness();p.sample(10000,true,1000);p.sample(10000,true,3200);require(p.project(5000)==3200&&p.waiting(5000),"stationary source still advances");});
            check("advancing native sample releases a held clock",()->{PlaybackFreshness p=new PlaybackFreshness();p.sample(10000,true,1000);p.sample(10000,true,3200);p.sample(11000,true,5000);require(p.project(5500)==5500&&!p.waiting(5500),"fresh progress did not resume");});
            check("coarse duplicate samples do not instantly declare a hold",()->{PlaybackFreshness p=new PlaybackFreshness();p.sample(10000,true,1000);p.sample(10000,true,1500);require(!p.waiting(1700),"normal duplicate stalled display");});
            check("local seek grants a bounded preview without replacing native evidence",()->{PlaybackFreshness p=new PlaybackFreshness();p.sample(10000,true,1000);p.seek(20000);require(p.sampledAt()==1000&&p.project(40000)==26000&&p.waiting(40000),"seek fabricated native progress");});
            Config cfg=new Config(test.getTargetContext());cfg.put("autoRecoverApps",false);
            cfg.p.edit().putString("qqLastTitle","缓存歌曲").putString("qqLastArtist","缓存歌手").putString("qqLyricSong","缓存歌曲\n缓存歌手").putString("qqLrc","[00:01.00]有效缓存歌词\n[00:05.00]下一句").apply();
            singleton=LinkedAppCoordinator.class.getDeclaredField("instance");singleton.setAccessible(true);previous=singleton.get(null);final Field field=singleton;
            Context wrapped=new ContextWrapper(test.getTargetContext()){
                private final PackageManager packages=new MockPackageManager(){@Override public ApplicationInfo getApplicationInfo(String pkg,int flags)throws NameNotFoundException{if(!Config.MUSIC.equals(pkg))throw new NameNotFoundException(pkg);ApplicationInfo info=new ApplicationInfo();info.packageName=pkg;info.uid=android.os.Process.myUid();return info;}};
                @Override public PackageManager getPackageManager(){return packages;}
                @Override public Context getApplicationContext(){return this;}
                @Override public void sendBroadcast(Intent intent){sent.add(new Intent(intent));}
            };
            main(()->{try{coordinator=new LinkedAppCoordinator(wrapped,i->sent.add(new Intent(i)),pkg->true);field.set(null,coordinator);coordinator.hostReady();bridge=new QqMusicBridge(wrapped,()->{},"-weak-network");bridge.start();bridge.resume();}catch(Exception error){throw new RuntimeException(error);}});
            event("update_song","\"key_title\":\"缓存歌曲\",\"key_artist\":[{\"name\":\"缓存歌手\"}],\"curr_time\":1000,\"total_time\":180000");
            event("update_state","\"curr_time\":1000,\"state\":2");
            event("update_lyric","\"code\":-1,\"lyric\":\"\",\"curr_time\":0");
            check("failed lyric response preserves matching cached LRC",()->main(()->require(bridge.lyrics.size()==2&&"有效缓存歌词".equals(bridge.lyrics.get(0).text),"cache was erased")));
            check("failed lyric response does not overwrite valid lyric cache",()->require(cfg.p.getString("qqLrc","").contains("有效缓存歌词"),"disk cache erased"));
            check("late lyric time cannot rewind established playback",()->main(()->require(bridge.currentPosition()>=1000,"metadata rewound progress")));
            int initial=subscriptions();Thread.sleep(2300);
            check("lyric failure requests read-only recovery without playback",()->require(subscriptions()>initial&&controls()==0,"recovery missing or replayed control"));
            event("update_lyric","\"code\":0,\"lyric\":\"[00:01.00]恢复后的歌词\\n[00:04.00]恢复第二句\"");
            check("valid LRC replaces the previous cached text",()->main(()->require(bridge.lyrics.size()==2&&"恢复后的歌词".equals(bridge.lyrics.get(0).text),"successful lyrics not installed")));
            event("update_lyric","\"code\":0,\"lyric\":\"\"");
            check("empty later response cannot erase valid current-song LRC",()->main(()->require(bridge.lyrics.size()==2,"empty response erased valid lyrics")));
            event("update_song","\"key_title\":\"新歌曲\",\"key_artist\":[],\"curr_time\":1000,\"total_time\":180000");
            event("update_lyric","\"code\":-1");
            check("new song cannot display the former song's cached lyrics",()->main(()->require(bridge.lyrics.isEmpty()&&bridge.lyricMessage.contains("稍后重试"),"old lyrics leaked to new song")));
            event("update_state","\"curr_time\":2000,\"state\":2");Thread.sleep(7300);
            long[] held={0};main(()->held[0]=bridge.currentPosition());Thread.sleep(900);
            check("silent native state holds the display instead of advancing forever",()->main(()->require(bridge.currentPosition()<=8100&&Math.abs(bridge.currentPosition()-held[0])<100&&bridge.waitingForProgress(),"stale progress still advances")));
            check("progress recovery never issues play or seek",()->require(controls()==0,"clock recovery changed playback"));
            event("update_state","\"curr_time\":12000,\"state\":2");long[] resumed={0};main(()->resumed[0]=bridge.currentPosition());Thread.sleep(600);
            check("fresh native progress resumes the display",()->main(()->require(bridge.currentPosition()>resumed[0]+350&&!bridge.waitingForProgress(),"fresh progress did not resume")));
            event("update_state","\"curr_time\":12500,\"state\":1");long[] buffering={0};main(()->buffering[0]=bridge.currentPosition());Thread.sleep(600);
            check("native non-playing state stops the clock without changing the player",()->main(()->require(Math.abs(bridge.currentPosition()-buffering[0])<50&&bridge.state==1,"non-playing state advanced")));
            event("update_song","\"key_title\":\"无进度新歌\",\"key_artist\":[],\"total_time\":180000");event("update_state","\"state\":2");Thread.sleep(250);
            check("track change without native timing cannot inherit a running clock",()->main(()->require(bridge.currentPosition()==0&&bridge.waitingForProgress(),"track with no timing kept advancing")));
            int beforeNetwork=controls();main(bridge::networkAvailable);Thread.sleep(250);
            check("network recovery does not restart or seek the track",()->require(controls()==beforeNetwork,"network callback sent playback"));
            main(()->{bridge.pause();coordinator.hostPaused();});Thread.sleep(300);int paused=subscriptions();Thread.sleep(2300);
            check("paused session cancels delayed metadata recovery",()->require(subscriptions()==paused,"paused session kept polling"));
            main(bridge::stop);int stopped=sent.size();Thread.sleep(600);
            check("released bridge stops all remaining network repair work",()->require(sent.size()==stopped,"released bridge still sends requests"));
        }catch(Throwable error){failed++;report.append("FAIL fixture ").append(error).append('\n');}
        finally{final Field field=singleton;final Object old=previous;main(()->{if(bridge!=null)bridge.stop();if(coordinator!=null)coordinator.hostPaused();try{if(field!=null)field.set(null,old);}catch(Exception e){throw new RuntimeException(e);}});preferences.close();}
        Bundle result=new Bundle();result.putString("stream",report+"WEAK_NETWORK_RESULT passed="+passed+" failed="+failed+"\n");test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
