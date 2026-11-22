package cn.hydrogen.launcher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import android.view.*;
import org.json.JSONException;
import java.util.ArrayList;
import java.util.concurrent.*;

public class WeatherSettingsActivity extends WorkspaceActivity {
    private WallpaperLayer defaultBackground;
    private float unit;private int category;private Config cfg;private LinearLayout body;private TextView result;private CheckBox automatic;private int generation;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    @Override public void onCreate(Bundle state){super.onCreate(state);cfg=new Config(this);build();}
    private void build(){
        unit=Math.max(.6f,Math.min(getResources().getDisplayMetrics().widthPixels/1280f,getResources().getDisplayMetrics().heightPixels/720f));
        FrameLayout backdrop=new FrameLayout(this);defaultBackground=WallpaperLayer.defaultBackground(this,defaultBackground);WallpaperLayer picture=defaultBackground;backdrop.addView(picture,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackgroundColor(0xc0091726);backdrop.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=Ui.column(this);root.setPadding(d(28),d(16),d(28),d(20));backdrop.addView(root,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.addView(action("‹  返回桌面",this::finish),new LinearLayout.LayoutParams(d(144),d(52)));
        LinearLayout heading=Ui.column(this);heading.setPadding(d(24),0,0,0);heading.addView(label("天气与位置",27,Ui.WHITE));heading.addView(label("出发前，先看看沿途天气",13,0xff839caf));top.addView(heading,new LinearLayout.LayoutParams(0,-2,1));root.addView(top,new LinearLayout.LayoutParams(-1,d(78)));
        LinearLayout panes=new LinearLayout(this);root.addView(panes,new LinearLayout.LayoutParams(-1,0,1));LinearLayout nav=Ui.column(this);nav.setPadding(0,d(12),d(18),0);panes.addView(nav,new LinearLayout.LayoutParams(d(228),-1));
        String[] names={"定位与位置","默认城市","地点显示","天气与来源"};for(int n=0;n<names.length;n++){final int selected=n;TextView v=label(names[n],18,n==category?0xffc5f0e3:0xff8fa9bd);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(d(18),0,d(8),0);v.setBackground(ThemeStyle.panel(this,"settings",n==category?0x50395963:0,d(14),n==category?d(1):0,0x40557e86));v.setOnClickListener(w->{category=selected;build();});LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,d(58));np.bottomMargin=d(8);nav.addView(v,np);}
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackground(ThemeStyle.panel(this,"settings",0xb5112536,d(22),d(1),0x354b6b80));panes.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));body=Ui.column(this);body.setPadding(d(28),d(20),d(28),d(24));scroll.addView(body);
        if(category==0){
            add("自动跟随你的旅程",23,Ui.WHITE);add("启动桌面先尝试定位；8 秒内未取得位置时，使用默认城市天气。",15,Ui.MUTED);
            automatic=new CheckBox(this);automatic.setText("跟随当前定位");automatic.setTextColor(Ui.WHITE);automatic.setTextSize(17*unit/getResources().getDisplayMetrics().scaledDensity);automatic.setChecked(cfg.b("weatherAuto",true));automatic.setOnCheckedChangeListener((button,on)->{cfg.put("weatherAuto",on);if(on)permission();});body.addView(automatic,new LinearLayout.LayoutParams(-1,d(58)));
            String name=getSharedPreferences("location-name",0).getString("name","");add(name.isEmpty()?"地点名称将显示市、县 / 区、乡镇":"上次识别位置："+name,17,0xffc2d7e5);
            body.addView(action("允许定位 / 检查权限",this::permission));body.addView(action("打开系统定位设置",()->Ui.open(this,new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))));
        }else if(category==1){
            add("没有定位时，仍有天气",23,Ui.WHITE);
            try{add("默认城市："+WeatherController.City.parse(cfg.s("weatherCity",WeatherController.DEFAULT_CITY)).label,18,0xffc2d7e5);}catch(Exception ignored){}
            add("定位关闭、未授权或无信号时使用此城市。初始城市为北京市。",15,Ui.MUTED);
            body.addView(action("搜索城市 / 区县",this::search));body.addView(action("手动输入名称和经纬度",this::coordinates));body.addView(action("恢复北京市",()->{try{save(WeatherController.City.parse(WeatherController.DEFAULT_CITY));}catch(Exception ignored){}}));
        }else if(category==2){
            add("每一页，都有清晰的位置",23,Ui.WHITE);
            add("第一桌面保留省名，名称之间留空格，最多显示三级。地址服务未返回的层级会自动省略。",16,Ui.MUTED);
            String[] levels={"到市 / 区 · 省 市", "到县 / 区 · 省 市 县（默认）", "到镇 / 街道 · 省 市或区 镇街道", "到村 / 社区 · 省 市或区 村社区"};
            RadioGroup group=new RadioGroup(this);int selected=Math.max(0,Math.min(3,cfg.n("weatherPlaceLevel",1)));
            for(int i=0;i<levels.length;i++){RadioButton radio=new RadioButton(this);radio.setId(710+i);radio.setText(levels[i]);radio.setTextSize(16*unit/getResources().getDisplayMetrics().scaledDensity);radio.setTextColor(Ui.WHITE);radio.setMinHeight(d(54));group.addView(radio);}
            group.check(710+selected);group.setOnCheckedChangeListener((g,id)->cfg.put("weatherPlaceLevel",id-710));body.addView(group);
            add("直辖市会去掉重复名称，例如：北京市 朝阳区 望京街道。第二桌面和所有应用页固定显示市、县 / 区两级，应用页位置在日期之后、天气图标之前。",15,Ui.MUTED);
        }else{
            add("天气数据与隐私",23,Ui.WHITE);add("天气每 30 分钟更新；离线保留上次结果及时间。Open-Meteo 为模型估算，请结合实际天气。",16,Ui.MUTED);add("先使用车机系统解析地点名称，失败时尝试 OpenStreetMap / Nominatim，并缓存结果。能显示的行政层级取决于地址服务返回的数据。",16,Ui.MUTED);add("仅在桌面前台定位。为获取当地天气和地点名称，会向对应服务发送经纬度；不读取行程。",16,Ui.MUTED);body.addView(action("查看 Open-Meteo 数据来源",()->Ui.open(this,new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://open-meteo.com/en/docs")))));
        }
        result=add("",15,Ui.MUTED);ThemeStyle.apply(backdrop,"settingsText");setContentView(backdrop);Ui.fullscreen(this);
    }
    private void permission(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},501);else Ui.toast(this,"已允许定位，返回桌面后更新天气");}
    private int d(int n){return Math.round(n*unit);}
    private TextView label(String text,int size,int color){return Ui.text(this,text,size*unit/getResources().getDisplayMetrics().scaledDensity,color);}
    private TextView add(String text,int size,int color){TextView t=label(text,size,color);t.setPadding(0,d(10),0,d(12));t.setLineSpacing(d(3),1);body.addView(t);return t;}
    private View action(String title,Runnable run){TextView v=label(title,17,0xffc2d7e5);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(d(18),d(12),d(18),d(12));v.setMinHeight(d(54));v.setBackground(ThemeStyle.panel(this,"settings",0x283e5c6d,d(13),d(1),0x304f7186));v.setContentDescription(title);v.setOnClickListener(w->run.run());return v;}
    private void search(){EditText input=new EditText(this);input.setSingleLine();input.setHint("例如 北京、Beijing");new ThemeDialog.Builder(this).setTitle("搜索城市 / 区县").setView(input).setPositiveButton("搜索",(d,w)->{
        String query=input.getText().toString().trim();if(query.length()<2||query.length()>80){result.setText("请输入 2–80 个字符的城市名称");return;}
        final int token=++generation;result.setText("正在搜索…");worker.execute(()->{try{ArrayList<WeatherController.City> list=WeatherController.search(this,query);runOnUiThread(()->{if(isFinishing()||token!=generation)return;if(list.isEmpty()){result.setText("未找到城市，可尝试拼音或输入经纬度");return;}String[] labels=new String[list.size()];for(int i=0;i<labels.length;i++)labels[i]=list.get(i).label;new ThemeDialog.Builder(this).setTitle("选择城市，请核对省份").setItems(labels,(dialog,n)->save(list.get(n))).setNegativeButton("取消",null).show();result.setText("");});}catch(Exception e){runOnUiThread(()->{if(!isFinishing()&&token==generation)result.setText("搜索失败，请检查网络或输入经纬度");});}});
    }).setNegativeButton("取消",null).show();}
    private void coordinates(){
        LinearLayout form=Ui.column(this);form.setPadding(d(16),0,d(16),0);
        EditText province=new EditText(this),municipal=new EditText(this),county=new EditText(this),lat=new EditText(this),lon=new EditText(this);
        province.setHint("省 / 自治区 / 直辖市，例如 北京市");municipal.setHint("市，例如 北京市");county.setHint("县 / 区，例如 朝阳区");lat.setHint("纬度，例如 39.9075");lon.setHint("经度，例如 116.39723");
        for(EditText field:new EditText[]{province,municipal,county,lat,lon}){field.setSingleLine();form.addView(field);}
        lat.setInputType(12290);lon.setInputType(12290);ScrollView scroll=new ScrollView(this);scroll.addView(form);
        android.app.AlertDialog dialog=new ThemeDialog.Builder(this).setTitle("手动城市（WGS84 坐标）").setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();
        dialog.setOnShowListener(w->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
            PlaceName place=new PlaceName(province.getText().toString(),municipal.getText().toString(),county.getText().toString(),"","");
            if(place.province.isEmpty()||place.compact().isEmpty())throw new IllegalArgumentException();
            WeatherController.City c=new WeatherController.City(place.full(),Double.parseDouble(lat.getText().toString()),Double.parseDouble(lon.getText().toString()),place);WeatherController.City.parse(c.json());save(c);dialog.dismiss();
        }catch(Exception e){lat.setError("请填写省、市或县和有效经纬度");}}));dialog.show();
    }
    private void save(WeatherController.City city){try{cfg.put("weatherCity",city.json());generation++;build();Ui.toast(this,"城市已保存，返回桌面更新天气");}catch(JSONException e){result.setText("城市格式错误");}}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==501&&grants.length>0&&grants[0]!=PackageManager.PERMISSION_GRANTED)result.setText("定位未授权，将使用手动城市；可在系统应用权限中开启定位");}
    @Override protected void onDestroy(){if(defaultBackground!=null){defaultBackground.dispose();defaultBackground=null;}generation++;worker.shutdownNow();super.onDestroy();}
}
