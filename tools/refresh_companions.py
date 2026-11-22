from pathlib import Path
import zipfile,hashlib,json,subprocess,os,re,shutil
import argparse
parser=argparse.ArgumentParser(description="Refresh WindChaser-owned helpers in the matching adapted companion APKs. Windows JDK/SDK tools required.")
parser.add_argument('--map-source',type=Path,required=True)
parser.add_argument('--qq-source',type=Path,required=True)
parser.add_argument('--out',type=Path,required=True)
parser.add_argument('--map-only',action='store_true',help='Refresh only the map helper; preserve the existing QQ APK byte for byte.')
parser.add_argument('--java-home',type=Path,default=Path(os.environ.get('JAVA_HOME','')))
parser.add_argument('--sdk',type=Path,default=Path(os.environ.get('ANDROID_HOME',str(Path.home()/'AppData/Local/Android/Sdk'))))
parser.add_argument('--apktool',type=Path,required=True)
parser.add_argument('--keystore',type=Path,default=Path.home()/'.android/debug.keystore')
parser.add_argument('--key-alias',default='androiddebugkey')
args=parser.parse_args()
R=Path(__file__).resolve().parents[1];B=W=R;A=BUILD=R/'build/companion-refresh';BUILD.mkdir(parents=True,exist_ok=True);OUT=args.out.resolve();OUT.mkdir(parents=True,exist_ok=True)
J=args.java_home;SDK=args.sdk;BT=SDK/'build-tools/35.0.0';TOOL=args.apktool
if args.keystore==Path.home()/'.android/debug.keystore':os.environ.setdefault('WINDCHASER_KEYSTORE_PASSWORD','android')
if not os.environ.get('WINDCHASER_KEYSTORE_PASSWORD'):raise SystemExit('Set WINDCHASER_KEYSTORE_PASSWORD in the environment; no private key is included in this repository.')
os.environ.setdefault('WINDCHASER_KEY_PASSWORD',os.environ['WINDCHASER_KEYSTORE_PASSWORD'])

def run(*args):
 p=subprocess.run([str(x) for x in args],env=dict(os.environ,JAVA_HOME=str(J)),capture_output=True,timeout=240);s=(p.stdout+p.stderr).decode('utf-8','replace');print(s,flush=True);assert p.returncode==0,s;return s
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
classes=BUILD/'classes';classes.mkdir(exist_ok=True);dex=BUILD/'dex';dex.mkdir(exist_ok=True)
for p in classes.rglob('*.class'):p.unlink()
sources=sorted((W/'experiments/map-motion/src').rglob('*.java'))+sorted((W/'experiments/amap65-bridge/src').rglob('*.java'))+sorted((R/'experiments/dual-splash/src').rglob('*.java'))
trust_source=(W/'experiments/amap65-bridge/src/cn/windchaser/mapbridge/AdapterTrust.java').read_text('utf-8')
bridge_revision=int(re.search(r'public static final int BRIDGE_REVISION = (\d+);',trust_source).group(1))
run(J/'bin/javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',SDK/'platforms/android-35/android.jar','-d',classes,*sources)
run(BT/'d8.bat','--min-api','17','--lib',SDK/'platforms/android-35/android.jar','--output',dex,*sorted(classes.rglob('*.class')))
base_map=args.map_source.resolve();base_qq=args.qq_source.resolve()
for file,pkg in [(base_map,'com.autonavi.amapauto'),(base_qq,'com.tencent.qqmusiccar')]:
 with zipfile.ZipFile(file) as archive:
  ready=json.loads(archive.read('assets/windchaser-startup-ready.json'));assert ready['package']==pkg and ready['protocol']==1,'Expected the already adapted WindChaser companion, not an ordinary APK.'
with zipfile.ZipFile(base_map) as archive:
 marker=json.loads(archive.read('assets/windchaser-card-bridge.json'));assert marker['base_version']=='6.5.0.601323' and marker['bridge_revision']>=8

with zipfile.ZipFile(base_map) as z,zipfile.ZipFile(BUILD/'helper.apk','w') as n:
 for name in ['AndroidManifest.xml','resources.arsc']:n.writestr(name,z.read(name))
 n.writestr('classes.dex',(dex/'classes.dex').read_bytes())
helper=BUILD/'helper-decoded';run(J/'bin/java.exe','-jar',TOOL,'d','-r','-f','-o',helper,BUILD/'helper.apk')
reports=[]
for kind,source,root,filename in [('map',base_map,'smali','AmapAuto-6.5.0.601323-WindChaser-R1.apk'),('qq',base_qq,'smali_classes3','QQMusicCar-2.1.1.1-WindChaser-R1.apk')]:
 if args.map_only and kind!='map':continue
 decoded=BUILD/(kind+'-decoded');run(J/'bin/java.exe','-jar',TOOL,'d','-r','-f','-o',decoded,source)
 packages=['startup','mapbridge','motion'] if kind=='map' else ['startup']
 for pkg in packages:
  dest=decoded/root/'cn/windchaser'/pkg;assert dest.resolve().is_relative_to(BUILD.resolve())
  if dest.exists():shutil.rmtree(dest)
  shutil.copytree(helper/'smali/cn/windchaser'/pkg,dest)
 if kind=='map':
  protocol=decoded/'smali/com/autonavi/amapauto/jni/protocol/AndroidProtocolExe.smali'
  text=protocol.read_text('utf-8')
  for signature,hook in [
   ('notifyReadyStatus(Z)V','    invoke-static {p0}, Lcn/windchaser/mapbridge/DefaultMapView;->onReady(Z)V'),
   ('jniSendViewControlStatus(IZ)V','    invoke-static {p0, p1}, Lcn/windchaser/mapbridge/DefaultMapView;->onViewResult(IZ)V')]:
   method=re.search(r'(?m)^\.method public static '+re.escape(signature)+r'\n.*?^\.end method',text,re.S);assert method,signature
   body=method[0]
   if hook not in body:
    body=re.sub(r'(    \.locals \d+\n)',lambda match:match[0]+'\n'+hook+'\n',body,count=1)
   text=text[:method.start()]+body+text[method.end():]
  protocol.write_text(text,'utf-8')
  p=decoded/'smali/wl0.smali';s=p.read_text('utf-8');needle='    invoke-static {v0}, Lcn/windchaser/startup/DualSplash;->mapSplashAdded(Landroid/app/Activity;)V';assert s.count(needle)==1 or '->mapSplashState(Landroid/app/Activity;Landroid/view/View;)V' in s
  s=s.replace(needle,'    iget-object v1, p0, Lwl0;->a:Landroid/widget/ImageView;\n\n    invoke-static {v0, v1}, Lcn/windchaser/startup/DualSplash;->mapSplashState(Landroid/app/Activity;Landroid/view/View;)V')
  m=re.search(r'(?m)^\.method [^\n]* q\(\)V\n.*?^\.end method',s,re.S);assert m
  code=m[0];hook='    invoke-interface {p0}, Llm0;->l()Lzl0;\n\n    move-result-object v0\n\n    invoke-interface {v0}, Lzl0;->getActivity()Landroid/app/Activity;\n\n    move-result-object v0\n\n    invoke-static {v0}, Lcn/windchaser/startup/DualSplash;->mapSplashRemoved(Landroid/app/Activity;)V'
  assert code.count(hook)==1;code=code.replace(hook,'').replace('    return-void',hook+'\n\n    return-void');s=s[:m.start()]+code+s[m.end():];p.write_text(s,'utf-8')
 if kind=='qq':
  # This service lives in secondary dex, after KitKat multidex has been installed.
  # Register the read-only probe even when the ROM restores only the public API service.
  service=decoded/'smali_classes2/com/tencent/qqmusiccar/third/api/QQMusicApiService.smali'
  text=service.read_text('utf-8');method=re.search(r'(?m)^\.method public onCreate\(\)V\n.*?^\.end method',text,re.S);assert method
  hook='    invoke-static {p0}, Lcn/windchaser/startup/RuntimeProbe;->install(Landroid/content/Context;)V'
  body=method[0]
  if hook not in body:body=body.replace('    return-void',hook+'\n\n    return-void')
  text=text[:method.start()]+body+text[method.end():];service.write_text(text,'utf-8')
 if kind=='map':
  # Keep the original platform-manager test and all original signature checks.
  # Permit only the explicitly signed, version-pinned same-UID experimental host dispatcher.
  check=decoded/'smali/v80.smali';content=check.read_text('utf-8')
  match=re.search(r'(?m)^\.method public static b\(Landroid/content/Context;\)Z\n.*?^\.end method',content,re.S);assert match
  body=match[0];assert 'isTrustedVirtualPackageManager' not in body
  body=body.replace('    .locals 2','    .locals 3\n\n    move-object v2, p0',1)
  body=body.replace('    return p0','    if-nez p0, :windchaser_manager_done\n\n    invoke-static {v2}, Lcn/windchaser/mapbridge/AdapterTrust;->isTrustedVirtualPackageManager(Landroid/content/Context;)Z\n\n    move-result p0\n\n    :windchaser_manager_done\n    return p0',1)
  content=content[:match.start()]+body+content[match.end():];check.write_text(content,'utf-8')
 run(J/'bin/java.exe','-jar',TOOL,'b','--no-apk',decoded)
 replacements={p.name:p.read_bytes() for p in (decoded/'build/apk').glob('classes*.dex') if (kind=='map' and p.name=='classes.dex') or (kind=='qq' and p.name in ('classes2.dex','classes3.dex'))}
 with zipfile.ZipFile(source) as z:
  ready=json.loads(z.read('assets/windchaser-startup-ready.json'));ready.update(completion='artwork end immediate; bounded retries; PendingIntent, Messenger and HOME',ready_revision=7,interface_probe_version=1,final_app_direct_home=True,ready_pending_intent=True,primary_delay_ms=0,fallback_delay_ms=500 if kind=='map' else 650,final_fallback_delay_ms=5000 if kind=='map' else 3000,fallback_offsets_ms=[500,2350,5000] if kind=='map' else [650,3000]);ready_bytes=json.dumps(ready).encode();assert len(ready_bytes)<=512
  if not args.map_only:replacements['assets/windchaser-startup-ready.json']=ready_bytes
  if kind=='map':
   marker=json.loads(z.read('assets/windchaser-card-bridge.json'));marker.update(bridge_revision=bridge_revision,signature_protected_window_commands=True,guarded_independent_close=True,retry_after_initialization=True,physical_car_verified=False,retained_system_window=True,coalesced_window_motion=True,layout_confirmed_timeline=True,timeline_protocol=1,initial_view='3D head-up',initial_view_revision=1,initial_view_native_ack=True);replacements['assets/windchaser-card-bridge.json']=json.dumps(marker).encode()
 unsigned=BUILD/(kind+'-unsigned.apk');aligned=BUILD/(kind+'-aligned.apk');output=OUT/filename
 with zipfile.ZipFile(source) as old,zipfile.ZipFile(unsigned,'w') as new:
  for info in old.infolist():
   if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',info.filename,re.I):continue
   new.writestr(info,replacements.get(info.filename,old.read(info.filename)))
 run(BT/'zipalign.exe','-p','-f','4',unsigned,aligned)
 run(J/'bin/java.exe','-jar',BT/'lib/apksigner.jar','sign','--ks',args.keystore,'--ks-key-alias',args.key_alias,'--ks-pass','env:WINDCHASER_KEYSTORE_PASSWORD','--key-pass','env:WINDCHASER_KEY_PASSWORD','--v1-signing-enabled','true','--v2-signing-enabled','true','--v4-signing-enabled','false','--out',output,aligned)
 def certificate(file):
  text=run(J/'bin/java.exe','-jar',BT/'lib/apksigner.jar','verify','--min-sdk-version','19','--max-sdk-version','19','--print-certs',file)
  return re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',text).group(1)
 assert certificate(source)==certificate(output),'A matching existing signing key is required; this tool does not change the map startup certificate policy.'
 with zipfile.ZipFile(source) as old,zipfile.ZipFile(output) as new:
  changed=[n for n in old.namelist() if not n.startswith('META-INF/') and old.read(n)!=new.read(n)];assert set(changed).issubset(replacements),changed
  libs=[n for n in old.namelist() if n.startswith('lib/')];assert all(old.read(n)==new.read(n) for n in libs)
 reports.append({'package':kind,'base_apk_sha256':sha(source),'apk_sha256':sha(output),'bytes':output.stat().st_size,'changed_entries':changed,'native_count':len(libs),'manifest_ui_resources_native_binaries_unchanged':True,'physical_car_verified':False,'commercial_engine_runtime_tested':False})
(A/'latest-companions.json').write_text(json.dumps({'apks':reports,'source_sha256':{str(p.relative_to(B)).replace('\\','/'):sha(p) for p in sources}},ensure_ascii=False,indent=2),'utf-8');print(json.dumps(reports,ensure_ascii=False),flush=True)
