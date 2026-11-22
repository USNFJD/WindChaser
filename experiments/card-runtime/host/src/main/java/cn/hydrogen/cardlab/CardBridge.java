package cn.hydrogen.cardlab;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/** Same-UID IPC for this isolated experiment, never exported to other apps. */
public class CardBridge extends ContentProvider {
    static volatile IBinder parent;
    static volatile int[][] bounds = new int[2][4];
    static final Map<String, Integer> slots = new ConcurrentHashMap<>();
    public boolean onCreate() { return true; }
    @Override public synchronized Bundle call(String method, String pkg, Bundle extras) {
        Bundle reply = new Bundle();
        if ("window".equals(method)) {
            pkg = extras == null ? null : extras.getString("guest");
            if (pkg == null) return reply;
            Integer slot = slots.get(pkg);
            if (slot == null) slot = getContext().getSharedPreferences("slots",0).getInt(pkg,-1);
            if (slot >= 0 && slot < 2 && parent != null && parent.isBinderAlive()) {
                reply.putBinder("parent", parent);
                reply.putIntArray("bounds", bounds[slot]);
            }
        } else if ("event".equals(method)) {
            Log.i("CardLab", pkg + " " + extras);
        }
        return reply;
    }
    public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    public String getType(Uri u){return null;}
    public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
