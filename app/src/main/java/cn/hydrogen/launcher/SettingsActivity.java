package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.concurrent.*;

public class SettingsActivity extends WorkspaceActivity {
    private WallpaperLayer defaultBackground;

    private Config cfg; private LinearLayout body; private final ExecutorService io=Executors.newSingleThreadExecutor();
    private static final int IMAGE=301,VIDEO=302,RESTORE=303;
    private int category;private float unit;
    private static final String[] CATEGORIES={"背景","全局配色","文字与字号","卡片与布局","音乐与歌词","时间与天气","图标与快捷","关联与启动","备份与维护"};
    @Override public void onCreate(Bundle saved){super.onCreate(saved);cfg=new Config(this);category=saved==null?getIntent().getIntExtra("category",0):saved.getInt("category",0);category=Math.max(0,Math.min(CATEGORIES.length-1,category));build();}
    @Override protected void onSaveInstanceState(Bundle state){super.onSaveInstanceState(state);state.putInt("category",category);}
    private float s(float size){return size*unit/getResources().getDisplayMetrics().scaledDensity;}
    private TextView text(String value,int size,int color){return Ui.text(this,value,s(size),color);}
    private void build(){
        unit=Math.max(.72f,Math.min(getResources().getDisplayMetrics().widthPixels/1280f,getResources().getDisplayMetrics().heightPixels/720f));
        LinearLayout root=Ui.column(this);root.setPadding(d(28),d(16),d(28),d(20));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text("‹  返回桌面",17,0xffc2d7e5);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());top.addView(back,new LinearLayout.LayoutParams(d(144),d(52)));
        LinearLayout heading=Ui.column(this);heading.setPadding(d(24),0,0,0);
        heading.addView(text("主题设置",27,Ui.WHITE),new LinearLayout.LayoutParams(-1,d(36)));
        heading.addView(text("让桌面更合你的习惯",13,0xff839caf),new LinearLayout.LayoutParams(-1,d(24)));
        top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        TextView preview=text("查看桌面  ↗",16,0xffbce9dd);preview.setGravity(Gravity.CENTER);preview.setBackground(ThemeStyle.panel(this,"settings",0x2533585b,d(14),d(1),0x305f8c90));preview.setOnClickListener(v->finish());top.addView(preview,new LinearLayout.LayoutParams(d(140),d(46)));
        root.addView(top,new LinearLayout.LayoutParams(-1,d(78)));
        LinearLayout panes=new LinearLayout(this);LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,0,1);pp.topMargin=d(12);root.addView(panes,pp);
        ScrollView navScroll=new ScrollView(this);navScroll.setFillViewport(true);panes.addView(navScroll,new LinearLayout.LayoutParams(d(228),-1));LinearLayout nav=Ui.column(this);nav.setPadding(0,d(12),d(18),0);navScroll.addView(nav);
        TextView caption=text("个 性 化",12,0xff607e94);caption.setPadding(d(18),0,0,0);nav.addView(caption,new LinearLayout.LayoutParams(-1,d(36)));
        for(int n=0;n<CATEGORIES.length;n++){final int selected=n;TextView item=text(CATEGORIES[n],18,n==category?0xffc5f0e3:0xff8fa9bd);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(d(18),0,d(8),0);item.setContentDescription("设置分类："+CATEGORIES[n]);item.setBackground(ThemeStyle.panel(this,"settings",n==category?0x50395963:0x0,d(14),n==category?d(1):0,0x40557e86));item.setOnClickListener(v->{category=selected;build();});LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,-2);item.setMinHeight(d(54));np.bottomMargin=d(6);nav.addView(item,np);}
        
        String release="";try{release=getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception ignored){}
        TextView version=text("WINDCHASER   /   "+release,11,0xff526f85);version.setPadding(d(18),0,0,0);nav.addView(version,new LinearLayout.LayoutParams(-1,d(32)));
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackground(ThemeStyle.panel(this,"settings",0xb0101f2d,d(22),d(1),0x384c6b81));
        body=Ui.column(this);body.setPadding(d(28),d(18),d(28),d(28));scroll.addView(body);panes.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        section(CATEGORIES[category]);
        switch(category){
            case 0:
                note("设置界面使用默认背景。桌面可选择图片或静音循环视频，返回桌面查看实际效果。");
                row("选择背景图片",v->pick("image/*",IMAGE));row("选择背景视频 · 静音循环",v->pick("video/mp4",VIDEO));
                choice("图片显示方式","fit",new String[]{"铺满屏幕","完整显示","原尺寸居中"},0);
                row("预览当前桌面背景",v->preview());row("恢复默认背景",v->{cfg.put("wallpaperKind","");Ui.toast(this,"已恢复默认背景");});break;
            case 1: note("主色、文字、边框和图标统一调整。分区未指定颜色时继承全局配色。颜色支持六位十六进制输入。");themeGroup("配色");break;
            case 2: note("先调整全局大小，再分别调整各区域。100% 为原大小；大字号会减少歌词行数。设置界面、文件中心与弹窗也会跟随主题。");themeGroup("字号");break;
            case 3:
                note("左右卡片与右侧快捷区分别调宽。适配屏幕时按可用尺寸缩放；快捷控件的行列占用仍在桌面长按调整。");
                toggle("快速翻页动画 · 160 毫秒","pageAnimation",true);toggle("交换地图和音乐位置","swap",false);toggle("显示原生小组件标题","titles",true);themeGroup("布局");section("弹窗外观");note("统一调整天气、添加应用、歌曲信息和胎压窗口。长内容在窗口内滚动；地图旁弹窗保持地图亮度，其他页面使用所设周围暗度。");themeGroup("弹窗");break;
            case 4:
                note("分别控制歌曲、歌手、歌词、封面和播放控制栏。正在演唱的歌词保持在可见区域中间，上下选歌词、左右切页和拖动进度继续保留。");themeGroup("音乐");break;
            case 5:row("天气城市与自动定位",v->startActivity(new Intent(this,WeatherSettingsActivity.class)));note("适用于简洁主页、快捷区和所有应用分页。分区颜色留空时继承主要文字或强调色。");themeGroup("天气");break;
            case 6:
                note("调整应用页图标与文字；桌面内置图标随全局图标颜色变化，第三方应用图标保留其原图。");themeGroup("图标");section("底栏快捷应用");
                for(int n=0;n<4;n++){final int slot=n;row("快捷位置 "+(n+1)+" · "+shortcutName(n),v->new ThemeDialog.Builder(this).setTitle("快捷位置 "+(slot+1)).setItems(new String[]{"选择应用","清空"},(dialog,which)->{if(which==0)Ui.pick(this,a->{cfg.put("slot"+slot,Ui.component(a));build();});else{cfg.put("slot"+slot,"");build();}}).show());}break;
            case 7:
                section("地图与音乐关联");note("地图固定使用高德6.5.0.601323卡片适配第七版或更新版本。首次完成高德授权后会保存数据，后续自动恢复卡片。高德内部文字和控件样式由高德自身管理。");
                note(AmapFloatingController.supported(this)?"已检测到新版高德6.5适配模块":"未检测到新版高德6.5适配包；请安装本项目提供的适配第七版或更新版本");
                row("打开高德6.5",v->Ui.launch(this,Config.MAP));toggle("显示高德地图卡片","amapFloating",true);row("高德悬浮窗权限",v->AmapFloatingController.openPermissions(this));
                row("音乐应用 · "+appName(true),v->Ui.pick(this,a->{cfg.put("musicPackage",a.activityInfo.packageName);build();}));choice("音乐卡片内容","musicMode",new String[]{"播放控制与自适应歌词","应用原生小组件"},0);
                section("启动与后台");toggle("启动桌面时启动高德地图和 QQ音乐","autoRecoverApps",true);
                note("配套双启动页测试包：仅本开关触发的自动启动显示图标与 WindChaser·MarLous；手动打开 QQ音乐或高德仍显示各自官方启动页。已在运行时直接恢复应用，不强制重播启动画面。");
                note("自动启动顺序：高德地图 → QQ音乐 → 追风桌面。已就绪的应用直接复用；启动页结束后继续下一步，异常时保留定时兜底。首次登录和授权请先手动完成。");
                toggle("关联应用后台保护","keepCompanions",true);note("随自动启动开关生效，降低应用被回收的概率；正常关机可以结束。ROM深度清理仍由系统决定。");row("车机 ROM 后台保护设置",v->romProtection());
                section("蓝牙音乐关联");
                row("蓝牙音乐应用 · "+bluetoothMusicName(),v->editBluetoothMusic());
                note("选择车机自带的蓝牙音乐应用，关联会自动保存并随设置备份。");
                choice("启动后显示的页面","homePage",new String[]{"第一屏 · 简洁音乐主页","第二屏 · 地图与音乐卡片","第三屏 · 应用列表"},0);toggle("开机进入追风桌面","boot",false);
                row("设为默认桌面",v->{Intent intent=new Intent("android.settings.HOME_SETTINGS");if(getPackageManager().resolveActivity(intent,0)!=null)Ui.open(this,intent);else Ui.toast(this,"请按 HOME 键，选择追风桌面并设为始终使用");});break;
            default:
                note("外观、字号、配色、布局与应用选择统一备份。恢复旧版备份时地图自动使用新版6.5。壁纸文件和系统小组件授权需单独设置。");
                row("导出设置备份",v->{try{ExportProvider.share(this,"config.json",cfg.exportJson(),"导出追风桌面配置");}catch(Exception e){failure("备份导出失败",e);}});row("从备份恢复设置",v->pick("*/*",RESTORE));
                row("恢复所有主题样式",v->new ThemeDialog.Builder(this).setTitle("恢复主题默认样式？").setMessage("恢复配色、字号、卡片与图标尺寸。保留壁纸、应用关联、快捷应用和启动设置。").setPositiveButton("恢复",(dialog,n)->{ThemeOptions.reset(cfg,null);build();}).setNegativeButton("取消",null).show());
                row("安装检查与运行日志",v->startActivity(new Intent(this,DiagnosticsActivity.class)));row("应用容器实验",v->startActivity(new Intent(this,ContainerActivity.class)));row("开源许可 · 中文",v->licenses());break;
        }
        FrameLayout backdrop=new FrameLayout(this);backdrop.setBackgroundColor(0xff08121e);
        defaultBackground=WallpaperLayer.defaultBackground(this,defaultBackground);WallpaperLayer background=defaultBackground;background.setContentDescription("主题设置默认背景");background.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);backdrop.addView(background,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackgroundColor(0x58040d18);backdrop.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        backdrop.addView(root,new FrameLayout.LayoutParams(-1,-1));ThemeStyle.apply(backdrop,"settingsText");setContentView(backdrop);Ui.fullscreen(this);
    }
    private String appName(boolean music){try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(cfg.pkg(music),0)).toString();}catch(Exception e){return music?"QQ音乐":"高德地图";}}
    private String shortcutName(int n){String flat=cfg.s("slot"+n,"");if(flat.isEmpty())return "未设置";try{ComponentName c=ComponentName.unflattenFromString(flat);return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(c==null?flat:c.getPackageName(),0)).toString();}catch(Exception e){return "应用不可用";}}
    private int d(float n){return Math.max(0,Math.round(n*unit));}
    private void section(String name){TextView t=text(name,24,Ui.WHITE);body.addView(t,new LinearLayout.LayoutParams(-1,d(42)));}
    private void note(String value){TextView t=text(value,15,0xff91aabc);t.setPadding(0,d(8),0,d(18));t.setLineSpacing(d(4),1);body.addView(t,new LinearLayout.LayoutParams(-1,-2));}
    private void row(String title,View.OnClickListener click){TextView row=text(title+"  ›",17,0xffd1e1ec);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(18),d(12),d(16),d(12));row.setMinHeight(d(58));row.setBackground(ThemeStyle.panel(this,"settings",0x50304456,d(12),d(1),0x254c6d84));row.setOnClickListener(click);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=d(10);body.addView(row,p);}
    private void toggle(String title,String key,boolean def){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(16),d(10),d(14),d(10));row.setBackground(ThemeStyle.panel(this,"settings",0x30304456,d(12),0,0));
        TextView name=text(title,17,Ui.WHITE);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        ToggleButton state=new ToggleButton(this);state.setTextOn("开启");state.setTextOff("关闭");state.setTextSize(s(14));state.setMinWidth(0);state.setMinimumWidth(0);state.setMinHeight(0);state.setMinimumHeight(0);state.setPadding(0,0,0,0);state.setContentDescription(title);state.setChecked(cfg.b(key,def));styleToggle(state);
        state.setOnCheckedChangeListener((button,on)->{cfg.put(key,on);styleToggle(state);if(key.equals("keepCompanions")||key.equals("autoRecoverApps"))CompanionService.sync(this);});row.setOnClickListener(v->state.setChecked(!state.isChecked()));row.addView(state,new LinearLayout.LayoutParams(d(76),d(36)));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=d(10);body.addView(row,p);
    }
    private void themeGroup(String group){
        for(ThemeOptions.Option o:ThemeOptions.ALL)if(o.group.equals(group)){
            if(o.color){String value=cfg.s(o.key,o.def);colorRow(o,value);}
            else slider(o.label,o.key,o.min,o.max,o.number(),o.key.equals("radius")||o.key.equals("border")||o.key.equals("iconSize")||o.key.equals("fontSize")||o.key.equals("spacing")||o.key.equals("pageInset")||o.key.equals("cardGap")||o.key.equals("dockHeight")?"":"%");
        }
        row("恢复本分类默认样式",v->{ThemeOptions.reset(cfg,group);build();});
    }
    private void colorRow(ThemeOptions.Option o,String value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(d(18),d(12),d(16),d(12));row.setBackground(ThemeStyle.panel(this,"settings",0x50304456,d(12),d(1),0));
        int fallback=ThemeStyle.color(cfg,o.key.endsWith("Surface")?"themeSurface":o.key.endsWith("Border")?"themeBorder":"themeText",Ui.WHITE);
        View swatch=new View(this);swatch.setBackground(Ui.panel(ThemeColors.valid(value)?android.graphics.Color.parseColor(value):fallback,d(8),d(1),0x80ffffff));row.addView(swatch,new LinearLayout.LayoutParams(d(30),d(30)));
        TextView name=text(o.label+" · "+(value.isEmpty()?"跟随全局":value)+"  ›",17,Ui.WHITE);name.setPadding(d(16),0,0,0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.setContentDescription(o.label);row.setOnClickListener(v->customColor(o.key,o.def));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=d(10);body.addView(row,lp);
    }
    private void customColor(String key,String fallback){
        EditText input=new EditText(this);input.setSingleLine();input.setText(cfg.s(key,fallback));input.setHint("#RRGGBB；留空跟随全局");input.setContentDescription("自定义十六进制颜色");
        AlertDialog dialog=new ThemeDialog.Builder(this).setTitle("自定义颜色").setView(input).setPositiveButton("保存",null).setNeutralButton("恢复默认",(d,n)->{cfg.p.edit().remove(key).apply();build();}).setNegativeButton("取消",null).create();dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=input.getText().toString().trim();if(!value.isEmpty()&&!ThemeColors.valid(value)){input.setError("请输入 # 加六位颜色值");return;}cfg.put(key,value.toUpperCase(java.util.Locale.US));dialog.dismiss();build();}));dialog.show();
    }
    private String bluetoothMusicName(){
        String flat=cfg.s(Config.BLUETOOTH_MUSIC_COMPONENT,"");if(flat.isEmpty())return "未关联";
        try{ComponentName component=ComponentName.unflattenFromString(flat);if(component==null)return "应用不可用，请重新选择";
            android.content.pm.ActivityInfo info=getPackageManager().getActivityInfo(component,0);
            if(!info.enabled||!info.applicationInfo.enabled)return "应用已停用，请重新选择";
            return info.loadLabel(getPackageManager()).toString();
        }catch(android.content.pm.PackageManager.NameNotFoundException e){return "应用已卸载，请重新选择";}
    }
    private void chooseBluetoothMusic(){
        Ui.pick(this,app->{cfg.put(Config.BLUETOOTH_MUSIC_COMPONENT,Ui.component(app));build();});
    }
    private void editBluetoothMusic(){
        if(cfg.s(Config.BLUETOOTH_MUSIC_COMPONENT,"").isEmpty()){chooseBluetoothMusic();return;}
        new ThemeDialog.Builder(this).setTitle("蓝牙音乐应用").setItems(new String[]{"更换关联应用","清除关联"},(dialog,which)->{
            if(which==0)chooseBluetoothMusic();else{cfg.put(Config.BLUETOOTH_MUSIC_COMPONENT,"");build();}
        }).setNegativeButton("取消",null).show();
    }
    private void romProtection(){
        String[] actions={"查看设置说明","追风桌面 · 应用信息","QQ音乐 · 应用信息","高德地图 · 应用信息","系统应用 / 自启动管理","电量与后台管理","开发者选项"};
        new ThemeDialog.Builder(this).setTitle("车机 ROM 后台保护").setItems(actions,(dialog,n)->{
            if(n==0){new ThemeDialog.Builder(this).setTitle("建议在 ROM 中设置").setMessage("请对追风桌面、QQ音乐、高德地图分别设置：\n\n1. 允许自启动、关联启动和后台运行。\n2. 加入省电、内存清理、休眠的白名单。\n3. 若有最近任务锁定，锁定这三个应用。\n4. 开发者选项：后台进程限制保持“标准限制”，“不保留活动”关闭。\n\nAndroid 4.4.2 没有统一的后台白名单接口，名称和入口由车机厂商决定；普通 APK 不能代替 ROM 授权。若系统未提供相应开关，需由固件厂商支持。\n\n正常关机时由系统停止这些应用。熄火后的休眠策略仍由车机 ROM 决定，桌面不持有唤醒锁。") .setPositiveButton("知道了",null).show();return;}
            if(n<=3){String pkg=n==1?getPackageName():cfg.pkg(n==2);Ui.open(this,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+pkg)));return;}
            String action=n==4?Settings.ACTION_APPLICATION_SETTINGS:n==5?(android.os.Build.VERSION.SDK_INT>=23?"android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS":Settings.ACTION_BATTERY_SAVER_SETTINGS):Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS;
            Intent intent=new Intent(action);if(getPackageManager().resolveActivity(intent,0)==null){Ui.toast(this,"此 ROM 没有标准入口，请在系统设置中查找后台、自启动或省电管理");intent=new Intent(Settings.ACTION_SETTINGS);}Ui.open(this,intent);
        }).setNegativeButton("关闭",null).show();
    }
    private void styleToggle(ToggleButton state){state.setTextColor(state.isChecked()?0xffc1eddf:0xff91aabc);state.setBackground(ThemeStyle.panel(this,"settings",state.isChecked()?0x70547774:0x403e5264,d(18),0,0));}
    private void choice(String title,String key,String[] names,int def){int value=Math.max(0,Math.min(names.length-1,cfg.n(key,def)));row(title+" · "+names[value],v->new ThemeDialog.Builder(this).setTitle(title).setSingleChoiceItems(names,value,(dialog,n)->{cfg.put(key,n);dialog.dismiss();build();}).setNegativeButton("取消",null).show());}
    private void slider(String title,String key,int min,int max,int def,String suffix){
        TextView label=text(title+" · "+cfg.n(key,def)+suffix,16,0xffc2d7e5);body.addView(label,new LinearLayout.LayoutParams(-1,-2));
        SeekBar seek=new SeekBar(this);seek.setMax(max-min);seek.setProgress(cfg.n(key,def)-min);seek.setContentDescription(title);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){ThemeStyle.apply(getWindow().getDecorView(),"settingsText");}public void onProgressChanged(SeekBar b,int value,boolean user){if(user){cfg.put(key,min+value);label.setText(title+" · "+(min+value)+suffix);}}});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,d(44));p.bottomMargin=d(12);body.addView(seek,p);
    }
    private void licenses(){showLicense("开源许可 · 中文说明",new String[]{"licenses-zh.txt"},true);}
    private void showLicense(String title,String[] files,boolean chinese){try(ByteArrayOutputStream out=new ByteArrayOutputStream()){for(String name:files)try(InputStream in=getAssets().open(name)){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.write('\n');}ScrollView scroll=new ScrollView(this);TextView label=text(out.toString("UTF-8"),chinese?16:13,Ui.WHITE);label.setPadding(d(22),d(16),d(22),d(16));label.setLineSpacing(d(6),1);label.setTextIsSelectable(true);label.setContentDescription(chinese?"开源许可中文说明":"开源许可原文");scroll.addView(label);AlertDialog.Builder dialog=new ThemeDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭",null);if(chinese)dialog.setNeutralButton("查看许可原文",(d,n)->showLicense("开源许可 · 原文",new String[]{"third-party-licenses.txt","container-licenses.txt"},false));dialog.show();}catch(Exception e){failure("许可文件无法读取",e);}}
    private void pick(String type,int request){try{startActivityForResult(new Intent(this,FileCenterActivity.class).putExtra(FileCenterActivity.PICK,true).putExtra(FileCenterActivity.MIME,type),request);}catch(Exception e){failure("车机没有可用的文件选择器，请先安装文件管理器",e);}}
    private void preview(){
        final WallpaperLayer layer=new WallpaperLayer(this);Dialog dialog=new Dialog(this);dialog.setContentView(layer);android.view.Window w=dialog.getWindow();if(w!=null)w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.85),(int)(getResources().getDisplayMetrics().heightPixels*.8));
        dialog.setOnShowListener(d->layer.resume());dialog.setOnDismissListener(d->layer.dispose());dialog.show();MapWorkspace.get(this).watch(dialog);if(w!=null)w.setLayout((int)(getResources().getDisplayMetrics().widthPixels*.85),(int)(getResources().getDisplayMetrics().heightPixels*.8));
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;
        if(request!=IMAGE&&request!=VIDEO&&request!=RESTORE)return;
        Ui.toast(this,"正在读取文件…");
        io.execute(()->{
            File tmp=null;
            try(InputStream in=getContentResolver().openInputStream(data.getData())){
                if(in==null)throw new IOException("文件无法读取");
                if(request==RESTORE){ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out,256*1024);cfg.restore(out.toString("UTF-8"));runOnUiThread(()->{if(!isFinishing()){build();Ui.toast(this,"配置已恢复，返回桌面后生效");}});return;}
                tmp=new File(getFilesDir(),"wallpaper.incoming");try(FileOutputStream out=new FileOutputStream(tmp)){copy(in,out,request==IMAGE?64L*1024*1024:256L*1024*1024);}
                if(request==IMAGE){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(tmp.toString(),o);if(o.outWidth<=0||o.outHeight<=0)throw new IOException("不是可解码的图片");}
                else {MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(tmp.toString());if(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)==null)throw new IOException("文件没有可播放的视频轨道");}finally{m.release();}}
                File target=new File(getFilesDir(),"wallpaper"),old=new File(getFilesDir(),"wallpaper.previous");
                if(old.exists()&&!old.delete())throw new IOException("无法清理旧壁纸");
                if(target.exists()&&!target.renameTo(old))throw new IOException("无法替换壁纸");
                if(!tmp.renameTo(target)){old.renameTo(target);throw new IOException("无法保存壁纸");}
                if(old.exists())old.delete();cfg.put("wallpaperKind",request==IMAGE?"image":"video");
                LauncherApp.record("壁纸导入成功 type="+request,null);runOnUiThread(()->Ui.toast(this,"背景已保存，可点击背景预览；返回桌面后生效"));
            }catch(Exception|OutOfMemoryError e){if(tmp!=null)tmp.delete();runOnUiThread(()->failure("读取失败："+e.getMessage(),e));}
        });
    }
    private static void copy(InputStream in,OutputStream out,long limit)throws IOException{byte[] b=new byte[32768];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>limit)throw new IOException("文件过大，超过 "+limit/1024/1024+" MB 限制");out.write(b,0,n);}}
    private void failure(String text,Throwable e){LauncherApp.record(text,e);if(!isFinishing())Ui.toast(this,text);}
    @Override protected void onDestroy(){if(defaultBackground!=null){defaultBackground.dispose();defaultBackground=null;}io.shutdown();super.onDestroy();}
}
