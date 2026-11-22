package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** First-use controls reuse the normal native surfaces and signed card transport. */
final class UnifiedMapSetup {
    private final Activity activity;private final Dialog dialog;private final AmapFloatingController map;
    private final Handler timer=new Handler(Looper.getMainLooper());private final TextView hint;private final Button next;
    private boolean closed,checking;
    private final Runnable tick=new Runnable(){public void run(){
        if(closed)return;
        boolean focus=dialog.isShowing()&&dialog.getWindow()!=null&&dialog.getWindow().getDecorView().hasWindowFocus();
        map.setHostState(focus,true,focus);
        if(!checking){checking=true;LinkedAppCoordinator.get(activity).probeInitializing(Config.MAP,ready->{
            checking=false;if(closed)return;
            next.setEnabled(ready&&map.connected());
            hint.setText(map.connected()?"请在地图内完成必要设置，完成后点击继续。":"正在连接首次设置画面，请稍候…");
        });}
        timer.postDelayed(this,400);
    }};
    UnifiedMapSetup(Activity owner){
        activity=owner;dialog=new Dialog(owner);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);dialog.setCancelable(false);
        Config cfg=new Config(owner);
        // Initialization deliberately does not start the normal recovery/warm-up
        // owner. It sends only sequenced surface/window commands to the running map.
        // This window is required to complete first use even when the normal
        // desktop map card is disabled. It never writes the user's preference.
        map=AmapFloatingController.firstSetup(owner,cfg);
        map.setDialogFocus(()->dialog.isShowing()&&dialog.getWindow().getDecorView().hasWindowFocus());
        LinearLayout body=new LinearLayout(owner);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(16),dp(12),dp(16),dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xf2152638);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),0x604f7898);body.setBackground(bg);
        TextView title=text("高德地图 · 首次设置",19);body.addView(title,new LinearLayout.LayoutParams(-1,-2));
        hint=text("正在连接首次设置画面，请稍候…",13);hint.setPadding(0,dp(6),0,dp(8));body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout viewport=new FrameLayout(owner);viewport.setBackgroundColor(0xff091827);body.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout actions=new LinearLayout(owner);actions.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(8),0,0);
        Button cancel=new Button(owner);cancel.setText("取消");actions.addView(cancel,new LinearLayout.LayoutParams(-2,dp(48)));
        next=new Button(owner);next.setText("完成设置，继续");next.setEnabled(false);actions.addView(next,new LinearLayout.LayoutParams(-2,dp(48)));
        body.addView(actions,new LinearLayout.LayoutParams(-1,-2));dialog.setContentView(body);dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);dialog.getWindow().setWindowAnimations(0);
        map.bind(viewport);
        cancel.setOnClickListener(v->{close();UnifiedInitService.cancelFirstMap();activity.finish();});
        next.setOnClickListener(v->{
            next.setEnabled(false);
            LinkedAppCoordinator.get(activity).probeInitializing(Config.MAP,ready->{if(closed)return;if(ready&&map.connected()){
                close();UnifiedInitService.completeFirstMap();
            }else hint.setText("地图尚未就绪，请先完成地图内的必要设置。");});
        });
    }
    private int dp(float v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}
    private TextView text(String value,float size){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(0xffe3eff8);return view;}
    void show(){
        if(closed)return;dialog.show();android.util.DisplayMetrics dm=activity.getResources().getDisplayMetrics();
        dialog.getWindow().setLayout(Math.round(dm.widthPixels*.88f),Math.round(dm.heightPixels*.88f));
        dialog.getWindow().getDecorView().setSystemUiVisibility(activity.getWindow().getDecorView().getSystemUiVisibility());
        timer.post(tick);
    }
    boolean focused(){return !closed&&dialog.isShowing()&&dialog.getWindow().getDecorView().hasWindowFocus();}
    void paused(){map.setHostState(false,false,false);}
    void close(){if(closed)return;closed=true;timer.removeCallbacksAndMessages(null);map.setChanged(null);map.release();dialog.dismiss();}
}
