/*
**        DroidPlugin Project
**
** Copyright(c) 2015 Andy Zhang <zhangyong232@gmail.com>
**
** This file is part of DroidPlugin.
**
** DroidPlugin is free software: you can redistribute it and/or
** modify it under the terms of the GNU Lesser General Public
** License as published by the Free Software Foundation, either
** version 3 of the License, or (at your option) any later version.
**
** DroidPlugin is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
** Lesser General Public License for more details.
**
** You should have received a copy of the GNU Lesser General Public
** License along with DroidPlugin.  If not, see <http://www.gnu.org/licenses/lgpl.txt>
**
**/

package com.morgoo.droidplugin.core;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.morgoo.droidplugin.hook.HookFactory;
import com.morgoo.droidplugin.pm.PluginManager;
import com.morgoo.droidplugin.reflect.FieldUtils;
import com.morgoo.droidplugin.reflect.MethodUtils;
import com.morgoo.droidplugin.stub.ActivityStub;
import com.morgoo.droidplugin.stub.ServiceStub;
import com.morgoo.helper.compat.ActivityThreadCompat;
import com.morgoo.helper.compat.CompatibilityInfoCompat;
import com.morgoo.helper.Log;
import com.morgoo.helper.compat.ProcessCompat;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/2/4.
 */
public class PluginProcessManager {


    private static final String TAG = "PluginProcessManager";

    private static String sCurrentProcessName;
    private static Object sGetCurrentProcessNameLock = new Object();
    private static Map<String, ClassLoader> sPluginClassLoaderCache = new WeakHashMap<String, ClassLoader>(1);
    private static Map<String, Object> sPluginLoadedApkCache = new WeakHashMap<String, Object>(1);

    public static String getCurrentProcessName(Context context) {
        if (context == null)
            return sCurrentProcessName;

        synchronized (sGetCurrentProcessNameLock) {
            if (sCurrentProcessName == null) {
                ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<RunningAppProcessInfo> infos = activityManager.getRunningAppProcesses();
                if (infos == null)
                    return null;

                for (RunningAppProcessInfo info : infos) {
                    if (info.pid == android.os.Process.myPid()) {
                        sCurrentProcessName = info.processName;
                        return sCurrentProcessName;
                    }
                }
            }
        }
        return sCurrentProcessName;
    }

    private static List<String> sProcessList = new ArrayList<>();


    private static void initProcessList(Context context) {
        try {
            if (sProcessList.size() > 0) {
                return;
            }

            sProcessList.add(context.getPackageName());

            PackageManager pm = context.getPackageManager();
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_RECEIVERS | PackageManager.GET_ACTIVITIES | PackageManager.GET_PROVIDERS | PackageManager.GET_SERVICES);
            if (packageInfo.receivers != null) {
                for (ActivityInfo info : packageInfo.receivers) {
                    if (!sProcessList.contains(info.processName)) {
                        sProcessList.add(info.processName);
                    }
                }
            }


            if (packageInfo.providers != null) {
                for (ProviderInfo info : packageInfo.providers) {
                    if (!sProcessList.contains(info.processName) && info.processName != null && info.authority != null && info.authority.indexOf(PluginManager.STUB_AUTHORITY_NAME) < 0) {
                        sProcessList.add(info.processName);
                    }
                }
            }

            if (packageInfo.services != null) {
                for (ServiceInfo info : packageInfo.services) {
                    if (!sProcessList.contains(info.processName) && info.processName != null && info.name != null && info.name.indexOf(ServiceStub.class.getSimpleName()) < 0) {
                        sProcessList.add(info.processName);
                    }
                }
            }

            if (packageInfo.activities != null) {
                for (ActivityInfo info : packageInfo.activities) {
                    if (!sProcessList.contains(info.processName) && info.processName != null && info.name != null && info.name.indexOf(ActivityStub.class.getSimpleName()) < 0) {
                        sProcessList.add(info.processName);
                    }
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
    }

    public static final boolean isPluginProcess(Context context) {
        String currentProcessName = getCurrentProcessName(context);
        if (TextUtils.equals(currentProcessName, context.getPackageName()))
            return false;

        initProcessList(context);
        return !sProcessList.contains(currentProcessName);
    }

    public static ClassLoader getPluginClassLoader(String pkg) throws IllegalAccessException, NoSuchMethodException, ClassNotFoundException, InvocationTargetException {
        ClassLoader classLoader = sPluginClassLoaderCache.get(pkg);
        if (classLoader == null) {
            Application app = getPluginContext(pkg);
            if (app != null) {
                sPluginClassLoaderCache.put(app.getPackageName(), app.getClassLoader());
            }
        }
        return sPluginClassLoaderCache.get(pkg);
    }


    public static void preLoadApk(Context hostContext, ComponentInfo pluginInfo) throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, PackageManager.NameNotFoundException, ClassNotFoundException {
        if (pluginInfo == null && hostContext == null) {
            return;
        }

        /*添加插件的LoadedApk对象到ActivityThread.mPackages*/

        boolean found = false;
        synchronized (sPluginLoadedApkCache) {
            Object object = ActivityThreadCompat.currentActivityThread();
            if (object != null) {
                Object mPackagesObj = FieldUtils.readField(object, "mPackages");
                Object containsKeyObj = MethodUtils.invokeMethod(mPackagesObj, "containsKey", pluginInfo.packageName);
                if (containsKeyObj instanceof Boolean && !(Boolean) containsKeyObj) {
                    final Object loadedApk;
                    if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
                        loadedApk = MethodUtils.invokeMethod(object, "getPackageInfoNoCheck", pluginInfo.applicationInfo, CompatibilityInfoCompat.DEFAULT_COMPATIBILITY_INFO());
                    } else {
                        loadedApk = MethodUtils.invokeMethod(object, "getPackageInfoNoCheck", pluginInfo.applicationInfo);
                    }
                    sPluginLoadedApkCache.put(pluginInfo.packageName, loadedApk);

                /*添加ClassLoader LoadedApk.mClassLoader*/

                    String optimizedDirectory = PluginDirHelper.getPluginDalvikCacheDir(hostContext, pluginInfo.packageName);
                    String libraryPath = PluginDirHelper.getPluginNativeLibraryDir(hostContext, pluginInfo.packageName);
                    String apk = pluginInfo.applicationInfo.publicSourceDir;
                    if (TextUtils.isEmpty(apk)) {
                        pluginInfo.applicationInfo.publicSourceDir = PluginDirHelper.getPluginApkFile(hostContext, pluginInfo.packageName);
                        apk = pluginInfo.applicationInfo.publicSourceDir;
                    }
                    if (apk != null) {
                        ClassLoader classloader = null;
                        try {
                            if(VERSION.SDK_INT>=34)new java.io.File(apk).setReadOnly();
                            classloader = new PluginClassLoader(apk, optimizedDirectory, libraryPath, hostContext.getClassLoader().getParent());
                        } catch (Exception e) {
                        }
                        if(classloader==null){
                            PluginDirHelper.cleanOptimizedDirectory(optimizedDirectory);
                            if(VERSION.SDK_INT>=34)new java.io.File(apk).setReadOnly();
                            classloader = new PluginClassLoader(apk, optimizedDirectory, libraryPath, hostContext.getClassLoader().getParent());
                        }
                        synchronized (loadedApk) {
                            FieldUtils.writeDeclaredField(loadedApk, "mClassLoader", classloader);
                        }
                        sPluginClassLoaderCache.put(pluginInfo.packageName, classloader);
                        Thread.currentThread().setContextClassLoader(classloader);
                        found = true;
                    }
                    ProcessCompat.setArgV0(pluginInfo.processName);
                }
            }
        }
        PluginProcessManager.preMakeApplication(hostContext, pluginInfo);
    }

    private static final Handler sHandle = new Handler(Looper.getMainLooper());
    private static final java.util.Map<String, FutureTask<Void>> applicationTasks = new java.util.HashMap<String, FutureTask<Void>>();
    private static final java.util.Set<String> initializingOnMain = new java.util.HashSet<String>();

    private static void preMakeApplication(final Context hostContext, final ComponentInfo pluginInfo) throws IOException {
        final Object loadedApk;
        synchronized (sPluginLoadedApkCache) { loadedApk=sPluginLoadedApkCache.get(pluginInfo.packageName); }
        if (loadedApk == null) return;
        final FutureTask<Void> task;
        final boolean onMain=Looper.getMainLooper()==Looper.myLooper();
        synchronized (applicationTasks) {
            // An Application may re-enter the hooks from attach/onCreate. Never
            // have the main thread wait for its own still-running initialization.
            if (onMain && initializingOnMain.contains(pluginInfo.packageName)) return;
            FutureTask<Void> existing=applicationTasks.get(pluginInfo.packageName);
            if (existing == null) {
                existing=new FutureTask<Void>(new Callable<Void>() {
                    @Override public Void call() throws Exception {
                        synchronized (applicationTasks) { initializingOnMain.add(pluginInfo.packageName); }
                        try {
                            com.morgoo.droidplugin.unified.GuestInitializers.make(loadedApk,hostContext,pluginInfo.processName);
                            return null;
                        } finally {
                            synchronized (applicationTasks) { initializingOnMain.remove(pluginInfo.packageName); }
                        }
                    }
                });
                applicationTasks.put(pluginInfo.packageName,existing);
            }
            task=existing;
        }
        // FutureTask records completion even when it precedes get(). A separate
        // wait()/notify() plus shared flag could lose the completion notification.
        // If already queued, main may run the same task now; it still runs once.
        if (onMain) task.run();
        else if (!task.isDone() && !sHandle.post(task)) throw new IOException("Main looper rejected guest initialization");
        try { task.get(30,TimeUnit.SECONDS); }
        catch (InterruptedException e) { Thread.currentThread().interrupt();throw new IOException("Interrupted guest initialization",e); }
        catch (ExecutionException e) { throw new IOException("Guest Application initialization failed: "+pluginInfo.packageName,e.getCause()); }
        catch (TimeoutException e) { throw new IOException("Guest Application main thread did not complete: "+pluginInfo.packageName,e); }
    }

    public static void registerStaticReceiver(Context context, ApplicationInfo pluginApplicationInfo, ClassLoader cl) throws Exception {
        List<ActivityInfo> infos = PluginManager.getInstance().getReceivers(pluginApplicationInfo.packageName, 0);
        if (infos != null && infos.size() > 0) {
            // A guest Application can be created before the manager has published
            // its PID mapping (especially when the player Service starts first).
            // Use the component identity fixed before Application.attach, so its
            // manifest receivers cannot be skipped permanently by that race.
            CharSequence myPname = com.morgoo.droidplugin.unified.GuestProcessIdentity.receiverProcess(pluginApplicationInfo.packageName);
            try {
                if (TextUtils.isEmpty(myPname)) myPname = PluginManager.getInstance().getProcessNameByPid(android.os.Process.myPid());
            } catch (Exception e) {
            }
            for (ActivityInfo info : infos) {
                if (TextUtils.equals(info.processName, myPname)) {
                    try {
                        List<IntentFilter> filters = PluginManager.getInstance().getReceiverIntentFilter(info);
                        if (com.morgoo.droidplugin.unified.VirtualBroadcasts.guest(info.packageName)
                                && com.morgoo.droidplugin.unified.VirtualBroadcasts.attached(context)) {
                            if (!info.enabled) continue;
                            // One instance per manifest component, even when it declares multiple
                            // filters. The private route accepts explicit intents regardless of URI.
                            BroadcastReceiver receiver = (BroadcastReceiver) cl.loadClass(info.name).newInstance();
                            context.registerReceiver(receiver,
                                com.morgoo.droidplugin.unified.VirtualBroadcasts.explicitFilter(info.packageName,info.name),
                                com.morgoo.droidplugin.unified.VirtualBroadcasts.INTERNAL_PERMISSION,null);
                            for (IntentFilter filter : filters)
                                context.registerReceiver(receiver,filter,info.permission,null);
                        } else {
                            for (IntentFilter filter : filters) {
                                BroadcastReceiver receiver = (BroadcastReceiver) cl.loadClass(info.name).newInstance();
                                context.registerReceiver(receiver, filter);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "registerStaticReceiver error=%s", e, info.name);
                    }
                }
            }
        }
    }

    public static void setHookEnable(boolean enable) {
        HookFactory.getInstance().setHookEnable(enable);
    }

    public static void setHookEnable(boolean enable, boolean reinstallHook) {
        HookFactory.getInstance().setHookEnable(enable, reinstallHook);
    }

    public static void installHook(Context hostContext) throws Throwable {
        HookFactory.getInstance().installHook(hostContext, null);
    }

    private static HashMap<String, Application> sApplicationsCache = new HashMap<String, Application>(2);

    public static Application getPluginContext(String packageName) throws IllegalAccessException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException {
        if (!sApplicationsCache.containsKey(packageName)) {
            Object at = ActivityThreadCompat.currentActivityThread();
            Object mAllApplications = FieldUtils.readField(at, "mAllApplications");
            if (mAllApplications instanceof List) {
                List apps = (List) mAllApplications;
                for (Object o : apps) {
                    if (o instanceof Application) {
                        Application app = (Application) o;
                        if (!sApplicationsCache.containsKey(app.getPackageName())) {
                            sApplicationsCache.put(app.getPackageName(), app);
                        }
                    }
                }
            }
        }
        return sApplicationsCache.get(packageName);
    }


    private static Context getBaseContext(Context c) {
        while(c instanceof ContextWrapper) {
            Context next=((ContextWrapper)c).getBaseContext();
            if(next==null||next==c)break;
            c=next;
        }
        return c;
    }

    private static WeakHashMap<Integer, Context> mFakedContext = new WeakHashMap<Integer, Context>(1);
    private static Object mServiceCache = null;

    private static List<String> sSkipService = new ArrayList<String>();

    static {
        sSkipService.add(Context.LAYOUT_INFLATER_SERVICE);
        sSkipService.add(Context.NOTIFICATION_SERVICE);
        sSkipService.add("storage");
        sSkipService.add("accessibility");
        sSkipService.add("audio");
        sSkipService.add("clipboard");
        sSkipService.add("media_router");
        sSkipService.add("wifi");
        sSkipService.add("captioning");
        sSkipService.add("account");
        sSkipService.add("activity");
        //fake这个wifiscanner服务可能会导致部分手机重启。例如三星，华为
        sSkipService.add("wifiscanner");
        sSkipService.add("rttmanager");
        sSkipService.add("tv_input");
        sSkipService.add("jobscheduler");
        sSkipService.add("sensorhub");
        
        //NSDManager init初始化anr的问题
        sSkipService.add("servicediscovery");
//        sSkipService.add("usagestats");

    }


    private static void fakeSystemServiceInner(Context hostContext, Context targetContext) {
        try {
            Context baseContext = getBaseContext(targetContext);
            if (mFakedContext.containsValue(baseContext)) {
                return;
            } else if (mServiceCache != null) {
                FieldUtils.writeField(baseContext, "mServiceCache", mServiceCache);
                //for context ContentResolver
                ContentResolver cr = baseContext.getContentResolver();
                if (cr != null) {
                    Object crctx = FieldUtils.readField(cr, "mContext");
                    if (crctx != null) {
                        FieldUtils.writeField(crctx, "mServiceCache", mServiceCache);
                    }
                }
                if (!mFakedContext.containsValue(baseContext)) {
                    mFakedContext.put(baseContext.hashCode(), baseContext);
                }
                return;
            }
            Object SYSTEM_SERVICE_MAP = null;
            try {
                SYSTEM_SERVICE_MAP = FieldUtils.readStaticField(baseContext.getClass(), "SYSTEM_SERVICE_MAP");
            } catch (Exception e) {
                Log.w(TAG, "readStaticField(SYSTEM_SERVICE_MAP) from %s fail", e, baseContext.getClass());
            }
            if (SYSTEM_SERVICE_MAP == null) {
                try {
                    SYSTEM_SERVICE_MAP = FieldUtils.readStaticField(Class.forName("android.app.SystemServiceRegistry"), "SYSTEM_SERVICE_FETCHERS");
                } catch (Exception e) {
                    Log.e(TAG, "readStaticField(SYSTEM_SERVICE_FETCHERS) from android.app.SystemServiceRegistry fail", e);
                }
            }

            if (SYSTEM_SERVICE_MAP != null && (SYSTEM_SERVICE_MAP instanceof Map)) {
                //如没有，则创建一个新的。
                Map<?, ?> sSYSTEM_SERVICE_MAP = (Map<?, ?>) SYSTEM_SERVICE_MAP;
                Context originContext = getBaseContext(hostContext);

                Object mServiceCache = FieldUtils.readField(originContext, "mServiceCache");
                if (mServiceCache instanceof List) {
                    ((List) mServiceCache).clear();
                }

                for (Object key : sSYSTEM_SERVICE_MAP.keySet()) {
                    if (sSkipService.contains(key)) {
                        continue;
                    }
                    Object serviceFetcher = sSYSTEM_SERVICE_MAP.get(key);

                    try {
                        Method getService = serviceFetcher.getClass().getMethod("getService", baseContext.getClass());
                        getService.invoke(serviceFetcher, originContext);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause != null) {
                            Log.w(TAG, "Fake system service faile", e);
                        } else {
                            Log.w(TAG, "Fake system service faile", e);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Fake system service faile", e);
                    }
                }
                mServiceCache = FieldUtils.readField(originContext, "mServiceCache");
                FieldUtils.writeField(baseContext, "mServiceCache", mServiceCache);

                //for context ContentResolver
                ContentResolver cr = baseContext.getContentResolver();
                if (cr != null) {
                    Object crctx = FieldUtils.readField(cr, "mContext");
                    if (crctx != null) {
                        FieldUtils.writeField(crctx, "mServiceCache", mServiceCache);
                    }
                }
            }
            if (!mFakedContext.containsValue(baseContext)) {
                mFakedContext.put(baseContext.hashCode(), baseContext);
            }
        } catch (Exception e) {
            Log.e(TAG, "fakeSystemServiceOldAPI", e);
        }
    }

    //这里为了解决某些插件调用系统服务时，系统服务必须要求要以host包名的身份去调用的问题。
    public static void fakeSystemService(Context hostContext, Context targetContext) {
        if(VERSION.SDK_INT>=26) {
            try{com.morgoo.droidplugin.unified.ModernIdentity.context(targetContext,hostContext);}catch(Exception error){Log.e(TAG,"Modern context identity",error);}
            return;
        }
        if (VERSION.SDK_INT >= VERSION_CODES.ICE_CREAM_SANDWICH_MR1 && !TextUtils.equals(hostContext.getPackageName(), targetContext.getPackageName())) {
            long b = System.currentTimeMillis();
            fakeSystemServiceInner(hostContext, targetContext);
            Log.i(TAG, "Fake SystemService for originContext=%s context=%s,cost %s ms", targetContext.getPackageName(), targetContext.getPackageName(), (System.currentTimeMillis() - b));
        }
    }

}
