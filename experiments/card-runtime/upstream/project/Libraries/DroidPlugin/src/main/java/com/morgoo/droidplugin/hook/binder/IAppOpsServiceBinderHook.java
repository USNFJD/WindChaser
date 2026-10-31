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
package com.morgoo.droidplugin.hook.binder;

import android.content.Context;
import android.os.IBinder;

import com.morgoo.droidplugin.hook.BaseHookHandle;
import com.morgoo.droidplugin.hook.handle.IAppOpsServiceHookHandle;
import com.morgoo.helper.compat.IAppOpsServiceCompat;

/**
 * Created by Andy Zhang(zhangyong232@gmail.com) on  on 16/5/11.
 */
public class IAppOpsServiceBinderHook extends BinderHook{

    private static final String SERVICE_NAME = Context.APP_OPS_SERVICE;

    public IAppOpsServiceBinderHook(Context hostContext) {
        super(hostContext);
    }

    @Override protected void onInstall(ClassLoader loader)throws Throwable {
        super.onInstall(loader);
        if(android.os.Build.VERSION.SDK_INT>=26) {
            Object proxy=MyServiceManager.getProxiedObj(SERVICE_NAME);
            Object manager=mHostContext.getSystemService(SERVICE_NAME);
            com.morgoo.droidplugin.reflect.FieldUtils.writeField(manager,"mService",proxy,true);
            try{com.morgoo.droidplugin.reflect.FieldUtils.writeStaticField(Class.forName("android.app.AppOpsManager"),"sService",proxy);}catch(Exception ignored){}
        }
    }

    @Override
    Object getOldObj() throws Exception {
        IBinder iBinder = MyServiceManager.getOriginService(SERVICE_NAME);
        return IAppOpsServiceCompat.asInterface(iBinder);
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    protected BaseHookHandle createHookHandle() {
        return new IAppOpsServiceHookHandle(mHostContext);
    }
}
