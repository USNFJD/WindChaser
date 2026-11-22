"""JVM boundary tests execute the actual NativeFloatDefaults implementation.
Android storage semantics are separately exercised on the dedicated emulator.
"""
from pathlib import Path
import subprocess,argparse,os
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args();work=a.work.resolve();work.mkdir(parents=True,exist_ok=True)
source=Path(__file__).resolve().parents[1];jdk=Path(os.environ['JAVA_HOME'])
stubs={
'android/content/Context.java':'package android.content; import java.io.File; public abstract class Context{public abstract String getPackageName();public abstract Context getApplicationContext();public abstract SharedPreferences getSharedPreferences(String n,int m);public abstract File getFilesDir();}',
'android/content/SharedPreferences.java':'package android.content;public interface SharedPreferences{int getInt(String k,int d);Editor edit();interface Editor{Editor putInt(String k,int v);boolean commit();}}',
'android/os/Looper.java':'package android.os; public class Looper{public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os;public class Handler{public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}public boolean postDelayed(Runnable r,long d){return true;}public void removeCallbacks(Runnable r){}}',
'android/util/Log.java':'package android.util;public class Log{public static int i(String t,String m){return 0;}public static int w(String t,String m,Throwable e){return 0;}}',
'android/util/AtomicFile.java':'package android.util;import java.io.*;public class AtomicFile{private File f;public AtomicFile(File x){f=x;}public File getBaseFile(){return f;}public FileInputStream openRead()throws IOException{return new FileInputStream(f);}public FileOutputStream startWrite()throws IOException{f.getParentFile().mkdirs();return new FileOutputStream(f);}public void finishWrite(FileOutputStream s)throws IOException{s.getFD().sync();s.close();}public void failWrite(FileOutputStream s){try{s.close();}catch(IOException e){}}}',
}
files=[]
for name,text in stubs.items():
 f=work/'stubs'/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text,'utf-8');files.append(f)
files.extend([source/'experiments/amap65-bridge/src/cn/windchaser/mapbridge/NativeFloatDefaults.java',source/'experiments/amap65-bridge/test/NativeFloatDefaultsTest.java'])
classes=work/'classes';classes.mkdir(exist_ok=True)
subprocess.run([str(jdk/'bin/javac.exe'),'-encoding','UTF-8','-d',str(classes),*map(str,files)],check=True)
subprocess.run([str(jdk/'bin/java.exe'),'-cp',str(classes),'cn.windchaser.mapbridge.NativeFloatDefaultsTest',str(work/'fixtures')],check=True)
