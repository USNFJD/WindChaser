package cn.hydrogen.launcher;

import android.app.Service;
import android.content.*;
import android.os.*;
import android.util.AtomicFile;
import java.io.*;
import org.json.JSONObject;

/** Runs in :mapguard. Binder death and task removal do not depend on Activity callbacks. */
public class MapWindowGuardService extends Service {
    static final int ARM=1,CLOSE=2;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Messenger messenger=new Messenger(new Handler(Looper.getMainLooper()){
        @Override public void handleMessage(Message message){
            if(message.replyTo==null)return;IBinder token=message.replyTo.getBinder();
            if(message.what==ARM)arm(token);
            else if(message.what==CLOSE&&(owner==null||owner.equals(token)))cleanup("host-hidden");
        }
    });
    private IBinder owner;private IBinder.DeathRecipient death;private int attempt;private String reason="";
    private final Runnable sweep=this::sendClose;
    private final Runnable idle=()->{if(owner==null)cleanup("no-owner");};
    @Override public void onCreate(){super.onCreate();handler.postDelayed(idle,1500);}
    @Override public IBinder onBind(Intent intent){return messenger.getBinder();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){return START_NOT_STICKY;}
    private void arm(IBinder token){
        handler.removeCallbacks(sweep);handler.removeCallbacks(idle);unlink();owner=token;
        death=()->handler.post(()->{if(owner!=null&&owner.equals(token))cleanup("owner-died");});
        try{owner.linkToDeath(death,0);state("armed",0);}catch(RemoteException gone){cleanup("owner-already-gone");}
    }
    private void unlink(){if(owner!=null&&death!=null)try{owner.unlinkToDeath(death,0);}catch(Exception ignored){}owner=null;death=null;}
    private void cleanup(String why){handler.removeCallbacks(sweep);handler.removeCallbacks(idle);unlink();reason=why;attempt=0;sendClose();}
    private void sendClose(){
        if(owner!=null)return;
        try{AmapFloatingController.closeAll(this);}catch(Exception error){LauncherApp.record("地图独立守护关闭失败",error);}
        state(reason,++attempt);
        int[] delay={80,200,500,1000,1600};
        if(attempt<=delay.length)handler.postDelayed(sweep,delay[attempt-1]);else stopSelf();
    }
    private void state(String status,int count){
        AtomicFile file=new AtomicFile(new File(getFilesDir(),"map-window-guard.json"));FileOutputStream out=null;
        try{out=file.startWrite();out.write(new JSONObject().put("reason",status).put("close_requests",count).put("pid",android.os.Process.myPid()).put("time",SystemClock.elapsedRealtime()).toString().getBytes("UTF-8"));file.finishWrite(out);}catch(Exception error){if(out!=null)file.failWrite(out);}
        if(count==1)LauncherApp.record("地图独立守护关闭；原因="+status+"；无高德成功回执",null);
    }
    @Override public boolean onUnbind(Intent intent){cleanup("host-unbound");return false;}
    @Override public void onTaskRemoved(Intent rootIntent){if(rootIntent==null||rootIntent.getComponent()==null||MainActivity.class.getName().equals(rootIntent.getComponent().getClassName()))cleanup("task-removed");super.onTaskRemoved(rootIntent);}
    @Override public void onDestroy(){handler.removeCallbacksAndMessages(null);unlink();try{AmapFloatingController.closeAll(this);}catch(Exception ignored){}super.onDestroy();}
}
