package cn.hydrogen.launcher;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LauncherApp extends Application {
    private static Context context;
    @Override public void onCreate() {
        super.onCreate(); context = getApplicationContext();MapWorkspace.get(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks(){
            public void onActivityCreated(android.app.Activity a,android.os.Bundle state){}
            public void onActivityStarted(android.app.Activity a){}
            public void onActivityResumed(android.app.Activity a){if(!(a instanceof MainActivity))a.getWindow().getDecorView().post(()->ThemeStyle.activity(a));}
            public void onActivityPaused(android.app.Activity a){}public void onActivityStopped(android.app.Activity a){}public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}public void onActivityDestroyed(android.app.Activity a){}
        });
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error) -> {
            record("FATAL " + thread.getName(), error);
            if (previous != null) previous.uncaughtException(thread,error);
            else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
        });
        record("进程启动 API=" + Build.VERSION.SDK_INT + " model=" + Build.MODEL + " ABI=" + Build.CPU_ABI, null);
    }
    public static synchronized void record(String message, Throwable error) {
        Log.i("Hydrogen",message,error); if (context==null) return;
        try {
            File f = new File(context.getFilesDir(),"launcher.log");
            if (f.length()>256*1024) {
                File old = new File(context.getFilesDir(),"launcher.previous.log");
                if (old.exists() && !old.delete()) return;
                if (!f.renameTo(old)) return;
            }
            try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(f,true),"UTF-8"))) {
                out.println(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date())+" "+message);
                if (error!=null) error.printStackTrace(out);
            }
        } catch (Exception ignored) { Log.w("Hydrogen","日志写入失败",ignored); }
    }
}
