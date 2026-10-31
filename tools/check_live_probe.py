from pathlib import Path
import argparse,os,subprocess,shutil,json,re
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args();s=Path(__file__).resolve().parents[1];w=a.work.resolve();w.mkdir(parents=True,exist_ok=True)
stubs=w/'stubs';classes=w/'classes';classes.mkdir(exist_ok=True);files=[]
for f in (s/'tests/startup-return/src/android').rglob('*.java'):
 t=f.read_text('utf-8');rel=f.relative_to(s/'tests/startup-return/src')
 if rel.as_posix()=='android/content/Context.java':t=t.replace('public void sendBroadcast(Intent i)', 'public void sendBroadcast(Intent i,String permission){sendBroadcast(i);}public void sendBroadcast(Intent i)').replace('public Context getApplicationContext()', 'public ClassLoader getClassLoader(){return getClass().getClassLoader();}public Context getApplicationContext()')
 if rel.as_posix()=='android/os/Handler.java':
  t='package android.os;import java.util.*;public class Handler {\n static class Entry{Handler h;Runnable r;long due,order;Entry(Handler h,Runnable r,long due,long o){this.h=h;this.r=r;this.due=due;order=o;}}\n static final Object lock=new Object();static final PriorityQueue<Entry> queue=new PriorityQueue<>((a,b)->a.due!=b.due?Long.compare(a.due,b.due):Long.compare(a.order,b.order));static long next;\n public Handler(){}public Handler(Looper l){}public boolean post(Runnable r){return postDelayed(r,0);}public boolean postDelayed(Runnable r,long d){synchronized(lock){queue.add(new Entry(this,r,SystemClock.now+d,next++));}return true;}\n public boolean postAtTime(Runnable r,long t){return postDelayed(r,t-SystemClock.now);}public void removeCallbacks(Runnable r){synchronized(lock){queue.removeIf(e->e.h==this&&e.r==r);}}public void removeCallbacksAndMessages(Object o){synchronized(lock){queue.removeIf(e->e.h==this);}}public void handleMessage(Message m){}\n public static void runFor(long ms){long end=SystemClock.now+ms;int count=0;while(true){Entry e;synchronized(lock){if(queue.isEmpty()||queue.peek().due>end){SystemClock.now=end;return;}e=queue.poll();SystemClock.now=e.due;}if(++count>100000)throw new AssertionError("busy loop");e.r.run();}}\n public static void reset(){synchronized(lock){queue.clear();SystemClock.now=1000;next=0;}}\n}'
 if rel.as_posix()=='android/os/SystemClock.java':t=t.replace('static long now','static volatile long now')
 dest=stubs/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(t,'utf-8');files.append(dest)
files+=list((s/'tests/live-probe/src').rglob('*.java'))+[s/'tests/unified-startup/src/fixture/Env.java',s/'experiments/dual-splash/src/cn/windchaser/startup/RuntimeProbe.java']
jdk=Path(r'D:/SOFT/Android Studio/jbr/bin');jar=Path(r'D:/SSlunw/py/CHEJI/.tooling/startup-customization/place-layout-check/json-20240303.jar');cp=os.pathsep.join(map(str,[classes,jar,Path(r'C:/Users/Wnnp/AppData/Local/Android/Sdk/platforms/android-35/android.jar')]));env={**os.environ,'JAVA_TOOL_OPTIONS':'-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'}
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=30,env=env);t=(r.stdout+r.stderr).decode('utf-8','replace')
 if r.returncode:raise RuntimeError(t)
 return t
run([jdk/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',cp,'-d',classes,*files]);results=[]
for name in ['map-wait','map-timeout','qq-slow','qq-content','qq-coalesced','qq-expired','qq-normal','qq-invalid']:
 t=run([jdk/'java.exe','-cp',cp,'cn.windchaser.startup.LiveProbeChecks',name]);print(t,flush=True);results.append({'scenario':name,'output':t})
report={'platform':'Production RuntimeProbe on JVM Android/native boundary stubs, not a physical vehicle','all_passed':True,'scenarios':len(results),'assertions':sum(int(n) for r in results for n in re.findall(r'checks=(\d+)',r['output'])),'results':results}
(w/'live-probe-results.json').write_text(json.dumps(report,indent=2),'utf-8');print(json.dumps({k:v for k,v in report.items() if k!='results'}))
