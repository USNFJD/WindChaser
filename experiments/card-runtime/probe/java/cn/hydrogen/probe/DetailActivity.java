package cn.hydrogen.probe;
import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
public class DetailActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);Button back=new Button(this);back.setText(getPackageName()+"\nSecond Activity - return");back.setOnClickListener(v->finish());setContentView(back);}
}
