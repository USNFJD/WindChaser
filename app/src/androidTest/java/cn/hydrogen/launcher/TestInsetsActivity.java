package cn.hydrogen.launcher;
import android.app.Activity;
import android.os.Bundle;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
/** External screen with ordinary system bars/IME, never included in the product APK. */
public final class TestInsetsActivity extends Activity {
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setSystemUiVisibility(0);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        EditText input=new EditText(this);input.setHint("Return-layout regression fixture");setContentView(input);input.requestFocus();
        input.post(()->((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(input,InputMethodManager.SHOW_FORCED));
    }
}
