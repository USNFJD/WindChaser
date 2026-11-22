"""Disable only QQ home-screen's automatic promotional login prompt.
Manual sign-in UI, account restoration, access checks, playback and privacy prompts are retained.
"""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile,re,shutil

def main():
 p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--apktool',type=Path,required=True);a=p.parse_args()
 source=Path(__file__).resolve().parents[1];work=a.work.resolve();work.mkdir(parents=True,exist_ok=True)
 sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME']);bt=sdk/'build-tools/35.0.0'
 def run(*cmd):
  r=subprocess.run([str(v) for v in cmd],capture_output=True,timeout=300);print((r.stdout+r.stderr).decode('utf-8','replace'),flush=True)
  if r.returncode:raise RuntimeError(str(cmd[0])+' failed')
 def sha(f):return hashlib.sha256(f.read_bytes()).hexdigest()
 assets=source/'app/src/main/unifiedAssets/unified';spec=assets/'modules.json';data=json.loads(spec.read_text('utf-8'))
 module=next(m for m in data['modules'] if m['package']=='com.tencent.qqmusiccar');amap=next(m for m in data['modules'] if m['package']=='com.autonavi.amapauto');map_before=sha(assets/amap['file'])
 original=assets/module['file'];baseline=work/'qq-before.apk'
 if baseline.exists():assert sha(baseline)==module['sha256'],'Use a fresh work directory if the source module changed'
 else:shutil.copy2(original,baseline)
 assert sha(baseline)==module['sha256']
 decoded=work/'qq-decoded';run(jdk/'bin/java.exe','-jar',a.apktool,'d','-r','-f','-o',decoded,baseline)
 before={f.relative_to(decoded).as_posix():sha(f) for f in decoded.glob('smali*/**/*.smali')}
 targets=list(decoded.glob('smali*/com/tencent/qqmusiccar/v2/activity/home/MainFragment.smali'));assert len(targets)==1
 target=targets[0];text=target.read_text('utf-8')
 methods=list(re.finditer(r'(?m)^\.method private final e0\(\)V\r?\n[\s\S]*?^\.end method',text));assert len(methods)==1
 old=methods[0].group(0)
 assert 'showLoginDialogIfNeed' in old and 'MainFragment$showLoginDialogIfNeed$1;' in old
 assert 'UserManager;->getLoginState()I' in old and 'LoginPreference;->getLastLoginQq()Ljava/lang/String;' in old
 new='.method private final e0()V\n    .locals 0\n\n    # WindChaser: home startup must not automatically present a login prompt.\n    # Manual LoginDialog and all account/playback code remain untouched.\n    return-void\n.end method'
 changed_text=text[:methods[0].start()]+new+text[methods[0].end():]
 assert changed_text.replace(new,'',1)==text.replace(old,'',1)
 target.write_text(changed_text,'utf-8')
 after={f.relative_to(decoded).as_posix():sha(f) for f in decoded.glob('smali*/**/*.smali')}
 changed=sorted(k for k in set(before)|set(after) if before.get(k)!=after.get(k));assert changed==[target.relative_to(decoded).as_posix()]
 run(jdk/'bin/java.exe','-jar',a.apktool,'b','--no-apk',decoded)
 root=target.relative_to(decoded).parts[0];dex_name='classes.dex' if root=='smali' else root.replace('smali_','')+'.dex';replacement=(decoded/'build/apk'/dex_name).read_bytes()
 unsigned=work/'qq-unsigned.apk'
 with zipfile.ZipFile(baseline) as oldzip,zipfile.ZipFile(unsigned,'w') as newzip:
  for info in oldzip.infolist():
   if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',info.filename,re.I):continue
   newzip.writestr(info,replacement if info.filename==dex_name else oldzip.read(info.filename))
 aligned=work/'qq-aligned.apk';signed=work/'qq-signed.apk'
 run(bt/'zipalign.exe','-p','-f','4',unsigned,aligned)
 run(bt/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--v1-signing-enabled','true','--v2-signing-enabled','true','--out',signed,aligned)
 run(bt/'apksigner.bat','verify','--min-sdk-version','17','--verbose','--print-certs',signed)
 with zipfile.ZipFile(baseline) as oldzip,zipfile.ZipFile(signed) as newzip:
  assert newzip.testzip() is None
  retained=[n for n in oldzip.namelist() if n!=dex_name and not n.startswith('META-INF/')]
  assert all(oldzip.read(n)==newzip.read(n) for n in retained)
 # Verify the real final DEX disassembly, not merely the pre-build edit.
 final=work/'qq-final-decoded';run(jdk/'bin/java.exe','-jar',a.apktool,'d','-r','-f','-o',final,signed)
 final_map={f.relative_to(final).as_posix():sha(f) for f in final.glob('smali*/**/*.smali')}
 final_changed=sorted(k for k in set(before)|set(final_map) if before.get(k)!=final_map.get(k));assert final_changed==changed,final_changed
 final_text=(final/target.relative_to(decoded)).read_text('utf-8');final_method=re.search(r'(?m)^\.method private final e0\(\)V\r?\n[\s\S]*?^\.end method',final_text).group(0)
 assert 'return-void' in final_method and 'invoke-' not in final_method and 'new-instance' not in final_method
 shutil.copy2(signed,original);module['sha256']=sha(original);module['bytes']=original.stat().st_size;spec.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf-8')
 assert sha(assets/amap['file'])==map_before
 audit={'package':module['package'],'before':sha(baseline),'after':sha(signed),'changed_dex_entries':[dex_name],'changed_classes':changed,'changed_method':'MainFragment.e0()V (showLoginDialogIfNeed)','only_automatic_home_prompt_removed':True,'manual_login_classes_unchanged':True,'login_managers_and_playback_classes_unchanged':True,'all_other_methods_in_MainFragment_unchanged':True,'native_libraries_resources_manifest_unchanged':True,'all_other_zip_entries_unchanged':True,'amap_unchanged':True,'final_apk_disassembly_verified':True}
 (work/'module-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n','utf-8');print(json.dumps(audit,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
