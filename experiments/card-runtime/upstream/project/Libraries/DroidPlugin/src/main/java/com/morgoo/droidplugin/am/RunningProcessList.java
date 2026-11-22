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

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.text.TextUtils;

import com.morgoo.droidplugin.pm.PluginManager;
import com.morgoo.helper.Log;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 正在运行的进程列表
 * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/3/10.
 */
class RunningProcessList {
    private final ProcessSlotReservations reservations=new ProcessSlotReservations();
    boolean availableFor(String stub,String pkg,String process){return reservations.canUse(stub,pkg,process,android.os.SystemClock.elapsedRealtime());}

    private static final Collator sCollator = Collator.getInstance();
    private static final String TAG = RunningProcessList.class.getSimpleName();
    private static Comparator sComponentInfoComparator = new Comparator<ComponentInfo>() {
        @Override
        public int compare(ComponentInfo lhs, ComponentInfo rhs) {
            return sCollator.compare(lhs.name, rhs.name);
        }
    };

    private static Comparator sProviderInfoComparator = new Comparator<ProviderInfo>() {
        @Override
        public int compare(ProviderInfo lhs, ProviderInfo rhs) {
            return sCollator.compare(lhs.authority, rhs.authority);
        }
    };

    private static final class Candidate {
        final String stub, target;
        final List<String> packages;
        Candidate(ProcessItem item) { stub=item.stubProcessName;target=item.targetProcessName;packages=new ArrayList<String>(item.pkgs); }
    }
    private synchronized List<Candidate> candidates() {
        List<Candidate> result=new ArrayList<Candidate>();
        for(ProcessItem item:items.values())result.add(new Candidate(item));
        return result;
    }
    public String getStubProcessByTarget(ComponentInfo targetInfo) {
        // Take a snapshot under the list lock; never call the manager over Binder
        // while holding the lock needed by process registration/death callbacks.
        for(Candidate item:candidates()) {
            if(!TextUtils.equals(item.target,targetInfo.processName))continue;
            if(item.packages.contains(targetInfo.packageName))return item.stub;
            for(String pkg:item.packages) {
                try {
                    if(PluginManager.getInstance().checkSignatures(targetInfo.packageName,pkg)==PackageManager.SIGNATURE_MATCH)return item.stub;
                } catch(Exception e) { Log.e(TAG,"getStubProcessByTarget:error",e); }
            }
        }
        return null;
    }

    public synchronized boolean isPersistentApplication(int pid) {
        //是否是持久化的app。
        for (ProcessItem processItem : items.values()) {
            if (processItem.pid == pid) {

                if (processItem.pkgs != null && processItem.pkgs.size() > 0) {
                    for (String pkg : processItem.pkgs) {
                        if (isPersistentApp(pkg)) {
                            return true;
                        }
                    }
                }

                if (processItem.targetActivityInfos != null && processItem.targetActivityInfos.size() > 0) {
                    for (ActivityInfo info : processItem.targetActivityInfos.values()) {
                        if ((info.applicationInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) {
                            return true;
                        } else if (isPersistentApp(info.packageName)) {
                            return true;
                        }
                    }
                }

                if (processItem.targetProviderInfos != null && processItem.targetProviderInfos.size() > 0) {
                    for (ProviderInfo info : processItem.targetProviderInfos.values()) {
                        if ((info.applicationInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) {
                            return true;
                        } else if (isPersistentApp(info.packageName)) {
                            return true;
                        }
                    }
                }

                if (processItem.targetServiceInfos != null && processItem.targetServiceInfos.size() > 0) {
                    for (ServiceInfo info : processItem.targetServiceInfos.values()) {
                        if ((info.applicationInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) {
                            return true;
                        } else if (isPersistentApp(info.packageName)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    private synchronized boolean isPersistentApp(String packageName) {
        try {
            PackageInfo info = mHostContext.getPackageManager().getPackageInfo(packageName, PackageManager.GET_META_DATA);
            if (info != null && info.applicationInfo.metaData != null && info.applicationInfo.metaData.containsKey(PluginManager.EXTRA_APP_PERSISTENT)) {
                if ((info.applicationInfo.flags & ApplicationInfo.FLAG_PERSISTENT) != 0) {
                    return true;
                }
                boolean isPersistentApp = info.applicationInfo.metaData.getBoolean(PluginManager.EXTRA_APP_PERSISTENT);
                return isPersistentApp;
            }
        } catch (Exception e) {
            Log.e(TAG, "isPersistentApp:error", e);
        }
        return false;
    }

    private Context mHostContext;

    public synchronized void setContext(Context context) {
        this.mHostContext = context;
    }


    /**
     * 正在运行的进程item
     * <p/>
     * Created by Andy Zhang(zhangyong232@gmail.com) on 2015/3/10.
     */
    private class ProcessItem {

        private String stubProcessName;
        private String targetProcessName;
        private int pid;
        private int uid;
        private long startTime;

        private List<String> pkgs = new ArrayList<String>(1);

        //正在运行的插件ActivityInfo
        //key=ActivityInfo.name, value=插件的ActivityInfo,
        private Map<String, ActivityInfo> targetActivityInfos = new HashMap<String, ActivityInfo>(4);


        //正在运行的插件ProviderInfo
        //key=ProviderInfo.authority, value=插件的ProviderInfo
        private Map<String, ProviderInfo> targetProviderInfos = new HashMap<String, ProviderInfo>(1);

        //正在运行的插件ServiceInfo
        //key=ServiceInfo.name, value=插件的ServiceInfo
        private Map<String, ServiceInfo> targetServiceInfos = new HashMap<String, ServiceInfo>(1);


        //正在运行的插件ActivityInfo与代理ActivityInfo的映射
        //key=代理ActivityInfo.name, value=插件的ActivityInfo.name,
        private Map<String, Set<ActivityInfo>> activityInfosMap = new HashMap<String, Set<ActivityInfo>>(4);


        //正在运行的插件ProviderInfo与代理ProviderInfo的映射
        //key=代理ProviderInfo.authority, value=插件的ProviderInfo.authority,
        private Map<String, Set<ProviderInfo>> providerInfosMap = new HashMap<String, Set<ProviderInfo>>(4);

        //正在运行的插件ServiceInfo与代理ServiceInfo的映射
        //key=代理ServiceInfo.name, value=插件的ServiceInfo.name,
        private Map<String, Set<ServiceInfo>> serviceInfosMap = new HashMap<String, Set<ServiceInfo>>(4);


        private void updatePkgs() {
            ArrayList<String> newList = new ArrayList<String>();
            for (ActivityInfo info : targetActivityInfos.values()) {
                newList.add(info.packageName);
            }

            for (ServiceInfo info : targetServiceInfos.values()) {
                newList.add(info.packageName);
            }

            for (ProviderInfo info : targetProviderInfos.values()) {
                newList.add(info.packageName);
            }
            pkgs.clear();
            pkgs.addAll(newList);
        }


        private void addActivityInfo(String stubActivityName, ActivityInfo info) {
            if (!targetActivityInfos.containsKey(info.name)) {
                targetActivityInfos.put(info.name, info);
            }

            //pkgs
            if (!pkgs.contains(info.packageName)) {
                pkgs.add(info.packageName);
            }

            //stub map to activity info
            Set<ActivityInfo> list = activityInfosMap.get(stubActivityName);
            if (list == null) {
                list = new TreeSet<ActivityInfo>(sComponentInfoComparator);
                list.add(info);
                activityInfosMap.put(stubActivityName, list);
            } else {
                list.add(info);
            }
        }

        void removeActivityInfo(String stubActivityName, ActivityInfo targetInfo) {
            targetActivityInfos.remove(targetInfo.name);
            //remove form map
            if (stubActivityName == null) {
                for (Set<ActivityInfo> set : activityInfosMap.values()) {
                    set.remove(targetInfo);
                }
            } else {
                Set<ActivityInfo> list = activityInfosMap.get(stubActivityName);
                if (list != null) {
                    list.remove(targetInfo);
                }
            }
            updatePkgs();
        }


        private void addServiceInfo(String stubServiceName, ServiceInfo info) {
            if (!targetServiceInfos.containsKey(info.name)) {
                targetServiceInfos.put(info.name, info);

                if (!pkgs.contains(info.packageName)) {
                    pkgs.add(info.packageName);
                }

                //stub map to activity info
                Set<ServiceInfo> list = serviceInfosMap.get(stubServiceName);
                if (list == null) {
                    list = new TreeSet<ServiceInfo>(sComponentInfoComparator);
                    list.add(info);
                    serviceInfosMap.put(stubServiceName, list);
                } else {
                    list.add(info);
                }
            }
        }

        void removeServiceInfo(String stubServiceName, ServiceInfo targetInfo) {
            targetServiceInfos.remove(targetInfo.name);
            //remove form map
            if (stubServiceName == null) {
                for (Set<ServiceInfo> set : serviceInfosMap.values()) {
                    set.remove(targetInfo);
                }
            } else {
                Set<ServiceInfo> list = serviceInfosMap.get(stubServiceName);
                if (list != null) {
                    list.remove(targetInfo);
                }
            }
            updatePkgs();
        }


        private void addProviderInfo(String stubAuthority, ProviderInfo info) {
            if (!targetProviderInfos.containsKey(info.authority)) {
                targetProviderInfos.put(info.authority, info);

                if (!pkgs.contains(info.packageName)) {
                    pkgs.add(info.packageName);
                }

                //stub map to activity info
                Set<ProviderInfo> list = providerInfosMap.get(stubAuthority);
                if (list == null) {
                    list = new TreeSet<ProviderInfo>(sProviderInfoComparator);
                    list.add(info);
                    providerInfosMap.put(stubAuthority, list);
                } else {
                    list.add(info);
                }
            }
        }

        void removeProviderInfo(String stubAuthority, ProviderInfo targetInfo) {
            targetProviderInfos.remove(targetInfo.authority);
            //remove form map
            if (stubAuthority == null) {
                for (Set<ProviderInfo> set : providerInfosMap.values()) {
                    set.remove(targetInfo);
                }
            } else {
                Set<ProviderInfo> list = providerInfosMap.get(stubAuthority);
                if (list != null) {
                    list.remove(targetInfo);
                }
            }

            updatePkgs();
        }


    }

    //key=pid, value=ProcessItem;
    private Map<Integer, ProcessItem> items = new HashMap<Integer, ProcessItem>(5);

    synchronized ProcessItem removeByPid(int pid) {
        return items.remove(pid);
    }

    synchronized List<String> getStubServiceByPid(int pid) {
        ProcessItem item = items.get(pid);
        if (item != null && item.serviceInfosMap != null && item.serviceInfosMap.size() > 0) {
            return new ArrayList<String>(item.serviceInfosMap.keySet());
        }
        return null;
    }


    synchronized void addActivityInfo(int pid, int uid, ActivityInfo stubInfo, ActivityInfo targetInfo) {
        ProcessItem item = items.get(pid);
        if (TextUtils.isEmpty(targetInfo.processName)) {
            targetInfo.processName = targetInfo.packageName;
        }
        if (item == null) {
            item = new ProcessItem();
            item.pid = pid;
            item.uid = uid;
            items.put(pid, item);
        }
        item.stubProcessName = stubInfo.processName;
        if (!item.pkgs.contains(targetInfo.packageName)) {
            item.pkgs.add(targetInfo.packageName);
        }
        item.targetProcessName = targetInfo.processName;
        item.addActivityInfo(stubInfo.name, targetInfo);
    }

    synchronized void removeActivityInfo(int pid, int uid, ActivityInfo stubInfo, ActivityInfo targetInfo) {
        ProcessItem item = items.get(pid);
        if (TextUtils.isEmpty(targetInfo.processName)) {
            targetInfo.processName = targetInfo.packageName;
        }
        if (item != null) {
            item.removeActivityInfo(stubInfo.name, targetInfo);
        }
    }


    synchronized void addServiceInfo(int pid, int uid, ServiceInfo stubInfo, ServiceInfo targetInfo) {
        ProcessItem item = items.get(pid);
        if (TextUtils.isEmpty(targetInfo.processName)) {
            targetInfo.processName = targetInfo.packageName;
        }
        if (item == null) {
            item = new ProcessItem();
            item.pid = pid;
            item.uid = uid;

            items.put(pid, item);
        }
        item.stubProcessName = stubInfo.processName;
        if (!item.pkgs.contains(targetInfo.packageName)) {
            item.pkgs.add(targetInfo.packageName);
        }
        item.targetProcessName = targetInfo.processName;
        item.addServiceInfo(stubInfo.name, targetInfo);
    }

    synchronized void removeServiceInfo(int pid, int uid, ServiceInfo stubInfo, ServiceInfo targetInfo) {
        ProcessItem item = items.get(pid);
        if (TextUtils.isEmpty(targetInfo.processName)) {
            targetInfo.processName = targetInfo.packageName;
        }
        if (item != null) {
            if (stubInfo != null) {
                item.removeServiceInfo(stubInfo.name, targetInfo);
            } else {
                item.removeServiceInfo(null, targetInfo);
            }
        }
    }


    synchronized void addProviderInfo(int pid, int uid, ProviderInfo stubInfo, ProviderInfo targetInfo) {
        ProcessItem item = items.get(pid);
        if (TextUtils.isEmpty(targetInfo.processName)) {
            targetInfo.processName = targetInfo.packageName;
        }
        if (item == null) {
            item = new ProcessItem();
            item.pid = pid;
            item.uid = uid;
            items.put(pid, item);
        }
        item.stubProcessName = stubInfo.processName;
        if (!item.pkgs.contains(targetInfo.packageName)) {
            item.pkgs.add(targetInfo.packageName);
        }
        item.targetProcessName = targetInfo.processName;
        item.addProviderInfo(stubInfo.authority, targetInfo);
    }

    synchronized void addItem(int pid, int uid) {
        ProcessItem item = items.get(pid);
        if (item == null) {
            item = new ProcessItem();
            item.pid = pid;
            item.uid = uid;
            item.startTime = System.currentTimeMillis();
            items.put(pid, item);
        } else {
            item.pid = pid;
            item.uid = uid;
            item.startTime = System.currentTimeMillis();
        }
    }

    synchronized boolean isProcessRunning(String stubProcessName) {
        for (ProcessItem processItem : items.values()) {
            if (TextUtils.equals(stubProcessName, processItem.stubProcessName)) {
                return true;
            }
        }
        return false;
    }


    boolean isPkgCanRunInProcess(String packageName, String stubProcessName, String targetProcessName) throws RemoteException {
        for(Candidate item:candidates()) {
            if(!TextUtils.equals(stubProcessName,item.stub))continue;
            if(!TextUtils.isEmpty(item.target) && !TextUtils.equals(item.target,targetProcessName))continue;
            if(item.packages.contains(packageName))return true;
            for(String pkg:item.packages)if(PluginManager.getInstance().checkSignatures(packageName,pkg)==PackageManager.SIGNATURE_MATCH)return true;
        }
        return false;
    }

    synchronized boolean isPkgEmpty(String stubProcessName) {
        for (ProcessItem item : items.values()) {
            if (TextUtils.equals(stubProcessName, item.stubProcessName)) {
                return item.pkgs.size() <= 0;
            }
        }
        return true;
    }


    synchronized boolean isStubInfoUsed(ProviderInfo stubInfo) {
        //TODO
        return false;
    }

    synchronized boolean isStubInfoUsed(ServiceInfo stubInfo) {
        //TODO
        return false;
    }

    synchronized boolean isStubInfoUsed(ActivityInfo stubInfo, ActivityInfo targetInfo, String stubProcessName) {
        for (Integer pid : items.keySet()) {
            ProcessItem item = items.get(pid);
            if (TextUtils.equals(item.stubProcessName, stubProcessName)) {
                Set<ActivityInfo> infos = item.activityInfosMap.get(stubInfo.name);
                if (infos != null && infos.size() > 0) {
                    for (ActivityInfo info : infos) {
                        if (TextUtils.equals(info.name, targetInfo.name) && TextUtils.equals(info.packageName, targetInfo.packageName)) {
                            return false;
                        }
                    }
                    return true;
                }
                return false;
            }
        }
        return false;
    }

    synchronized boolean hasPackageProcess(String pkg) {
        if(reservations.hasPackage(pkg,android.os.SystemClock.elapsedRealtime()))return true;
        for(ProcessItem item:items.values())if(item.pkgs.contains(pkg))return true;
        return false;
    }

    synchronized List<String> getPackageNameByPid(int pid) {
        ProcessItem item = items.get(pid);
        return item != null ? new ArrayList<String>(item.pkgs) : new ArrayList<String>();
    }

    synchronized String getTargetProcessNameByPid(int pid) {
        ProcessItem item = items.get(pid);
        return item != null ? item.targetProcessName : null;
    }

    public synchronized String getStubProcessNameByPid(int pid) {
        ProcessItem item = items.get(pid);
        return item != null ? item.stubProcessName : null;
    }

    synchronized void setTargetProcessName(ComponentInfo stubInfo, ComponentInfo targetInfo) {
        reservations.reserve(stubInfo.processName,targetInfo.packageName,targetInfo.processName,android.os.SystemClock.elapsedRealtime());
        for (ProcessItem item : items.values()) {
            if (TextUtils.equals(item.stubProcessName, stubInfo.processName)) {
                if (!item.pkgs.contains(targetInfo.packageName)) {
                    item.pkgs.add(targetInfo.packageName);
                }
                item.targetProcessName = targetInfo.processName;
            }
        }
    }

    synchronized int getActivityCountByPid(int pid) {
        ProcessItem item = items.get(pid);
        return item != null ? item.targetActivityInfos.size() : 0;
    }

    synchronized int getServiceCountByPid(int pid) {
        ProcessItem item = items.get(pid);
        return item != null ? item.targetServiceInfos.size() : 0;
    }

    synchronized int getProviderCountByPid(int pid) {
        ProcessItem item = items.get(pid);
        return item != null ? item.targetProviderInfos.size() : 0;
    }

    synchronized void setProcessName(int pid, String stubProcessName, String targetProcessName, String targetPkg) {
        reservations.claim(stubProcessName,targetPkg,targetProcessName,pid,android.os.SystemClock.elapsedRealtime());
        ProcessItem item = items.get(pid);
        if (item != null) {
            if (!item.pkgs.contains(targetPkg)) {
                item.pkgs.add(targetPkg);
            }
            item.targetProcessName = targetProcessName;
            item.stubProcessName = stubProcessName;
        }
    }

    synchronized void onProcessDied(int pid, int uid) {
        //进程死掉的时候，移除相关item
        items.remove(pid);reservations.died(pid);
    }

    synchronized void clear() {
        items.clear();reservations.clear();
    }

    synchronized boolean isPlugin(int pid) {
        ProcessItem item = items.get(pid);
        if (item != null) {
            return !TextUtils.isEmpty(item.stubProcessName) && !TextUtils.isEmpty(item.targetProcessName);
        }
        return false;
    }

    synchronized void dump(String msg) {
        StringBuilder sb = new StringBuilder("\r\n\r\ndump[" + msg + "]RunningProcess[");
        for (Integer pid : items.keySet()) {
            ProcessItem item = items.get(pid);
            sb.append("  pid:").append(pid).append("\r\n");
            sb.append("  Item[\r\n");
            sb.append("    pid:").append(item.pid).append("\r\n");
            sb.append("    uid:").append(item.uid).append("\r\n");
            sb.append("    stubProcessName:").append(item.stubProcessName).append("\r\n");
            sb.append("    targetProcessName:").append(item.targetProcessName).append("\r\n");
            sb.append("    pkgs:").append(Arrays.toString(item.pkgs.toArray())).append("\r\n");

            sb.append("    targetActivityInfos:[\r\n");
            for (String name : item.targetActivityInfos.keySet()) {
                sb.append("        " + name + ":" + item.targetActivityInfos.get(name).name);
            }
            sb.append("    ]\r\n");

            sb.append("    targetServiceInfos:[\r\n");
            for (String name : item.targetServiceInfos.keySet()) {
                sb.append("        " + name + ":" + item.targetServiceInfos.get(name).name);
            }
            sb.append("  ]\r\n");

            sb.append("  targetProviderInfos:[\r\n");
            for (String name : item.targetProviderInfos.keySet()) {
                sb.append("        " + name + ":" + item.targetProviderInfos.get(name).name);
            }
            sb.append("    ]\r\n");

            sb.append("    activityInfosMap:[\r\n");
            for (String name : item.activityInfosMap.keySet()) {
                Set<ActivityInfo> infos = item.activityInfosMap.get(name);
                sb.append("        " + name + ":[\r\n");
                for (ActivityInfo info : infos) {
                    sb.append("            " + info.name + ":\r\n");
                }
                sb.append("        ]\r\n");
            }
            sb.append("    ]\r\n");

            sb.append("    serviceInfosMap:[\r\n");
            for (String name : item.serviceInfosMap.keySet()) {
                Set<ServiceInfo> infos = item.serviceInfosMap.get(name);
                sb.append("        " + name + ":[\r\n");
                for (ServiceInfo info : infos) {
                    sb.append("            " + info.name + ":\r\n");
                }
                sb.append("        ]\r\n");
            }
            sb.append("    ]\r\n");


            sb.append("    activityInfosMap:[\r\n");
            for (String name : item.providerInfosMap.keySet()) {
                Set<ProviderInfo> infos = item.providerInfosMap.get(name);
                sb.append("        " + name + ":[\r\n");
                for (ProviderInfo info : infos) {
                    sb.append("            " + info.authority + ":\r\n");
                }
                sb.append("        ]\r\n");
            }
            sb.append("    ]\r\n");
        }
        sb.append("]  \r\n");
        Log.e(TAG, sb.toString());


    }
}
