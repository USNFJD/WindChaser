package cn.hydrogen.launcher;

import android.content.Context;
import android.os.Build;
import com.morgoo.droidplugin.PluginHelper;

/** The host is the only Android-installed app. Platform hooks are selected by the running Android API level. */
public final class UnifiedApplication extends LauncherApp {
    private boolean hostProcess;
    @Override protected void attachBaseContext(Context base) {
        if(Build.VERSION.SDK_INT >= 28) org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("");
        hostProcess = base.getPackageName().equals(processName());
        if (Build.VERSION.SDK_INT >= 17) PluginHelper.getInstance().applicationAttachBaseContext(base);
        super.attachBaseContext(base);
    }
    @Override public void onCreate() {
        if (Build.VERSION.SDK_INT >= 17) PluginHelper.getInstance().applicationOnCreate(getBaseContext());
        // Avoid initializing wallpaper, map state and UI listeners in every stub/service process.
        if (hostProcess) super.onCreate();
    }
    private static String processName() {
        try {
            java.io.FileInputStream in = new java.io.FileInputStream("/proc/self/cmdline");
            try { byte[] b = new byte[256]; int n=in.read(b), end=0; while(end<n && b[end]!=0)end++;return new String(b,0,end,"UTF-8"); }
            finally { in.close(); }
        } catch(Exception e) { return ""; }
    }
}
