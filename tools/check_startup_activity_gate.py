from pathlib import Path
import argparse,os,subprocess,re,json
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args();s=Path(__file__).resolve().parents[1];w=a.work.resolve();w.mkdir(parents=True,exist_ok=True)
generated=w/'stubs';classes=w/'classes';classes.mkdir(exist_ok=True);files=[]
for f in (s/'tests/startup-return/src/android').rglob('*.java'):
 rel=f.relative_to(s/'tests/startup-return/src');text=f.read_text('utf-8')
 if rel.as_posix()=='android/content/Context.java':text=text.replace('public Context getApplicationContext()', 'public ContentResolver getContentResolver(){return new ContentResolver();}public Context getApplicationContext()')
 if rel.as_posix()=='android/content/ComponentName.java':text=text.replace('public String toString()', 'public String flattenToString(){return toString();}public String toString()')
 if rel.as_posix()=='android/os/Bundle.java':text=text.replace('public long getLong(String k)', 'public long getLong(String k,long d){return values.containsKey(k)?getLong(k):d;}public long getLong(String k)')
 dest=generated/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(text,'utf-8');files.append(dest)
files+=list((s/'tests/startup-activity-gate/src').rglob('*.java'))
files.append(s/'experiments/card-runtime/upstream/project/Libraries/DroidPlugin/src/main/java/com/morgoo/droidplugin/unified/StartupActivityGate.java')
java=Path(r'D:/SOFT/Android Studio/jbr/bin');cp=os.pathsep.join([str(classes),r'C:/Users/Wnnp/AppData/Local/Android/Sdk/platforms/android-35/android.jar'])
def run(cmd):
 q=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=30,env={**os.environ,'JAVA_TOOL_OPTIONS':'-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'});text=(q.stdout+q.stderr).decode('utf-8','replace')
 if q.returncode:raise RuntimeError(text)
 return text
run([java/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',cp,'-d',classes,*files]);results=[]
for case in ['music','map','deduplicate','cancelled','new-session','contracts','permission-result','permission-host-caller','permission-host-ordinary','late-replay-cancelled','broker-unavailable','long-camera']:
 text=run([java/'java.exe','-cp',cp,'com.morgoo.droidplugin.unified.StartupActivityGateChecks',case]);print(text,flush=True);results.append({'scenario':case,'output':text})
report={'platform':'Production native-home gate on deterministic JVM Android boundaries; not a physical car','all_passed':True,'scenarios':len(results),'assertions':sum(int(n) for r in results for n in re.findall(r'checks=(\d+)',r['output'])),'results':results}
(w/'results.json').write_text(json.dumps(report,indent=2),'utf-8');print(json.dumps({k:v for k,v in report.items() if k!='results'}))
