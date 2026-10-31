from pathlib import Path
import subprocess,json,os
import argparse,re
p=argparse.ArgumentParser(description="Run production startup/default-view helpers on JVM Android test stubs.")
p.add_argument('--java-home',type=Path,required=True)
p.add_argument('--sdk',type=Path,required=True)
p.add_argument('--json-jar',type=Path,required=True)
p.add_argument('--work',type=Path)
a=p.parse_args()
s=Path(__file__).resolve().parents[1]
w=(a.work or s/'.tooling/startup-return-check').resolve();w.mkdir(parents=True,exist_ok=True)
j=a.java_home.resolve()/'bin'
sdk=a.sdk.resolve()/'platforms/android-35/android.jar'
jsonjar=a.json_jar.resolve()
classes=w/'classes';classes.mkdir(exist_ok=True)
suffix='.exe' if os.name=='nt' else ''
files=list((s/'tests/startup-return/src').rglob('*.java'))+[
 s/'app/src/main/java/cn/hydrogen/launcher/StartupSequenceService.java',
 s/'experiments/dual-splash/src/cn/windchaser/startup/DualSplash.java',
 s/'experiments/dual-splash/src/cn/windchaser/startup/StartupReadyPolicy.java',
 s/'experiments/amap65-bridge/src/cn/windchaser/mapbridge/DefaultMapView.java',
 s/'app/src/main/java/cn/hydrogen/launcher/StartupApps.java',
 s/'app/src/main/java/cn/hydrogen/launcher/LinkedAppCoordinator.java',
 s/'experiments/dual-splash/src/cn/windchaser/startup/RuntimeProbe.java',
 s/'experiments/amap65-bridge/src/cn/windchaser/mapbridge/MapInterfaceReadiness.java']
for dependency in [j/('java'+suffix),j/('javac'+suffix),sdk,jsonjar]:
 if not dependency.is_file():p.error('Missing dependency: '+str(dependency))
def run(cmd):
 p=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=90);out=p.stdout+p.stderr
 try:text=out.decode('utf-8')
 except UnicodeDecodeError:text=out.decode('gb18030','replace')
 if p.returncode:print(text,flush=True);raise SystemExit(p.returncode)
 return text
run([j/('javac'+suffix),'-source','8','-target','8','-encoding','UTF-8','-cp',str(sdk)+os.pathsep+str(jsonjar),'-d',classes,*files])
results=[]
for scenario in ['chain','map-first-restart-delays','single-map','single-qq','map-timer','qq-timer','service-restart','qq-service-restart','foreign-foreground','screen-off','manual','direct-no-callback','warm-map','kitkat-focus-gap','bad-token','duplicates','cover-regression','manual-cancels-timer','cancelled-ticket','map-delay-boundary','map-repeat-events','map-delay-foreign','map-delay-screen-off','map-delay-manual','map-delay-cancelled-ticket','qq-third-timer','map-third-timer','third-foreign','third-screen-off','third-manual','third-cancelled-ticket','late-map-splash','map-third-exhaustion','qq-third-exhaustion','qq-third-service-restart','map-third-service-restart']:
 text=run([j/('java'+suffix),'-cp',os.pathsep.join(map(str,[classes,jsonjar,sdk])),'cn.hydrogen.launcher.StartupReturnChecks',scenario]);print(text,flush=True);results.append({'scenario':scenario,'output':text})
(w/'jvm-results.json').write_text(json.dumps({'platform':'JVM Android test stubs, not Android or commercial engine execution','scenarios':results},ensure_ascii=False,indent=2),'utf-8')

view_results=[]
for scenario in ['success','saved','failure','timeout','manual','not-ready']:
 text=run([j/('java'+suffix),'-cp',os.pathsep.join(map(str,[classes,jsonjar,sdk])),'DefaultMapViewChecks',scenario]);print(text,flush=True);view_results.append({'scenario':scenario,'output':text})
(w/'default-map-view-results.json').write_text(json.dumps({'platform':'JVM Android test stubs, not commercial engine execution','scenarios':view_results},indent=2),'utf-8')

coordinator_results=[]
for scenario in ['coalesce','timeout','invalid-replies','binder-death','sequence-gates','all-ready','qq-ready-map-cold','qq-cold-map-ready','probe-foreign','probe-screen-off','probe-disabled','first-frame','frame-reattach','frame-focus','frame-closed','frame-disabled','resume-recheck','late-proof','qq-runtime-state','qq-runtime-timeout','map-native-readiness','kitkat-cold-negative','package-change-pending','service-destroy-owner','recovery-after-timeout','qq-aux-process','background-binding','warm-recovery','warm-ready-gates','warm-sequence-race','actual-handshake','qq-subscription-reentry','qq-loss-proof','cold-subscription']:
 text=run([j/('java'+suffix),'-cp',os.pathsep.join(map(str,[classes,jsonjar,sdk])),'cn.hydrogen.launcher.CoordinatorChecks',scenario]);print(text,flush=True);coordinator_results.append({'scenario':scenario,'output':text})
(w/'coordinator-results.json').write_text(json.dumps({'platform':'JVM Android test stubs, not Android/commercial engine execution','scenarios':coordinator_results},indent=2),'utf-8')
reuse_results=[]
for scenario in ['probe-shared-success','probe-shared-timeout','probe-bounded-waiters','map-method-reuse']:
 text=run([j/('java'+suffix),'-cp',os.pathsep.join(map(str,[classes,jsonjar,sdk])),'cn.hydrogen.launcher.ResourceReuseChecks',scenario]);print(text,flush=True);reuse_results.append({'scenario':scenario,'output':text})
(w/'resource-reuse-results.json').write_text(json.dumps({'platform':'JVM Android test stubs, not Android/commercial engine execution','scenarios':reuse_results},indent=2),'utf-8')
all_results=results+view_results+coordinator_results+reuse_results
assertions=sum(int(n) for result in all_results for n in re.findall(r'checks=(\d+)',result['output']))
summary={'platform':'JVM Android test stubs; not Android/commercial engine execution',
 'scenarios':len(all_results),'assertions':assertions,'all_passed':True}
(w/'summary.json').write_text(json.dumps(summary,indent=2),'utf-8')
print(json.dumps(summary),flush=True)
