from pathlib import Path
import argparse,json,os,re,subprocess
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args();s=Path(__file__).resolve().parents[1];w=a.work.resolve();w.mkdir(parents=True,exist_ok=True);stubs=w/'stubs';classes=w/'classes';classes.mkdir(exist_ok=True)
sources={
 'android/content/Context.java':'package android.content;public class Context{}',
 'android/view/View.java':'package android.view;public class View{public android.content.Context mContext;}',
 'android/os/Build.java':'package android.os;public class Build{public static class VERSION{public static int SDK_INT=19;}}',
 'android/os/IBinder.java':'package android.os;public interface IBinder{}',
 'android/os/Binder.java':'package android.os;public class Binder implements IBinder{}',
 'android/app/ITransientNotification.java':'package android.app;public interface ITransientNotification{}',
 'android/util/Log.java':'package android.util;public class Log{public static int w(String t,String m,Throwable e){return 0;}}',
}
files=[]
for name,text in sources.items():
 f=stubs/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text,'utf-8');files.append(f)
files += [s/'experiments/card-runtime/upstream/project/Libraries/DroidPlugin/src/main/java/com/morgoo/droidplugin/unified/ToastCallbackCompat.java',s/'tests/toast-compat/src/com/morgoo/droidplugin/unified/ToastCallbackChecks.java']
j=Path(r'D:/SOFT/Android Studio/jbr/bin')
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=30,env={**os.environ,'JAVA_TOOL_OPTIONS':'-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'});text=(r.stdout+r.stderr).decode('utf-8','replace')
 if r.returncode:raise RuntimeError(text)
 return text
run([j/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-d',classes,*files]);results=[]
for case in ['legacy-fields','legacy-next-only','modern-token','modern-cancel','legacy-cancel','unknown-fields','empty-callback','no-callback-signature']:
 t=run([j/'java.exe','-cp',classes,'com.morgoo.droidplugin.unified.ToastCallbackChecks',case]);print(t,flush=True);results.append({'scenario':case,'output':t})
report={'platform':'Production toast compatibility on deterministic JVM platform-shaped boundaries, not a physical car','all_passed':True,'scenarios':len(results),'assertions':sum(int(n) for r in results for n in re.findall(r'checks=(\d+)',r['output'])),'results':results}
(w/'results.json').write_text(json.dumps(report,indent=2),'utf-8');print(json.dumps({k:v for k,v in report.items() if k!='results'}))
