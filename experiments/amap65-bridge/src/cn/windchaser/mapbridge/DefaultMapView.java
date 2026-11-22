package cn.windchaser.mapbridge;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/** One-time default through the original 6.5 MapOperaAction, after HMI readiness.
 * No native code, view controls, or saved map preferences are replaced.
 */
public final class DefaultMapView {
    private static final String PREFS = "windchaser-map-defaults";
    private static final String REVISION = "initial-view-revision";
    private static final int CURRENT_REVISION = 1;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Context app;
    private static boolean ready, inFlight, done;
    private static int attempts;
    private static final Runnable APPLY = new Runnable() {
        @Override public void run() { apply(); }
    };
    private static final Runnable TIMEOUT = new Runnable() {
        @Override public void run() {
            inFlight = false;
            // A missing native success response must never mark the migration complete.
            if (!done && ready && attempts < 3) MAIN.post(APPLY);
        }
    };
    private DefaultMapView() {}
    public static void install(Context context) {
        if (context == null || app != null || !"com.autonavi.amapauto".equals(context.getPackageName())) return;
        app = context.getApplicationContext();
        NativeFloatDefaults.install(app);
        done = preferences().getInt(REVISION, 0) >= CURRENT_REVISION;
        // Also handle installation after HMI has already reported ready.
        try {
            Class<?> protocol = Class.forName("com.autonavi.amapauto.jni.protocol.AndroidProtocolExe");
            if (Boolean.TRUE.equals(protocol.getMethod("getReadyStatus").invoke(null))) onReady(true);
        } catch (Exception | LinkageError unavailable) {
            Log.w("WindChaserMap", "Default view waits for original HMI ready callback");
        }
    }
    public static void onReady(final boolean value) {
        MAIN.post(new Runnable() { @Override public void run() {
            NativeFloatDefaults.onReady(value);
            ready = value;
            if (!ready) { MAIN.removeCallbacks(APPLY); MAIN.removeCallbacks(TIMEOUT); inFlight = false; return; }
            if (app != null && !done && !inFlight && attempts < 3) {
                MAIN.removeCallbacks(APPLY);
                MAIN.post(APPLY);
            }
        }});
    }
    private static SharedPreferences preferences() { return app.getSharedPreferences(PREFS, 0); }
    private static void apply() {
        if (app == null || done || !ready || inFlight || attempts >= 3) return;
        if (preferences().getInt(REVISION, 0) >= CURRENT_REVISION) { done = true; return; }
        attempts++;
        inFlight = true;
        try {
            // The original action selects its native/protocol path itself.
            // EXTRA_TYPE=2 means view control; EXTRA_OPERA=2 maps to native mode 2 (3D head-up).
            Class<?> action = Class.forName("a20");
            Intent request = new Intent().putExtra("EXTRA_TYPE", 2).putExtra("EXTRA_OPERA", 2);
            Object operation = action.getConstructor(Intent.class).newInstance(request);
            action.getMethod("c").invoke(operation);
            MAIN.postDelayed(TIMEOUT, 1500);
            Log.i("WindChaserMap", "Initial 3D head-up requested through original map action");
        } catch (Exception | LinkageError unavailable) {
            inFlight = false;
            Log.w("WindChaserMap", "Initial map view not applied; original map continues", unavailable);
        }
    }
    /** Called before the original JNI result method mutates its arguments. */
    public static void onViewResult(final int type, final boolean success) {
        MAIN.post(new Runnable() { @Override public void run() {
            if (app == null || done || !inFlight || !success) return;
            if (type < 0 || type > 2) return;
            // A successful manual 2D selection wins over any pending default retry.
            MAIN.removeCallbacks(TIMEOUT); MAIN.removeCallbacks(APPLY);
            inFlight = false;
            done = preferences().edit().putInt(REVISION, CURRENT_REVISION).commit();
            Log.i("WindChaserMap", type == 2 ? "Initial 3D head-up confirmed" : "Manual map view retained");
        }});
    }
}
