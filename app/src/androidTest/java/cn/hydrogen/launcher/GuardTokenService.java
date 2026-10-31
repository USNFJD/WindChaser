package cn.hydrogen.launcher;
import android.app.Service;
import android.content.Intent;
import android.os.*;
/** Test APK only: provides a genuinely remote Binder which can die independently. */
public class GuardTokenService extends Service {
    private Messenger forward;
    private final Messenger token=new Messenger(new Handler(Looper.getMainLooper()){
        @Override public void handleMessage(Message message){if(message.what==77){android.os.Process.killProcess(android.os.Process.myPid());return;}if(message.what==78){forward=message.replyTo;return;}if(forward!=null)try{forward.send(Message.obtain(message));}catch(RemoteException ignored){}}
    });
    @Override public IBinder onBind(Intent intent){return token.getBinder();}
}
