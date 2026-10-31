package fixture;
import java.util.*;import android.content.*;import android.os.*;
public final class Env {
 public static java.util.function.BiConsumer<Context,Intent> route,activityStarted;
 public static boolean acknowledgeDesktop=true,reportForeground=true,clearDesktopProofOnArrival;public static int dropDesktop;
 public static List<Intent> launches=new ArrayList<>(),broadcasts=new ArrayList<>(),serviceStarts=new ArrayList<>();
 public static List<String> logs=new ArrayList<>();public static List<BroadcastReceiver> receivers=new ArrayList<>();
 public static Map<String,Object> services=new HashMap<>();public static Map<String,SharedPreferences> prefs=new HashMap<>();
 public static String top="cn.windchaser.unified";public static boolean enabled=true,blockHome;
 public static void dispatch(Intent i){}
}
