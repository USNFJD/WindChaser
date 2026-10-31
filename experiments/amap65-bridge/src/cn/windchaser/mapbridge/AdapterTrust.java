package cn.windchaser.mapbridge;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import org.json.JSONObject;

/** Explicit trust for this locally signed adapter, after the original signer check.
 * Does not impersonate the original certificate or alter native sign()/activation.
 */
public final class AdapterTrust {
    /** Must match the marker written by the companion packaging tool. */
    public static final int BRIDGE_REVISION = 12;
    private static final String PACKAGE = "com.autonavi.amapauto";
    private static final String CERT_SHA256 =
            "8b4f8e84026a5ac5ad75b4a0f34d21bd81957e4fb38dd80b7fe0c1537e66e7d8";
    private static final String BASE_SHA256 =
            "7daa40731d4f1bf2b1e521280bc031e5892c48f9092d918ded12da963b7c3950";

    private AdapterTrust() {}

    /** A narrowly scoped compatibility check; ordinary system-manager checks remain intact. */
    public static boolean isTrustedVirtualPackageManager(Context context) {
        try {
            if (!isTrustedAdapter(context)) return false;
            PackageManager manager=context.getPackageManager();
            java.lang.reflect.Field field=manager.getClass().getDeclaredField("mPM");field.setAccessible(true);
            Object dispatcher=field.get(manager);
            if (!java.lang.reflect.Proxy.isProxyClass(dispatcher.getClass())) return false;
            if (!"com.morgoo.droidplugin.hook.proxy.IPackageManagerHook".equals(java.lang.reflect.Proxy.getInvocationHandler(dispatcher).getClass().getName())) return false;
            PackageInfo host=manager.getPackageInfo("cn.windchaser.unified",PackageManager.GET_SIGNATURES);
            if(host.applicationInfo==null||host.applicationInfo.uid!=android.os.Process.myUid()||host.signatures==null||host.signatures.length!=1)return false;
            byte[] value=MessageDigest.getInstance("SHA-256").digest(host.signatures[0].toByteArray());
            StringBuilder hash=new StringBuilder();for(byte item:value)hash.append(String.format(java.util.Locale.US,"%02x",item&255));
            boolean valid=CERT_SHA256.equals(hash.toString());
            if(valid)Log.i("WindChaserMap","Verified same-UID WindChaser unified package dispatcher");return valid;
        } catch(Exception error){Log.w("WindChaserMap","Unified manager validation failed",error);return false;}
    }

    @SuppressWarnings("deprecation")
    public static boolean isTrustedAdapter(Context context) {
        try {
            if (context == null || !PACKAGE.equals(context.getPackageName())) return false;
            PackageInfo info = context.getPackageManager().getPackageInfo(
                    PACKAGE, PackageManager.GET_SIGNATURES);
            if (!PACKAGE.equals(info.packageName) || info.versionCode != 15000001
                    || !"6.5.0.601323".equals(info.versionName)
                    || info.signatures == null || info.signatures.length != 1
                    || info.signatures[0] == null) return false;
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    info.signatures[0].toByteArray());
            byte[] expected = new byte[CERT_SHA256.length() / 2];
            for (int i = 0; i < expected.length; i++)
                expected[i] = (byte) Integer.parseInt(CERT_SHA256.substring(i * 2, i * 2 + 2), 16);
            if (!MessageDigest.isEqual(expected, digest)) return false;

            // The development certificate alone must not authorize other versions.
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            InputStream stream = context.getAssets().open("windchaser-card-bridge.json");
            try {
                byte[] buffer = new byte[256];
                int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > 2048) return false;
                    bytes.write(buffer, 0, count);
                }
            } finally {
                stream.close();
            }
            JSONObject marker = new JSONObject(new String(bytes.toByteArray(), "UTF-8"));
            boolean trusted = marker.optInt("bridge_version") == 1
                    && marker.optInt("bridge_revision") == BRIDGE_REVISION
                    && "6.5.0.601323".equals(marker.optString("base_version"))
                    && BASE_SHA256.equals(marker.optString("base_sha256"));
            if (trusted) Log.i("WindChaserMap", "Recognized local adapter certificate, revision " + BRIDGE_REVISION);
            return trusted;
        } catch (Exception error) {
            Log.w("WindChaserMap", "Adapter certificate/marker validation failed", error);
            return false;
        }
    }
}
