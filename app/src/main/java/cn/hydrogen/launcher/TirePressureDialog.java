package cn.hydrogen.launcher;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

/** A bounded, launcher-owned TPMS panel. Sensor values require an actual hardware adapter. */
final class TirePressureDialog {
    private TirePressureDialog() {}

    static AlertDialog show(MainActivity activity) {
        boolean compact=activity.getResources().getDisplayMetrics().heightPixels
                /activity.getResources().getDisplayMetrics().density<540;
        int gap=Ui.dp(activity,compact?6:8);
        int accent=ThemeStyle.color(ThemeStyle.config(activity),"themeAccent",0xffa7e2d2);
        LinearLayout shell=ThemeStyle.role(Ui.column(activity),"dialogText");
        shell.setPadding(gap*2,gap*2,gap*2,gap*2);
        shell.setBackground(ThemeStyle.dialogPanel(activity,Ui.dp(activity,18)));

        LinearLayout header=new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        Icons icon=new Icons(activity,"tpms");
        header.addView(icon,new LinearLayout.LayoutParams(Ui.dp(activity,24),Ui.dp(activity,24)));
        TextView title=Ui.text(activity,"胎压信息",compact?18:20,Ui.WHITE);
        title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleSlot=new LinearLayout.LayoutParams(0,-2,1);
        titleSlot.leftMargin=gap;titleSlot.rightMargin=gap;header.addView(title,titleSlot);
        FrameLayout close=new FrameLayout(activity);
        close.setContentDescription("关闭胎压窗口");close.setFocusable(true);
        StateListDrawable feedback=new StateListDrawable();
        feedback.addState(new int[]{android.R.attr.state_pressed},Ui.panel(0x387faed0,Ui.dp(activity,12),0,0));
        feedback.addState(new int[]{android.R.attr.state_focused},Ui.panel(0x247faed0,Ui.dp(activity,12),0,0));
        feedback.addState(new int[]{},Ui.panel(0x107faed0,Ui.dp(activity,12),0,0));
        close.setBackground(feedback);
        close.addView(new Icons(activity,"close"),new FrameLayout.LayoutParams(
                Ui.dp(activity,24),Ui.dp(activity,24),Gravity.CENTER));
        header.addView(close,new LinearLayout.LayoutParams(Ui.dp(activity,44),Ui.dp(activity,44)));
        shell.addView(header,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout body=Ui.column(activity);body.setGravity(Gravity.CENTER_VERTICAL);
        TextView status=Ui.text(activity,"",compact?11:12,Ui.MUTED);
        status.setGravity(Gravity.CENTER);status.setPadding(0,gap,0,gap);
        body.addView(status,new LinearLayout.LayoutParams(-1,-2));
        final TextView[] pressures=new TextView[4],temperatures=new TextView[4];
        LinearLayout wheelLayout=new LinearLayout(activity);wheelLayout.setBaselineAligned(false);
        String[] wheelNames={"左前","右前","左后","右后"};
        TirePressureCarView car=new TirePressureCarView(activity,accent);
        for(int column=0;column<2;column++){
            LinearLayout side=Ui.column(activity);
            for(int row=0;row<2;row++){
                int wheelIndex=row*2+column;
                LinearLayout cell=Ui.column(activity);cell.setGravity(Gravity.CENTER);
                cell.setMinimumHeight(Ui.dp(activity,compact?75:88));
                cell.setPadding(gap/2,gap,gap/2,gap);
                cell.setBackground(ThemeStyle.panel(activity,"card",0x35101e2a,
                        Ui.dp(activity,12),Ui.dp(activity,1),Ui.GLASS_LINE));
                TextView label=Ui.text(activity,wheelNames[wheelIndex],compact?12:13,Ui.MUTED);
                label.setGravity(Gravity.CENTER);label.setSingleLine(true);cell.addView(label);
                TextView pressure=Ui.text(activity,"",compact?21:25,accent);
                SpannableString reading=new SpannableString("— bar");
                reading.setSpan(new RelativeSizeSpan(.58f),2,reading.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                pressure.setText(reading);pressure.setGravity(Gravity.CENTER);
                pressure.setSingleLine(true);pressure.setEllipsize(TextUtils.TruncateAt.END);
                cell.addView(pressure);pressures[wheelIndex]=pressure;
                TextView temperature=Ui.text(activity,"— ℃",compact?10:11,Ui.MUTED);
                temperature.setGravity(Gravity.CENTER);temperature.setSingleLine(true);
                temperature.setEllipsize(TextUtils.TruncateAt.END);
                cell.addView(temperature);temperatures[wheelIndex]=temperature;
                LinearLayout.LayoutParams slot=new LinearLayout.LayoutParams(-1,-2);
                if(row==0)slot.bottomMargin=gap;side.addView(cell,slot);
            }
            wheelLayout.addView(side,new LinearLayout.LayoutParams(0,-2,.355f));
            if(column==0){
                LinearLayout.LayoutParams carSlot=new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,.29f);
                carSlot.leftMargin=gap/2;carSlot.rightMargin=gap/2;
                car.setMinimumHeight(Ui.dp(activity,compact?156:184));wheelLayout.addView(car,carSlot);
            }
        }
        LinearLayout.LayoutParams wheelSlot=new LinearLayout.LayoutParams(-1,-2);
        wheelSlot.bottomMargin=gap;body.addView(wheelLayout,wheelSlot);
        TextView source=Ui.text(activity,"来源：未接入",compact?10:11,Ui.MUTED);
        source.setGravity(Gravity.CENTER);source.setSingleLine(true);source.setEllipsize(TextUtils.TruncateAt.END);body.addView(source);
        android.os.Handler timer=new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable update=new Runnable(){public void run(){
            TirePressureData.Snapshot data=TirePressureData.snapshot();long now=android.os.SystemClock.elapsedRealtime();
            status.setText(data.status(now));status.setVisibility(data.installed?View.VISIBLE:View.GONE);source.setText(String.format(java.util.Locale.CHINA,"来源：%s",data.source));source.setVisibility(data.installed?View.VISIBLE:View.GONE);
            car.readings(data,now);
            for(int index=0;index<4;index++){
                TirePressureData.Wheel wheel=data.wheels[index];boolean fresh=data.fresh(index,now);
                String pressure=fresh&&wheel.bar!=null?String.format(java.util.Locale.CHINA,"%.2f",wheel.bar):"—";
                SpannableString value=new SpannableString(pressure+" bar");value.setSpan(new RelativeSizeSpan(.58f),pressure.length()+1,value.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);pressures[index].setText(value);
                String label=!data.installed?"— ℃":!data.connected?"已断开":!fresh?(wheel!=null?"数据已过期":"等待新数据"):wheel.celsius==null?"— ℃":String.format(java.util.Locale.CHINA,"%.0f ℃ · %d秒前",wheel.celsius,Math.max(0,now-wheel.elapsed)/1000);
                temperatures[index].setText(label);
            }
            if(TirePressureData.attached()&&shell.getWindowToken()!=null)timer.postDelayed(this,1000);
        }};
        shell.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){timer.removeCallbacks(update);update.run();}public void onViewDetachedFromWindow(View v){timer.removeCallbacksAndMessages(null);}});
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);
        scroll.setClipToPadding(false);scroll.setPadding(0,0,0,gap/2);scroll.addView(body);
        shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        AlertDialog dialog=new ThemeDialog.Builder(activity).widthFraction(.68f)
                .heightDp(compact?244:288).panel(shell).show();
        if(dialog.getWindow()!=null)dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        close.setOnClickListener(view->dialog.dismiss());
        return dialog;
    }
}
