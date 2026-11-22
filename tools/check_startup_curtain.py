from pathlib import Path
import argparse,json,os,re,subprocess
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args()
s=Path(__file__).resolve().parents[1];w=a.work.resolve();w.mkdir(parents=True,exist_ok=True);generated=w/'stubs';classes=w/'classes';classes.mkdir(exist_ok=True);files=[]
for f in (s/'tests/startup-return/src/android').rglob('*.java'):
 rel=f.relative_to(s/'tests/startup-return/src');t=f.read_text('utf-8')
 if rel.as_posix()=='android/content/Context.java':
  t=t.replace('public Context getApplicationContext()', 'public ContentResolver getContentResolver(){return new ContentResolver();}public Context getApplicationContext()').replace('public void sendBroadcast(Intent i)', 'public void sendBroadcast(Intent i,String permission){sendBroadcast(i);}public void sendBroadcast(Intent i)')
 if rel.as_posix()=='android/os/Bundle.java':t=t.replace('public long getLong(String k)', 'public long getLong(String k,long d){return values.containsKey(k)?getLong(k):d;}public long getLong(String k)')
 if rel.as_posix()=='android/app/Activity.java':t=t.replace('public int getTaskId()', 'public Application getApplication(){return null;}public void overridePendingTransition(int a,int b){}public int getTaskId()')
 if rel.as_posix()=='android/view/Window.java':t=t.replace('public View getDecorView()', 'public void setWindowAnimations(int a){}public View getDecorView()')
 if rel.as_posix()=='android/view/View.java':
  t=t.replace('public class View {','public class View {public View(){}public View(android.content.Context c){}public interface OnClickListener{void onClick(View v);}public void setOnClickListener(OnClickListener l){}public View findViewWithTag(Object t){return null;}public void setTag(Object t){}public void setElevation(float v){}public void bringToFront(){}public void setSystemUiVisibility(int f){}public static final int SYSTEM_UI_FLAG_FULLSCREEN=4,SYSTEM_UI_FLAG_HIDE_NAVIGATION=2,SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN=1024,SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION=512,SYSTEM_UI_FLAG_LAYOUT_STABLE=256,SYSTEM_UI_FLAG_IMMERSIVE_STICKY=4096;')
 if rel.as_posix()=='android/view/ViewGroup.java':t=t.replace('public int getChildCount()', 'public static class LayoutParams{public LayoutParams(int w,int h){}}public void addView(View v,LayoutParams p){}public void removeView(View v){}public int getChildCount()')
 dest=generated/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(t,'utf-8');files.append(dest)
extra={
 'android/graphics/drawable/Drawable.java':'''package android.graphics.drawable;public class Drawable{}''',
 'fixture/Env.java':'''package fixture;import android.os.*;import android.content.*;import java.util.*;public class Env{public static boolean unavailable,blockHome;public static int calls;public static Bundle state;public static String top="fixture";public static java.util.function.BiConsumer<Context,Intent> route;public static Map<String,Object> services=new HashMap<>();public static Map<String,SharedPreferences> prefs=new HashMap<>();public static List<String> logs=new ArrayList<>();public static List<Intent> launches=new ArrayList<>(),broadcasts=new ArrayList<>(),serviceStarts=new ArrayList<>();public static List<BroadcastReceiver> receivers=new ArrayList<>();public static void dispatch(Intent i){}}''',
 'android/content/ContentResolver.java':'''package android.content;import android.os.*;import android.net.Uri;public class ContentResolver{public Bundle call(Uri u,String m,String a,Bundle b){fixture.Env.calls++;if(fixture.Env.unavailable)throw new IllegalStateException("unavailable");return fixture.Env.state;}}''',
 'android/graphics/drawable/ColorDrawable.java':'''package android.graphics.drawable;import android.graphics.*;public class ColorDrawable extends Drawable{public ColorDrawable(int c){}public void draw(Canvas c){}public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return 0;}}''',
 'cn/windchaser/startup/StartupVisual.java':'''package cn.windchaser.startup;import android.graphics.*;public class StartupVisual extends android.graphics.drawable.ColorDrawable{public StartupVisual(android.content.Context c){super(0);}public static android.view.View view(android.content.Context c){return new android.view.View(c);}}''',
}
for name,t in extra.items():
 f=generated/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(t,'utf-8');files.append(f)
files += [s/'tests/live-probe/src/cn/windchaser/startup/DualSplash.java',s/'experiments/dual-splash/src/cn/windchaser/startup/StartupCurtain.java',s/'tests/startup-curtain/src/cn/windchaser/startup/CurtainRecoveryChecks.java']
j=Path(r'D:/SOFT/Android Studio/jbr/bin');cp=os.pathsep.join(map(str,[classes,Path(r'C:/Users/Wnnp/AppData/Local/Android/Sdk/platforms/android-35/android.jar')]))
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=30,env={**os.environ,'JAVA_TOOL_OPTIONS':'-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'})
 t=(r.stdout+r.stderr).decode('utf-8','replace')
 if r.returncode:raise RuntimeError(t)
 return t
run([j/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',cp,'-d',classes,*files]);results=[]
for case in ['slow-owner','camera-owner','screen-owner','replaced-owner','unavailable-owner']:
 t=run([j/'java.exe','-cp',cp,'cn.windchaser.startup.CurtainRecoveryChecks',case]);print(t,flush=True);results.append({'scenario':case,'output':t})
report={'platform':'Production StartupCurtain on deterministic JVM Android/owner IPC boundaries, not a physical car','all_passed':True,'scenarios':len(results),'assertions':sum(int(n) for r in results for n in re.findall(r'checks=(\d+)',r['output'])),'results':results}
(w/'results.json').write_text(json.dumps(report,indent=2),'utf-8');print(json.dumps({k:v for k,v in report.items() if k!='results'}))
