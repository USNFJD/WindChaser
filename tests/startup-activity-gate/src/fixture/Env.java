package fixture;
import java.util.*;import android.content.*;import android.os.*;
public final class Env {
 public static java.util.function.BiConsumer<Context,Intent> route;
 public static List<Intent> launches=new ArrayList<>(),broadcasts=new ArrayList<>(),serviceStarts=new ArrayList<>();
 public static List<String> logs=new ArrayList<>();public static List<BroadcastReceiver> receivers=new ArrayList<>();
 public static Map<String,Object> services=new HashMap<>();public static Map<String,SharedPreferences> prefs=new HashMap<>();
 public static String top="cn.hydrogen.launcher";public static boolean blockHome,unavailable,guestProcess;public static Bundle state=new Bundle();
 public static void dispatch(Intent i){}
}
