package cn.hydrogen.launcher;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;

/** Navigation exposed by the supplied, unmodified QQ Music Car 2.1.1.1. */
final class QqNavigation {
    private QqNavigation() {}
    static Intent homeIntent() {
        return new Intent(Intent.ACTION_VIEW,Uri.parse("qqmusiccar://qqmusic.com/MainFragment"))
                .setComponent(new ComponentName(Config.MUSIC,"com.tencent.qqmusiccar.v2.activity.home.MainActivity"))
                // QQ can ignore a warm MainFragment link while its player is on top.
                // Reset only its UI task; the independent playback service stays alive.
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }
    static void openHome(Context context) {
        PackageManager pm=context.getPackageManager();
        try {
            PackageInfo info=pm.getPackageInfo(Config.MUSIC,0);
            if("2.1.1.1".equals(info.versionName)) {
                context.startActivity(homeIntent());
                return;
            }
        } catch(PackageManager.NameNotFoundException missing) {
            Ui.toast(context,"请先安装 QQ音乐车机版");return;
        } catch(ActivityNotFoundException|SecurityException unavailable) {
            LauncherApp.record("QQ音乐首页直达不可用，尝试应用启动入口",unavailable);
        }
        Intent fallback=pm.getLaunchIntentForPackage(Config.MUSIC);
        if(fallback==null){Ui.toast(context,"此 QQ音乐版本没有可用的启动入口");return;}
        if(Ui.open(context,fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
            Ui.toast(context,"已打开 QQ音乐；此版本的首页直达尚未验证");
    }
}
