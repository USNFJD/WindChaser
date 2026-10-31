package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;

/** Deterministic clock cases and captured QQ transport; no commercial player. */
final class SeekSyncFlow {
    private final Instrumentation test;
    private final StringBuilder report=new StringBuilder("Synthetic untimestamped QQ snapshots; production clock/bridge. No commercial player executed.\n");
    private int passed,failed;
    private interface Check {void run() throws Exception;}
    SeekSyncFlow(Instrumentation test){this.test=test;}
    private void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private void check(String name,Check check){try{check.run();passed++;report.append("PASS ");}catch(Throwable error){failed++;report.append("FAIL ").append(error).append(' ');}report.append(name).append('\n');}
    private SeekPosition seek(long before,long target,boolean playing){SeekPosition p=new SeekPosition();p.begin(before,target,1000,playing);return p;}
    void run(){
        TestPreferences preferences=new TestPreferences(test.getTargetContext());
        check("forward seek advances beyond former 2.5s preview expiry",()->{SeekPosition p=seek(10000,60000,true);require(p.position(14000,5000)==64000,"reverted or froze");});
        check("backward seek rejects old advancing progress",()->{SeekPosition p=seek(90000,20000,true);require(!p.accept(93000,93000,4000,true)&&p.position(93000,4000)==23000,"old position won");});
        check("no callback does not reset the seek clock at a deadline",()->{SeekPosition p=seek(10000,60000,true);require(p.position(30000,21000)==80000&&!p.pending(21000),"deadline reset or spinner stuck");});
        check("matching progress confirms and later old snapshot is rejected",()->{SeekPosition p=seek(10000,60000,true);require(p.accept(61000,11000,2000,true)&&!p.pending(2000),"confirmation ignored");require(!p.accept(13000,63000,4000,true),"late old state accepted");});
        check("late lyric metadata never rewinds a seek",()->{SeekPosition p=seek(10000,60000,true);require(!p.accept(30000,80000,21000,false),"lyric metadata changed clock");});
        check("pause freezes estimate and resume continues from same position",()->{SeekPosition p=seek(10000,60000,true);p.state(false,3000);require(p.position(0,6000)==62000,"advanced during pause");p.state(true,6000);require(p.position(0,8000)==64000,"resume drift");});
        check("paused seek stays still until a playing state",()->{SeekPosition p=seek(10000,60000,false);require(p.position(0,5000)==60000,"paused seek advanced");p.state(true,5000);require(p.position(0,7000)==62000,"did not resume");});
        check("rapid second seek rejects both prior timelines",()->{SeekPosition p=seek(10000,60000,true);p.begin(p.position(0,2000),90000,2000,true);require(!p.accept(12000,0,3000,true)&&!p.accept(63000,0,4000,true),"obsolete seek won");require(p.position(0,4000)==92000,"new seek lost");});
        check("new QQ-side seek can correct current timeline",()->{SeekPosition p=seek(10000,60000,true);require(p.accept(120000,0,3000,true),"fresh correction blocked");});
        check("failed seek reconciles to fresh state after bounded guard",()->{SeekPosition p=seek(10000,60000,true);require(p.accept(19000,0,10000,true)&&p.position(19000,10000)==19000,"failed command hidden forever");});
        check("song change removes pending seek and metadata restrictions",()->{SeekPosition p=seek(10000,60000,true);p.reset();require(p.position(0,2000)==0&&!p.pending(2000)&&p.accept(100,0,2000,false),"old song survived");});
        check("small accepted correction remains authoritative",()->{SeekPosition p=seek(10000,60000,true);require(p.accept(60500,0,2200,true)&&p.position(60500,2200)==60500,"did not use QQ sample");});
        final QqMusicBridge[] holder={null};ArrayList<Intent> sent=new ArrayList<>();
        try{
            test.runOnMainSync(()->holder[0]=new QqMusicBridge(new ContextWrapper(test.getTargetContext()){
                @Override public void sendBroadcast(Intent intent){sent.add(new Intent(intent));}
            },()->{}));QqMusicBridge bridge=holder[0];
            check("bridge sends quantized relative seek and shares projected position",()->{test.runOnMainSync(()->{try{
                bridge.accept(message("update_song","\"key_title\":\"seek fixture\",\"key_artist\":[],\"curr_time\":10000,\"total_time\":180000"));
                bridge.state=2;bridge.receivedAt=SystemClock.elapsedRealtime();
                require(bridge.seekTo(60000,bridge.trackRevision),"seek not sent");require(bridge.currentPosition()>=59500&&bridge.currentPosition()<61000,"target not shared");
                bridge.accept(message("update_lyric","\"curr_time\":10000,\"code\":-1"));require(bridge.currentPosition()>=59500,"metadata reverted clock");
            }catch(Exception e){throw new RuntimeException(e);}});});
            SystemClock.sleep(3100);
            check("bridge still follows sought audio after 3 seconds",()->test.runOnMainSync(()->{require(bridge.currentPosition()>=62500&&bridge.currentPosition()<65000,"2.5s snapback");int commands=0;for(Intent intent:sent)if(intent.getData()!=null&&intent.getData().getQueryParameter("m1")!=null)commands++;require(commands==1,"relative seek retried");}));
            check("bridge accepts matching state then ignores delayed old state",()->test.runOnMainSync(()->{try{bridge.accept(message("update_state","\"curr_time\":63500,\"state\":2"));bridge.accept(message("update_state","\"curr_time\":13500,\"state\":2"));require(bridge.currentPosition()>=63000&&!bridge.seekPending(),"old state overwrote confirmed seek");}catch(Exception e){throw new RuntimeException(e);}}));
            check("bridge reset on next song prevents progress leaking",()->test.runOnMainSync(()->{try{bridge.accept(message("update_song","\"key_title\":\"next fixture\",\"key_artist\":[],\"curr_time\":0,\"total_time\":120000"));require(bridge.currentPosition()<1000&&!bridge.seekPending(),"seek leaked into next song");}catch(Exception e){throw new RuntimeException(e);}}));
        }finally{test.runOnMainSync(()->{if(holder[0]!=null)holder[0].stop();});preferences.close();}
        Bundle result=new Bundle();result.putString("stream",report+"SEEK_SYNC_RESULT passed="+passed+" failed="+failed+"\n");test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private static String message(String method,String fields){return "{\"module\":\"play\",\"command\":{\"method\":\""+method+"\",\"data\":{"+fields+"}}}";}
}
