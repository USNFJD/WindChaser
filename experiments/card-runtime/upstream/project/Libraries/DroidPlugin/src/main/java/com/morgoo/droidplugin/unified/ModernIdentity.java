package com.morgoo.droidplugin.unified;
import android.content.*;
import android.os.Build;
import android.os.Process;
import com.morgoo.droidplugin.reflect.FieldUtils;
/** Attribute real system calls to the actual host UID, never to a nonexistent guest install. */
public final class ModernIdentity {
    public static void arguments(Object[] args,Context host) {
        if(Build.VERSION.SDK_INT<31 || args==null)return;
        for(int i=0;i<args.length;i++)if(args[i] instanceof AttributionSource) {
            AttributionSource old=(AttributionSource)args[i];
            if(VirtualBroadcasts.guest(old.getPackageName()) && old.getUid()==Process.myUid())
                args[i]=installedCaller(old,host);
        }
    }
    @android.annotation.TargetApi(31)
    private static AttributionSource installedCaller(AttributionSource old,Context host) {
        if(Build.VERSION.SDK_INT>=33)return new AttributionSource.Builder(old).setPackageName(host.getPackageName()).build();
        return new AttributionSource.Builder(old.getUid()).setPackageName(host.getPackageName()).setAttributionTag(old.getAttributionTag()).setNext(old.getNext()).build();
    }
    public static void context(Context target,Context host)throws Exception {
        if(Build.VERSION.SDK_INT<31)return;
        Context base=target;
        while(base instanceof ContextWrapper)base=((ContextWrapper)base).getBaseContext();
        if(base==null || !VirtualBroadcasts.guest(base.getPackageName()))return;
        FieldUtils.writeField(base,"mOpPackageName",host.getPackageName(),true);
        AttributionSource old=base.getAttributionSource();
        FieldUtils.writeField(base,"mAttributionSource",installedCaller(old,host),true);
    }
}
