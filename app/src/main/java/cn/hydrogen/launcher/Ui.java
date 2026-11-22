package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.text.Collator;
import java.util.*;

final class Ui {
    static final int WHITE=0xffedf5ff, MUTED=0xffa9c0dc, LINE=0xff7faed0, BLUE=0xff1689ff;
    static final int GLASS_LINE=0x386e9bb4;
    static int dp(Context c,float n) { return (int)(n*c.getResources().getDisplayMetrics().density+.5f); }
    static GradientDrawable panel(int color,float radius,int stroke,int line) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); d.setStroke(stroke,line); return d;
    }
    static TextView text(Context c,String t,float sp,int color) {
        TextView v=new TextView(c); v.setText(t); v.setTextSize(sp); v.setTextColor(color); v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    static View appButton(MainActivity a,String pkg,String fallback,String description,View.OnClickListener action) {
        return appButton(a,pkg,fallback,description,action,1f);
    }
    static View appButton(MainActivity a,String pkg,String fallback,String description,View.OnClickListener action,float radiusScale) {
        FrameLayout button=new FrameLayout(a);
        button.setContentDescription(description);button.setOnClickListener(action);
        android.graphics.drawable.StateListDrawable background=new android.graphics.drawable.StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed},panel(0x50688c9d,a.u(12)*radiusScale,a.u(1),GLASS_LINE));
        background.addState(new int[]{},panel(0x182e4859,a.u(12)*radiusScale,a.u(1),0x284e7489));
        button.setBackground(background);
        View icon;
        try {
            ImageView image=new ImageView(a);image.setImageDrawable(a.getPackageManager().getApplicationIcon(pkg));
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);icon=image;
        } catch(Exception missing) {icon=new Icons(a,fallback);}
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setAlpha(.75f);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(a.u(28),a.u(28),Gravity.CENTER);
        button.addView(icon,p);return button;
    }
    static Button button(Context c,String title,View.OnClickListener action) {
        return button(c,title,action,1f);
    }
    static Button button(Context c,String title,View.OnClickListener action,float radiusScale) {
        Button b=new Button(c); b.setText(title); b.setTextColor(WHITE); b.setTextSize(16); b.setAllCaps(false);
        b.setBackground(ThemeStyle.panel(c,"settings",0xff203e61,dp(c,10)*radiusScale,dp(c,1),LINE));
        b.setPadding(dp(c,12),dp(c,6),dp(c,12),dp(c,6)); b.setOnClickListener(action); return b;
    }
    static void toast(Context c,String s) { Toast.makeText(c,s,Toast.LENGTH_LONG).show(); }
    static boolean open(Context c,Intent i) {
        try { c.startActivity(i); return true; }
        catch (Exception e) { LauncherApp.record("无法打开 "+i,e); toast(c,"无法打开："+e.getClass().getSimpleName()+"，请查看诊断日志"); return false; }
    }
    static boolean installed(Context c,String pkg) {
        try { c.getPackageManager().getApplicationInfo(pkg,0); return true; } catch (Exception e) { return false; }
    }
    static void launch(Context c,String flatOrPackage) {
        if (flatOrPackage.isEmpty()) { toast(c,"尚未配置应用"); return; }
        ComponentName name=ComponentName.unflattenFromString(flatOrPackage);
        // This is an internal page, not a separate launch task. Reusing launcher
        // task-reset flags can merely bring MainActivity forward on Android 4.4.
        if(name!=null&&c.getPackageName().equals(name.getPackageName())&&(FileCenterActivity.class.getName().equals(name.getClassName())||DeviceInfoActivity.class.getName().equals(name.getClassName()))){
            Intent internal=new Intent().setComponent(name);if(!(c instanceof Activity))internal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);open(c,internal);return;
        }
        Intent i=name==null ? c.getPackageManager().getLaunchIntentForPackage(flatOrPackage) : new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(name);
        if(i==null) { toast(c,"应用未安装或没有可启动的界面"); return; }
        open(c,i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
    }
    static List<ResolveInfo> apps(Context c) {
        Intent i=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> out=c.getPackageManager().queryIntentActivities(i,0);
        try{ResolveInfo files=new ResolveInfo();files.activityInfo=c.getPackageManager().getActivityInfo(new ComponentName(c,FileCenterActivity.class),0);out.add(files);}catch(PackageManager.NameNotFoundException ignored){}
        try{ResolveInfo device=new ResolveInfo();device.activityInfo=c.getPackageManager().getActivityInfo(new ComponentName(c,DeviceInfoActivity.class),0);out.add(device);}catch(PackageManager.NameNotFoundException ignored){}
        final Collator col=Collator.getInstance(Locale.CHINA);
        Collections.sort(out,(a,b)->{int ar=internalRank(a),br=internalRank(b);return ar!=br?(ar<br?-1:1):col.compare(a.loadLabel(c.getPackageManager()).toString(),b.loadLabel(c.getPackageManager()).toString());});
        return out;
    }
    private static int internalRank(ResolveInfo info){String name=info.activityInfo.name;return FileCenterActivity.class.getName().equals(name)?0:DeviceInfoActivity.class.getName().equals(name)?1:2;}
    interface AppChosen { void choose(ResolveInfo app); }
    static void pick(Activity a,AppChosen onPick) {
        List<ResolveInfo> apps=apps(a); String[] names=new String[apps.size()];
        for(int i=0;i<names.length;i++) names[i]=apps.get(i).loadLabel(a.getPackageManager()).toString();
        new ThemeDialog.Builder(a).widthFraction(.6f).setTitle("选择已安装应用").setItems(names,(d,n)->onPick.choose(apps.get(n))).setNegativeButton("取消",null).show();
    }
    static String component(ResolveInfo info) { return new ComponentName(info.activityInfo.packageName,info.activityInfo.name).flattenToString(); }
    static LinearLayout column(Context c) { LinearLayout l=new LinearLayout(c); l.setOrientation(LinearLayout.VERTICAL); return l; }
    static void fullscreen(Activity a) {
        int flags=View.SYSTEM_UI_FLAG_LOW_PROFILE|View.SYSTEM_UI_FLAG_FULLSCREEN;
        // Before KitKat, hiding navigation causes SystemUI to consume the next
        // touch. Keep its dimmed bar visible so launcher controls respond once.
        if(android.os.Build.VERSION.SDK_INT>=19)flags|=View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                |View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        a.getWindow().getDecorView().setSystemUiVisibility(flags);
    }
}
