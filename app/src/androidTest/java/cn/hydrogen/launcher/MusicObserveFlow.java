package cn.hydrogen.launcher;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;import java.util.*;
/** Diagnostics only: actual module broadcasts, never synthetic events. */
final class MusicObserveFlow {
    private final Instrumentation test;MusicObserveFlow(Instrumentation t){test=t;}
    @android.annotation.SuppressLint("UnspecifiedRegisterReceiverFlag") // Legacy overload is confined to API < 33.
    void run(){StringBuilder log=new StringBuilder("Actual QQ event observation\n");Context app=test.getTargetContext();List<String> events=Collections.synchronizedList(new ArrayList<>());
        BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){try{JSONObject root=new JSONObject(i.getStringExtra(QqMusicBridge.EXTRA));JSONObject command=root.optJSONObject("command"),data=command==null?null:command.optJSONObject("data");if(command!=null&&data!=null)events.add(command.optString("method")+" keys="+data.names()+" title="+data.optString("key_title")+" state="+data.optInt("state",-1));}catch(Exception e){events.add(e.toString());}}};
        try{test.runOnMainSync(()->{if(Build.VERSION.SDK_INT>=33)app.registerReceiver(receiver,new IntentFilter(QqMusicBridge.EVENTS),Context.RECEIVER_EXPORTED);else app.registerReceiver(receiver,new IntentFilter(QqMusicBridge.EVENTS));});
            MainActivity home=(MainActivity)test.startActivitySync(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
            Thread.sleep(1000);java.lang.reflect.Field f=MainActivity.class.getDeclaredField("musicBridge");f.setAccessible(true);QqMusicBridge bridge=(QqMusicBridge)f.get(home);test.runOnMainSync(bridge::subscribe);Thread.sleep(2200);
            log.append("bridge connected=").append(bridge.connected).append(" currentTrack=").append(bridge.trackConfirmed).append(" title=").append(bridge.title).append(" state=").append(bridge.state).append('\n');
            test.runOnMainSync(()->bridge.command(1));Thread.sleep(1200);test.runOnMainSync(()->bridge.command(0));Thread.sleep(2200);
            synchronized(events){for(String event:events)log.append(event).append('\n');}
            log.append("final state=").append(bridge.state).append(" confirmed=").append(bridge.trackConfirmed).append('\n');
        }catch(Exception error){log.append(error);}finally{test.runOnMainSync(()->app.unregisterReceiver(receiver));}
        Bundle result=new Bundle();result.putString("stream",log.toString());test.finish(Activity.RESULT_OK,result);
    }
}
