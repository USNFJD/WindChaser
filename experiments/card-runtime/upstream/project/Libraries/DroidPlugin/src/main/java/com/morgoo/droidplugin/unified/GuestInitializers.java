/* WindChaser single-package experiment, LGPL-3.0-or-later. */
package com.morgoo.droidplugin.unified;
import android.app.Application;
import android.app.Instrumentation;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.util.Log;
import com.morgoo.droidplugin.pm.PluginManager;
import com.morgoo.droidplugin.reflect.MethodUtils;
import com.morgoo.helper.compat.ActivityThreadCompat;
import java.util.Arrays;
import java.util.Comparator;

/** Match the framework order: attach Application, create local providers, then Application.onCreate. */
public final class GuestInitializers {
    public static void make(Object loadedApk, android.content.Context host, String intendedProcess) throws Exception {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper())
            throw new IllegalStateException("guest Application must be created on main thread");
        if(android.os.Build.VERSION.SDK_INT>=28 && intendedProcess!=null) {
            Object thread=ActivityThreadCompat.currentActivityThread();
            Object bound=com.morgoo.droidplugin.reflect.FieldUtils.readField(thread,"mBoundApplication",true);
            com.morgoo.droidplugin.reflect.FieldUtils.writeField(bound,"processName",intendedProcess,true);
        }
        // Native attachBaseContext can cache its process role. The remote process
        // table may not yet contain this pid (or can be temporarily unavailable).
        // Publish this process's declared component identity before attach runs.
        android.content.pm.ApplicationInfo declared=(android.content.pm.ApplicationInfo)
            com.morgoo.droidplugin.reflect.FieldUtils.readField(loadedApk,"mApplicationInfo",true);
        GuestProcessIdentity.initialize(declared.packageName,intendedProcess);
        // MethodUtils infers null as a null parameter type, which breaks its cache key on API19.
        java.lang.reflect.Method make=loadedApk.getClass().getDeclaredMethod("makeApplication",boolean.class,Instrumentation.class);
        make.setAccessible(true);
        Application app=(Application)make.invoke(loadedApk,false,null);
        if(app==null)throw new IllegalStateException("guest Application unavailable");
        com.morgoo.droidplugin.core.PluginProcessManager.fakeSystemService(host, app);
        java.lang.reflect.Field ops=com.morgoo.droidplugin.reflect.FieldUtils.getField(app.getBaseContext().getClass(),"mOpPackageName");
        if(ops!=null)ops.set(app.getBaseContext(),host.getPackageName());
        android.content.ContentResolver resolver=app.getContentResolver();
        java.lang.reflect.Field resolverPackage=com.morgoo.droidplugin.reflect.FieldUtils.getField(resolver.getClass(),"mPackageName");
        if(resolverPackage!=null)resolverPackage.set(resolver,host.getPackageName());
        ModernIdentity.context(app,host);
        VirtualBroadcasts.attach(app,host);
        PackageInfo info=PluginManager.getInstance().getPackageInfo(app.getPackageName(),PackageManager.GET_PROVIDERS|PackageManager.GET_META_DATA);
        String process=intendedProcess;
        if(info!=null&&info.providers!=null){
            ProviderInfo[] providers=info.providers.clone();Arrays.sort(providers,new Comparator<ProviderInfo>(){public int compare(ProviderInfo a,ProviderInfo b){return b.initOrder<a.initOrder?-1:(b.initOrder==a.initOrder?0:1);}});
            Object thread=ActivityThreadCompat.currentActivityThread();
            for(ProviderInfo listed:providers){
                // Archive PackageInfo component ApplicationInfos are not fixed by
                // PluginPackageParser. Resolve a component copy with the real
                // private data/lib paths and a normalized default process name.
                ProviderInfo provider=PluginManager.getInstance().getProviderInfo(new android.content.ComponentName(listed.packageName,listed.name),PackageManager.GET_META_DATA);
                if(provider==null)throw new IllegalStateException("Guest provider metadata unavailable: "+listed.name);
                Log.i("WindChaserUnified","guest provider candidate "+provider.name+" enabled="+provider.enabled+" process="+provider.processName+" intended="+process);
                if(!provider.enabled || (!provider.multiprocess && !android.text.TextUtils.equals(provider.processName,process)))continue;
                // Local registration preserves normal provider lookup without publishing uninstalled packages to AMS.
                java.lang.reflect.Method install=null;
                for(java.lang.reflect.Method candidate:thread.getClass().getDeclaredMethods()) {
                    Class<?>[] parameters=candidate.getParameterTypes();
                    if(candidate.getName().equals("installProvider") && parameters.length==6
                        && parameters[0]==android.content.Context.class && parameters[2]==ProviderInfo.class) {install=candidate;break;}
                }
                if(install==null)throw new NoSuchMethodException("API19 installProvider");
                install.setAccessible(true);
                install.invoke(thread,app,null,provider,false,true,true);
                Log.i("WindChaserUnified","guest startup provider "+provider.name+" in "+process);
            }
        }
        Instrumentation instrumentation=ActivityThreadCompat.getInstrumentation();
        instrumentation.callApplicationOnCreate(app);
        // Publish the existing read-only endpoint after native Application init,
        // including a QQ main process prepared by its original control service.
        // Activity hooks still call this idempotent installer; no second native
        // Application, UI, permission prompt or service is initialized here.
        if(("com.autonavi.amapauto".equals(app.getPackageName())||"com.tencent.qqmusiccar".equals(app.getPackageName()))
            &&app.getPackageName().equals(intendedProcess)){
            try{
                Class<?> probe=Class.forName("cn.windchaser.startup.RuntimeProbe",true,app.getClassLoader());
                probe.getMethod("install",android.content.Context.class).invoke(null,app);
            }catch(Exception|LinkageError unavailable){Log.w("WindChaserUnified","early read-only endpoint unavailable; retain Activity installation",unavailable);}
        }
        Log.i("WindChaserUnified","guest Application initialized once: "+app.getPackageName()+" in "+process);
    }
}
