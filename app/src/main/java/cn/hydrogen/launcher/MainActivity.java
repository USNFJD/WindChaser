package cn.hydrogen.launcher;

import android.app.*;
import android.appwidget.*;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.*;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.util.LruCache;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends WorkspaceActivity {
    static final int MINIMAL=0,CARDS=1,APPS=2;
    static final float SECOND_PAGE_RADIUS_SCALE=.6f;
    private Config cfg; private WidgetCoordinator widgets; private WallpaperLayer wallpaper;
    private AmapFloatingController floatingMap; private TextView floatingMapStatus;private View floatingMapSetup;
    private boolean mapPlaceholderScheduled;
    private MapDialogHost mapDialogs;
    private View muteButton;private TextView muteLabel;private AlertDialog tirePressureDialog;
    boolean mapDialogEligible(){return resumed&&!leaving&&!destroyed&&currentPage==CARDS;}
    boolean mapCardConnected(){return floatingMap!=null&&floatingMap.connected();}
    void prepareMapDialog(Dialog dialog){if(mapDialogs!=null)mapDialogs.prepare(dialog);}
    void mapDialogShowing(Dialog dialog){if(mapDialogs!=null)mapDialogs.showing(dialog);}
    void mapDialogFailed(Dialog dialog){if(mapDialogs!=null)mapDialogs.failed(dialog);}
    void mapDialogShown(Dialog dialog){if(mapDialogs!=null)mapDialogs.shown(dialog);}
    android.graphics.Rect mapDialogArea(){
        if(root==null||floatingMap==null)return null;
        View anchor=findMapAnchor(root);if(anchor==null||!anchor.isShown()||anchor.getWidth()<2)return null;
        int[] origin=new int[2],xy=new int[2];root.getLocationOnScreen(origin);anchor.getLocationOnScreen(xy);
        int pad=Math.max(8,u(12)),left=origin[0]+pad,right=origin[0]+root.getWidth()-pad;
        int mapLeft=xy[0],mapRight=xy[0]+anchor.getWidth();
        // The wider side also covers the user's swapped map/music arrangement.
        if(right-mapRight>=mapLeft-left)left=mapRight+pad;else right=mapLeft-pad;
        return new android.graphics.Rect(left,origin[1]+pad,right,origin[1]+root.getHeight()-pad);
    }
    private View findMapAnchor(View view){
        if("高德悬浮地图区域".contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int n=0;n<group.getChildCount();n++){View found=findMapAnchor(group.getChildAt(n));if(found!=null)return found;}}return null;
    }
    boolean mapWorkspaceFocus(){return resumed&&!leaving&&!destroyed&&(getWindow().getDecorView().hasWindowFocus()||(mapDialogs!=null&&mapDialogs.focused()));}
    private boolean mapHostFocused(){return hostFocused||(mapDialogs!=null&&mapDialogs.focused());}
    private final Runnable revealMapPlaceholder=()->{if(floatingMapSetup!=null&&this.resumed&&!this.leaving&&this.currentPage==CARDS&&!floatingMap.connected())floatingMapSetup.setVisibility(View.VISIBLE);};
    private ScreenLayout screenLayout;
    private StartupApps startupApps;private ResponsiveRoot root;private Map<String,Object> renderedSettings;
    private String wallpaperKey="";private boolean appsLoaded,appsLoading,appsDirty=true;
    private int renderCount,appScanCount,renderedWidth,renderedHeight,packageGeneration,renderedPackages=-1;
    private int appsRevision,shownAppsRevision=-1,shownAppsOffset=-1,shownAppsCapacity=-1,shownAppsCount=-1;
    private boolean destroyed;
    private static final android.view.animation.Interpolator PAGE_EASE=new android.view.animation.DecelerateInterpolator();
    private final Handler layoutHandler=new Handler(Looper.getMainLooper());
    private final Runnable resize=()->{if(this.resumed&&!isFinishing())render();};
    private boolean layoutReady;
    private FrameLayout minimalPage; private LinearLayout standardContent; private TextView minimalClock,minimalDate,minimalStatus; private QqMusicBridge musicBridge;private MusicSession.Lease musicLease;private QqMusicBridge renderedBridge; private final ArrayList<MusicCard> musicCards=new ArrayList<>();
    private WeatherController weather;private WeatherPanel weatherPanel;private WeatherChip quickWeather,headerWeather;
    private ThemeColors colors;private View animatedPage;private boolean transitioning;private android.animation.ValueAnimator pageAnimator;
    private int pageAnimationStarts;
    private FrameLayout page; private LinearLayout home,topBar,dock; private GridView grid; private TextView clock,date,status,quickClock,quickDate;
    private final ArrayList<ResolveInfo> applications=new ArrayList<>();
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final LruCache<String,Drawable> iconCache=new LruCache<>(48);
    private int currentPage,version,appsPerPage=18; private float scale=1; private boolean registered,resumed,leaving,hostFocused;
    private final BroadcastReceiver system=new BroadcastReceiver() { @Override public void onReceive(Context c,Intent i) { if("android.media.VOLUME_CHANGED_ACTION".equals(i.getAction())){updateMute();return;}if(floatingMap!=null){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction())){floatingMap.setScreenOn(false);if(mapDialogs!=null)mapDialogs.dismissAll();}if(Intent.ACTION_SCREEN_ON.equals(i.getAction()))floatingMap.setScreenOn(true);}updateStatus(); } };
    private final BroadcastReceiver packages=new BroadcastReceiver() { @Override public void onReceive(Context c,Intent i) { LauncherApp.record("应用变化 "+i.getAction()+" "+i.getData(),null); LinkedAppCoordinator.get(MainActivity.this).packagesChanged();packageGeneration++;iconCache.evictAll();appsDirty=true; loadApps(); render(); } };
    private boolean unifiedRedirected;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); if(!UnifiedRuntime.ready()||UnifiedInitService.required(this)){unifiedRedirected=true;startActivity(new Intent(this,UnifiedBootstrapActivity.class));finish();return;} DesktopReturnReceiver.attach(this);cfg=new Config(this);cfg.applyGlassStyle(); widgets=new WidgetCoordinator(this,cfg); musicLease=MusicSession.acquire(this,this::updateMusic);musicBridge=musicLease.bridge();weather=new WeatherController(this,this::updateWeather);
        AmapFloatingController.autoSelect(this,cfg);floatingMap=MapWorkspace.get(this).controller(this,cfg);mapDialogs=new MapDialogHost(this,this::updateFloatingHost);floatingMap.setDialogFocus(mapDialogs::focused);
        startupApps=new StartupApps(this,cfg);
        floatingMap.setChanged(()->{if(floatingMapStatus!=null)floatingMapStatus.setText(floatingMap.status());updateMapPlaceholder();});
        currentPage=state==null?(getIntent().getBooleanExtra(StartupApps.RETURN,false)?getIntent().getIntExtra(StartupApps.PAGE,cfg.n("homePage",0)):cfg.n("homePage",0)):state.getInt("page",0);
        screenLayout=new ScreenLayout(getResources().getDisplayMetrics().widthPixels,getResources().getDisplayMetrics().heightPixels);scale=screenLayout.unit;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); render(); loadApps();
        IntentFilter f=new IntentFilter(Intent.ACTION_TIME_TICK); f.addAction(Intent.ACTION_TIME_CHANGED); f.addAction(Intent.ACTION_TIMEZONE_CHANGED); f.addAction(Intent.ACTION_DATE_CHANGED); f.addAction(Intent.ACTION_SCREEN_OFF);f.addAction(Intent.ACTION_SCREEN_ON);f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION); f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED); f.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);f.addAction("android.media.VOLUME_CHANGED_ACTION"); registerReceiver(system,f);
        IntentFilter p=new IntentFilter(Intent.ACTION_PACKAGE_ADDED); p.addAction(Intent.ACTION_PACKAGE_REMOVED); p.addAction(Intent.ACTION_PACKAGE_CHANGED); p.addDataScheme("package"); registerReceiver(packages,p); registered=true;
        LauncherApp.record("桌面启动；仅使用新版高德6.5卡片窗口接口",null);
    }
    int u(float value) { return Math.max(0,Math.round(value*scale)); }
    float cardRadius(float value) { return u(value)*SECOND_PAGE_RADIUS_SCALE; }
    float sp(float value) { return value*scale/getResources().getDisplayMetrics().scaledDensity; }
    private TextView label(String text,int size,int color) { return Ui.text(this,text,sp(size),color==Ui.WHITE&&colors!=null?colors.text:color); }
    public void render() {
        if(isFinishing()||destroyed)return;
        AmapFloatingController.autoSelect(this,cfg);
        java.io.File selected=new java.io.File(getFilesDir(),"wallpaper");
        String selectedKey=cfg.s("wallpaperKind","")+"/"+cfg.n("fit",0)+"/"+selected.lastModified()+"/"+selected.length();
        if(root!=null&&musicBridge==renderedBridge&&renderedWidth==screenLayout.width&&renderedHeight==screenLayout.height&&renderedPackages==packageGeneration
                &&cfg.visualState().equals(renderedSettings)&&selectedKey.equals(wallpaperKey)&&!widgets.waiting()){
            showPage(currentPage);updateStatus();updateMusic();if(resumed&&wallpaper!=null)wallpaper.resume();return;
        }
        layoutReady=false;if(root!=null)root.resetGeometry();updateFloatingHost();
        renderCount++;
        cancelPageAnimation();colors=new ThemeColors(cfg);quickClock=quickDate=null;quickWeather=headerWeather=null;
        AmapFloatingController.autoSelect(this,cfg);
        if(floatingMap!=null)floatingMap.bind(null);floatingMapStatus=null;floatingMapSetup=null;mapPlaceholderScheduled=false;layoutHandler.removeCallbacks(revealMapPlaceholder);
        for(MusicCard card:musicCards)card.release();musicCards.clear();shownAppsRevision=-1;
        boolean first=root==null;if(first)root=new ResponsiveRoot();
        java.io.File file=new java.io.File(getFilesDir(),"wallpaper");
        String nextWallpaper=cfg.s("wallpaperKind","")+"/"+cfg.n("fit",0)+"/"+file.lastModified()+"/"+file.length();
        while(root.getChildCount()>1)root.removeViewAt(1);
        if(wallpaper==null||!nextWallpaper.equals(wallpaperKey)){
            if(wallpaper!=null){root.removeView(wallpaper);wallpaper.dispose();}
            wallpaper=new WallpaperLayer(this);root.addView(wallpaper,0,new FrameLayout.LayoutParams(-1,-1));wallpaperKey=nextWallpaper;
        }
        LinearLayout content=Ui.column(this); standardContent=content; content.setPadding(u(cfg.n("pageInset",20)),0,u(cfg.n("pageInset",20)),u(14)); root.addView(content,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);topBar=top;top.setContentDescription("顶部时间与设备状态"); top.setBaselineAligned(false);top.setGravity(Gravity.CENTER_VERTICAL); top.setPadding(u(8),0,u(8),0);
        clock=new ClockTextView(this,40);clock.setGravity(Gravity.CENTER_VERTICAL);clock.setIncludeFontPadding(false); top.addView(clock,new LinearLayout.LayoutParams(u(165),-1));
        LinearLayout dateWeather=new LinearLayout(this);dateWeather.setBaselineAligned(false);dateWeather.setGravity(Gravity.CENTER_VERTICAL);dateWeather.setContentDescription("应用页日期与天气");
        date=ThemeStyle.role(label("",20,Ui.WHITE),"date");date.setSingleLine();date.setGravity(Gravity.CENTER_VERTICAL);date.setIncludeFontPadding(false);dateWeather.addView(date,new LinearLayout.LayoutParams(-2,-1));
        headerWeather=new WeatherChip(this,weather,false,"应用页天气");LinearLayout.LayoutParams hw=new LinearLayout.LayoutParams(0,u(40),1);hw.leftMargin=u(16);dateWeather.addView(headerWeather,hw);top.addView(dateWeather,new LinearLayout.LayoutParams(0,-1,1));
        status=ThemeStyle.role(label("",16,Ui.MUTED),"statusText"); status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);status.setContentDescription("蓝牙和网络状态"); top.addView(status,new LinearLayout.LayoutParams(u(300),-1));
        top.setOnTouchListener(new SwipeTouch()); content.addView(top,new LinearLayout.LayoutParams(-1,u(90)));
        page=new SwipeFrame(); content.addView(page,new LinearLayout.LayoutParams(-1,0,1));
        home=new LinearLayout(this); home.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout pair=new LinearLayout(this); int share=cfg.n("mapShare",60);
        View map=card(false),music=card(true); boolean swap=cfg.b("swap",false);
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(0,-1,share); mp.rightMargin=u(cfg.n("cardGap",10));
        LinearLayout.LayoutParams qp=new LinearLayout.LayoutParams(0,-1,100-share);
        if(swap) { pair.addView(music,new LinearLayout.LayoutParams(0,-1,100-share)); LinearLayout.LayoutParams m=new LinearLayout.LayoutParams(0,-1,share); m.leftMargin=u(cfg.n("cardGap",10)); pair.addView(map,m); }
        else { pair.addView(map,mp); pair.addView(music,qp); }
        home.addView(pair,new LinearLayout.LayoutParams(0,-1,100));
        QuickPanel quick=quickPanel();LinearLayout.LayoutParams quickParams=new LinearLayout.LayoutParams(0,-1,26*cfg.n("quickWidth",100)/100f);quickParams.leftMargin=u(cfg.n("cardGap",10)+2);home.addView(quick,quickParams);
        page.addView(home,new FrameLayout.LayoutParams(-1,-1));
        grid=new GridView(this){private final Runnable paginate=()->{if(!destroyed&&grid==this)updateAppPagination(getHeight());};@Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);removeCallbacks(paginate);if(h>0)post(paginate);}@Override protected void onDetachedFromWindow(){removeCallbacks(paginate);super.onDetachedFromWindow();}}; grid.setNumColumns(screenLayout.gridColumns()); grid.setGravity(Gravity.CENTER); grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(u(cfg.n("spacing",18)));grid.setHorizontalSpacing(u(cfg.n("spacing",18)));grid.setPadding(u(20),u(15),u(20),u(4)); grid.setClipToPadding(false); grid.setSelector(android.R.color.transparent);
        grid.setVerticalScrollBarEnabled(false);
        grid.setAdapter(new AppAdapter()); grid.setOnItemClickListener((parent,view,pos,id)->Ui.launch(this,Ui.component((ResolveInfo)parent.getItemAtPosition(pos))));
        grid.setOnItemLongClickListener((parent,view,pos,id)->{ appMenu((ResolveInfo)parent.getItemAtPosition(pos));return true; });
        page.addView(grid,new FrameLayout.LayoutParams(-1,-1));
        View breathingSpace=new View(this);content.addView(breathingSpace,new LinearLayout.LayoutParams(-1,u(12)));
        dock=ThemeStyle.role(new LinearLayout(this),"dockText"); dock.setPadding(u(10),u(9),u(10),u(9));
        dock.setBackground(ThemeStyle.panel(this,"dock",colors.panel(cfg.n("dockAlpha",Config.DOCK_ALPHA)*255/100),u(22),u(1),Ui.GLASS_LINE));
        String[] icons={"home","down","mute","up","tpms","theme"};String[] names={"主页","音量减","静音","音量加","胎压","主题设置"};int[] actions={0,1,2,3,5,4};
        for(int i=0;i<icons.length;i++) { final int n=actions[i]; View b=dockButton(icons[i],names[i],n==0?0x482c638a:n==4?0x483e3759:0x20263e53,null);b.setOnClickListener(v->fixed(n));if(n==2){muteButton=b;muteLabel=(TextView)((ViewGroup)b).getChildAt(1);} dock.addView(b,dockParams()); }
        for(int i=0;i<4;i++) {
            final int slot=i; String value=cfg.s("slot"+i,""); ResolveInfo info=resolve(value);
            View b=dockButton("plus",info==null?"添加应用":info.loadLabel(getPackageManager()).toString(),0x18263e53,info);
            b.setOnClickListener(v->{ if(info==null) Ui.pick(this,a->{cfg.put("slot"+slot,Ui.component(a));render();}); else Ui.launch(this,value); });
            b.setOnLongClickListener(v->{ shortcutMenu(slot);return true; }); dock.addView(b,dockParams());
        }
        content.addView(dock,new LinearLayout.LayoutParams(-1,u(cfg.n("dockHeight",94))));
        minimalPage=new SwipeFrame();
        LinearLayout ambient=Ui.column(this);ambient.setPadding(u(76),u(32),u(76),u(28));
        LinearLayout miniTop=new LinearLayout(this);miniTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView wordmark=Ui.text(this,"W I N D C H A S E R ·MarLous",sp(15.6f),0xff829fb6);wordmark.setMaxLines(1);wordmark.setContentDescription("WINDCHASER品牌标识");miniTop.addView(wordmark,new LinearLayout.LayoutParams(0,u(48),1));
        String[] links={"卡片","应用","设置"};for(int n=0;n<3;n++){final int index=n;TextView link=label(links[n],15,0xffbed1df);link.setGravity(Gravity.CENTER);link.setContentDescription("简洁页"+links[n]);link.setOnClickListener(v->{if(index==2)fixed(4);else showPage(index==0?CARDS:APPS);});miniTop.addView(link,new LinearLayout.LayoutParams(u(76),u(48)));}ambient.addView(miniTop);
        LinearLayout calm=new LinearLayout(this);calm.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left=Ui.column(this);left.setGravity(Gravity.CENTER_VERTICAL);
        minimalClock=new ClockTextView(this,120);minimalClock.setIncludeFontPadding(false);minimalClock.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));minimalClock.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);minimalClock.setContentDescription("时间，长按打开主题设置");minimalClock.setOnLongClickListener(v->{fixed(4);return true;});left.addView(minimalClock,new LinearLayout.LayoutParams(-1,u(140)));
        minimalDate=ThemeStyle.role(label("",21,0xffcbdce8),"date");left.addView(minimalDate,new LinearLayout.LayoutParams(-1,u(40)));
        weatherPanel=new WeatherPanel(this,weather);LinearLayout.LayoutParams weatherSpace=new LinearLayout.LayoutParams(-1,u(224));weatherSpace.topMargin=u(42);left.addView(weatherPanel,weatherSpace);
        calm.addView(left,new LinearLayout.LayoutParams(0,-1,43));
        int musicWidth=cfg.n("minimalMusicWidth",100);View calmMusic=nativeMusic(true);
        if(musicWidth<100){LinearLayout lane=new LinearLayout(this);lane.addView(new View(this),new LinearLayout.LayoutParams(0,-1,100-musicWidth));lane.addView(calmMusic,new LinearLayout.LayoutParams(0,-1,musicWidth));calmMusic=lane;}
        LinearLayout.LayoutParams calmParams=new LinearLayout.LayoutParams(0,-1,57);calmParams.leftMargin=u(54);calm.addView(calmMusic,calmParams);
        ambient.addView(calm,new LinearLayout.LayoutParams(-1,0,1));
        minimalStatus=label("",12,0xff849eb1);minimalStatus.setGravity(Gravity.CENTER_VERTICAL);ambient.addView(minimalStatus,new LinearLayout.LayoutParams(-1,u(32)));
        minimalPage.addView(ambient,new FrameLayout.LayoutParams(-1,-1));root.addView(minimalPage,new FrameLayout.LayoutParams(-1,-1));
        ThemeStyle.apply(root,"cardText");if(first)setContentView(root);showPage(currentPage); updateStatus(); Ui.fullscreen(this);renderedSettings=cfg.visualState();renderedWidth=screenLayout.width;renderedHeight=screenLayout.height;renderedPackages=packageGeneration;renderedBridge=musicBridge;
        if(resumed) wallpaper.resume();updateFloatingHost();
    }
    private LinearLayout.LayoutParams dockParams() { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.leftMargin=u(4);p.rightMargin=u(4);return p; }
    private View dockButton(String kind,String name,int color,ResolveInfo info) {
        LinearLayout b=Ui.column(this);b.setGravity(Gravity.CENTER);b.setBackground(ThemeStyle.panel(this,"dock",color,u(14),u(1),0x244f7289));
        View icon;
        if(info!=null) { ImageView i=new ImageView(this);i.setImageDrawable(icon(info));i.setScaleType(ImageView.ScaleType.FIT_CENTER);icon=i; } else icon=new Icons(this,kind);
        b.addView(icon,new LinearLayout.LayoutParams(u(30),u(30)));TextView t=label(name,14,Ui.WHITE);t.setGravity(Gravity.CENTER);t.setSingleLine(true);b.addView(t,new LinearLayout.LayoutParams(-1,u(25)));b.setContentDescription(name);return b;
    }
    private View quick(String icon,String title,String subtitle,int color,View.OnClickListener action,boolean compact) {
        LinearLayout v=Ui.column(this);v.setGravity(Gravity.CENTER);v.setBackground(ThemeStyle.panel(this,"quick",color,cardRadius(22),u(1),Ui.GLASS_LINE));
        v.addView(new Icons(this,icon),new LinearLayout.LayoutParams(u(compact?34:48),u(compact?34:48)));
        TextView t=label(title,compact?17:20,Ui.WHITE);t.setGravity(Gravity.CENTER);t.setSingleLine();t.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,u(34));p.topMargin=u(8);v.addView(t,p);
        if(!subtitle.isEmpty()){TextView sub=label(subtitle,compact?12:14,0xffa2bccb);sub.setGravity(Gravity.CENTER);sub.setSingleLine();v.addView(sub,new LinearLayout.LayoutParams(-1,u(24)));}v.setOnClickListener(action);v.setContentDescription(title);return v;
    }
    private View nativeMusic(boolean minimal){MusicCard card=new MusicCard(this,musicBridge,minimal,()->cardMenu(true));musicCards.add(card);return card;}
    private QuickPanel quickPanel(){
        QuickPanel panel=ThemeStyle.role(new QuickPanel(this),"quickText");
        for(QuickGrid.Cell cell:QuickGrid.cells(cfg)){
            View tile;
            if(cell.id.equals("clock")){
                ClockWeatherTile clockTile=new ClockWeatherTile(this,cfg,weather);quickClock=clockTile.time;quickDate=clockTile.date;quickWeather=clockTile.weather;tile=clockTile;
            }else if(cell.id.equals("phone"))tile=quick("phone","蓝牙电话","",colors.panel(80),v->phone(),true);
            else if(cell.id.equals("apps"))tile=quick("apps","应用列表","",colors.panel(80),v->showPage(APPS),true);
            else if(cell.id.equals("settings"))tile=quick("settings","系统设置","",colors.panel(80),v->Ui.open(this,new Intent(Settings.ACTION_SETTINGS)),true);
            else{
                int slot=cell.id.endsWith("0")?0:1;String value=cfg.s("quickSlot"+slot,"");ResolveInfo app=resolve(value);
                LinearLayout shortcut=Ui.column(this);shortcut.setGravity(Gravity.CENTER);shortcut.setBackground(ThemeStyle.panel(this,"quick",colors.panel(64),cardRadius(20),u(1),Ui.GLASS_LINE));
                if(app==null)shortcut.addView(new Icons(this,"plus"),new LinearLayout.LayoutParams(u(30),u(30)));else{ImageView icon=new ImageView(this);icon.setImageDrawable(icon(app));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);shortcut.addView(icon,new LinearLayout.LayoutParams(u(34),u(34)));}
                TextView name=label(app==null?"添加应用":app.loadLabel(getPackageManager()).toString(),15,0xffc6dce8);name.setGravity(Gravity.CENTER);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);shortcut.addView(name,new LinearLayout.LayoutParams(-1,u(32)));shortcut.setContentDescription("快捷区自定义应用"+(slot+1));
                shortcut.setOnClickListener(v->{if(app==null)pickQuickApp(slot);else Ui.launch(this,value);});tile=shortcut;
            }
            tile.setTag("quick-"+cell.id);tile.setOnLongClickListener(v->{quickMenu(cell.id);return true;});panel.addView(tile,new QuickPanel.TileParams(cell));
        }return panel;
    }
    private void pickQuickApp(int slot){Ui.pick(this,app->{String old=cfg.s("quickSlot"+slot,"");cfg.put("quickSlot"+slot,Ui.component(app));try{cfg.put("quickLayout",QuickGrid.resize(cfg,"shortcut"+slot,1,1));render();}catch(Exception error){cfg.put("quickSlot"+slot,old);Ui.toast(this,"快捷区空间不足，请先缩小其他控件");}});}
    private void quickMenu(String id){
        boolean custom=id.startsWith("shortcut");String[] actions=custom?new String[]{"调整占用行列","选择 / 更换应用","清空应用","恢复默认快捷区布局"}:new String[]{"调整占用行列","恢复默认快捷区布局"};
        new ThemeDialog.Builder(this).setTitle("快捷控件").setItems(actions,(dialog,n)->{
            if(n==0){String[] sizes=new String[QuickGrid.ROWS*QuickGrid.COLUMNS];for(int i=0;i<sizes.length;i++)sizes[i]=(i/QuickGrid.COLUMNS+1)+"行 × "+(i%QuickGrid.COLUMNS+1)+"列";new ThemeDialog.Builder(this).setTitle("占用行列 · 五行两列网格").setItems(sizes,(d,selected)->{try{cfg.put("quickLayout",QuickGrid.resize(cfg,id,selected/QuickGrid.COLUMNS+1,selected%QuickGrid.COLUMNS+1));render();}catch(Exception error){Ui.toast(this,error.getMessage());}}).setNegativeButton("取消",null).show();}
            else if(custom&&n==1)pickQuickApp(id.endsWith("0")?0:1);
            else if(custom&&n==2){cfg.put("quickSlot"+(id.endsWith("0")?0:1),"");render();}
            else{cfg.put("quickLayout","");render();}
        }).setNegativeButton("取消",null).show();
    }
    private View card(boolean music) {
        if(!music)return floatingMapCard();
        if(music && Config.MUSIC.equals(cfg.pkg(true)) && cfg.n("musicMode",0)==0)return nativeMusic(false);
        String pkg=cfg.pkg(music); boolean installed=Ui.installed(this,pkg);
        LinearLayout card=ThemeStyle.role(Ui.column(this),"cardText"); card.setPadding(u(12),u(10),u(12),u(10));
        card.setBackground(ThemeStyle.panel(this,"map",colors.panel(cfg.n("cardAlpha",Config.CARD_ALPHA)*255/100),cardRadius(20),u(1),ThemeStyle.border(cfg,"map")));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView appIcon=new ImageView(this);
        if(installed) try { appIcon.setImageDrawable(getPackageManager().getApplicationIcon(pkg)); }catch(Exception ignored){}
        if(installed) header.addView(appIcon,new LinearLayout.LayoutParams(u(40),u(40)));
        else header.addView(new Icons(this,music?"music":"map"),new LinearLayout.LayoutParams(u(34),u(34)));
        String title=music?"QQ音乐":"高德地图"; if(installed) try{title=getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception ignored){}
        TextView name=label("  "+title,24,Ui.WHITE);name.setSingleLine(true);header.addView(name,new LinearLayout.LayoutParams(0,u(50),1));
        TextView open=label("打开",17,Ui.MUTED);open.setGravity(Gravity.CENTER);open.setOnClickListener(v->Ui.launch(this,pkg));header.addView(open,new LinearLayout.LayoutParams(u(60),u(48)));
        TextView menu=label("•••",24,Ui.WHITE);menu.setGravity(Gravity.CENTER);menu.setContentDescription(music?"音乐卡片菜单":"地图卡片菜单");menu.setOnClickListener(v->cardMenu(music));header.addView(menu,new LinearLayout.LayoutParams(u(44),u(48)));
        if(cfg.b("titles",true))card.addView(header,new LinearLayout.LayoutParams(-1,u(54)));
        View line=new View(this);line.setBackgroundColor(0xff54728f);card.addView(line,new LinearLayout.LayoutParams(-1,u(1)));
        FrameLayout body=new FrameLayout(this);card.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        AppWidgetHostView widget=widgets.view(music);
        if(widget!=null) {
            body.addView(widget,new FrameLayout.LayoutParams(-1,-1)); body.setTag("live-widget");
            if(music){TextView note=label("原生小组件 · 完整功能请打开应用",13,Ui.MUTED);note.setGravity(Gravity.CENTER);note.setOnClickListener(v->cardMenu(true));card.addView(note,new LinearLayout.LayoutParams(-1,u(28)));}
        } else {
            LinearLayout empty=Ui.column(this);empty.setGravity(Gravity.CENTER);empty.setPadding(u(22),u(12),u(22),u(12));
            Icons symbol=new Icons(this,music?"music":"map");symbol.setAlpha(.55f);empty.addView(symbol,new LinearLayout.LayoutParams(u(54),u(54)));
            TextView text=label(installed?"连接应用原生小组件":"尚未安装"+title,22,Ui.WHITE);text.setGravity(Gravity.CENTER);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,u(48));p.topMargin=u(12);empty.addView(text,p);
            String detail=widgets.error(music);
            TextView reason=label(detail!=null?detail:installed?"普通应用权限不支持完整应用内嵌。\n可连接应用提供的真实桌面小组件。":"请先安装你提供的原版 APK。\n桌面不会替代应用的登录和授权。",16,Ui.MUTED);reason.setGravity(Gravity.CENTER);empty.addView(reason,new LinearLayout.LayoutParams(-1,u(68)));
            Button connect=Ui.button(this,installed?"连接小组件":"检查安装环境",v->{ if(installed) widgets.choose(music);else startActivity(new Intent(this,DiagnosticsActivity.class)); },SECOND_PAGE_RADIUS_SCALE);
            connect.setTextSize(sp(18));empty.addView(connect,new LinearLayout.LayoutParams(u(music?220:250),u(48)));
            TextView launch=label(installed?"打开原应用，使用完整功能":"安装后点击此处重试",15,Ui.MUTED);launch.setGravity(Gravity.CENTER);launch.setOnClickListener(v->{if(installed)Ui.launch(this,pkg);else render();});empty.addView(launch,new LinearLayout.LayoutParams(-1,u(48)));
            body.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        }
        card.setOnLongClickListener(v->{cardMenu(music);return true;});return card;
    }
    private void cardMenu(boolean music) {
        if(!music){floatingMapMenu();return;}
        String[] actions={"打开原应用（全屏）","连接 / 更换原生小组件","移除小组件","主题设置","诊断与系统限制",music?"切换 QQ音乐卡片样式":"打开地图 + 悬浮音乐控制","显示悬浮音乐控制","应用容器实验 · 新增"};
        ArrayList<String> options=new ArrayList<>(Arrays.asList(actions));
        new ThemeDialog.Builder(this).setTitle(music?"音乐卡片":"地图卡片").setItems(options.toArray(new String[0]),(d,n)->{
            if(n==0)Ui.launch(this,cfg.pkg(music));if(n==1)widgets.choose(music);if(n==2)widgets.remove(music);if(n==3)fixed(4);if(n==4)startActivity(new Intent(this,DiagnosticsActivity.class));if(n==5){if(music){cfg.put("musicMode",1-cfg.n("musicMode",0));render();}else if(FloatingMusicService.show(this))Ui.launch(this,cfg.pkg(false));}if(n==6)FloatingMusicService.show(this);if(n==7)startActivity(new Intent(this,ContainerActivity.class));
        }).setNegativeButton("取消",null).show();
    }
    private View floatingMapCard() {
        LinearLayout card=ThemeStyle.role(Ui.column(this),"cardText");card.setPadding(u(12),u(10),u(12),u(10));
        card.setBackground(ThemeStyle.panel(this,"map",colors.panel(cfg.n("cardAlpha",Config.CARD_ALPHA)*255/100),cardRadius(20),u(1),ThemeStyle.border(cfg,"map")));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("高德地图",21,Ui.WHITE);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);header.addView(title,new LinearLayout.LayoutParams(0,u(50),1));
        TextView reconnect=label("重连",16,Ui.MUTED);reconnect.setGravity(Gravity.CENTER);reconnect.setContentDescription("重连高德悬浮地图");reconnect.setOnClickListener(v->reconnectFloatingMap());header.addView(reconnect,new LinearLayout.LayoutParams(u(58),u(48)));
        TextView stop=label(cfg.b("amapFloating",true)?"关闭":"启用",16,Ui.WHITE);stop.setGravity(Gravity.CENTER);stop.setContentDescription("切换高德悬浮地图");stop.setOnClickListener(v->{cfg.put("amapFloating",!cfg.b("amapFloating",true));floatingMap.closeWindow();render();});header.addView(stop,new LinearLayout.LayoutParams(u(58),u(48)));
        header.addView(Ui.appButton(this,cfg.pkg(false),"map","打开高德地图",v->Ui.launch(this,cfg.pkg(false)),SECOND_PAGE_RADIUS_SCALE),new LinearLayout.LayoutParams(u(44),u(48)));
        TextView menu=label("•••",24,Ui.WHITE);menu.setGravity(Gravity.CENTER);menu.setContentDescription("地图卡片菜单");menu.setOnClickListener(v->floatingMapMenu());header.addView(menu,new LinearLayout.LayoutParams(u(44),u(48)));
        // Keep escape controls outside the external window, including when ordinary card titles are hidden.
        card.addView(header,new LinearLayout.LayoutParams(-1,u(54)));
        View line=new View(this);line.setBackgroundColor(0x3054728f);card.addView(line,new LinearLayout.LayoutParams(-1,u(1)));
        FrameLayout slot=new FrameLayout(this);slot.setContentDescription("高德悬浮地图区域");
        LinearLayout.LayoutParams slotParams=new LinearLayout.LayoutParams(-1,0,1);slotParams.topMargin=u(3);card.addView(slot,slotParams);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);LinearLayout setup=Ui.column(this);setup.setGravity(Gravity.CENTER);setup.setPadding(u(24),u(12),u(24),u(12));scroll.addView(setup);slot.addView(scroll,new FrameLayout.LayoutParams(-1,-1));floatingMapSetup=scroll;scroll.setVisibility(cfg.b("amapFloating",true)&&AmapFloatingController.supported(this)?View.INVISIBLE:View.VISIBLE);
        TextView heading=label(cfg.b("amapFloating",true)?"地图准备中":"地图已关闭",23,Ui.WHITE);heading.setGravity(Gravity.CENTER);setup.addView(heading,new LinearLayout.LayoutParams(-1,u(44)));
        floatingMapStatus=label(AmapFloatingController.supported(this)?floatingMap.status():"请安装配套高德6.5卡片适配包",15,Ui.MUTED);floatingMapStatus.setGravity(Gravity.CENTER);floatingMapStatus.setMinLines(2);setup.addView(floatingMapStatus,new LinearLayout.LayoutParams(-1,-2));
        Button initialize=Ui.button(this,"打开地图",v->initializeFloatingMap());initialize.setTextSize(sp(17));initialize.setContentDescription("初始化高德悬浮地图");initialize.setBackground(ThemeStyle.panel(this,"map",0x305a8396,cardRadius(14),u(1),Ui.GLASS_LINE));LinearLayout.LayoutParams button=new LinearLayout.LayoutParams(-1,u(48));button.topMargin=u(18);setup.addView(initialize,button);
        TextView retry=label("重新显示地图",16,Ui.MUTED);retry.setGravity(Gravity.CENTER);retry.setOnClickListener(v->reconnectFloatingMap());setup.addView(retry,new LinearLayout.LayoutParams(-1,u(48)));
        TextView permission=label("允许高德显示悬浮窗",15,Ui.MUTED);permission.setGravity(Gravity.CENTER);permission.setOnClickListener(v->AmapFloatingController.openPermissions(this));setup.addView(permission,new LinearLayout.LayoutParams(-1,u(44)));
        floatingMap.bind(slot);return card;
    }
    private void initializeFloatingMap(){
        if(!AmapFloatingController.supported(this,cfg.pkg(false))){Ui.toast(this,"请安装配套高德6.5卡片适配包；普通原版无法提供卡片窗口");return;}
        cfg.put("amapFloating",true);floatingMap.closeWindow();
        Ui.toast(this,"高德地图加载完成后，按 HOME 返回追风桌面");Ui.launch(this,cfg.pkg(false));
    }
    private void reconnectFloatingMap(){
        if(!AmapFloatingController.supported(this)){Ui.toast(this,"未检测到新版高德6.5卡片适配包");return;}
        if(!cfg.b("amapFloating",true)){cfg.put("amapFloating",true);render();}
        else floatingMap.reconnect();
    }
    private void floatingMapMenu(){
        String[] actions={"首次启动高德 / 初始化","重新显示地图",cfg.b("amapFloating",true)?"关闭悬浮地图":"启用悬浮地图","高德悬浮窗权限","主题设置","诊断与日志"};
        new ThemeDialog.Builder(this).setTitle("高德悬浮地图").setItems(actions,(d,n)->{
            if(n==0)initializeFloatingMap();if(n==1)reconnectFloatingMap();if(n==2){cfg.put("amapFloating",!cfg.b("amapFloating",true));floatingMap.closeWindow();render();}if(n==3)AmapFloatingController.openPermissions(this);if(n==4)fixed(4);if(n==5)startActivity(new Intent(this,DiagnosticsActivity.class));
        }).setNegativeButton("取消",null).show();
    }
    private void updateFloatingHost(){if(floatingMap!=null&&MapWorkspace.get(this).ownsMain(this))floatingMap.setHostState(resumed&&!leaving&&(!transitioning||floatingMap.retainsSurfaces())&&layoutReady,currentPage==CARDS,mapHostFocused());updateMapPlaceholder();}
    private void updateMapPlaceholder(){
        if(floatingMapSetup==null||floatingMap==null)return;
        if(currentPage!=CARDS||!resumed||leaving){layoutHandler.removeCallbacks(revealMapPlaceholder);mapPlaceholderScheduled=false;floatingMapSetup.setVisibility(View.INVISIBLE);return;}
        if(!cfg.b("amapFloating",true)||!AmapFloatingController.selected(cfg)){floatingMapSetup.setVisibility(View.VISIBLE);return;}
        if(floatingMap.connected()){floatingMapSetup.setVisibility(View.INVISIBLE);layoutHandler.removeCallbacks(revealMapPlaceholder);mapPlaceholderScheduled=false;}
        else if(!mapPlaceholderScheduled){mapPlaceholderScheduled=true;layoutHandler.postDelayed(revealMapPlaceholder,1200);}
    }
    private void prepareToLeave(){leaving=true;if(tirePressureDialog!=null){tirePressureDialog.dismiss();tirePressureDialog=null;}if(mapDialogs!=null)mapDialogs.dismissAll();cancelPageAnimation();MapWorkspace.get(this).mainLeaving(this);}
    @Override public void startActivityForResult(Intent intent,int request,Bundle options){boolean wasLeaving=leaving;MapWorkspace.get(this).beforeLaunch(this,intent);prepareToLeave();try{super.startActivityForResult(intent,request,options);}catch(RuntimeException error){leaving=wasLeaving;updateFloatingHost();throw error;}}
    @Override public void finish(){prepareToLeave();super.finish();}
    @Override public void finishAffinity(){prepareToLeave();super.finishAffinity();}
    private void showTirePressure(){
        if(tirePressureDialog!=null&&tirePressureDialog.isShowing())return;
        tirePressureDialog=TirePressureDialog.show(this);
        tirePressureDialog.setOnDismissListener(d->tirePressureDialog=null);
    }
    private void fixed(int n) {
        if(n==5){showTirePressure();return;}
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        try {
            switch(n) {
                case 0: showPage(cfg.n("homePage",0));Ui.fullscreen(this);break;
                case 1:audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_LOWER,AudioManager.FLAG_SHOW_UI);break;
                case 2:
                    int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
                    if(volume>0){cfg.put("lastVolume",volume);audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,AudioManager.FLAG_SHOW_UI);Ui.toast(this,"媒体音量已静音");}
                    else {audio.setStreamVolume(AudioManager.STREAM_MUSIC,Math.min(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),Math.max(1,cfg.n("lastVolume",5))),AudioManager.FLAG_SHOW_UI);Ui.toast(this,"媒体音量已恢复");}break;
                case 3:audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,AudioManager.ADJUST_RAISE,AudioManager.FLAG_SHOW_UI);break;
                case 4:startActivity(new Intent(this,SettingsActivity.class));break;
            }
            updateMute();
        }catch(Exception e){LauncherApp.record("音量操作失败",e);Ui.toast(this,"车机限制了系统媒体音量，请使用原车音量控制");}
    }
    private void phone() {
        for(ResolveInfo a:applications) {
            String name=a.loadLabel(getPackageManager()).toString();
            if(name.contains("蓝牙") && (name.contains("电话") || name.contains("拨号"))) {Ui.launch(this,Ui.component(a));return;}
        }
        new ThemeDialog.Builder(this).setTitle("蓝牙电话").setItems(new String[]{"选择车机蓝牙电话应用","打开系统蓝牙设置"},(d,n)->{
            if(n==0)Ui.pick(this,a->Ui.launch(this,Ui.component(a)));else Ui.open(this,new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        }).setNegativeButton("取消",null).show();
    }
    AlertDialog appMenu(ResolveInfo info) {
        String pkg=info.activityInfo.packageName;
        boolean builtIn=pkg.equals(getPackageName())||pkg.equals(Config.MAP)||pkg.equals(Config.MUSIC);
        boolean systemApp=(info.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0;
        String[] options=builtIn?new String[]{"添加到底部快捷栏","应用信息"}:new String[]{"添加到底部快捷栏","应用信息",systemApp?"系统应用 · 管理 / 停用":"卸载应用"};
        return new ThemeDialog.Builder(this).setTitle(info.loadLabel(getPackageManager())).setItems(options,(dialog,n)->{
            if(n==0)addShortcut(info);
            else if(n==1||systemApp)Ui.open(this,new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+(builtIn?getPackageName():pkg))));
            else try{startActivity(new Intent(Intent.ACTION_DELETE,Uri.parse("package:"+pkg)));}catch(ActivityNotFoundException e){Ui.toast(this,"系统未提供卸载界面，请在应用信息中卸载");}
        }).setNegativeButton("取消",null).show();
    }
    private void addShortcut(ResolveInfo info) {
        String[] labels=new String[4];for(int i=0;i<4;i++){ResolveInfo old=resolve(cfg.s("slot"+i,"")); labels[i]="位置 "+(i+1)+" · "+(old==null?"空":old.loadLabel(getPackageManager()));}
        new ThemeDialog.Builder(this).setTitle("添加到底部快捷栏").setItems(labels,(d,n)->{cfg.put("slot"+n,Ui.component(info));render();}).setNegativeButton("取消",null).show();
    }
    private void shortcutMenu(int slot) {
        new ThemeDialog.Builder(this).setTitle("快捷方式 "+(slot+1)).setItems(new String[]{"添加 / 替换","删除","向左移动","向右移动"},(d,n)->{
            if(n==0){Ui.pick(this,a->{cfg.put("slot"+slot,Ui.component(a));render();});return;}
            if(n==1)cfg.put("slot"+slot,"");
            else {int to=slot+(n==2?-1:1);if(to<0||to>3){Ui.toast(this,"已经到达快捷栏边缘");return;}String old=cfg.s("slot"+slot,"");cfg.put("slot"+slot,cfg.s("slot"+to,""));cfg.put("slot"+to,old);}
            render();
        }).setNegativeButton("取消",null).show();
    }
    private ResolveInfo resolve(String value) {
        if(value.isEmpty())return null;ComponentName c=ComponentName.unflattenFromString(value);
        Intent i=c==null?getPackageManager().getLaunchIntentForPackage(value):new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(c);
        return i==null?null:getPackageManager().resolveActivity(i,0);
    }
    private Drawable icon(ResolveInfo info) {String k=Ui.component(info);Drawable d=iconCache.get(k);if(d==null){d=info.loadIcon(getPackageManager());if(d!=null)iconCache.put(k,d);}return d;}
    private void loadApps() {
        if(destroyed||isFinishing()||worker.isShutdown()||appsLoading||(appsLoaded&&!appsDirty))return;
        appsLoading=true;appsDirty=false;appScanCount++;final int generation=++version;
        final Context app=getApplicationContext();final java.lang.ref.WeakReference<MainActivity> owner=new java.lang.ref.WeakReference<>(this);
        final Handler delivery=new Handler(Looper.getMainLooper());
        worker.execute(()->{
            List<ResolveInfo> loaded=null;try{loaded=Ui.apps(app);}catch(Exception e){LauncherApp.record("读取应用列表失败",e);}
            final List<ResolveInfo> result=loaded;
            delivery.post(()->{MainActivity target=owner.get();if(target!=null)target.appsLoaded(generation,result);});
        });
    }
    private void appsLoaded(int generation,List<ResolveInfo> result){
        if(destroyed||isFinishing()||generation!=version)return;appsLoading=false;
        if(result==null){appsDirty=true;return;}
        appsLoaded=true;applications.clear();applications.addAll(result);appsRevision++;showPage(currentPage);if(appsDirty)loadApps();
    }
    int pageCount(){return APPS+Math.max(1,(applications.size()+appsPerPage-1)/appsPerPage);}
    private int appOffset(){return Math.max(0,currentPage-APPS)*appsPerPage;}
    private int appLabelHeight(){
        android.graphics.Paint paint=new android.graphics.Paint();
        paint.setTextSize(cfg.n("fontSize",18)*scale*ThemeStyle.scale(cfg,"appText"));
        android.graphics.Paint.FontMetricsInt font=paint.getFontMetricsInt();
        return Math.max(u(48),(font.bottom-font.top)*2+u(2));
    }
    private int appItemHeight(){return u(cfg.n("iconSize",66))+appLabelHeight()+u(6);}
    private void updateAppPagination(int height){
        int spacing=u(cfg.n("spacing",18)),item=appItemHeight();
        int rows=Math.max(1,(height-u(19)+spacing)/Math.max(1,item+spacing));
        int capacity=rows*screenLayout.gridColumns();if(capacity==appsPerPage)return;
        // Keep the first visible application in view when size or icon settings change.
        int first=appOffset();appsPerPage=capacity;if(currentPage>=APPS)currentPage=APPS+first/appsPerPage;showPage(currentPage);
    }
    void swipePage(boolean next){showPage(currentPage+(next?1:-1));}
    int visiblePage(){return currentPage;}
    void showPage(int n) { showPage(n,true); }
    private void showPage(int n,boolean allowAnimation) {
        int next=Math.max(MINIMAL,Math.min(n,pageCount()-1));
        // App scans and pagination can request the page already entering. Keep
        // its one animation running instead of snapping it to the end mid-frame.
        if(allowAnimation&&next==currentPage&&animatedPage!=null&&cfg.b("pageAnimation",true)){refreshAppGrid();return;}
        int previous=currentPage;boolean animate=allowAnimation&&next!=previous&&resumed&&cfg.b("pageAnimation",true);cancelPageAnimation();
        if(animate&&floatingMap!=null&&!floatingMap.retainsSurfaces())floatingMap.setHostState(false,false,false);
        if(next!=CARDS&&floatingMap!=null)floatingMap.setHostState(resumed,false,getWindow().getDecorView().hasWindowFocus());
        currentPage=next;if(home==null)return;
        if(dock!=null)ThemeStyle.radiusScale(dock,currentPage==CARDS?SECOND_PAGE_RADIUS_SCALE:1f);
        home.setVisibility(currentPage==CARDS?View.VISIBLE:View.GONE);grid.setVisibility(currentPage>=APPS?View.VISIBLE:View.GONE);
        topBar.setVisibility(currentPage==CARDS?View.GONE:View.VISIBLE);standardContent.setPadding(u(cfg.n("pageInset",20)),currentPage==CARDS?u(18):0,u(cfg.n("pageInset",20)),u(14));
        standardContent.setVisibility(currentPage==MINIMAL?View.GONE:View.VISIBLE);minimalPage.setVisibility(currentPage==MINIMAL?View.VISIBLE:View.GONE);
        refreshAppGrid();
        if(animate){
            transitioning=true;animatedPage=currentPage==MINIMAL?minimalPage:currentPage==CARDS?home:grid;
            View target=animatedPage;boolean cards=currentPage==CARDS;
            // Enter cards in place on the first frame. Waiting for a map ACK
            // after exposing the page caused a second, visibly delayed slide.
            target.setAlpha(cards?.86f:.72f);target.setTranslationX(cards?0:u(24)*(next>previous?1:-1));
            startPageAnimation(target,cards?120:160);
        }updateFloatingHost();
    }
    private void refreshAppGrid(){
        if(grid==null)return;
        if(currentPage>=APPS&&(shownAppsRevision!=appsRevision||shownAppsOffset!=appOffset()||shownAppsCapacity!=appsPerPage||shownAppsCount!=applications.size())){
            shownAppsRevision=appsRevision;shownAppsOffset=appOffset();shownAppsCapacity=appsPerPage;shownAppsCount=applications.size();
            ((BaseAdapter)grid.getAdapter()).notifyDataSetChanged();grid.setSelection(0);
        }
        grid.setContentDescription("应用第"+(Math.max(0,currentPage-APPS)+1)+"页，共"+(pageCount()-APPS)+"页");
    }
    private void startPageAnimation(View target,long duration){
        final float offset=target.getTranslationX(),alpha=target.getAlpha();
        android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofFloat(0,1);pageAnimator=animator;pageAnimationStarts++;
        animator.setDuration(duration);animator.setInterpolator(PAGE_EASE);
        animator.addUpdateListener(value->{if(animatedPage!=target)return;float fraction=(Float)value.getAnimatedValue();target.setTranslationX(offset*(1-fraction));target.setAlpha(alpha+(1-alpha)*fraction);});
        animator.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator value){if(animatedPage==target&&pageAnimator==value){pageAnimator=null;finishPageTransition(target);}}});animator.start();
    }
    private void finishPageTransition(View target){
        if(animatedPage!=target)return;
        target.setAlpha(1);target.setTranslationX(0);
        animatedPage=null;transitioning=false;updateFloatingHost();
    }
    private void cancelPageAnimation(){
        if(pageAnimator!=null){android.animation.ValueAnimator old=pageAnimator;pageAnimator=null;old.removeAllListeners();old.removeAllUpdateListeners();old.cancel();}
        if(animatedPage!=null){View old=animatedPage;animatedPage=null;old.setAlpha(1);old.setTranslationX(0);}transitioning=false;if(floatingMap!=null&&MapWorkspace.get(this).ownsMain(this))floatingMap.setMotion(false);
    }

    private void updateMusic(){if(destroyed)return;for(MusicCard card:musicCards)if(card.isShown())card.update();}
    private void updateWeather(){if(weatherPanel!=null)weatherPanel.update();if(quickWeather!=null)quickWeather.update();if(headerWeather!=null)headerWeather.update();}
    private void updateMute(){
        if(muteLabel==null||muteButton==null)return;
        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        if(audio==null)return;String title=audio.getStreamVolume(AudioManager.STREAM_MUSIC)==0?"已静音":"静音";
        if(!android.text.TextUtils.equals(muteLabel.getText(),title))muteLabel.setText(title);
        muteButton.setContentDescription(title);
    }
    private void updateStatus() {
        updateMute();
        if(clock==null)return;Date now=new Date();clock.setText(new SimpleDateFormat("HH:mm",Locale.CHINA).format(now));if(minimalClock!=null)minimalClock.setText(clock.getText());date.setText(new SimpleDateFormat("M月d日  EEEE",Locale.CHINA).format(now));
        if(quickClock!=null)quickClock.setText(clock.getText());if(quickDate!=null)quickDate.setText(date.getText());
        String network="离线",bluetooth="蓝牙关";
        try {ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkInfo n=cm.getActiveNetworkInfo();if(n!=null&&n.isConnected())network=n.getType()==ConnectivityManager.TYPE_WIFI?"Wi-Fi 已连接":"网络已连接";}catch(Exception ignored){}
        bluetooth=bluetoothState();
        status.setText(bluetooth+"   "+network);
        if(minimalDate!=null)minimalDate.setText(new SimpleDateFormat("M月d日  EEEE",Locale.CHINA).format(now));
        if(minimalStatus!=null)minimalStatus.setText(network+"    ·    "+bluetooth);
        updateWeather();
    }
    @android.annotation.SuppressLint("MissingPermission")
    private String bluetoothState() {
        // The supported OS range is 17-29. Never call the legacy Bluetooth API
        // on Android 12+, where a different runtime permission is required.
        if(Build.VERSION.SDK_INT>=31)return "蓝牙状态受限";
        if(checkCallingOrSelfPermission("android.permission.BLUETOOTH")!=PackageManager.PERMISSION_GRANTED)return "蓝牙权限缺失";
        try {BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null)return "无蓝牙";if(!a.isEnabled())return "蓝牙关";return a.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET)==BluetoothAdapter.STATE_CONNECTED?"蓝牙已连接":"蓝牙已开";}
        catch(SecurityException e){return "蓝牙状态受限";}
    }
    private class AppAdapter extends BaseAdapter {
        @Override public int getCount(){return Math.max(0,Math.min(appsPerPage,applications.size()-appOffset()));}@Override public Object getItem(int n){return applications.get(appOffset()+n);}@Override public long getItemId(int n){return appOffset()+n;}
        @Override public View getView(int n,View reuse,android.view.ViewGroup parent){
            LinearLayout item;ImageView image;TextView text;
            if(reuse==null){item=Ui.column(MainActivity.this);item.setGravity(Gravity.CENTER);image=new ImageView(MainActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);item.addView(image,new LinearLayout.LayoutParams(u(cfg.n("iconSize",66)),u(cfg.n("iconSize",66))));text=label("",cfg.n("fontSize",18),Ui.WHITE);text.setGravity(Gravity.CENTER);text.setMaxLines(2);item.addView(text,new LinearLayout.LayoutParams(-1,appLabelHeight()));}
            else {item=(LinearLayout)reuse;image=(ImageView)item.getChildAt(0);text=(TextView)item.getChildAt(1);}
            ResolveInfo a=(ResolveInfo)getItem(n);image.setImageDrawable(icon(a));text.setText(a.loadLabel(getPackageManager()));item.setContentDescription(a.loadLabel(getPackageManager()));if(item.getLayoutParams()==null||item.getLayoutParams().height!=appItemHeight())item.setLayoutParams(new AbsListView.LayoutParams(-1,appItemHeight()));ThemeStyle.apply(item,"appText");return item;
        }
    }
    private class SwipeTouch implements View.OnTouchListener {float x,y;@Override public boolean onTouch(View v,android.view.MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(e.getX()-x)>u(70)&&Math.abs(e.getY()-y)<u(65)){showPage(currentPage+(e.getX()<x?1:-1));return true;}return true;}}
    private class SwipeFrame extends FrameLayout {
        float x,y;boolean protect;
        SwipeFrame(){super(MainActivity.this);}
        @Override public boolean onInterceptTouchEvent(MotionEvent e){
            if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getX();y=e.getY();protect=insideWidget(this,x,y);}
            if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!protect&&Math.abs(e.getX()-x)>u(80)&&Math.abs(e.getY()-y)<u(60))return true;
            return false;
        }
        @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_UP&&Math.abs(e.getX()-x)>u(80)){showPage(currentPage+(e.getX()<x?1:-1));return true;}return true;}
    }
    private boolean insideWidget(View view,float x,float y){
        if(x<0||y<0||x>view.getWidth()||y>view.getHeight()||view.getVisibility()!=View.VISIBLE)return false;
        if("live-widget".equals(view.getTag()))return true;
        if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View child=g.getChildAt(i);if(insideWidget(child,x-child.getLeft(),y-child.getTop()))return true;}}return false;
    }
    private void restoreHomeWindow(){
        if(root==null)return;
        layoutReady=false;root.resetGeometry();updateFloatingHost();
        Window window=getWindow();WindowManager.LayoutParams params=window.getAttributes();
        if(params.width!=-1||params.height!=-1||params.x!=0||params.y!=0){params.width=params.height=-1;params.x=params.y=0;window.setAttributes(params);}
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        Ui.fullscreen(this);
        android.view.inputmethod.InputMethodManager ime=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(ime!=null&&root.getWindowToken()!=null)ime.hideSoftInputFromWindow(root.getWindowToken(),0);
        root.requestFitSystemWindows();root.requestLayout();root.postInvalidateOnAnimation();
    }
    @Override protected void onResume(){super.onResume();if(unifiedRedirected)return;com.morgoo.droidplugin.unified.StartupWindowState.report(this,this,true);getWindow().setWindowAnimations(0);overridePendingTransition(0,0);leaving=false;resumed=true;hostFocused=getWindow().getDecorView().hasWindowFocus();restoreHomeWindow();
        java.io.File file=new java.io.File(getFilesDir(),"wallpaper");String key=cfg.s("wallpaperKind","")+"/"+cfg.n("fit",0)+"/"+file.lastModified()+"/"+file.length();
        if(!cfg.visualState().equals(renderedSettings)||!key.equals(wallpaperKey))render();else{updateStatus();wallpaper.resume();updateFloatingHost();}
        widgets.start();musicLease.resume();weather.start();loadApps();
        layoutHandler.post(()->{if(resumed&&!isFinishing()){startupApps.resume();}});
    }
    @Override protected void onPause(){if(unifiedRedirected){super.onPause();return;}com.morgoo.droidplugin.unified.StartupWindowState.report(this,this,false);prepareToLeave();resumed=false;layoutReady=false;layoutHandler.removeCallbacks(resize);startupApps.pause();widgets.stop();musicLease.pause();weather.pause();if(wallpaper!=null)wallpaper.pause();super.onPause();}
    @Override protected void onStop(){prepareToLeave();super.onStop();}
    @Override protected void onUserLeaveHint(){prepareToLeave();super.onUserLeaveHint();}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(unifiedRedirected)return;boolean internal=mapDialogs!=null&&mapDialogs.inTransfer();hostFocused=focused;if(focused&&!internal)restoreHomeWindow();else if(!focused&&!internal)layoutReady=false;updateFloatingHost();if(focused){UnifiedInitService.desktopVisible(this);StartupSequenceService.acknowledge(this,getIntent());if(resumed&&!leaving&&startupApps!=null)startupApps.resume();}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);widgets.result(request,result);}
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);leaving=false;setIntent(i);if(!i.getBooleanExtra(StartupApps.RETURN,false)&&!i.getBooleanExtra("windchaser.return_to_workspace",false)){StartupSequenceService.userReturned(this);showPage(cfg.n("homePage",0),false);}Ui.fullscreen(this);if(hasWindowFocus()){UnifiedInitService.desktopVisible(this);StartupSequenceService.acknowledge(this,i);}}
    @Override protected void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putInt("page",currentPage);}
    @Override public void onBackPressed(){showPage(cfg.n("homePage",0));Ui.fullscreen(this);}
    @Override public void onTrimMemory(int level){
        super.onTrimMemory(level);
        if(level>=TRIM_MEMORY_RUNNING_LOW)iconCache.evictAll();
        // A low-memory hint must not permanently stop the visible wallpaper.
        // Release the decoder for severe pressure, then retry only after memory recovers.
        if((level==TRIM_MEMORY_RUNNING_CRITICAL||level>=TRIM_MEMORY_BACKGROUND)&&wallpaper!=null){
            wallpaper.suspendForMemory();LauncherApp.record("内存紧张，暂缓视频背景 level="+level,null);
        }
    }
    private class ResponsiveRoot extends FrameLayout{
        private final android.graphics.Rect frame=new android.graphics.Rect();private int stableFrames;
        private final android.view.ViewTreeObserver.OnPreDrawListener geometry=this::settleGeometry;
        ResponsiveRoot(){super(MainActivity.this);getViewTreeObserver().addOnPreDrawListener(geometry);}
        void dispose(){if(getViewTreeObserver().isAlive())getViewTreeObserver().removeOnPreDrawListener(geometry);}
        void resetGeometry(){stableFrames=0;frame.setEmpty();layoutReady=false;}
        private boolean settleGeometry(){
            if(!resumed||leaving||!mapHostFocused())return true;
            int w=getWidth(),h=getHeight();if(w<2||h<2)return true;
            if(w!=screenLayout.width||h!=screenLayout.height){
                resetGeometry();screenLayout=new ScreenLayout(w,h);scale=screenLayout.unit;
                layoutHandler.removeCallbacks(resize);layoutHandler.post(resize);updateFloatingHost();return true;
            }
            int[] xy=new int[2];getLocationOnScreen(xy);android.graphics.Rect current=new android.graphics.Rect(xy[0],xy[1],xy[0]+w,xy[1]+h);
            // An owned picker changes focus and its own layout, but leaves the
            // already measured map rectangle intact. Only main-window geometry
            // changes invalidate that rectangle while our dialog owns focus.
            boolean dialogOwnsFocus=mapDialogs!=null&&mapDialogs.focused();
            if(!frame.equals(current)||(!dialogOwnsFocus&&(isLayoutRequested()||(standardContent.getVisibility()==View.VISIBLE&&standardContent.isLayoutRequested())))){
                frame.set(current);stableFrames=0;if(layoutReady){layoutReady=false;updateFloatingHost();}postInvalidateOnAnimation();
            }else if(!layoutReady){
                if(++stableFrames>=1){layoutReady=true;updateFloatingHost();}else postInvalidateOnAnimation();
            }
            return true;
        }
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);resetGeometry();if(floatingMap!=null)updateFloatingHost();}
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){super.onConfigurationChanged(configuration);restoreHomeWindow();}
    @Override protected void onDestroy(){com.morgoo.droidplugin.unified.StartupWindowState.forget(this,this);
        if(unifiedRedirected){worker.shutdownNow();super.onDestroy();return;}
        destroyed=true;muteButton=null;muteLabel=null;if(mapDialogs!=null){mapDialogs.close();mapDialogs=null;}cancelPageAnimation();layoutHandler.removeCallbacksAndMessages(null);
        DesktopReturnReceiver.detach(this);if(startupApps!=null)startupApps.close();if(floatingMap!=null)MapWorkspace.get(this).detachMain(this);widgets.stop();weather.close();
        for(MusicCard card:musicCards)card.release();musicCards.clear();if(musicLease!=null){musicLease.close();musicLease=null;}
        version++;worker.shutdownNow();if(wallpaper!=null)wallpaper.dispose();iconCache.evictAll();applications.clear();
        if(registered){unregisterReceiver(system);unregisterReceiver(packages);registered=false;}
        if(grid!=null){grid.setOnItemClickListener(null);grid.setOnItemLongClickListener(null);grid.setAdapter(null);}
        if(root!=null){root.dispose();root.removeAllViews();}
        root=null;wallpaper=null;page=minimalPage=null;standardContent=home=topBar=dock=null;grid=null;
        clock=date=status=quickClock=quickDate=minimalClock=minimalDate=minimalStatus=floatingMapStatus=null;
        weatherPanel=null;quickWeather=headerWeather=null;floatingMapSetup=null;musicBridge=renderedBridge=null;
        super.onDestroy();
    }
}
