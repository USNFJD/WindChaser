"""Refresh only the read-only live-probe classes in matching unified modules."""
from pathlib import Path
import argparse,os,subprocess,json,zipfile,hashlib,shutil,re
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--apktool',type=Path,required=True);a=p.parse_args()
s=Path(__file__).resolve().parents[1];w=a.work.resolve();w.mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME']);bt=sdk/'build-tools/35.0.0'
def run(*cmd):
 r=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=300);t=(r.stdout+r.stderr).decode('utf-8','replace');print(t,flush=True)
 if r.returncode:raise RuntimeError(str(cmd[0])+' failed')
def sha(p):
 with open('\\\\?\\'+str(p.resolve()),'rb') as f:return hashlib.sha256(f.read()).hexdigest()
assets=s/'app/src/main/unifiedAssets/unified';manifest=assets/'modules.json';data=json.loads(manifest.read_text('utf-8'))
classes=w/'classes';classes.mkdir(exist_ok=True);dex=w/'dex';dex.mkdir(exist_ok=True)
run(jdk/'bin/javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',sdk/'platforms/android-35/android.jar','-d',classes,*sorted((s/'experiments/dual-splash/src').rglob('*.java')),*sorted((s/'experiments/startup-ui/src').rglob('*.java')))
run(bt/'d8.bat','--min-api','17','--lib',sdk/'platforms/android-35/android.jar','--output',dex,*sorted(classes.rglob('*.class')))
with zipfile.ZipFile(assets/data['modules'][0]['file']) as old,zipfile.ZipFile(w/'helper.apk','w') as new:
 for n in ['AndroidManifest.xml','resources.arsc']:new.writestr(n,old.read(n))
 new.writestr('classes.dex',(dex/'classes.dex').read_bytes())
run(jdk/'bin/java.exe','-jar',a.apktool,'d','-r','-f','-o',w/'helper',w/'helper.apk')
helper=w/'helper/smali/cn/windchaser/startup';audit=[]
for m in data['modules']:
 original=assets/m['file'];baseline=w/(m['package']+'-before.apk')
 if not baseline.exists():shutil.copy2(original,baseline)
 decoded=w/(m['package']+'-decoded');run(jdk/'bin/java.exe','-jar',a.apktool,'d','-r','-f','-o',decoded,baseline)
 before={str(f.relative_to(decoded)):sha(f) for f in decoded.glob('smali*/**/*.smali')}
 candidates=list(decoded.glob('smali*/cn/windchaser/startup/RuntimeProbe.smali'));assert len(candidates)==1
 dest=candidates[0].parent;assert dest.resolve().is_relative_to(w)
 for f in dest.glob('RuntimeProbe*.smali'):f.unlink()
 for f in helper.glob('RuntimeProbe*.smali'):shutil.copy2(f,dest/f.name)
 after={str(f.relative_to(decoded)):sha(f) for f in decoded.glob('smali*/**/*.smali')}
 changed=sorted(k for k in before.keys()|after.keys() if before.get(k)!=after.get(k))
 assert changed and all('/cn/windchaser/startup/RuntimeProbe' in k.replace('\\','/') for k in changed),changed
 run(jdk/'bin/java.exe','-jar',a.apktool,'b','--no-apk',decoded)
 root=candidates[0].relative_to(decoded).parts[0];name='classes.dex' if root=='smali' else root.replace('smali_','')+'.dex'
 replacement=(decoded/'build/apk'/name).read_bytes();unsigned=w/(m['package']+'-unsigned.apk')
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(unsigned,'w') as new:
  for info in old.infolist():
   if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',info.filename,re.I):continue
   new.writestr(info,replacement if info.filename==name else old.read(info.filename))
 aligned=w/(m['package']+'-aligned.apk');signed=w/(m['package']+'-signed.apk')
 run(bt/'zipalign.exe','-p','-f','4',unsigned,aligned)
 run(bt/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--v1-signing-enabled','true','--v2-signing-enabled','true','--out',signed,aligned)
 run(bt/'apksigner.bat','verify','--min-sdk-version','17','--verbose','--print-certs',signed)
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(signed) as new:
  assert new.testzip() is None
  retained=[n for n in old.namelist() if n!=name and not n.startswith('META-INF/')]
  assert all(old.read(n)==new.read(n) for n in retained)
 audit.append({'package':m['package'],'before':sha(baseline),'after':sha(signed),'changed_dex_entries':[name],'changed_classes':changed,'native_and_other_classes_unchanged':True,'other_entries_identical':True})
 shutil.copy2(signed,original);m['sha256']=sha(original);m['bytes']=original.stat().st_size
manifest.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf-8')
(w/'module-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n','utf-8')
print('Only RuntimeProbe helper classes refreshed. Native classes, resources and manifest preserved.')
