/* WindChaser single-package experiment, LGPL-3.0-or-later. */
package com.morgoo.droidplugin.unified;

import android.content.*;
import android.os.Handler;
import android.util.Log;
import com.morgoo.droidplugin.reflect.FieldUtils;
import java.util.IdentityHashMap;

/** Routes guest broadcasts inside the one installed UID while keeping package/component scopes. */
public final class VirtualBroadcasts {
    private static final String HOST="cn.windchaser.unified";
    private static final String PACKAGE="cn.windchaser.unified.target.package";
    private static final String COMPONENT="cn.windchaser.unified.target.component";
    private static final String ORIGINAL="cn.windchaser.unified.original.broadcast";
    private static final String ROUTE="cn.windchaser.unified.explicit.broadcast/";
    public static final String INTERNAL_PERMISSION="cn.windchaser.unified.permission.INTERNAL_BROADCAST";
    public static IntentFilter explicitFilter(String pkg,String type){return new IntentFilter(ROUTE+pkg+"/"+type);}
    public static boolean guest(String pkg){return "com.autonavi.amapauto".equals(pkg)||"com.tencent.qqmusiccar".equals(pkg);}
    public static Intent outgoing(Intent source) {
        ComponentName component=source.getComponent();String pkg=component==null?source.getPackage():component.getPackageName();
        if(!guest(pkg))return source;
        if(component!=null){
            // Explicit delivery must bypass the original IntentFilter's action/data matching.
            // Keep URI, MIME, categories and extras intact inside a private envelope; stripping
            // the component alone loses QQ play/seek commands whose URI has no matching scheme.
            return new Intent(ROUTE+pkg+"/"+component.getClassName()).setPackage(HOST)
                .setFlags(source.getFlags()).putExtra(ORIGINAL,new Intent(source));
        }
        Intent result=new Intent(source);result.putExtra(PACKAGE,pkg);
        return result.setPackage(HOST);
    }
    public static boolean attached(Context context){
        return context instanceof ContextWrapper && ((ContextWrapper)context).getBaseContext() instanceof ScopedContext;
    }
    public static void attach(ContextWrapper component,Context host) {
        if(!HOST.equals(host.getPackageName()))return;
        try {
            Context base=component.getBaseContext();if(base instanceof ScopedContext)return;
            FieldUtils.writeField(component,"mBase",new ScopedContext(base,component.getPackageName()));
        }catch(Exception error){Log.e("WindChaserUnified","receiver scope context unavailable",error);}
    }
    private static final class ScopedContext extends ContextWrapper {
        final String owner;final IdentityHashMap<BroadcastReceiver,BroadcastReceiver> receivers=new IdentityHashMap<>();
        ScopedContext(Context base,String pkg){super(base);owner=pkg;}
        private synchronized BroadcastReceiver scoped(final BroadcastReceiver original){
            if(original==null)return null;BroadcastReceiver old=receivers.get(original);if(old!=null)return old;
            BroadcastReceiver wrapper=new BroadcastReceiver(){public void onReceive(Context ignored,Intent incoming){
                // ReceiverDispatcher uses the host LoadedApk by default. Select this guest's loader
                // before Bundle.unparcel encounters any app-defined Parcelable (e.g. map weather).
                incoming.setExtrasClassLoader(ScopedContext.this.getClassLoader());
                Intent intent;
                if(incoming.getAction()!=null&&incoming.getAction().startsWith(ROUTE)){
                    // Only a static receiver's dedicated, signature-protected registration gets
                    // this envelope. Verify its exact target before loading any guest payload.
                    String expected=ROUTE+owner+"/"+original.getClass().getName();
                    if(!expected.equals(incoming.getAction()))return;
                    Intent payload=incoming.getParcelableExtra(ORIGINAL);
                    ComponentName target=payload==null?null:payload.getComponent();
                    if(target==null||!owner.equals(target.getPackageName())
                        ||!original.getClass().getName().equals(target.getClassName()))return;
                    intent=new Intent(payload);intent.setExtrasClassLoader(ScopedContext.this.getClassLoader());
                }else{
                    String target=incoming.getStringExtra(PACKAGE),type=incoming.getStringExtra(COMPONENT);
                    if(target!=null&&!owner.equals(target))return;
                    if(type!=null&&!original.getClass().getName().equals(type))return;
                    intent=new Intent(incoming);if(target!=null)intent.setPackage(target);if(type!=null)intent.setComponent(new ComponentName(target,type));
                    intent.removeExtra(PACKAGE);intent.removeExtra(COMPONENT);
                }
                try {
                    Object pending=FieldUtils.readField(this,"mPendingResult");
                    FieldUtils.writeField(this,"mPendingResult",null);FieldUtils.writeField(original,"mPendingResult",pending);
                    try{original.onReceive(ScopedContext.this,intent);}
                    finally{FieldUtils.writeField(this,"mPendingResult",FieldUtils.readField(original,"mPendingResult"));FieldUtils.writeField(original,"mPendingResult",null);}
                }catch(IllegalAccessException error){throw new IllegalStateException("receiver result bridge",error);}
            }};receivers.put(original,wrapper);return wrapper;
        }
        @Override public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){return getBaseContext().registerReceiver(scoped(r),f);}
        @Override public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,String permission,Handler handler){return getBaseContext().registerReceiver(scoped(r),f,permission,handler);}
        @Override public synchronized void unregisterReceiver(BroadcastReceiver r){BroadcastReceiver wrapped=receivers.remove(r);getBaseContext().unregisterReceiver(wrapped==null?r:wrapped);}
        @Override public void sendBroadcast(Intent i){getBaseContext().sendBroadcast(outgoing(i));}
        @Override public void sendBroadcast(Intent i,String permission){getBaseContext().sendBroadcast(outgoing(i),permission);}
        @Override public void sendOrderedBroadcast(Intent i,String permission){getBaseContext().sendOrderedBroadcast(outgoing(i),permission);}
        @Override public void sendOrderedBroadcast(Intent i,String permission,BroadcastReceiver r,Handler h,int code,String data,android.os.Bundle extras){getBaseContext().sendOrderedBroadcast(outgoing(i),permission,r,h,code,data,extras);}
    }
}
