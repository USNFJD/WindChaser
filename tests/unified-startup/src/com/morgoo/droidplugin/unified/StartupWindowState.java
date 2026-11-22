package com.morgoo.droidplugin.unified;
import fixture.Env;
public final class StartupWindowState {
 private static Runnable listener;private static String last;private static long epoch;
 public static boolean allowed(){return "cn.windchaser.unified".equals(Env.top)||"com.autonavi.amapauto".equals(Env.top)||"com.tencent.qqmusiccar".equals(Env.top);}
 public static boolean foreground(String pkg){return allowed()&&pkg.equals(Env.top);}
 public static long epoch(){if(!Env.top.equals(last)){last=Env.top;if(!allowed())epoch++;}return epoch;}
 public static void listen(Runnable r){listener=r;}
 public static void focusChanged(){epoch();if(listener!=null)new android.os.Handler().post(listener);}
}
