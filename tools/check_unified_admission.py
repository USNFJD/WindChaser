from pathlib import Path
import argparse,os,subprocess,json,re
p=argparse.ArgumentParser(description='Exercise production unified admission service and coordinator on deterministic Android boundary stubs.')
p.add_argument('--work',type=Path,required=True)
a=p.parse_args();s=Path(__file__).resolve().parents[1];w=a.work.resolve();w.mkdir(parents=True,exist_ok=True)
stub=s/'tests/startup-return/src';own=s/'tests/unified-startup/src';generated=w/'stubs';classes=w/'classes';classes.mkdir(exist_ok=True)
files=[]
for f in (stub/'android').rglob('*.java'):
 rel=f.relative_to(stub);text=f.read_text('utf-8')
 if rel.as_posix()=='android/content/SharedPreferences.java':
  text=text.replace('public Editor edit()', 'public boolean getBoolean(String k,boolean d){Object v=map.get(k);return v==null?d:(Boolean)v;}public Editor edit()').replace('public Editor putInt(', 'public Editor putBoolean(String k,boolean v){puts.put(k,v);return this;}public Editor putInt(').replace('public boolean commit()', 'public void apply(){commit();}public boolean commit()')
 if rel.as_posix()=='android/content/Context.java':
  text=text.replace('public String pkg="cn.hydrogen.launcher"','public String pkg="cn.windchaser.unified"')
  text=text.replace('public void sendBroadcast(Intent i)', 'public void sendBroadcast(Intent i,String permission){sendBroadcast(i);}public void sendBroadcast(Intent i)')
 if rel.as_posix()=='android/content/Context.java':
  text=text.replace('fixture.Env.top=i.getComponent().getPackageName();', 'if(i.getComponent().getClassName().equals("cn.hydrogen.launcher.MainActivity")&&fixture.Env.dropDesktop>0){fixture.Env.dropDesktop--;return;}fixture.Env.top=i.getComponent().getPackageName();if(fixture.Env.activityStarted!=null)fixture.Env.activityStarted.accept(this,i);')
 if rel.as_posix()=='android/app/PendingIntent.java':
  text=text.replace('public static PendingIntent getService(', 'public static PendingIntent getActivity(Context c,int code,Intent i,int f){return getService(c,code,i,f);}public static PendingIntent getService(')
 if rel.as_posix()=='android/app/Notification.java':
  text=text.replace('public Builder setSmallIcon(', 'public Builder setContentIntent(PendingIntent p){return this;}public Builder setSmallIcon(')
 dest=generated/rel;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(text,'utf-8');files.append(dest)
files+=list(own.rglob('*.java'))
for name in ['R.java','LauncherApp.java','CompanionService.java','QqMusicBridge.java','MainActivity.java']:
 files.append(stub/'cn/hydrogen/launcher'/name)
for name in ['UnifiedInitService.java','LinkedAppCoordinator.java']:
 files.append(s/'app/src/main/java/cn/hydrogen/launcher'/name)
java=Path(r'D:/SOFT/Android Studio/jbr/bin');android=Path(r'C:/Users/Wnnp/AppData/Local/Android/Sdk/platforms/android-35/android.jar');jsonjar=s.parents[2]/'startup-customization/place-layout-check/json-20240303.jar'
if not jsonjar.exists():jsonjar=Path(r'D:/SSlunw/py/CHEJI/.tooling/startup-customization/place-layout-check/json-20240303.jar')
cp=os.pathsep.join(map(str,[classes,jsonjar,android]))
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=60,env={**os.environ,'JAVA_TOOL_OPTIONS':'-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'});text=(r.stdout+r.stderr).decode('utf-8','replace')
 if r.returncode:raise RuntimeError(text)
 return text
run([java/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',cp,'-d',classes,*files])
results=[]
for scenario in ['camera-qq-foreground-mismatch','camera-desktop-proof-refresh','camera-desktop-unacknowledged','camera-desktop-return-race','camera-desktop-ready-loss','camera-desktop-late-ack','camera-background-readiness','camera-background-service-once','camera-background-cold','camera-background-first-use','camera-background-disabled','camera-background-death','camera-background-screen-off','camera-pending-release','camera-visual-deadline','camera-during-map','camera-before-first-launch','camera-final-reply','camera-first-setup','camera-repeated','camera-screen-off','camera-resume-one-dead','camera-timeout-budget','both-ready','map-only','music-only','cold-sequence','death-before-entry','timeout','first-timeout','screen-off','external-window','auto-disabled','interrupted','map-setup','map-setup-permission-return','warm-once','warm-unavailable','warm-first-use','warm-ready-music','warm-cancelled','parallel-final','await-slow','parallel-round','await-expired','slow-cancel','focus-gap','parallel-death','receiver-installed','inflight-proof','overlap-external','overlap-runtime-permission','resume-live','resume-dead','resume-lost','handoff-fresh','handoff-permission','cold-private-runtime','cold-external','cold-auto-disabled']:
 text=run([java/'java.exe','-cp',cp,'cn.hydrogen.launcher.UnifiedAdmissionChecks',scenario]);print(text,flush=True);results.append({'scenario':scenario,'output':text})
report={'platform':'Deterministic JVM Android stubs, not Android or native engine execution','all_passed':True,'scenarios':len(results),'assertions':sum(int(n) for r in results for n in re.findall(r'checks=(\d+)',r['output'])),'results':results}
(w/'unified-admission-results.json').write_text(json.dumps(report,indent=2),'utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='results'}),flush=True)
