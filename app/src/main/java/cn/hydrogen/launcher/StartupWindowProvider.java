package cn.hydrogen.launcher;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import com.morgoo.droidplugin.unified.StartupWindowState;
import com.morgoo.helper.compat.BundleCompat;

/** Private host-process broker; companion stub processes have the same Linux UID. */
public final class StartupWindowProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle data){
        if(Binder.getCallingUid()!=android.os.Process.myUid())throw new SecurityException("Private startup window broker");
        if("state".equals(method))return UnifiedInitService.startupWindowState();
        if("window".equals(method)&&data!=null)StartupWindowState.update(BundleCompat.getBinder(data,"window"),data.getString("package"),data.getBoolean("resumed"));
        return new Bundle();
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){return null;}
    @Override public int delete(Uri u,String s,String[] a){return 0;}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){return 0;}
}
