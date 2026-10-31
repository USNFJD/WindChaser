package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

/** A launcher-owned, scrollable device overview. No resident service or root access. */
public final class DeviceInfoActivity extends WorkspaceActivity {
    private WallpaperLayer defaultBackground;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private LinearLayout body;private TextView refresh,status,ramTotal,ramAvailable,ramUsed;private ProgressBar ramBar;
    private AppMemory appMemory;private DeviceInfo.Snapshot snapshot;private float unit;private boolean visible,closed,loading;
    private final Runnable tick=new Runnable(){@Override public void run(){if(!visible||closed)return;updateMemory();handler.postDelayed(this,3000);}};
    private int d(float value){return Math.round(value*unit);}
    private TextView text(String value,int size,int color){return Ui.text(this,value,size*unit/getResources().getDisplayMetrics().scaledDensity,color);}
    @Override public void onCreate(Bundle state){super.onCreate(state);unit=Math.max(.65f,Math.min(getResources().getDisplayMetrics().widthPixels/1280f,getResources().getDisplayMetrics().heightPixels/720f));build();load();}
    private View button(String title,Runnable action){TextView v=text(title,19,Ui.WHITE);v.setGravity(Gravity.CENTER);v.setPadding(d(12),d(8),d(12),d(8));v.setBackground(ThemeStyle.panel(this,"settings",0x80506b7c,d(12),d(1),Ui.GLASS_LINE));v.setContentDescription(title);v.setOnClickListener(w->action.run());return v;}
    private void build(){
        FrameLayout backdrop=new FrameLayout(this);defaultBackground=WallpaperLayer.defaultBackground(this,defaultBackground);WallpaperLayer picture=defaultBackground;picture.setContentDescription("车机信息默认背景");backdrop.addView(picture,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(0xbb061322);backdrop.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout content=Ui.column(this);content.setPadding(d(28),d(18),d(28),d(12));backdrop.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(button("‹ 返回",this::finish),new LinearLayout.LayoutParams(d(100),d(48)));
        ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.ic_device_info);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(d(44),d(44));ip.leftMargin=d(22);ip.rightMargin=d(12);header.addView(mark,ip);
        TextView title=text("车机信息",29,Ui.WHITE);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));refresh=(TextView)button("刷新信息",this::load);header.addView(refresh,new LinearLayout.LayoutParams(d(120),d(48)));content.addView(header,new LinearLayout.LayoutParams(-1,d(64)));
        status=text("正在读取车机信息…",15,Ui.MUTED);status.setPadding(d(4),d(6),d(4),d(12));content.addView(status,new LinearLayout.LayoutParams(-1,-2));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setContentDescription("车机信息内容");body=Ui.column(this);body.setPadding(0,0,0,d(12));scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(backdrop);Ui.fullscreen(this);ThemeStyle.apply(backdrop,"settingsText");
    }
    private void load(){
        if(loading||closed)return;loading=true;refresh.setEnabled(false);status.setText("正在读取车机信息…");
        worker.execute(()->{try{DeviceInfo.Snapshot next=new DeviceInfo.Snapshot();AppMemory sample=AppMemory.capture(getApplicationContext());handler.post(()->{if(closed)return;snapshot=next;appMemory=sample;loading=false;refresh.setEnabled(true);render();});}catch(RuntimeException error){handler.post(()->{if(closed)return;loading=false;refresh.setEnabled(true);status.setText("部分信息读取失败，点击刷新重试");LauncherApp.record("读取车机信息",error);});}});
    }
    private LinearLayout panel(String title){LinearLayout card=Ui.column(this);card.setPadding(d(22),d(16),d(22),d(16));card.setBackground(ThemeStyle.panel(this,"settings",0xb0122434,d(20),d(1),Ui.GLASS_LINE));TextView name=text(title,23,0xffb0eadb);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);name.setPadding(0,0,0,d(10));card.addView(name,new LinearLayout.LayoutParams(-1,-2));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.bottomMargin=d(14);body.addView(card,cp);return card;}
    private TextView row(LinearLayout card,String key,String value){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.TOP);TextView label=text(key,18,Ui.MUTED);label.setPadding(0,d(7),d(12),d(7));row.addView(label,new LinearLayout.LayoutParams(d(190),-2));TextView data=text(value,20,Ui.WHITE);data.setTextIsSelectable(true);data.setPadding(0,d(6),0,d(6));row.addView(data,new LinearLayout.LayoutParams(0,-2,1));card.addView(row,new LinearLayout.LayoutParams(-1,-2));return data;}
    private void render(){
        body.removeAllViews();DeviceInfo.Cpu cpu=snapshot.cpu;
        LinearLayout overview=new LinearLayout(this);String[] labels={"处理器核心",cpu.frequencyCap?"调频上限":"最高频率","Android 版本"};String[] values={cpu.cores+(cpu.availableOnly?" 可用核心":" 核"),DeviceInfo.frequency(cpu.maxKhz),Build.VERSION.RELEASE};
        for(int i=0;i<labels.length;i++){LinearLayout tile=Ui.column(this);tile.setPadding(d(20),d(16),d(16),d(16));tile.setBackground(ThemeStyle.panel(this,"settings",0xb0193545,d(18),d(1),Ui.GLASS_LINE));tile.addView(text(labels[i],17,Ui.MUTED));TextView value=text(values[i],32,Ui.WHITE);value.setTypeface(Typeface.DEFAULT,Typeface.BOLD);value.setPadding(0,d(10),0,0);tile.addView(value);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);if(i>0)p.leftMargin=d(12);overview.addView(tile,p);}LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(-1,-2);op.bottomMargin=d(16);body.addView(overview,op);
        LinearLayout processor=panel("处理器 · CPU");row(processor,"处理器 / 芯片",cpu.name);row(processor,"核心数量",cpu.cores+(cpu.availableOnly?" 个当前可用核心（系统未公开总数）":" 个（包含暂时离线核心）"));row(processor,cpu.frequencyCap?"系统调频上限":"硬件最高频率",DeviceInfo.frequency(cpu.maxKhz));row(processor,"指令集 / ABI",DeviceInfo.abi());row(processor,"硬件标识",DeviceInfo.known(Build.HARDWARE));row(processor,"主板",DeviceInfo.known(Build.BOARD));
        LinearLayout memory=panel("运行内存 · RAM");ramTotal=row(memory,"系统总内存","读取中…");ramAvailable=row(memory,"当前可用","读取中…");ramUsed=row(memory,"当前已用","读取中…");ramBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);ramBar.setMax(1000);ramBar.setContentDescription("运行内存使用比例");LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,d(8));bp.topMargin=d(10);bp.bottomMargin=d(8);memory.addView(ramBar,bp);
        if(appMemory!=null){LinearLayout application=panel("整合应用内存 · 按需采样");row(application,"进程合计 PSS",appMemory.total());
            row(application,"已读取进程",appMemory.items.size()+" 个");
            TextView explanation=text("包含系统允许读取的桌面、内置地图、音乐及辅助进程。点击刷新信息重新采样。",14,Ui.MUTED);application.addView(explanation);
        }
        LinearLayout system=panel("设备与系统");row(system,"品牌 / 制造商",DeviceInfo.known(Build.BRAND)+" / "+DeviceInfo.known(Build.MANUFACTURER));row(system,"车机型号",DeviceInfo.known(Build.MODEL));row(system,"Android / API",Build.VERSION.RELEASE+" / API "+Build.VERSION.SDK_INT);row(system,"设备 / 产品",DeviceInfo.known(Build.DEVICE)+" / "+DeviceInfo.known(Build.PRODUCT));row(system,"固件版本",DeviceInfo.known(Build.DISPLAY));row(system,"系统内核",snapshot.kernel);row(system,"固件指纹",DeviceInfo.known(Build.FINGERPRINT));row(system,"运行时长",duration(SystemClock.elapsedRealtime()));
        LinearLayout screen=panel("屏幕与图形");DisplayMetrics real=new DisplayMetrics();getWindowManager().getDefaultDisplay().getRealMetrics(real);row(screen,"屏幕分辨率",real.widthPixels+" × "+real.heightPixels+" px");DisplayMetrics app=getResources().getDisplayMetrics();row(screen,"桌面显示区域",app.widthPixels+" × "+app.heightPixels+" px");row(screen,"系统逻辑密度",real.densityDpi+" dpi");row(screen,"刷新率",String.format(Locale.CHINA,"%.1f Hz",getWindowManager().getDefaultDisplay().getRefreshRate()));ActivityManager manager=(ActivityManager)getSystemService(ACTIVITY_SERVICE);android.content.pm.ConfigurationInfo graphics=manager.getDeviceConfigurationInfo();row(screen,"OpenGL ES 声明",graphics==null?DeviceInfo.UNKNOWN:graphics.getGlEsVersion());
        LinearLayout storage=panel("存储空间");row(storage,"内部数据分区",snapshot.internal);row(storage,"共享存储",snapshot.shared);TextView storageNote=text("共享存储与数据分区可能位于同一块磁盘，容量不相加。",15,Ui.MUTED);storage.addView(storageNote);
        TextView footer=text("WindChaser·MarLous",15,Ui.MUTED);footer.setPadding(d(6),d(4),d(6),d(12));body.addView(footer);status.setText(DeviceInfo.known(Build.MODEL)+"  ·  更新于 "+new SimpleDateFormat("HH:mm:ss",Locale.CHINA).format(new Date()));updateMemory();ThemeStyle.apply(getWindow().getDecorView(),"settingsText");
    }
    private static String duration(long milliseconds){long minutes=milliseconds/60000;return minutes/60+" 小时 "+minutes%60+" 分钟";}
    private void updateMemory(){if(ramTotal==null)return;try{ActivityManager.MemoryInfo info=DeviceInfo.memory(this);long available=Math.max(0,Math.min(info.availMem,info.totalMem));long used=Math.max(0,info.totalMem-available);ramTotal.setText(DeviceInfo.capacity(info.totalMem));ramAvailable.setText(DeviceInfo.capacity(available));ramUsed.setText(DeviceInfo.capacity(used)+(info.lowMemory?" · 系统内存紧张":""));ramBar.setProgress(info.totalMem>0?(int)(used*1000/info.totalMem):0);}catch(RuntimeException unavailable){ramTotal.setText(DeviceInfo.UNKNOWN);ramAvailable.setText(DeviceInfo.UNKNOWN);ramUsed.setText(DeviceInfo.UNKNOWN);}}
    @Override protected void onResume(){super.onResume();visible=true;handler.removeCallbacks(tick);handler.post(tick);Ui.fullscreen(this);}
    @Override protected void onPause(){visible=false;handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){if(defaultBackground!=null){defaultBackground.dispose();defaultBackground=null;}closed=true;handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}
