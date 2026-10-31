package cn.hydrogen.launcher;
import android.app.*;import android.content.*;import java.lang.ref.WeakReference;
/** User-clicked, signature-protected return to the existing launcher task. */
public final class DesktopReturnReceiver extends BroadcastReceiver{
    static final String ACTION="cn.windchaser.unified.RETURN_DESKTOP";
    private static WeakReference<MainActivity> desktop=new WeakReference<>(null);
    static void attach(MainActivity activity){desktop=new WeakReference<>(activity);}
    static void detach(MainActivity activity){if(desktop.get()==activity)desktop.clear();}
    @Override public void onReceive(Context context,Intent intent){
        if(intent==null||!ACTION.equals(intent.getAction()))return;
        MainActivity activity=desktop.get();
        if(activity!=null&&!activity.isFinishing()&&UnifiedInitService.admitted()){
            try{
                ActivityManager manager=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);
                if(manager!=null){manager.moveTaskToFront(activity.getTaskId(),0);LauncherApp.record("QQ返回桌面：复用现有任务 "+activity.getTaskId(),null);return;}
            }catch(RuntimeException denied){LauncherApp.record("复用桌面任务受限，尝试显式返回",denied);}
        }
        // No HOME category: it can create a second task when the desktop was
        // originally opened as an ordinary launcher icon or from bootstrap.
        context.startActivity(new Intent(context,MainActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)
            .putExtra("windchaser.return_to_workspace",true));
    }
}
