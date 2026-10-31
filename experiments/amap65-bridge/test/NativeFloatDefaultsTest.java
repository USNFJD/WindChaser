package cn.windchaser.mapbridge;
import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
public final class NativeFloatDefaultsTest {
 static int count;static final Charset UTF16=Charset.forName("UTF-16LE");static File root;
 static void pass(String name,boolean ok){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);count++;}
 static byte[] encoded(String s){return s.getBytes(UTF16);}
 static String str(byte[] b){return new String(b,UTF16);}
 static void rejects(String name,byte[] b)throws Exception{try{NativeFloatDefaults.closedConfig(b);throw new AssertionError(name);}catch(IOException expected){pass(name,true);}}
 static void reset()throws Exception{for(String name:new String[]{"app","done","ready","checks"}){Field f=NativeFloatDefaults.class.getDeclaredField(name);f.setAccessible(true);if(name.equals("app"))f.set(null,null);else if(name.equals("checks"))f.setInt(null,0);else f.setBoolean(null,false);}}
 static class Pref implements SharedPreferences,SharedPreferences.Editor{int revision;Pref(int r){revision=r;}public int getInt(String k,int d){return revision;}public SharedPreferences.Editor edit(){return this;}public SharedPreferences.Editor putInt(String k,int v){revision=v;return this;}public boolean commit(){return true;}}
 static class Ctx extends Context{File dir;Pref p;Ctx(String name,int revision)throws Exception{dir=new File(root,name);dir.mkdirs();p=new Pref(revision);}public String getPackageName(){return "com.autonavi.amapauto";}public Context getApplicationContext(){return this;}public File getFilesDir(){return dir;}public SharedPreferences getSharedPreferences(String k,int mode){return p;}File config(){return new File(dir,"uiconfig.data");}void write(String s)throws Exception{Files.write(config().toPath(),encoded(s));}String read()throws Exception{return str(Files.readAllBytes(config().toPath()));}}
 public static void main(String[] args)throws Exception{root=new File(args[0]);root.mkdirs();String nul=String.valueOf((char)0);
  String source="ANIMATION[1]OpenBuiltInInput[1]VoiceRole[中文语音]isOpenFloatingWindow[1]speedWindowSwitchCurrent[1]user_json[{\"x\":1}]"+nul;
  byte[] input=encoded(source),closed=NativeFloatDefaults.closedConfig(input);
  pass("only the real native float property changes; Unicode and all other options preserved",str(closed).equals(source.replace("isOpenFloatingWindow[1]","isOpenFloatingWindow[0]"))&&str(input).equals(source));
  pass("already closed config is byte-identical",NativeFloatDefaults.closedConfig(closed)==closed);
  pass("absent file produces valid default-off setting",str(NativeFloatDefaults.closedConfig(new byte[0])).equals("isOpenFloatingWindow[0]"+nul));
  pass("missing property retains other values and terminating nulls",str(NativeFloatDefaults.closedConfig(encoded("OpenBuiltInInput[1]"+nul+nul))).equals("OpenBuiltInInput[1]isOpenFloatingWindow[0]"+nul+nul));
  rejects("odd-length UTF16 is rejected",new byte[]{1});
  rejects("ambiguous duplicate setting is rejected",encoded("isOpenFloatingWindow[1]isOpenFloatingWindow[0]"));
  rejects("unknown setting value is rejected",encoded("isOpenFloatingWindow[2]"));
  rejects("incomplete setting is rejected",encoded("isOpenFloatingWindow["));
  rejects("oversized config is rejected",new byte[1024*1024+2]);
  reset();Ctx repaired=new Ctx("old-marker-on",1);repaired.write(source);NativeFloatDefaults.install(repaired);
  pass("old false-success revision1 is repaired before native initialization",repaired.read().contains("isOpenFloatingWindow[0]")&&repaired.p.revision==1);
  NativeFloatDefaults.onReady(true);pass("completion revision2 requires ready plus persisted off",repaired.p.revision==2);
  repaired.write(source);reset();NativeFloatDefaults.install(repaired);NativeFloatDefaults.onReady(true);pass("subsequent manual on is retained",repaired.read().equals(source)&&repaired.p.revision==2);
  reset();Ctx fresh=new Ctx("fresh",0);NativeFloatDefaults.install(fresh);pass("only default property is prepared without bypassing native readiness",fresh.read().equals("isOpenFloatingWindow[0]"+nul)&&fresh.p.revision==0);fresh.write(source.replace("isOpenFloatingWindow[1]","isOpenFloatingWindow[0]"));NativeFloatDefaults.onReady(true);pass("native-created remaining settings are retained after readiness",fresh.read().contains("OpenBuiltInInput[1]")&&fresh.read().contains("isOpenFloatingWindow[0]")&&fresh.p.revision==2);
  reset();Ctx broken=new Ctx("invalid",1);byte[] original=encoded("isOpenFloatingWindow[unknown]");Files.write(broken.config().toPath(),original);NativeFloatDefaults.install(broken);NativeFloatDefaults.onReady(true);pass("invalid config is retained and never marked successful",Arrays.equals(original,Files.readAllBytes(broken.config().toPath()))&&broken.p.revision==1);
  reset();Ctx blocked=new Ctx("write-failure",1);File obstruction=new File(root,"not-a-directory");Files.write(obstruction.toPath(),new byte[]{1});blocked.dir=obstruction;NativeFloatDefaults.install(blocked);NativeFloatDefaults.onReady(true);pass("write failure does not record success",blocked.p.revision==1);
  System.out.println("RESULT native-float checks="+count+" PASS");
 }
}
