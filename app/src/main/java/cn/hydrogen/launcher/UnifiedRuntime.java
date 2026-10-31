package cn.hydrogen.launcher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.morgoo.droidplugin.pm.PluginManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/** Private module installation, not Android package installation or an external app fallback. */
public final class UnifiedRuntime {
    public interface Listener { void update(String status, boolean ready, String failure); }
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ArrayList<WeakReference<Listener>> listeners=new ArrayList<>();
    private static boolean started,ready;
    private static String status="等待准备",failure="";
    private static Context app;
    public static synchronized void prepare(Context c, Listener listener) {
        app=c.getApplicationContext();listeners.add(new WeakReference<>(listener));
        listener.update(status,ready(),failure);
        // Retry a failed setup or a disconnected manager without duplicating an active import.
        if(started && (!failure.isEmpty() || (ready && !PluginManager.getInstance().isConnected()))) {
            started=false;ready=false;failure="";
        }
        if(started)return;started=true;
        new Thread(UnifiedRuntime::install,"unified-modules").start();
    }
    public static synchronized boolean ready() { return ready && PluginManager.getInstance().isConnected(); }
    private static void report(String value) {
        synchronized(UnifiedRuntime.class){status=value;}
        Log.i("WindChaserUnified",value);
        try(PrintWriter out=new PrintWriter(new OutputStreamWriter(new FileOutputStream(new File(app.getFilesDir(),"unified.log"),true),"UTF-8"))){out.println(System.currentTimeMillis()+" "+value);}catch(Exception ignored){}
        MAIN.post(()->{
            ArrayList<Listener> active=new ArrayList<>();String current,error;boolean available;
            synchronized(UnifiedRuntime.class){
                current=status;error=failure;available=ready();
                for(int i=listeners.size()-1;i>=0;i--){Listener listener=listeners.get(i).get();if(listener==null)listeners.remove(i);else active.add(listener);}
            }
            for(Listener listener:active)listener.update(current,available,error);
        });
    }
    private static void install() {
        try {
            if(Build.VERSION.SDK_INT<17)throw new IOException("需要 Android 4.2 / API 17 或以上系统");
            if(Build.VERSION.SDK_INT<21 && !Build.CPU_ABI.startsWith("armeabi"))throw new IOException("内置应用仅含 ARMv7 原生库，当前为 "+Build.CPU_ABI);
            report("连接内置运行环境…");PluginManager pm=PluginManager.getInstance();pm.waitForConnected(20000);
            if(!pm.isConnected())throw new IOException("内置运行环境连接超时");
            String raw;try(InputStream in=app.getAssets().open("unified/modules.json")){raw=new String(read(in),"UTF-8");}
            JSONArray modules=new JSONObject(raw).getJSONArray("modules");
            for(int i=0;i<modules.length();i++) {
                JSONObject module=modules.getJSONObject(i);String pkg=module.getString("package"),hash=module.getString("sha256"),name=module.getString("name");
                PackageInfo present=pm.getPackageInfo(pkg,PackageManager.GET_SIGNATURES);
                String saved=app.getSharedPreferences("unified-modules",0).getString(pkg,"");
                if(present!=null && hash.equals(saved) && present.versionCode==module.getInt("versionCode")) {report(name+"：复用已有内置模块");continue;}
                if(app.getFilesDir().getUsableSpace()<module.getLong("bytes")*2+180L*1024*1024)throw new IOException("内部存储空间不足，首次准备建议至少保留 500 MB");
                File incoming=new File(app.getCacheDir(),"module-"+i+".apk");
                try {
                    report("展开 "+name+"…");MessageDigest digest=MessageDigest.getInstance("SHA-256");long total=0;int progress=-1;
                    try(InputStream in=app.getAssets().open("unified/"+module.getString("file"));FileOutputStream out=new FileOutputStream(incoming)){
                        byte[] b=new byte[128*1024];int n;while((n=in.read(b))!=-1){out.write(b,0,n);digest.update(b,0,n);total+=n;int percent=(int)(total*100/module.getLong("bytes"))/10*10;if(percent!=progress){progress=percent;report("展开 "+name+" "+percent+"%");}}out.getFD().sync();
                    }
                    if(total!=module.getLong("bytes") || !hash.equals(hex(digest.digest())))throw new IOException(name+"文件完整性校验失败");
                    PackageInfo info=app.getPackageManager().getPackageArchiveInfo(incoming.getPath(),PackageManager.GET_SIGNATURES);
                    if(info==null || !pkg.equals(info.packageName) || info.versionCode!=module.getInt("versionCode") || info.signatures==null || info.signatures.length!=1 || !module.getString("certificate").equals(hex(MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray()))))throw new IOException(name+"身份校验失败");
                    report("初始化 "+name+"，首次准备需要展开原生库和 DEX…");int result=pm.installPackage(incoming.getPath(),2);
                    if(result!=1)throw new IOException(name+"内置导入失败，代码 "+result);
                    present=pm.getPackageInfo(pkg,PackageManager.GET_SIGNATURES);
                    if(present==null || present.applicationInfo==null || !new File(present.applicationInfo.sourceDir).getCanonicalPath().startsWith(app.getApplicationInfo().dataDir+File.separator))throw new IOException(name+"未位于本应用的私有目录");
                    if(!app.getSharedPreferences("unified-modules",0).edit().putString(pkg,hash).commit())throw new IOException("保存内置模块状态失败");
                    report(name+"：内置导入完成，私有路径="+present.applicationInfo.sourceDir);
                } finally {if(incoming.exists()&&!incoming.delete())Log.w("WindChaserUnified","temporary module retained: "+incoming);}
            }
            ready=true;report("内置模块准备完成；系统只需安装本应用。地图出图、播放和登录仍需实测。");
        } catch(Throwable error) {failure=error.toString();Log.e("WindChaserUnified","setup failed",error);report("准备失败："+failure);}
    }
    // Query the private runtime, including pending process reservations. This can
    // only avoid probing a receiver which cannot exist; it never asserts ready.
    static boolean definitelyNotRunning(String pkg) {
        if(!Config.MAP.equals(pkg)||!ready())return false;
        try{return PluginManager.getInstance().getPackageProcessState(pkg)==0;}
        catch(android.os.RemoteException|RuntimeException unavailable){return false;}
    }
    public static boolean launch(Context context,String pkg) {return launch(context,pkg,null,0);}
    static boolean launch(Context context,String pkg,String visualSession,long until) {
        try {
            if(!ready() || !PluginManager.getInstance().isPluginPackage(pkg))throw new IOException("内置模块尚未准备好");
            Intent intent=context.getPackageManager().getLaunchIntentForPackage(pkg);
            if(intent==null)throw new IOException("未找到内置应用入口");
            if(visualSession!=null)intent.putExtra("windchaser.init.session",visualSession).putExtra("windchaser.init.until",until)
                .putExtra("windchaser.init.first",!context.getSharedPreferences("unified-native-initialization",0).getBoolean(pkg,false));
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_NO_ANIMATION));return true;
        } catch(Exception e) {report("启动失败 "+pkg+"："+e);return false;}
    }
    private static byte[] read(InputStream in)throws IOException {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
    private static String hex(byte[] b){StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format(java.util.Locale.US,"%02x",v&255));return out.toString();}
}
