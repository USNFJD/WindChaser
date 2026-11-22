package com.morgoo.droidplugin.hook.proxy;
import android.content.Context;
import com.morgoo.droidplugin.reflect.FieldUtils;
/** Android 10+ moved Activity starts and tasks from AM to ATM. */
public final class IActivityTaskManagerHook extends IActivityManagerHook {
    public IActivityTaskManagerHook(Context context){super(context);}
    @Override protected Object singleton()throws Exception {
        return FieldUtils.readStaticField(Class.forName("android.app.ActivityTaskManager"),"IActivityTaskManagerSingleton");
    }
}
