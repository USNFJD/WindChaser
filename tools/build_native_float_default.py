"""Rebuild only the one-time native information-float setting helper in the user's map APK.
The existing manifest, native engines, UI resources and QQ module are retained.
"""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile,re,shutil

def main():
 p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--apktool',type=Path,required=True);a=p.parse_args()
 source=Path(__file__).resolve().parents[1];work=a.work.resolve();work.mkdir(parents=True,exist_ok=True)
 sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME']);bt=sdk/'build-tools/35.0.0'
 def run(*cmd):
  r=subprocess.run([str(v) for v in cmd],capture_output=True,timeout=300);text=(r.stdout+r.stderr).decode('utf-8','replace');print(text,flush=True)
  if r.returncode:raise RuntimeError(f'{cmd[0]} failed ({r.returncode})')
 def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
 assets=source/'app/src/main/unifiedAssets/unified';spec=assets/'modules.json';data=json.loads(spec.read_text('utf-8-sig'))
 module=next(m for m in data['modules'] if m['package']=='com.autonavi.amapauto');qq=next(m for m in data['modules'] if m['package']=='com.tencent.qqmusiccar');qq_before=sha(assets/qq['file'])
 original=assets/module['file'];baseline=work/'map-before.apk'
 if baseline.exists():assert sha(baseline)==module['sha256'],'Use a fresh work directory when the source APK changes'
 else:shutil.copy2(original,baseline)
 assert sha(baseline)==module['sha256']
 classes=work/'classes';classes.mkdir(exist_ok=True);dex=work/'dex';dex.mkdir(exist_ok=True)
 helpers=[source/'experiments/amap65-bridge/src/cn/windchaser/mapbridge'/n for n in ['DefaultMapView.java','NativeFloatDefaults.java']]
 run(jdk/'bin/javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',sdk/'platforms/android-35/android.jar','-d',classes,*helpers)
 run(bt/'d8.bat','--min-api','17','--lib',sdk/'platforms/android-35/android.jar','--output',dex,*sorted(classes.rglob('*.class')))
 with zipfile.ZipFile(baseline) as z,zipfile.ZipFile(work/'helper.apk','w') as out:
  for n in ['AndroidManifest.xml','resources.arsc']:out.writestr(n,z.read(n))
  out.writestr('classes.dex',(dex/'classes.dex').read_bytes())
 run(jdk/'bin/java.exe','-jar',a.apktool,'d','-r','-f','-o',work/'helper',work/'helper.apk')
 decoded=work/'map-decoded';run(jdk/'bin/java.exe','-jar',a.apktool,'d','-r','-f','-o',decoded,baseline)
 before={f.relative_to(decoded).as_posix():sha(f) for f in decoded.glob('smali*/**/*.smali')}
 targets=list(decoded.glob('smali*/cn/windchaser/mapbridge/DefaultMapView.smali'));assert len(targets)==1
 helper=work/'helper/smali/cn/windchaser/mapbridge'
 for f in helper.glob('*.smali'):shutil.copy2(f,targets[0].parent/f.name)
 after={f.relative_to(decoded).as_posix():sha(f) for f in decoded.glob('smali*/**/*.smali')}
 changed=sorted(k for k in set(before)|set(after) if before.get(k)!=after.get(k))
 assert changed and all(re.search(r'/cn/windchaser/mapbridge/(?:DefaultMapView|NativeFloatDefaults)(?:\$[^/]*)?\.smali$',k) for k in changed),changed
 run(jdk/'bin/java.exe','-jar',a.apktool,'b','--no-apk',decoded)
 roots={Path(n).parts[0] for n in changed};replacements={}
 for root in roots:
  name='classes.dex' if root=='smali' else root.replace('smali_','')+'.dex';replacements[name]=(decoded/'build/apk'/name).read_bytes()
 unsigned=work/'map-unsigned.apk'
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(unsigned,'w') as new:
  for info in old.infolist():
   if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',info.filename,re.I):continue
   new.writestr(info,replacements.get(info.filename,old.read(info.filename)))
 aligned=work/'map-aligned.apk';signed=work/'map-signed.apk'
 run(bt/'zipalign.exe','-p','-f','4',unsigned,aligned)
 run(bt/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--v1-signing-enabled','true','--v2-signing-enabled','true','--out',signed,aligned)
 run(bt/'apksigner.bat','verify','--min-sdk-version','17','--verbose','--print-certs',signed)
 with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(signed) as new:
  assert new.testzip() is None
  retained=[n for n in old.namelist() if n not in replacements and not n.startswith('META-INF/')]
  assert all(new.read(n)==old.read(n) for n in retained)
 shutil.copy2(signed,original);module['sha256']=sha(original);module['bytes']=original.stat().st_size
 spec.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf-8')
 assert sha(assets/qq['file'])==qq_before
 audit={'package':module['package'],'before':sha(baseline),'after':sha(signed),'changed_dex_entries':sorted(replacements),'changed_classes':changed,'native_libraries_resources_manifest_unchanged':True,'all_other_zip_entries_unchanged':True,'qq_unchanged':True}
 (work/'module-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n','utf-8');print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
