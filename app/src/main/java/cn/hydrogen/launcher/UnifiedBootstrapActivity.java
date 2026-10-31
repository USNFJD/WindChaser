package cn.hydrogen.launcher;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;
import cn.windchaser.startup.StartupVisual;
import com.morgoo.droidplugin.unified.StartupWindowState;

/** One startup visual; first-use permissions and original controls are user-operated dialogs. */
public final class UnifiedBootstrapActivity extends Activity implements UnifiedRuntime.Listener {
    private static java.lang.ref.WeakReference<UnifiedBootstrapActivity> visible;
    private static java.lang.ref.WeakReference<UnifiedBootstrapActivity> current;
    static void desktopAccepted(){
        UnifiedBootstrapActivity a=current==null?null:current.get();
        if(a!=null&&!a.isFinishing())a.finish();
    }
    private final Handler refresh=new Handler(Looper.getMainLooper());
    private boolean opened,resumed,permissionPending,blocked,dialogShown,restoringDesktop;private TextView progress;private AlertDialog dialog;
    private final Runnable tick=new Runnable(){public void run(){
        if(!resumed||isFinishing()||restoringDesktop)return;
        if(mapSetup!=null&&!UnifiedInitService.firstMapPending()){mapSetup.close();mapSetup=null;mapSetupRequested=false;}
        if(recoveryHelp!=null)recoveryHelp.setVisibility(SystemClock.elapsedRealtime()-enteredAt>=20000?View.VISIBLE:View.GONE);
        progress.setText(progressText());
        if(opened&&!UnifiedInitService.active()&&!UnifiedInitService.admitted()&&!UnifiedInitService.status().isEmpty())showFailure(UnifiedInitService.status());
        else advance();
        refresh.postDelayed(this,500);
    }};
    private long enteredAt=SystemClock.elapsedRealtime();private TextView recoveryHelp;
    private UnifiedMapSetup mapSetup;private boolean mapSetupRequested;
    private String lastProgress="正在准备内置应用…";
    static boolean ownsInitializationWindow(){UnifiedBootstrapActivity a=visible==null?null:visible.get();return a!=null&&!a.isFinishing()&&(a.hasWindowFocus()||(a.mapSetup!=null&&a.mapSetup.focused()));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        if(restoreDesktop(getIntent()))return;
        current=new java.lang.ref.WeakReference<>(this);mapSetupRequested=UnifiedInitService.mapSetupFor(getIntent().getStringExtra("first_map_setup"));if(!mapSetupRequested&&!getIntent().getBooleanExtra("diagnostics",false))UnifiedInitService.newEntry();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);getWindow().setWindowAnimations(0);Ui.fullscreen(this);
        android.widget.FrameLayout root=new android.widget.FrameLayout(this);root.addView(StartupVisual.view(this),new android.widget.FrameLayout.LayoutParams(-1,-1));
        progress=new TextView(this);progress.setTextSize(14);progress.setTextColor(0xffb2c8db);progress.setGravity(Gravity.CENTER);progress.setPadding(16,8,16,12);
        android.widget.FrameLayout.LayoutParams bottom=new android.widget.FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);bottom.bottomMargin=Ui.dp(this,42);progress.setMaxLines(2);progress.setEllipsize(android.text.TextUtils.TruncateAt.END);root.addView(progress,bottom);setContentView(root);
        recoveryHelp=new TextView(this);recoveryHelp.setText("准备遇到问题？");recoveryHelp.setTextSize(14);recoveryHelp.setTextColor(0xffc3dceb);recoveryHelp.setPadding(20,16,20,16);recoveryHelp.setVisibility(View.GONE);
        root.addView(recoveryHelp,new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT));
        recoveryHelp.setOnClickListener(v->{UnifiedInitService.recoveryRequested();blocked=true;opened=false;if(mapSetup!=null){mapSetup.close();mapSetup=null;}mapSetupRequested=false;showFailure(UnifiedInitService.status());});
        boolean interrupted=UnifiedInitService.recoverInterrupted(this);
        if(getIntent().getBooleanExtra("recovery_requested",false))UnifiedInitService.recoveryRequested();
        blocked=interrupted||getIntent().getBooleanExtra("recovery_requested",false)||getIntent().getBooleanExtra("initialization_failed",false);UnifiedRuntime.prepare(this,this);
    }
    private boolean restoreDesktop(Intent intent){
        if(intent==null||intent.hasExtra("first_map_setup")||intent.getBooleanExtra("diagnostics",false)
            ||intent.getBooleanExtra("recovery_requested",false)||intent.getBooleanExtra("initialization_failed",false)
            ||!UnifiedInitService.canResumeDesktop(this))return false;
        restoringDesktop=true;getWindow().setWindowAnimations(0);refresh.post(this::finishRestore);return true;
    }
    private void finishRestore(){
        if(!restoringDesktop||!resumed||!hasWindowFocus())return;
        if(!UnifiedInitService.canResumeDesktop(this)){restoringDesktop=false;recreate();return;}
        try{
            startActivity(new Intent(this,MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_NO_ANIMATION)
                .putExtra("windchaser.return_to_workspace",true));
            finish();overridePendingTransition(0,0);
            LauncherApp.record("返回桌面：复用已就绪会话，不重新创建启动画面",null);
        }catch(RuntimeException error){restoringDesktop=false;LauncherApp.record("直接恢复桌面受限，重新检查接口",error);recreate();}
    }
    @Override protected void onResume(){super.onResume();resumed=true;StartupWindowState.report(this,this,true);if(restoringDesktop){finishRestore();return;}
        // A returned old task must recheck live interfaces rather than retain a
        // completed service's animation or reuse its previous admission.
        if(opened&&mapSetup==null&&UnifiedInitService.admitted()&&!UnifiedInitService.active()){
            opened=false;UnifiedInitService.newEntry();
        }
        resumed=true;visible=new java.lang.ref.WeakReference<>(this);Ui.fullscreen(this);refresh.removeCallbacks(tick);refresh.post(tick);}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){if(restoringDesktop)finishRestore();else advance();}}
    @Override protected void onPause(){StartupWindowState.report(this,this,false);resumed=false;refresh.removeCallbacks(tick);if(mapSetup!=null)mapSetup.paused();if(visible!=null&&visible.get()==this)visible=null;super.onPause();}
    @Override protected void onDestroy(){StartupWindowState.forget(this,this);refresh.removeCallbacksAndMessages(null);if(dialog!=null){dialog.dismiss();dialog=null;}if(mapSetup!=null){mapSetup.close();mapSetup=null;}if(visible!=null&&visible.get()==this)visible=null;if(current!=null&&current.get()==this)current=null;super.onDestroy();}
    private void advance(){
        if(restoringDesktop)return;
        if(mapSetupRequested){
            if(!UnifiedInitService.firstMapPending()){mapSetupRequested=false;}
            else if(resumed&&hasWindowFocus()&&mapSetup==null){
                if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)){
                    if(!dialogShown){dialogShown=true;dialog=new AlertDialog.Builder(this).setTitle("显示地图设置")
                        .setMessage("首次地图设置使用原有地图卡片通道，需要您允许追风桌面显示悬浮窗。")
                        .setPositiveButton("去授权",(d,w)->{dialogShown=false;permissionPending=true;startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),21);})
                        .setNegativeButton("取消",(d,w)->{UnifiedInitService.cancelFirstMap();finish();}).setCancelable(false).show();}
                    return;
                }
                opened=true;mapSetup=new UnifiedMapSetup(this);mapSetup.show();return;
            }
            else return;
        }
        if(!resumed||!hasWindowFocus()||isFinishing()||opened||dialogShown||permissionPending)return;
        if(blocked){showFailure(UnifiedInitService.status());return;}
        if(!UnifiedRuntime.ready())return;
        if(getIntent().getBooleanExtra("diagnostics",false)){showDiagnostics();return;}
        if(!getPreferences(0).getBoolean("firstSetupExplained",false)&&!(getSharedPreferences("unified-native-initialization",0).getBoolean(Config.MAP,false)&&getSharedPreferences("unified-native-initialization",0).getBoolean(Config.MUSIC,false))){
            dialogShown=true;
            dialog=new AlertDialog.Builder(this).setTitle("首次使用设置")
                .setMessage("接下来会在追风启动画面上完成必要设置：\n\n• 系统权限由您逐项选择。\n• 高德的首次设置在弹窗地图内操作。\n• QQ音乐保留原有协议弹窗，由您阅读并确认。\n\n准备完成后自动进入桌面。以后启动共用追风启动画面。")
                .setPositiveButton("开始设置",(d,w)->{getPreferences(0).edit().putBoolean("firstSetupExplained",true).apply();dialogShown=false;advance();})
                .setNegativeButton("退出",(d,w)->finish()).setCancelable(false).show();
            return;
        }
        if(Build.VERSION.SDK_INT>=23&&!getPreferences(0).getBoolean("permissionPrompted",false)){
            getPreferences(0).edit().putBoolean("permissionPrompted",true).apply();
            ArrayList<String> missing=new ArrayList<>();
            for(String p:new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE})
                if(checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
            if(!missing.isEmpty()){permissionPending=true;requestPermissions(missing.toArray(new String[0]),20);return;}
        }
        if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(this)&&!getPreferences(0).getBoolean("overlayPrompted",false)){
            getPreferences(0).edit().putBoolean("overlayPrompted",true).apply();permissionPending=true;
            try{startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName())),21);return;}
            catch(RuntimeException unavailable){permissionPending=false;}
        }
        openDesktop();
    }
    @Override public void onRequestPermissionsResult(int code,String[] names,int[] grants){super.onRequestPermissionsResult(code,names,grants);permissionPending=false;advance();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);permissionPending=false;advance();}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);
        if(restoreDesktop(intent))return;
        String mapSession=intent.getStringExtra("first_map_setup");
        if(mapSession!=null){if(UnifiedInitService.mapSetupFor(mapSession)){mapSetupRequested=true;opened=true;setIntent(intent);advance();}return;}
        if(mapSetup!=null){mapSetup.close();mapSetup=null;}mapSetupRequested=false;
if(dialog!=null){dialog.dismiss();dialog=null;}setIntent(intent);opened=false;dialogShown=false;if(intent.getBooleanExtra("recovery_requested",false))UnifiedInitService.recoveryRequested();blocked=intent.getBooleanExtra("recovery_requested",false)||intent.getBooleanExtra("initialization_failed",false);if(!intent.getBooleanExtra("diagnostics",false)&&!blocked)UnifiedInitService.newEntry();advance();}
    @Override public void update(String text,boolean ready,String error){if(isFinishing()||restoringDesktop)return;lastProgress=text;if(progress!=null)progress.setText(progressText());if(error!=null&&!error.isEmpty()){blocked=true;showFailure(text);}else advance();}
    private String progressText(){
        if(!UnifiedRuntime.ready())return lastProgress;
        String stage=UnifiedInitService.status();
        return stage==null||stage.isEmpty()?"正在检查地图与音乐连接…":stage;
    }
    private void openDesktop(){
        if(!UnifiedRuntime.ready()||opened)return;opened=true;
        if(UnifiedInitService.required(this)){
            try{UnifiedInitService.request(this);}catch(RuntimeException error){opened=false;blocked=true;showFailure("暂时无法准备地图和音乐，请重试。");}return;
        }
        startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));finish();overridePendingTransition(0,0);
    }
    private void showFailure(String message){
        blocked=true;if(!resumed||!hasWindowFocus()||dialogShown)return;dialogShown=true;
        dialog=new AlertDialog.Builder(this).setTitle("应用准备尚未完成").setMessage(message+"\n地图和音乐两个真实接口确认就绪后，才会进入桌面。授权和协议仍由您选择。")
            .setPositiveButton("重试",(d,w)->{UnifiedInitService.recoveryRequested();dialogShown=false;blocked=false;opened=false;setIntent(new Intent(this,UnifiedBootstrapActivity.class));UnifiedInitService.newEntry();UnifiedRuntime.prepare(this,this);advance();})
            .setNeutralButton("查看应用",(d,w)->{dialogShown=false;showDiagnostics();}).setNegativeButton("退出",(d,w)->finish()).setCancelable(false).show();
    }
    private void showDiagnostics(){
        if(dialogShown)return;dialogShown=true;
        dialog=new AlertDialog.Builder(this).setTitle("内置应用").setItems(new String[]{"高德地图","QQ音乐","重新准备桌面"},(d,w)->{
            dialogShown=false;if(w<2)UnifiedRuntime.launch(this,w==0?Config.MAP:Config.MUSIC);else{setIntent(new Intent(this,UnifiedBootstrapActivity.class));blocked=false;opened=false;advance();}
        }).setNegativeButton("返回",(d,w)->{dialogShown=false;setIntent(new Intent(this,UnifiedBootstrapActivity.class));blocked=false;opened=false;advance();}).show();
    }
}
