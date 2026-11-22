/* WindChaser single-package experiment, LGPL-3.0-or-later. */
package com.morgoo.droidplugin.unified;

import android.content.Context;
import android.os.Build;
import android.view.View;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Preserve legacy Toast view handling without interpreting modern Binder tokens as views. */
public final class ToastCallbackCompat {
    private ToastCallbackCompat(){}
    public static void prepare(Context host,Method method,Object[] args){
        // Android 11+ Toast.TN delegates its window to ToastPresenter. Keep that
        // platform-owned state intact. Modern enqueueToast also inserts an
        // IBinder token before the callback; cancelToast has only that token.
        if(Build.VERSION.SDK_INT<16||Build.VERSION.SDK_INT>=30||method==null||args==null)return;
        Class<?>[] types=method.getParameterTypes();
        for(int n=0;n<types.length&&n<args.length;n++){
            if(!"android.app.ITransientNotification".equals(types[n].getName())||args[n]==null)continue;
            Object callback=args[n];
            for(String name:new String[]{"mView","mNextView"}){
                try{
                    Field member=find(callback.getClass(),name);if(member==null)continue;
                    member.setAccessible(true);Object value=member.get(callback);
                    if(!(value instanceof View))continue;
                    Field context=find(View.class,"mContext");if(context==null)continue;
                    context.setAccessible(true);context.set(value,host);
                }catch(IllegalAccessException|RuntimeException unavailable){
                    // ROM-specific optional fields must not crash a guest while
                    // it is only trying to report an offline/network failure.
                    android.util.Log.w("WindChaserToast","Legacy toast view adjustment unavailable",unavailable);
                }
            }
        }
    }
    private static Field find(Class<?> type,String name){
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{return c.getDeclaredField(name);}
        catch(NoSuchFieldException absent){}
        return null;
    }
}
