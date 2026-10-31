package cn.hydrogen.launcher;
import android.content.*;
import android.os.Build;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c,Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()) || !new Config(c).b("boot",false)) return;
        LauncherApp.record("收到开机广播",null);
        if (Build.VERSION.SDK_INT < 29) {
            try { c.startActivity(new Intent(c,UnifiedBootstrapActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception e) { LauncherApp.record("开机启动失败；请设置默认桌面",e); }
        } else LauncherApp.record("Android 10+ 请设置默认 HOME，由系统启动桌面",null);
    }
}
