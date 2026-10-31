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

package com.morgoo.droidplugin.am;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.text.TextUtils;

import com.morgoo.droidplugin.pm.IApplicationCallback;
import com.morgoo.droidplugin.pm.IPluginManagerImpl;
import com.morgoo.droidplugin.reflect.FieldUtils;
import com.morgoo.droidplugin.stub.AbstractServiceStub;
import com.morgoo.helper.AttributeCache;
import com.morgoo.helper.Log;
import com.morgoo.helper.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 这是一个比较复杂的进程管理服务。
 * 主要实现的功能为：
 * 1、系统预定义N个进程。每个进程下有4中launchmod的activity，1个服务，一个ContentProvider。
 * 2、每个插件可以在多个进程中运行，这由插件自己的processName属性决定。
 * 3、插件系统最多可以同时运行N个进程，M个插件(M <= N or M >= N)。
 * 4、多个插件运行在同一个进程中，如果他们的签名相同。（我们可以通过一个开关来决定。）
 * 5、在运行第M+1个插件时，如果预定义的N个进程被占满，最低优先级的进程会被kill掉。腾出预定义的进程用来运行此个插件。
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/3/10.
 */
public class MyActivityManagerService extends BaseActivityManagerService {

    private static final String TAG = MyActivityManagerService.class.getSimpleName();
    private StaticProcessList mStaticProcessList = new StaticProcessList();
    private RunningProcessList mRunningProcessList = new RunningProcessList();

    public MyActivityManagerService(Context hostContext) {
        super(hostContext);
        mRunningProcessList.setContext(mHostContext);
    }

    @Override
    public void onCreate(IPluginManagerImpl pluginManagerImpl) throws Exception {
        super.onCreate(pluginManagerImpl);
        AttributeCache.init(mHostContext);
        mStaticProcessList.onCreate(mHostContext);
        mRunningProcessList.setContext(mHostContext);
    }

    @Override
    public void onDestroy() {
        mRunningProcessList.clear();
        mStaticProcessList.clear();
        runProcessGC();
        super.onDestroy();
    }

    @Override
    protected void onProcessDied(int pid, int uid) {
        mRunningProcessList.onProcessDied(pid, uid);
        runProcessGC();
        super.onProcessDied(pid, uid);
    }

    @Override
    public boolean registerApplicationCallback(int callingPid, int callingUid, IApplicationCallback callback) {
        boolean b = super.registerApplicationCallback(callingPid, callingUid, callback);
        mRunningProcessList.addItem(callingPid, callingUid);
        if (callingPid == android.os.Process.myPid()) {
            String stubProcessName = Utils.getProcessName(mHostContext, callingPid);
            String targetProcessName = Utils.getProcessName(mHostContext, callingPid);
            String targetPkg = mHostContext.getPackageName();
            mRunningProcessList.setProcessName(callingPid, stubProcessName, targetProcessName, targetPkg);
        }
        if (TextUtils.equals(mHostContext.getPackageName(), Utils.getProcessName(mHostContext, callingPid))) {
            String stubProcessName = mHostContext.getPackageName();
            String targetProcessName = mHostContext.getPackageName();
            String targetPkg = mHostContext.getPackageName();
            mRunningProcessList.setProcessName(callingPid, stubProcessName, targetProcessName, targetPkg);
        }
        return b;
    }

    @Override
    public synchronized ProviderInfo selectStubProviderInfo(int callingPid, int callingUid, ProviderInfo targetInfo) throws RemoteException {
        runProcessGC();

        //先从正在运行的进程中查找看是否有符合条件的进程，如果有则直接使用之
        String stubProcessName1 = mRunningProcessList.getStubProcessByTarget(targetInfo);
        if (stubProcessName1 != null && mRunningProcessList.availableFor(stubProcessName1,targetInfo.packageName,targetInfo.processName)) {
            List<ProviderInfo> stubInfos = mStaticProcessList.getProviderInfoForProcessName(stubProcessName1);
            for (ProviderInfo stubInfo : stubInfos) {
                if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                    mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                    return stubInfo;
                }
            }
        }

        List<String> stubProcessNames = mStaticProcessList.getProcessNames();
        for (String stubProcessName : stubProcessNames) {
            if(!mRunningProcessList.availableFor(stubProcessName,targetInfo.packageName,targetInfo.processName))continue;
            List<ProviderInfo> stubInfos = mStaticProcessList.getProviderInfoForProcessName(stubProcessName);
            if (mRunningProcessList.isProcessRunning(stubProcessName)) {
                if (mRunningProcessList.isPkgEmpty(stubProcessName)) {//空进程，没有运行任何插件包。
                    for (ProviderInfo stubInfo : stubInfos) {
                        if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        }
                    }
                    throw throwException("没有找到合适的StubInfo");
                } else if (mRunningProcessList.isPkgCanRunInProcess(targetInfo.packageName, stubProcessName, targetInfo.processName)) {
                    for (ProviderInfo stubInfo : stubInfos) {
                        if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        }
                    }
                    throw throwException("没有找到合适的StubInfo");
                } else {
                    //需要处理签名一样的情况。
                }
            } else {
                for (ProviderInfo stubInfo : stubInfos) {
                    if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                        mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                        return stubInfo;
                    }
                }
                throw throwException("没有找到合适的StubInfo");
            }
        }
        throw throwException("没有可用的进程了");
    }


    @Override
    public ServiceInfo getTargetServiceInfo(int callingPid, int callingUid, ServiceInfo stubInfo) throws RemoteException {
        //TODO getTargetServiceInfo
        return null;
    }

    @Override
    public int getPackageProcessState(String pkg) {return mRunningProcessList.hasPackageProcess(pkg)?1:0;}

    @Override
    public String getProcessNameByPid(int pid) {
        return mRunningProcessList.getTargetProcessNameByPid(pid);
    }

    @Override
    public synchronized ServiceInfo selectStubServiceInfo(int callingPid, int callingUid, ServiceInfo targetInfo) throws RemoteException {
        runProcessGC();

        //先从正在运行的进程中查找看是否有符合条件的进程，如果有则直接使用之
        String stubProcessName1 = mRunningProcessList.getStubProcessByTarget(targetInfo);
        if (stubProcessName1 != null && mRunningProcessList.availableFor(stubProcessName1,targetInfo.packageName,targetInfo.processName)) {
            List<ServiceInfo> stubInfos = mStaticProcessList.getServiceInfoForProcessName(stubProcessName1);
            for (ServiceInfo stubInfo : stubInfos) {
                if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                    mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                    return stubInfo;
                }
            }
        }

        List<String> stubProcessNames = mStaticProcessList.getProcessNames();
        for (String stubProcessName : stubProcessNames) {
            if(!mRunningProcessList.availableFor(stubProcessName,targetInfo.packageName,targetInfo.processName))continue;
            List<ServiceInfo> stubInfos = mStaticProcessList.getServiceInfoForProcessName(stubProcessName);
            if (mRunningProcessList.isProcessRunning(stubProcessName)) {//该预定义的进程正在运行。
                if (mRunningProcessList.isPkgEmpty(stubProcessName)) {//空进程，没有运行任何插件包。
                    for (ServiceInfo stubInfo : stubInfos) {
                        if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        }
                    }
                    throw throwException("没有找到合适的StubInfo");
                } else if (mRunningProcessList.isPkgCanRunInProcess(targetInfo.packageName, stubProcessName, targetInfo.processName)) {
                    for (ServiceInfo stubInfo : stubInfos) {
                        if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        }
                    }
                    throw throwException("没有找到合适的StubInfo");
                } else {
                    //这里需要考虑签名一样的情况，多个插件公用一个进程。
                }
            } else { //该预定义的进程没有。
                for (ServiceInfo stubInfo : stubInfos) {
                    if (!mRunningProcessList.isStubInfoUsed(stubInfo)) {
                        mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                        return stubInfo;
                    }
                }
                throw throwException("没有找到合适的StubInfo");
            }
        }
        throw throwException("没有可用的进程了");
    }

    private RemoteException throwException(String msg) {
        RemoteException remoteException = new RemoteException();
        remoteException.initCause(new RuntimeException(msg));
        return remoteException;
    }


    @Override
    public void onActivityCreated(int callingPid, int callingUid, ActivityInfo stubInfo, ActivityInfo targetInfo) {
        mRunningProcessList.addActivityInfo(callingPid, callingUid, stubInfo, targetInfo);
    }

    @Override
    public void onActivityDestroy(int callingPid, int callingUid, ActivityInfo stubInfo, ActivityInfo targetInfo) {
        mRunningProcessList.removeActivityInfo(callingPid, callingUid, stubInfo, targetInfo);
        runProcessGC();
    }

    @Override
    public void onActivityOnNewIntent(int callingPid, int callingUid, ActivityInfo stubInfo, ActivityInfo targetInfo, Intent intent) {
        mRunningProcessList.addActivityInfo(callingPid, callingUid, stubInfo, targetInfo);
    }

    @Override
    public void onServiceCreated(int callingPid, int callingUid, ServiceInfo stubInfo, ServiceInfo targetInfo) {
        mRunningProcessList.addServiceInfo(callingPid, callingUid, stubInfo, targetInfo);
    }

    @Override
    public void onServiceDestroy(int callingPid, int callingUid, ServiceInfo stubInfo, ServiceInfo targetInfo) {
        mRunningProcessList.removeServiceInfo(callingPid, callingUid, stubInfo, targetInfo);
        runProcessGC();
    }

    @Override
    public void onProviderCreated(int callingPid, int callingUid, ProviderInfo stubInfo, ProviderInfo targetInfo) {
        mRunningProcessList.addProviderInfo(callingPid, callingUid, stubInfo, targetInfo);
    }

    @Override
    public void onReportMyProcessName(int callingPid, int callingUid, String stubProcessName, String targetProcessName, String targetPkg) {
        mRunningProcessList.setProcessName(callingPid, stubProcessName, targetProcessName, targetPkg);
    }

    @Override
    public List<String> getPackageNamesByPid(int pid) {
        return new ArrayList<String>(mRunningProcessList.getPackageNameByPid(pid));
    }

    @Override
    public synchronized ActivityInfo selectStubActivityInfo(int callingPid, int callingUid, ActivityInfo targetInfo) throws RemoteException {
        runProcessGC();
//        if (targetInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK) {
//            targetInfo.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
//        }
//
//        if (targetInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
//            targetInfo.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
//        }
//
//        if (targetInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP) {
//            targetInfo.launchMode = ActivityInfo.LAUNCH_MULTIPLE;
//        }

        boolean Window_windowIsTranslucent = false;
        boolean Window_windowIsFloating = false;
        boolean Window_windowShowWallpaper = false;
        try {
            Class<?> R_Styleable_Class = Class.forName("com.android.internal.R$styleable");
            int[] R_Styleable_Window = (int[]) FieldUtils.readStaticField(R_Styleable_Class, "Window");
            int R_Styleable_Window_windowIsTranslucent = (int) FieldUtils.readStaticField(R_Styleable_Class, "Window_windowIsTranslucent");
            int R_Styleable_Window_windowIsFloating = (int) FieldUtils.readStaticField(R_Styleable_Class, "Window_windowIsFloating");
            int R_Styleable_Window_windowShowWallpaper = (int) FieldUtils.readStaticField(R_Styleable_Class, "Window_windowShowWallpaper");

            AttributeCache.Entry ent = AttributeCache.instance().get(targetInfo.packageName, targetInfo.theme,
                    R_Styleable_Window);
            if (ent != null && ent.array != null) {
                Window_windowIsTranslucent = ent.array.getBoolean(R_Styleable_Window_windowIsTranslucent, false);
                Window_windowIsFloating = ent.array.getBoolean(R_Styleable_Window_windowIsFloating, false);
                Window_windowShowWallpaper = ent.array.getBoolean(R_Styleable_Window_windowShowWallpaper, false);
            }
        } catch (Throwable e) {
            Log.e(TAG, "error on read com.android.internal.R$styleable", e);
        }

        boolean useDialogStyle = Window_windowIsTranslucent || Window_windowIsFloating || Window_windowShowWallpaper;

        //先从正在运行的进程中查找看是否有符合条件的进程，如果有则直接使用之
        String stubProcessName1 = mRunningProcessList.getStubProcessByTarget(targetInfo);
        if (stubProcessName1 != null && mRunningProcessList.availableFor(stubProcessName1,targetInfo.packageName,targetInfo.processName)) {
            List<ActivityInfo> stubInfos = mStaticProcessList.getActivityInfoForProcessName(stubProcessName1, useDialogStyle);
            for (ActivityInfo stubInfo : stubInfos) {
                if (stubInfo.launchMode == targetInfo.launchMode) {
                    if (stubInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                        mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                        return stubInfo;
                    } else if (!mRunningProcessList.isStubInfoUsed(stubInfo, targetInfo, stubProcessName1)) {
                        mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                        return stubInfo;
                    }
                }
            }
        }

        List<String> stubProcessNames = mStaticProcessList.getProcessNames();
        for (String stubProcessName : stubProcessNames) {
            if(!mRunningProcessList.availableFor(stubProcessName,targetInfo.packageName,targetInfo.processName))continue;
            List<ActivityInfo> stubInfos = mStaticProcessList.getActivityInfoForProcessName(stubProcessName, useDialogStyle);
            if (mRunningProcessList.isProcessRunning(stubProcessName)) {//该预定义的进程正在运行。
                if (mRunningProcessList.isPkgEmpty(stubProcessName)) {//空进程，没有运行任何插件包。
                    for (ActivityInfo stubInfo : stubInfos) {
                        if (stubInfo.launchMode == targetInfo.launchMode) {
                            if (stubInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                                mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                                return stubInfo;
                            } else if (!mRunningProcessList.isStubInfoUsed(stubInfo, targetInfo, stubProcessName1)) {
                                mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                                return stubInfo;
                            }
                        }
                    }
                    throw throwException("没有找到合适的StubInfo");
                } else if (mRunningProcessList.isPkgCanRunInProcess(targetInfo.packageName, stubProcessName, targetInfo.processName)) {
                    for (ActivityInfo stubInfo : stubInfos) {
                        if (stubInfo.launchMode == targetInfo.launchMode) {
                            if (stubInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                                mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                                return stubInfo;
                            } else if (!mRunningProcessList.isStubInfoUsed(stubInfo, targetInfo, stubProcessName1)) {
                                mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                                return stubInfo;
                            }
                        }
                    }
                    throw throwException("没有找到合适的StubInfo");
                } else {
                    //这里需要考虑签名一样的情况，多个插件公用一个进程。
                }
            } else { //该预定义的进程没有。
                for (ActivityInfo stubInfo : stubInfos) {
                    if (stubInfo.launchMode == targetInfo.launchMode) {
                        if (stubInfo.launchMode == ActivityInfo.LAUNCH_MULTIPLE) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        } else if (!mRunningProcessList.isStubInfoUsed(stubInfo, targetInfo, stubProcessName1)) {
                            mRunningProcessList.setTargetProcessName(stubInfo, targetInfo);
                            return stubInfo;
                        }
                    }
                }
                throw throwException("没有找到合适的StubInfo");
            }
        }
        throw throwException("没有可用的进程了");
    }

    /**
     * Android owns background process reclamation in this two-module host.
     * Component counts are reported AFTER Application/service creation, so a zero
     * count here does not mean idle. The former allocator-time GC killed the
     * requesting QQ player process while it was starting its own network service,
     * and also stopped valid background services, producing an endless restart loop.
     * Keep the existing allocation call sites cheap; release process slots on the
     * normal binder-death callback instead of killing live guest processes.
     */
    private void runProcessGC() {
        // Deliberately do not kill processes or stop services during allocation.
    }
}
