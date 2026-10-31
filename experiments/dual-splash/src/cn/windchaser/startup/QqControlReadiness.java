package cn.windchaser.startup;
import android.os.*;
/** Bindings verified against paired QQ 2.1.1.1: read the existing player Binder only. */
public final class QqControlReadiness{
    private QqControlReadiness(){}
    private static Object endpoint(ClassLoader loader)throws Exception{
        return Class.forName("com.tencent.qqmusicplayerprocess.service.d",false,loader).getField("a").get(null);
    }
    static boolean available(ClassLoader loader){
        try{Object remote=endpoint(loader);if(!(remote instanceof IInterface))return false;IBinder binder=((IInterface)remote).asBinder();return binder!=null&&binder.isBinderAlive();}
        catch(Exception|LinkageError unavailable){return false;}
    }
    static boolean ready(ClassLoader loader){
        try{
            Object remote=endpoint(loader);if(!(remote instanceof IInterface))return false;
            IBinder binder=((IInterface)remote).asBinder();if(binder==null||!binder.isBinderAlive())return false;
            // qm.b.E() calls b.o3(): the original read-only get-play-mode IPC.
            // Unlike service.d.k(), this never binds or initializes a service.
            Object mode=Class.forName("com.tencent.qqmusicplayerprocess.service.b",false,loader).getMethod("o3").invoke(remote);
            return mode instanceof Integer&&binder.isBinderAlive()&&endpoint(loader)==remote;
        }catch(Exception|LinkageError unavailable){return false;}
    }
}
