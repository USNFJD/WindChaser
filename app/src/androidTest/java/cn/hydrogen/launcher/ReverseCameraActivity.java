package cn.hydrogen.launcher;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
/** Test-only opaque Activity representing a camera screen; never shipped. */
public final class ReverseCameraActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);getWindow().getDecorView().setSystemUiVisibility(5894);FrameLayout content=new FrameLayout(this);content.setBackgroundColor(0xffc35e19);TextView title=new TextView(this);title.setText("CAMERA PRIORITY TEST - NOT REAL CAMERA");title.setTextColor(-1);title.setTextSize(24);content.addView(title);setContentView(content);}
}
