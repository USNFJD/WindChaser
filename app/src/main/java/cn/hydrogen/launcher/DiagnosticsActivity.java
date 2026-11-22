package cn.hydrogen.launcher;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class DiagnosticsActivity extends WorkspaceActivity {
    private String report;
    @Override public void onCreate(Bundle b){
        super.onCreate(b);LinearLayout root=Ui.column(this);root.setPadding(Ui.dp(this,24),Ui.dp(this,18),Ui.dp(this,24),Ui.dp(this,18));root.setBackgroundColor(0xff09182c);
        LinearLayout top=new LinearLayout(this);top.addView(Ui.button(this,"返回",v->finish()),new LinearLayout.LayoutParams(Ui.dp(this,110),Ui.dp(this,48)));
        TextView title=Ui.text(this,"  安装检查与系统诊断",24,Ui.WHITE);top.addView(title,new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));
        top.addView(Ui.button(this,"导出日志文件",v->{try{ExportProvider.share(this,"diagnostics.txt",snapshot()+"\n\n"+logs(),"导出桌面诊断");}catch(Exception e){LauncherApp.record("导出失败",e);Ui.toast(this,"导出失败："+e.getMessage());}}),new LinearLayout.LayoutParams(Ui.dp(this,170),Ui.dp(this,48)));root.addView(top);
        ScrollView scroll=new ScrollView(this);TextView text=Ui.text(this,"",16,Ui.WHITE);text.setTextIsSelectable(true);text.setPadding(0,Ui.dp(this,16),0,Ui.dp(this,16));report=snapshot();text.setText(report+"\n\n最近的桌面日志\n"+logs());scroll.addView(text);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);Ui.fullscreen(this);
    }
    private String snapshot(){
        Config c=new Config(this);StringBuilder s=new StringBuilder();
        try{s.append("追风桌面 ").append(getPackageManager().getPackageInfo(getPackageName(),0).versionName);}catch(Exception ignored){s.append("追风桌面");}s.append(" · 单应用整合实验 / QQ音乐互联 / 高德悬浮卡片\n");s.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z",Locale.CHINA).format(new Date()));
        s.append("\n\n系统：Android ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT);
        s.append("\n型号：").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\nCPU：").append(Build.CPU_ABI).append(" / ").append(Build.CPU_ABI2);
        s.append("\n设备 / 产品：").append(Build.DEVICE).append(" / ").append(Build.PRODUCT).append("\n固件：").append(Build.DISPLAY).append("\n指纹：").append(Build.FINGERPRINT);
        s.append("\n系统声明 PiP 特性：").append(getPackageManager().hasSystemFeature("android.software.picture_in_picture")?"是（仍需应用自身支持，不能托管任意应用）":"否");
        
        s.append("\n屏幕：").append(getResources().getDisplayMetrics().widthPixels).append(" × ").append(getResources().getDisplayMetrics().heightPixels).append(" density=").append(getResources().getDisplayMetrics().density);
        ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(m);s.append("\n可用内存：").append(m.availMem/1024/1024).append(" MB，低内存：").append(m.lowMemory);
        boolean su=new File("/system/bin/su").exists()||new File("/system/xbin/su").exists()||new File("/sbin/su").exists();
        s.append("\nsu 文件：").append(su?"发现（只检查文件；未执行 su，未确认 Root 授权）":"常见路径未发现（不等同于完整 Root 鉴定）");
        s.append("\n系统窗口权限 MANAGE_ACTIVITY_STACKS：").append(checkCallingOrSelfPermission("android.permission.MANAGE_ACTIVITY_STACKS")==PackageManager.PERMISSION_GRANTED?"已授予":"未授予");
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);ResolveInfo resolved=getPackageManager().resolveActivity(home,PackageManager.MATCH_DEFAULT_ONLY);
        s.append("\n当前默认 HOME：").append(resolved==null?"未知":resolved.activityInfo.packageName);
        s.append("\n\n安装兼容性\n整合宿主 minSdk=17、targetSdk=").append(getApplicationInfo().targetSdkVersion).append("；内置地图和音乐为 ARM32 模块。\n系统需要支持运行 armeabi-v7a 应用。旧系统的联网、账号、解码与窗口表现需要在具体车机验证。");
        for(boolean music:new boolean[]{false,true}){
            String pkg=c.pkg(music);s.append("\n\n").append(music?"音乐应用":"地图应用").append("：").append(pkg);
            try{
                PackageInfo p=getPackageManager().getPackageInfo(pkg,PackageManager.GET_PERMISSIONS);s.append("\n已安装 ").append(p.versionName).append(" / target=").append(p.applicationInfo.targetSdkVersion).append(" / enabled=").append(p.applicationInfo.enabled);
                s.append("\n启动入口：").append(getPackageManager().getLaunchIntentForPackage(pkg));
                if(p.requestedPermissions!=null){s.append("\n权限状态（系统记录）：");for(String permission:p.requestedPermissions)if(permission.contains("LOCATION")||permission.contains("STORAGE")||permission.contains("AUDIO"))s.append("\n  ").append(permission).append(" = ").append(getPackageManager().checkPermission(permission,pkg)==PackageManager.PERMISSION_GRANTED?"授予":"未授予");}
            }catch(Exception e){s.append("\n未安装，或包信息不可读取");}
            int count=0;try{for(AppWidgetProviderInfo i:AppWidgetManager.getInstance(this).getInstalledProviders())if(i.provider.getPackageName().equals(pkg)){count++;s.append("\n小组件：").append(i.provider.flattenToShortString());}}catch(Exception e){s.append("\n系统小组件服务不可用：").append(e.getClass().getSimpleName());}
            s.append("\n可用小组件数：").append(count).append(" / 已保存 ID：").append(c.n(music?"musicWidget":"mapWidget",-1));
        }
        s.append("\n\n高德悬浮版卡片\n适配包名：").append(AmapFloatingController.PACKAGE).append("\n适配版本：").append(AmapFloatingController.VERSION);
        s.append("\n仅支持新版6.5卡片适配包，适配修订号≥5：").append(AmapFloatingController.supported(this)).append("\n当前地图：").append(c.pkg(false));
        s.append("\n检测到适配版本：").append(AmapFloatingController.supported(this)).append("\n已选择该包名：").append(AmapFloatingController.selected(c)).append("\n悬浮开关：").append(c.b("amapFloating",true));
        s.append("\n通过高德自带模块接收显示 / 关闭广播，屏幕像素边界由卡片实测。显示请求无状态回执，不代表已连接。首次须打开高德完成地图加载，并允许高德自身显示悬浮窗。离开卡片页、失去焦点、屏幕关闭或桌面暂停时发送关闭请求。日志记录请求与坐标；真实画面和触摸效果须实机验证。");
        s.append("\n\n应用容器实验\n").append(ContainerRuntime.status(this));
        try{org.json.JSONObject runtime=ContainerRuntime.descriptor(this);s.append("\n内置组件：").append(runtime.getString("versionName")).append("\n内置组件 SHA256：").append(runtime.getString("sha256"));}catch(Exception e){s.append("\n无法读取内置组件信息：").append(e.getMessage());}
        s.append("\n通过主题设置或卡片菜单进入；首次由系统安装器安装组件。它打开独立双窗口测试页。已验证两个测试 APK；原版高德 / QQ、等比缩放与真车尚未通过验收。容器的独立诊断须在容器页导出。");
        s.append("\n\n窗口能力与功能边界\n高德指定悬浮版由其自身创建窗口，桌面负责位置和显示时机。未把任意第三方 Activity 变成桌面 View。虚拟化仅在另行安装的实验组件内运行；桌面主进程不加载这些 Hook。\n其他地图通过 AppWidgetHost 显示第三方主动提供的 RemoteViews。QQ音乐歌单仍在原应用中操作。桌面不伪造歌曲、地图或播放状态，不杀死原应用、不索取音频焦点。\nAndroid 4.2 的 LocalActivityManager 不能跨应用任意嵌入；Android 4.4 的 VirtualDisplay 不是可交互应用宿主。\n\n错误处理\n如悬浮地图未显示，先初始化指定高德版本并允许其显示悬浮窗，再按 HOME 返回卡片页重连。标题栏关闭按钮或主题设置中的悬浮开关可以停止显示请求。软键盘、搜索与对话框须重点实测。\n如小组件空白，先打开原应用完成必要的许可、账号及权限操作，再返回桌面重新连接。系统拒绝小组件绑定时，不会自动反复启动原应用。\n桌面日志只含本进程的异常，第三方崩溃需使用电脑 adb logcat。日志最多保留两份，每份约 256 KB。\n分享导出需要车机安装可接收文件的应用；也可通过 adb run-as 提取 files/exports。\n");
        return s.toString();
    }
    private String logs(){StringBuilder out=new StringBuilder();for(String name:new String[]{"launcher.previous.log","launcher.log"}){File f=new File(getFilesDir(),name);if(!f.exists())continue;try(BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"))){String line;while((line=r.readLine())!=null)out.append(line).append('\n');}catch(Exception e){out.append("读取日志失败：").append(e.getMessage());}}return out.toString();}
}
