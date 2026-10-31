package cn.hydrogen.cardlab;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Build;
import android.net.Uri;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.morgoo.droidplugin.pm.PluginManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Set;
import java.util.HashSet;
import java.util.zip.ZipFile;
import java.util.Enumeration;
import java.util.zip.ZipEntry;

/** A separate lab app: it never replaces the user's launcher or requests system privileges. */
public class LabActivity extends Activity {
    TextView status;
    final View[] cards = new View[2];
    boolean handled;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Log.i("CardLab","RUN "+getIntent().getStringExtra("run"));
        LinearLayout root = new LinearLayout(this); root.setOrientation(1); root.setPadding(16,10,16,10);
        root.setBackgroundColor(Color.rgb(15,24,39));
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(16);
        status.setText("完整应用窗口实验 · 未完成原应用验收"); root.addView(status);
        LinearLayout tools = new LinearLayout(this);
        addProbe(tools,"探针左",0);
        addProbe(tools,"探针右",1);
        Button amap=new Button(this);amap.setText("选高德 APK");amap.setOnClickListener(v->choose(0));tools.addView(amap);
        Button qq=new Button(this);qq.setText("选 QQ APK");qq.setOnClickListener(v->choose(1));tools.addView(qq);
        Button export=new Button(this);export.setText("导出诊断");export.setOnClickListener(v->export());tools.addView(export);
        Button exit = new Button(this); exit.setText("结束实验"); exit.setOnClickListener(v -> shutdown()); tools.addView(exit);
        root.addView(tools,new LinearLayout.LayoutParams(-1,52));
        LinearLayout row = new LinearLayout(this);
        for(int i=0;i<2;i++) {
            TextView card = new TextView(this); card.setGravity(Gravity.CENTER); card.setTextColor(Color.LTGRAY);
            card.setText(i==0?"左容器 · 等待窗口":"右容器 · 等待窗口");
            card.setBackgroundColor(i==0?Color.rgb(28,53,71):Color.rgb(49,40,65));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-1,1); lp.setMargins(5,5,5,5);
            row.addView(card,lp); cards[i]=card;
        }
        root.addView(row,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        root.post(() -> {
            publish();
            if (!handled && getIntent().hasExtra("apk")) { handled=true; importApk(getIntent().getStringExtra("apk"),getIntent().getIntExtra("slot",0)); }
        });
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); publish(); if(intent.hasExtra("apk")) importApk(intent.getStringExtra("apk"),intent.getIntExtra("slot",0)); }
    void publish() {
        int[][] bounds=new int[2][4];
        for(int i=0;i<2;i++) { int[] xy=new int[2];cards[i].getLocationOnScreen(xy);bounds[i]=new int[]{xy[0],xy[1],cards[i].getWidth(),cards[i].getHeight()}; }
        CardBridge.bounds=bounds;
        CardBridge.parent=getWindow().getDecorView().getWindowToken();
        Log.i("CardLab","PARENT uid="+android.os.Process.myUid()+" token="+CardBridge.parent+" left="+java.util.Arrays.toString(CardBridge.bounds[0])+" right="+java.util.Arrays.toString(CardBridge.bounds[1]));
    }
    void addProbe(LinearLayout row,String title,int slot) {
        Button b=new Button(this);b.setText(title);row.addView(b);
        b.setOnClickListener(v->new Thread(()->{
            File file=new File(getFilesDir(),slot==0?"probe-left.apk":"probe-right.apk");
            try(InputStream in=getAssets().open(file.getName());OutputStream out=new FileOutputStream(file)) {
                copy(in,out);
            }catch(Exception e){report("探针读取失败: "+e);return;}
            runOnUiThread(()->importApk(file.getAbsolutePath(),slot));
        }).start());
    }
    void choose(int slot) {
        Intent picker=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        try{startActivityForResult(picker,10+slot);}catch(Exception e){
            try{startActivityForResult(new Intent(Intent.ACTION_GET_CONTENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"),10+slot);}
            catch(Exception fallback){report("请使用支持选择文件的文件管理器: "+fallback);}
        }
    }
    void export() {
        Intent picker=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/plain").putExtra(Intent.EXTRA_TITLE,"hydrogen-cardlab-diagnostics.txt");
        try{startActivityForResult(picker,30);}catch(Exception e){report("无法选择诊断保存位置: "+e);}
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK || data==null || data.getData()==null)return;
        Uri uri=data.getData();
        new Thread(()->{
            try {
                if(request==30) {
                    try(OutputStream out=getContentResolver().openOutputStream(uri)) {
                        out.write(("Hydrogen card runtime experiment\nSDK="+Build.VERSION.SDK_INT+" ABI="+Build.CPU_ABI+"\n").getBytes("UTF-8"));
                        for(String name:new String[]{"diagnostics.txt","runtime-crashes.txt"}) {
                            File file=new File(getFilesDir(),name);
                            if(file.exists())try(InputStream in=new java.io.FileInputStream(file)){copy(in,out);}
                        }
                    }
                    report("诊断已导出");
                } else if(request==10 || request==11) {
                    File file=new File(getFilesDir(),"selected-"+(request-10)+".apk");
                    try(InputStream in=getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(file)){copy(in,out);}
                    runOnUiThread(()->importApk(file.getAbsolutePath(),request-10));
                }
            }catch(Exception e){report("文件操作失败: "+e);}
        }).start();
    }
    static void copy(InputStream in,OutputStream out) throws java.io.IOException {byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
    void report(String text) {
        Log.i("CardLab",text);runOnUiThread(()->status.setText(text));
        try(FileOutputStream out=new FileOutputStream(new File(getFilesDir(),"diagnostics.txt"),true)){out.write((System.currentTimeMillis()+" "+text+"\n").getBytes("UTF-8"));}catch(Exception ignored){}
    }
    void shutdown() {
        sendBroadcast(new Intent(getPackageName()+".CLOSE_GUESTS").setPackage(getPackageName()),getPackageName()+".CONTROL");
        new Thread(()->{
            try{Thread.sleep(500);for(PackageInfo info:PluginManager.getInstance().getInstalledPackages(0))PluginManager.getInstance().forceStopPackage(info.packageName);}
            catch(Exception e){report("关闭虚拟应用: "+e);}
            runOnUiThread(()->finish());
        }).start();
    }
    void importApk(String file,int slot) {
        if(Build.VERSION.SDK_INT!=19){report("此实验版仅验证 Android 4.4.x（API 19）");return;}
        publish();
        if (slot<0 || slot>1) {report("无效容器"); return;}
        new Thread(()->{
            try {
                PluginManager pm=PluginManager.getInstance();pm.waitForConnected(10000);
                if(!pm.isConnected()) throw new IllegalStateException("runtime not connected");
                PackageInfo info=getPackageManager().getPackageArchiveInfo(file,0);
                if(info==null) throw new IllegalArgumentException("APK unreadable: "+file);
                MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=new java.io.FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
                StringBuilder hash=new StringBuilder();for(byte b:digest.digest())hash.append(String.format(java.util.Locale.US,"%02x",b&255));
                report("APK "+info.packageName+" SHA256="+hash);
                Set<String> abis=new HashSet<>();
                try(ZipFile zip=new ZipFile(file)){Enumeration<? extends ZipEntry> entries=zip.entries();while(entries.hasMoreElements()){String name=entries.nextElement().getName();String[] parts=name.split("/");if(parts.length==3&&parts[0].equals("lib")&&name.endsWith(".so"))abis.add(parts[1]);}}
                if(!abis.isEmpty() && !abis.contains(Build.CPU_ABI) && !abis.contains(Build.CPU_ABI2))throw new IllegalStateException("APK native ABI="+abis+", device="+Build.CPU_ABI+"/"+Build.CPU_ABI2+"；当前环境无法验证");
                if(info.packageName.equals(getPackageName()))throw new IllegalArgumentException("不能导入容器自身");
                String previous=getSharedPreferences("active",0).getString("slot"+slot,null);
                if(previous!=null&&!previous.equals(info.packageName))pm.forceStopPackage(previous);
                CardBridge.slots.put(info.packageName,slot);
                getSharedPreferences("slots",0).edit().putInt(info.packageName,slot).commit();
                report("导入 "+info.packageName);
                int result=pm.installPackage(file,2);
                report("IMPORT "+info.packageName+" result="+result);
                if(result!=1) return;
                getSharedPreferences("active",0).edit().putString("slot"+slot,info.packageName).commit();
                Intent launch=getPackageManager().getLaunchIntentForPackage(info.packageName);
                if(launch==null) throw new IllegalStateException("no launcher Activity");
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                runOnUiThread(()->{ try{startActivity(launch);}catch(Throwable e){report("LAUNCH FAILED "+e);} });
            }catch(Throwable e){Log.e("CardLab","import failed",e);report("失败: "+e);}
        },"cardlab-import").start();
    }
}
