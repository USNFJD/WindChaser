package cn.hydrogen.launcher;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
/** Test APK only: a harmless external application screen for real launch/return checks. */
public class TestWarmActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView text=new TextView(this);text.setText("Startup test fixture");setContentView(text);sendBroadcast(new android.content.Intent("cn.hydrogen.launcher.TEST_WARM_STARTED").setPackage("cn.hydrogen.launcher"));}
    @Override public void onStop(){super.onStop();finish();}
}
