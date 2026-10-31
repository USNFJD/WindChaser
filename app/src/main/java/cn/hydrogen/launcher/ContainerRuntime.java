package cn.hydrogen.launcher;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;
final class ContainerRuntime {
    static final String APK_NAME="unified-modules-not-exported";
    static boolean supported(){return android.os.Build.VERSION.SDK_INT==19;}
    static boolean ready(Context c){return UnifiedRuntime.ready();}
    static String status(Context c){return ready(c)?"地图与音乐均为私有内置模块，无需单独安装":"内置模块尚未准备完成，请进入整合实验状态页";}
    static JSONObject descriptor(Context c)throws Exception{return new JSONObject().put("versionName","0.1-unified-experiment").put("sha256","详见 unified/modules.json");}
    static boolean launch(Context c){c.startActivity(new Intent(c,UnifiedBootstrapActivity.class).putExtra("diagnostics",true));return true;}
}
