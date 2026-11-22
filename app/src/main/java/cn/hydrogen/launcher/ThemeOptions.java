package cn.hydrogen.launcher;

import java.util.*;

/** One catalog drives settings, backup validation and reset. Empty colors inherit. */
final class ThemeOptions {
    static final class Option {
        final String key,label,group,def; final int min,max; final boolean color;
        Option(String k,String l,String g,String d,int lo,int hi,boolean c){key=k;label=l;group=g;def=d;min=lo;max=hi;color=c;}
        int number(){return Integer.parseInt(def);}
    }
    static final List<Option> ALL=new ArrayList<>();
    static { 
        color("themeAccent","强调色","配色",ThemeColors.ACCENT);color("themeSurface","卡片底色","配色",ThemeColors.SURFACE);
        color("themeText","主要文字","配色",ThemeColors.TEXT);color("themeMuted","次要文字","配色","#A9C0DC");
        color("themeBorder","边框颜色","配色","#6E9BB4");color("themeIcon","内置图标","配色","");
        number("borderOpacity","边框不透明度","配色",22,0,100);number("textScale","全局文字大小","字号",100,80,135);
        for(String[] a:new String[][]{{"card","卡片标题"},{"quick","快捷区文字"},{"dock","底栏文字"},{"app","应用名称"},{"settings","主题设置"},{"files","文件中心"},{"dialog","对话框"},{"status","网络与状态"}}){
            String group=a[0].equals("dialog")?"弹窗":"字号";
            number(a[0]+"TextSize",a[1]+"大小",group,100,80,140);color(a[0]+"TextColor",a[1]+"颜色",group,"");
        }
        number("dialogAlpha","弹窗背景不透明度","弹窗",74,30,100);number("dialogDim","弹窗周围暗度","弹窗",16,0,40);
        color("dialogSurface","弹窗底色","弹窗","");
        number("mapShare","地图占左右卡片比例","布局",60,35,70);number("minimalMusicWidth","第一主页音乐区宽度","布局",100,70,100);
        number("quickWidth","右侧快捷区宽度","布局",100,80,140);number("cardAlpha","卡片不透明度","布局",Config.CARD_ALPHA,0,100);
        number("dockAlpha","底栏不透明度","布局",Config.DOCK_ALPHA,0,100);number("radius","卡片圆角","布局",20,0,40);number("border","边框粗细","布局",1,0,5);
        number("cardGap","卡片间距","布局",10,4,28);number("pageInset","页面左右留白","布局",20,8,40);number("dockHeight","底栏高度","布局",94,76,120);
        for(String[] a:new String[][]{{"map","地图卡片"},{"music","音乐卡片"},{"quick","快捷区"},{"dock","底栏"},{"settings","设置面板"},{"files","文件面板"}}){color(a[0]+"Surface",a[1]+"底色","布局","");color(a[0]+"Border",a[1]+"边框","布局","");}
        number("titleSize","歌曲名称大小","音乐",100,80,135);number("artistSize","歌手名称大小","音乐",100,80,140);number("lyricSize","歌词大小","音乐",110,90,145);
        number("musicTextSize","音乐其他文字大小","音乐",100,80,140);number("transportWidth","播放控制栏宽度","音乐",100,75,100);number("coverSize","歌曲封面大小","音乐",100,75,120);
        color("titleColor","歌曲名称颜色","音乐","");color("artistColor","歌手名称颜色","音乐","");color("lyricActiveColor","正在演唱歌词","音乐","");color("lyricIdleColor","其他歌词","音乐","#8AA6B9");color("musicTextColor","音乐其他文字","音乐","");color("progressTrackColor","进度条底色","音乐","#698498");
        number("clockSize","时间大小","天气",100,75,130);number("dateSize","日期大小","天气",100,80,140);number("weatherSize","天气文字大小","天气",100,80,140);number("temperatureSize","温度大小","天气",100,80,135);
        number("weatherPillAlpha","天气胶囊不透明度","天气",25,0,70);color("themeClock","时间颜色","天气","");color("dateColor","日期颜色","天气","");color("themeWeather","天气文字颜色","天气","");color("weatherIconColor","天气图标主色","天气","");
        number("iconSize","应用图标尺寸","图标",66,40,90);number("spacing","应用图标间距","图标",18,0,35);number("fontSize","应用名称基准字号","图标",18,12,28);
    }
    private static void color(String k,String l,String g,String d){ALL.add(new Option(k,l,g,d,0,0,true));}
    private static void number(String k,String l,String g,int d,int lo,int hi){ALL.add(new Option(k,l,g,""+d,lo,hi,false));}
    static Option find(String key){for(Option o:ALL)if(o.key.equals(key))return o;return null;}
    static Set<String> keys(){Set<String> out=new LinkedHashSet<>();for(Option o:ALL)out.add(o.key);return out;}
    static void reset(Config cfg,String group){android.content.SharedPreferences.Editor e=cfg.p.edit();for(Option o:ALL)if(group==null||group.equals(o.group))e.remove(o.key);e.remove("borderColor").apply();}
}
