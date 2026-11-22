package cn.hydrogen.launcher;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.view.WindowManager;

/** Test APK only: dismiss an unsecured emulator keyguard before UI tests. */
public class TestUnlockActivity extends Activity {
    private static KeyguardManager.KeyguardLock lock;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED|WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD|WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        KeyguardManager manager=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        lock=manager.newKeyguardLock("hydrogen-test");lock.disableKeyguard();
        android.widget.TextView label=new android.widget.TextView(this);label.setText("Preparing an unlocked test emulator…");setContentView(label);
        new android.os.Handler().postDelayed(()->{startActivity(new Intent().setClassName("cn.hydrogen.launcher","cn.hydrogen.launcher.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));finish();},700);
    }
}
