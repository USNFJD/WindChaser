package cn.hydrogen.launcher;

import android.content.*;
import android.os.*;

/** The independent service can finish cleanup after this Activity or process is gone. */
final class MapWindowGuard {
    private final Context context;
    private final Messenger owner=new Messenger(new Handler(Looper.getMainLooper()));
    private Messenger remote;private boolean bound,visible;
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName name,IBinder binder){remote=new Messenger(binder);send(visible?MapWindowGuardService.ARM:MapWindowGuardService.CLOSE);}
        @Override public void onServiceDisconnected(ComponentName name){remote=null;}
    };
    MapWindowGuard(Context context){this.context=context.getApplicationContext();}
    void show(){
        visible=true;if(bound){send(MapWindowGuardService.ARM);return;}
        try{Intent intent=new Intent(context,MapWindowGuardService.class);context.startService(intent);bound=context.bindService(intent,connection,Context.BIND_AUTO_CREATE);}
        catch(Exception error){LauncherApp.record("地图退出守护启动失败",error);}
    }
    void hide(){
        visible=false;if(!bound)return;send(MapWindowGuardService.CLOSE);
        try{context.unbindService(connection);}catch(Exception ignored){}bound=false;remote=null;
    }
    private void send(int command){if(remote==null)return;try{Message message=Message.obtain(null,command);message.replyTo=owner;remote.send(message);}catch(RemoteException error){LauncherApp.record("地图退出守护连接中断",null);}}
}
