package cn.hydrogen.launcher;

import android.graphics.Color;

/** Small persisted palette shared by the desktop and settings preview. */
final class ThemeColors {
    static final String ACCENT="#A7E2D2",SURFACE="#101E2A",TEXT="#EDF5FF";
    final int accent,surface,text,clock,weather;
    ThemeColors(Config cfg){accent=ThemeStyle.color(cfg,"themeAccent",Color.parseColor(ACCENT));surface=ThemeStyle.color(cfg,"themeSurface",Color.parseColor(SURFACE));text=ThemeStyle.color(cfg,"themeText",Color.parseColor(TEXT));clock=ThemeStyle.color(cfg,"themeClock",text);weather=ThemeStyle.color(cfg,"themeWeather",accent);}
    static boolean valid(String value){return value!=null&&value.matches("#[0-9a-fA-F]{6}");}
    static int parse(String value,String fallback){return Color.parseColor(valid(value)?value:fallback);}
    int panel(int alpha){return (Math.max(0,Math.min(255,alpha))<<24)|(surface&0xffffff);}
    int highlight(){return blend(accent,0xffffffff,.25f);}
    static int blend(int a,int b,float amount){float t=Math.max(0,Math.min(1,amount));return Color.rgb(Math.round(Color.red(a)*(1-t)+Color.red(b)*t),Math.round(Color.green(a)*(1-t)+Color.green(b)*t),Math.round(Color.blue(a)*(1-t)+Color.blue(b)*t));}
}
