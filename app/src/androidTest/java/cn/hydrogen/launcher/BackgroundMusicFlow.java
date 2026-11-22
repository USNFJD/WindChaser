package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.test.mock.MockPackageManager;
import java.lang.reflect.Field;
import java.util.*;

/** Real Android receiver/bridge lifecycle; QQ replies are protocol fixtures. */
final class BackgroundMusicFlow {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder("QQ background recovery: production bridge, Android broadcasts, simulated QQ protocol replies\n");
    private final List<Intent> sent=Collections.synchronizedList(new ArrayList<>());
    private QqMusicBridge bridge;private LinkedAppCoordinator coordinator;
    private int passed,failed,updates;
    private interface Check{void run()throws Exception;}
    BackgroundMusicFlow(Instrumentation test){this.test=test;}
    private void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private void main(Runnable run){Throwable[] error={null};test.runOnMainSync(()->{try{run.run();}catch(Throwable e){error[0]=e;}});test.waitForIdleSync();if(error[0]!=null)throw new AssertionError(error[0]);}
    private void check(String name,Check run){try{run.run();passed++;report.append("PASS ");}catch(Throwable e){failed++;report.append("FAIL ").append(e).append(" ");}report.append(name).append('\n');}
    private int playbackCommands(){int count=0;synchronized(sent){for(Intent i:sent){if(i.getData()!=null)count++;else if(QqMusicBridge.WEDRIVE_ACTION.equals(i.getAction()))try{int index=new org.json.JSONObject(i.getStringExtra(QqMusicBridge.WEDRIVE_EXTRA)).getJSONObject("command").getJSONObject("extData").getInt("index");if(index==4||index==5||index==12||index==13)count++;}catch(Exception ignored){}}}return count;}
    private int subscriptions(){int count=0;synchronized(sent){for(Intent i:sent)if(i.getIntExtra("action",0)==100)count++;}return count;}
    private void event(String method,String fields)throws Exception{
        test.getTargetContext().sendBroadcast(new Intent(QqMusicBridge.EVENTS).setPackage(test.getTargetContext().getPackageName()).putExtra(QqMusicBridge.EXTRA,"{\"module\":\"play\",\"command\":{\"method\":\""+method+"\",\"data\":{"+fields+"}}}"));
        Thread.sleep(100);test.waitForIdleSync();
    }
    void run(){
        Config cfg=new Config(test.getTargetContext());String backup="";TestPreferences preferences=new TestPreferences(test.getTargetContext());Object previous=null;Field singleton=null;
        try{
            backup=cfg.exportJson();cfg.put("autoRecoverApps",false);
            singleton=LinkedAppCoordinator.class.getDeclaredField("instance");singleton.setAccessible(true);previous=singleton.get(null);final Field holder=singleton;
            Context wrapped=new ContextWrapper(test.getTargetContext()){
                private final PackageManager packages=new MockPackageManager(){@Override public ApplicationInfo getApplicationInfo(String pkg,int flags)throws NameNotFoundException{if(!Config.MUSIC.equals(pkg))throw new NameNotFoundException(pkg);ApplicationInfo info=new ApplicationInfo();info.packageName=pkg;info.uid=android.os.Process.myUid();return info;}};
                @Override public PackageManager getPackageManager(){return packages;}
                @Override public Context getApplicationContext(){return this;}
                @Override public void sendBroadcast(Intent intent){sent.add(new Intent(intent));}
            };
            main(()->{try{coordinator=new LinkedAppCoordinator(wrapped,i->sent.add(new Intent(i)),pkg->true);holder.set(null,coordinator);coordinator.hostReady();bridge=new QqMusicBridge(wrapped,()->updates++,"-background-test");bridge.start();bridge.resume();}catch(Exception e){throw new RuntimeException(e);}});
            check("running QQ is subscribed before independent readiness proof",()->require(subscriptions()>0,"no real subscription request"));
            int initialSubscriptions=subscriptions();Thread.sleep(850);
            check("missed initial snapshot is re-requested without playback commands",()->require(subscriptions()>initialSubscriptions&&playbackCommands()==0,"missing read-only snapshot repair"));
            event("update_state","\"curr_time\":5000,\"state\":2");
            check("cached track is not seekable before actual current-song metadata",()->main(()->require(!bridge.trackConfirmed&&!bridge.canSeek(),"cached metadata treated as current song")));
            event("update_lyric","\"key_title\":\"当前歌词歌曲\",\"key_artist\":[],\"curr_time\":5000,\"total_time\":180000,\"code\":-1");
            check("native lyric payload with full song metadata confirms current track",()->main(()->require(bridge.trackConfirmed&&bridge.canSeek(),"current lyric metadata ignored")));
            event("update_song","\"key_title\":\"后台歌曲\",\"key_artist\":[{\"name\":\"测试歌手\"}],\"curr_time\":12000,\"total_time\":180000");
            event("update_state","\"curr_time\":12000,\"state\":2");
            event("update_lyric","\"code\":0,\"lyric\":\"[00:10.00]第一行\\n[00:12.00]正在播放\\n[00:15.00]下一行\"");
            check("background replies restore song, clock and lyrics",()->main(()->require(bridge.connected&&bridge.canSeek()&&bridge.state==2&&bridge.lyrics.size()==3,"incomplete actual music state")));
            event("update_state","\"key_title\":\"后台歌曲\",\"curr_time\":12500,\"state\":2");
            check("partial state keeps known artist and lyrics",()->main(()->require("测试歌手".equals(bridge.artist)&&bridge.lyrics.size()==3,"partial state erased track data")));
            event("update_album","\"key_title\":\"后台歌曲\",\"code\":-1");
            check("partial album metadata does not reset the song",()->main(()->require("测试歌手".equals(bridge.artist)&&bridge.lyrics.size()==3&&bridge.canSeek(),"album metadata reset known song")));
            event("update_song","\"curr_time\":13000,\"total_time\":180000");
            check("song notification without a title is not an empty queue",()->main(()->require(bridge.trackConfirmed&&bridge.canSeek()&&bridge.lyrics.size()==3,"partial song notification cleared queue")));
            main(()->coordinator.probe(Config.MUSIC,ready->{}));Thread.sleep(650);
            check("separate probe timeout cannot erase proven music connection",()->main(()->require(bridge.connected&&bridge.canSeek()&&bridge.state==2,"probe timeout disconnected working controls")));
            check("pause, previous, next, seek and play mode keep direct commands",()->main(()->{bridge.command(1);bridge.command(2);bridge.command(3);require(bridge.seekTo(60000,bridge.trackRevision),"seek disabled");bridge.setMode(QqMusicBridge.SHUFFLE);int commands=playbackCommands();require(commands==4,"lost control broadcasts "+commands);require(bridge.pendingMode==QqMusicBridge.SHUFFLE,"mode not pending");}));
            event("update_play_mode","\"play_mode\":105");event("update_state","\"curr_time\":60000,\"state\":2");
            check("live command replies reconcile progress and mode",()->main(()->require(bridge.pendingMode==0&&bridge.playMode==105&&!bridge.seekPending()&&bridge.currentPosition()>=60000,"state replies not applied")));
            int before=subscriptions();main(()->{bridge.pause();coordinator.hostPaused();});Thread.sleep(250);main(()->{coordinator.hostReady();bridge.resume();});Thread.sleep(250);
            event("update_state","\"curr_time\":61000,\"state\":2");
            check("returning desktop refreshes already running music",()->{require(subscriptions()>before,"no reentry subscription");main(()->require(bridge.connected&&bridge.position>=61000,"reentry kept stale progress"));});
            // Recreate inside the 200ms coalescing window. The second subscription
            // must be queued, because the old receiver can already be destroyed.
            main(()->coordinator.requestMusicSubscription());int recreateBefore=subscriptions();
            main(()->{bridge.stop();bridge=new QqMusicBridge(wrapped,()->updates++,"-background-test");bridge.start();bridge.resume();});Thread.sleep(300);
            event("update_song","\"key_title\":\"重建后歌曲\",\"key_artist\":[],\"curr_time\":5000,\"total_time\":200000");event("update_state","\"curr_time\":5000,\"state\":2");
            check("launcher recreation within coalescing window still restores controls",()->{require(subscriptions()>recreateBefore,"recreated receiver missed deferred subscription");main(()->require(bridge.connected&&bridge.canSeek()&&"重建后歌曲".equals(bridge.title),"recreated bridge not updated"));});
            final int beforeRecovery=playbackCommands();
            main(()->bridge.command(3));Thread.sleep(2200);
            check("missing command ACK rechecks state without repeating next track",()->{int after=playbackCommands();require(after==beforeRecovery+1,"next-track command replayed");});
            event("update_album","\"code\":-1");
            check("album metadata cannot acknowledge a pending playback command",()->main(()->{try{Field pending=QqMusicBridge.class.getDeclaredField("commandPending");pending.setAccessible(true);require(pending.getInt(bridge)==3,"unrelated event cancelled command");}catch(Exception e){throw new RuntimeException(e);}}));
            event("update_song","\"key_title\":\"切歌确认\",\"key_artist\":[],\"curr_time\":0,\"total_time\":200000");
            check("real changed-track reply completes the command",()->main(()->{try{Field pending=QqMusicBridge.class.getDeclaredField("commandPending");pending.setAccessible(true);require(pending.getInt(bridge)==-1,"new track not confirmed");}catch(Exception e){throw new RuntimeException(e);}}));
            main(()->{bridge.setMode(QqMusicBridge.SINGLE);bridge.command(2);coordinator.packagesChanged();});
            check("known interface loss cancels outstanding mode and playback operations",()->main(()->{try{Field pending=QqMusicBridge.class.getDeclaredField("commandPending");pending.setAccessible(true);require(pending.getInt(bridge)==-1&&bridge.pendingMode==0,"old controls survive interface loss");}catch(Exception e){throw new RuntimeException(e);}}));
            main(()->coordinator.packagesChanged());
            check("actual package invalidation disconnects old music state",()->main(()->require(!bridge.connected&&bridge.state==0,"invalidated interface kept running clock")));
            event("update_state","\"curr_time\":6000,\"state\":2");main(()->coordinator.probe(Config.MUSIC,ready->{}));Thread.sleep(600);
            check("fresh music events recover after invalidation without stale loss latch",()->main(()->require(bridge.connected&&bridge.state==2,"old loss flag overwrote fresh event")));
            event("update_song","\"key_title\":\"\",\"key_artist\":[]");
            check("empty native queue revokes cached seek and clock",()->main(()->require(!bridge.trackConfirmed&&!bridge.canSeek()&&bridge.duration==0&&bridge.state==0,"empty queue reused old song")));
            main(()->{bridge.stop();coordinator.hostPaused();});int stopped=updates;event("update_state","\"curr_time\":7000,\"state\":2");Thread.sleep(550);
            check("released bridge removes receiver, listeners and clock",()->require(updates==stopped,"released bridge still receives events"));
            int sentBeforeClose=sent.size();main(()->bridge.command(0));check("closed bridge cannot issue playback commands",()->require(sent.size()==sentBeforeClose,"closed bridge sent a command"));
        }catch(Throwable e){failed++;report.append("FAIL fixture ").append(e).append('\n');}
        finally{final Object old=previous;final Field field=singleton;main(()->{if(bridge!=null)bridge.stop();if(coordinator!=null)coordinator.hostPaused();try{if(field!=null)field.set(null,old);}catch(Exception e){throw new RuntimeException(e);}});preferences.close();}
        Bundle result=new Bundle();result.putString("stream",report+"BACKGROUND_MUSIC_RESULT passed="+passed+" failed="+failed+"\n");test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
