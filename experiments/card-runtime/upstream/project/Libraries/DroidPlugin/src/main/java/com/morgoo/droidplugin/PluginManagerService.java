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

package com.morgoo.droidplugin;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.morgoo.droidplugin.hook.handle.IActivityManagerHookHandle;
import com.morgoo.droidplugin.pm.IPluginManagerImpl;
import com.morgoo.droidplugin.pm.PluginManager;

/**
 * 插件管理服务。
 * <p/>
 * Code by Andy Zhang (zhangyong232@gmail.com) on  2015/2/11.
 */
public class PluginManagerService extends Service {

    private static final String TAG = PluginManagerService.class.getSimpleName();
    private static IPluginManagerImpl mPluginPackageManager;

    public static IPluginManagerImpl getPluginPackageManager(Context context) {
        if (mPluginPackageManager == null) {
            synchronized (PluginManager.class) {
                if (mPluginPackageManager == null) {
                    mPluginPackageManager = new IPluginManagerImpl(context);
                    mPluginPackageManager.onCreate();
                }
            }
        }
        return mPluginPackageManager;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        keepAlive();
        getPluginPackageManager(this);
    }

    // The experiment targets API24; API34 foreground types are not enforced for this target.
    @android.annotation.SuppressLint("ForegroundServiceType")
    private void keepAlive() {
        try {
            String channel="windchaser-runtime";
            if(android.os.Build.VERSION.SDK_INT>=26)((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new android.app.NotificationChannel(channel,"内置应用运行环境",android.app.NotificationManager.IMPORTANCE_LOW));
            Notification.Builder builder=android.os.Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
            Notification notification=builder.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("追风桌面 · 整合实验").setContentText("内置地图与音乐运行环境").setOngoing(true).build();
            startForeground(57,notification);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        try {
            mPluginPackageManager.onDestroy();
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mPluginPackageManager;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //这里要处理IntentService
        IActivityManagerHookHandle.getIntentSender.handlePendingIntent(this, intent);
        return super.onStartCommand(intent, flags, startId);
    }


}
