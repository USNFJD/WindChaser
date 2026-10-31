/* WindChaser compatibility experiment. LGPL-3.0-or-later. */
package com.morgoo.droidplugin.hook.binder;
import android.content.Context;
import android.os.IBinder;
import com.morgoo.droidplugin.hook.BaseHookHandle;
import com.morgoo.droidplugin.reflect.FieldUtils;
/** Alarm operations retain platform permission checks and use the real installed caller. */
public final class IAlarmManagerBinderHook extends BinderHook {
    public IAlarmManagerBinderHook(Context context){super(context);}
    public String getServiceName(){return Context.ALARM_SERVICE;}
    Object getOldObj()throws Exception{
        return Class.forName("android.app.IAlarmManager$Stub").getDeclaredMethod("asInterface",IBinder.class).invoke(null,MyServiceManager.getOriginService(Context.ALARM_SERVICE));
    }
    protected BaseHookHandle createHookHandle(){return new BaseHookHandle(mHostContext){protected void init(){}};}
    @Override protected void onInstall(ClassLoader loader)throws Throwable{
        super.onInstall(loader);
        Object manager=mHostContext.getSystemService(Context.ALARM_SERVICE);
        FieldUtils.writeField(manager,"mService",MyServiceManager.getProxiedObj(Context.ALARM_SERVICE),true);
    }
}
