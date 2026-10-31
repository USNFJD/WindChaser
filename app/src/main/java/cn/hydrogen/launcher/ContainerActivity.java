package cn.hydrogen.launcher;
public class ContainerActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle state){super.onCreate(state);startActivity(new android.content.Intent(this,UnifiedBootstrapActivity.class).putExtra("diagnostics",true));finish();}
}
