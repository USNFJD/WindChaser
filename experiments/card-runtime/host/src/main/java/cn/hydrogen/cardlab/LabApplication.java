package cn.hydrogen.cardlab;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import com.morgoo.droidplugin.PluginHelper;
import java.io.File;
import java.io.FileOutputStream;

/** This prototype is deliberately limited to the API 19 runtime it was built for. */
public class LabApplication extends Application {
    @Override protected void attachBaseContext(Context base) {
        if(Build.VERSION.SDK_INT==19) PluginHelper.getInstance().applicationAttachBaseContext(base);
        super.attachBaseContext(base);
        final File log=new File(base.getFilesDir(),"runtime-crashes.txt");
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try(FileOutputStream out=new FileOutputStream(log,true)) {
                out.write(("\n"+System.currentTimeMillis()+" pid="+android.os.Process.myPid()+"\n"+android.util.Log.getStackTraceString(error)+"\n").getBytes("UTF-8"));
            }catch(Exception ignored){}
            if(previous!=null) previous.uncaughtException(thread,error);
        });
    }
    @Override public void onCreate() {
        super.onCreate();
        if(Build.VERSION.SDK_INT==19) PluginHelper.getInstance().applicationOnCreate(getBaseContext());
    }
}
