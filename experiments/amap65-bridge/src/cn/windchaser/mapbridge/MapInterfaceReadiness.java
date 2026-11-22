package cn.windchaser.mapbridge;

import android.app.Activity;
import android.view.*;

/** Read-only native readiness plus recoverable original surface objects. */
public final class MapInterfaceReadiness {
    private static java.lang.reflect.Method nativeReady;
    private static synchronized java.lang.reflect.Method nativeMethod()throws Exception{
        if(nativeReady==null)nativeReady=Class.forName("com.autonavi.amapauto.jni.protocol.AndroidProtocolExe").getMethod("getReadyStatus");return nativeReady;
    }
    private MapInterfaceReadiness(){}
    public static boolean ready(Activity activity,View map,View ui){
        if(activity==null||activity.isFinishing())return false;
        try{
            if(!Boolean.TRUE.equals(nativeMethod().invoke(null)))return false;
            String pkg=activity.getPackageName();
            if(map==null)map=activity.findViewById(activity.getResources().getIdentifier("map_surface","id",pkg));
            if(ui==null)ui=activity.findViewById(activity.getResources().getIdentifier("ui_surface","id",pkg));
            return map instanceof SurfaceView&&ui instanceof SurfaceView&&map!=ui&&map.getParent() instanceof ViewGroup&&ui.getParent() instanceof ViewGroup;
        }catch(Exception|LinkageError unavailable){return false;}
    }
}
