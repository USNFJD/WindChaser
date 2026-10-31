"""Rebuild only the companion startup helper DEX in the local single-APK experiment.
Requires the user's matching companion APKs, Android SDK tools and a JDK.
Does not change manifests, native engines, initialization, privacy or permission state.
"""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile,re,struct,zlib

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--work',type=Path,required=True);ap.add_argument('--apktool',type=Path,required=True);args=ap.parse_args()
    source=Path(__file__).resolve().parents[1];work=args.work.resolve();work.mkdir(parents=True,exist_ok=True)
    sdk=Path(os.environ['ANDROID_HOME']);jdk=Path(os.environ['JAVA_HOME']);bt=sdk/'build-tools/35.0.0'
    def run(*cmd):
        p=subprocess.run([str(v) for v in cmd],capture_output=True,timeout=300)
        text=(p.stdout+p.stderr).decode('utf-8','replace');print(text,flush=True)
        if p.returncode:raise RuntimeError(f'{cmd[0]} failed ({p.returncode})')
    def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
    assets=source/'app/src/main/unifiedAssets/unified';manifest=assets/'modules.json';data=json.loads(manifest.read_text('utf-8-sig'))
    classes=work/'classes';classes.mkdir(exist_ok=True);dex=work/'dex';dex.mkdir(exist_ok=True)
    run(jdk/'bin/javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',sdk/'platforms/android-35/android.jar','-d',classes,*sorted((source/'experiments/dual-splash/src').rglob('*.java')),*sorted((source/'experiments/startup-ui/src').rglob('*.java')),*sorted((source/'experiments/amap65-bridge/src').rglob('*.java')),*sorted((source/'experiments/map-motion/src').rglob('*.java')))
    run(bt/'d8.bat','--min-api','17','--lib',sdk/'platforms/android-35/android.jar','--output',dex,*sorted(classes.rglob('*.class')))
    with zipfile.ZipFile(assets/data['modules'][1]['file']) as old,zipfile.ZipFile(work/'helper.apk','w') as new:
        for n in ('AndroidManifest.xml','resources.arsc'):new.writestr(n,old.read(n))
        new.writestr('classes.dex',(dex/'classes.dex').read_bytes())
    run(jdk/'bin/java.exe','-jar',args.apktool,'d','-r','-f','-o',work/'helper',work/'helper.apk')
    helper=work/'helper/smali/cn/windchaser/startup';audit=[]
    for module in data['modules']:
        original=assets/module['file'];baseline=work/(module['package']+'-before.apk')
        if not baseline.exists():shutil.copy2(original,baseline)
        decoded=work/(module['package']+'-decoded')
        run(jdk/'bin/java.exe','-jar',args.apktool,'d','-r','-f','-o',decoded,baseline)
        before={str(p.relative_to(decoded)):sha(p) for p in decoded.glob('smali*/**/*.smali')}
        dests=list(decoded.glob('smali*/cn/windchaser/startup/DualSplash.smali'));assert len(dests)==1,dests
        dest=dests[0].parent
        for p in helper.glob('*.smali'):shutil.copy2(p,dest/p.name)
        if module['package']=='com.autonavi.amapauto':
            adapter=work/'helper/smali/cn/windchaser/mapbridge'
            targets=list(decoded.glob('smali*/cn/windchaser/mapbridge/MapCardBridge.smali'));assert len(targets)==1
            for p in adapter.glob('*.smali'):shutil.copy2(p,targets[0].parent/p.name)
        native_changed=[]
        if module['package']=='com.tencent.qqmusiccar':
            targets=list(decoded.glob('smali*/com/tencent/qqmusiccar/v2/fragment/player/PlayerFragment.smali'));assert len(targets)==1
            player=targets[0];text=player.read_text('utf-8')
            signature='.method public onCreateView(Landroid/view/LayoutInflater;Landroid/view/ViewGroup;Landroid/os/Bundle;)Landroid/view/View;'
            start=text.index(signature);end=text.index('.end method',start);method=text[start:end]
            anchor='    return-object p1';assert method.count(anchor)==1
            hook='    invoke-static {p1}, Lcn/windchaser/startup/QqHome;->installPlayer(Landroid/view/View;)V\n\n'
            assert method.count('QqHome;->installPlayer') in (0,1)
            updated=method if hook in method else method.replace(anchor,hook+anchor)
            assert updated.count(hook)==1 and updated.replace(hook,'')==method.replace(hook,'')
            player.write_text(text[:start]+updated+text[end:],'utf-8')
            native_changed=[str(player.relative_to(decoded))]
            # Only the native fixed-layout auto-hide heuristic is bypassed, and
            # only while QqHome owns this exact measured footer. Its original
            # method body and fallback remain byte-for-byte unchanged.
            bars=list(decoded.glob('smali*/com/tencent/qqmusiccar/v2/view/PlayBar.smali'));assert len(bars)==1
            bar=bars[0];text=bar.read_text('utf-8');signature='.method private final M()V'
            start=text.index(signature);end=text.index('.end method',start);method=text[start:end]
            anchor='    .locals 9\n';assert method.count(anchor)==1 and ':windchaser_native_footer' not in method
            hook=('\n    invoke-static {p0}, Lcn/windchaser/startup/QqHome;->ownsHomeLayout(Landroid/view/View;)Z\n'
                '    move-result v0\n    if-eqz v0, :windchaser_native_footer\n    return-void\n'
                '    :windchaser_native_footer\n')
            updated=method.replace(anchor,anchor+hook,1);assert updated.replace(hook,'')==method
            bar.write_text(text[:start]+updated+text[end:],'utf-8');native_changed.append(str(bar.relative_to(decoded)))
        after={str(p.relative_to(decoded)):sha(p) for p in decoded.glob('smali*/**/*.smali')}
        changed=sorted(k for k in set(before)|set(after) if before.get(k)!=after.get(k))
        assert changed and all(k in native_changed or any(part in k.replace('\\','/') for part in ('/cn/windchaser/startup/','/cn/windchaser/mapbridge/')) for k in changed)
        run(jdk/'bin/java.exe','-jar',args.apktool,'b','--no-apk',decoded)
        roots={Path(name).parts[0] for name in changed}
        replacements={}
        for root in sorted(roots):
            name='classes.dex' if root=='smali' else root.replace('smali_','')+'.dex'
            replacement=decoded/'build/apk'/name;assert replacement.exists(),replacement
            replacements[name]=replacement.read_bytes()
        assert replacements
        # QQ's theme engine can reapply the background after onCreate. Its
        # dedicated splash_logo/light bitmaps are used only by startup layouts.
        # Blank those exact images while retaining dimensions and the theme path.
        startup_resources={}
        if module['package']=='com.tencent.qqmusiccar':
            resource_dump=subprocess.run([str(bt/'aapt.exe'),'dump','--values','resources',str(baseline)],capture_output=True,check=True).stdout.decode('utf-8','replace')
            paths=set(re.findall(r'resource 0x[0-9a-f]+ [^\n]*:drawable/splash_logo(?:_light)?:[^\n]*\n\s*\(string8\) "([^"]+\.png)"',resource_dump))
            assert len(paths)>=2,'Missing pinned QQ startup images'
            def chunk(kind,data):return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
            with zipfile.ZipFile(baseline) as original_zip:
                for name in paths:
                    original_png=original_zip.read(name);assert original_png.startswith(b'\x89PNG\r\n\x1a\n')
                    width,height=struct.unpack('>II',original_png[16:24]);assert 0<width<=4096 and 0<height<=4096
                    pixels=zlib.compress((b'\0'+b'\0'*(width*4))*height,9)
                    startup_resources[name]=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',width,height,8,6,0,0,0))+chunk(b'IDAT',pixels)+chunk(b'IEND',b'')
        unsigned=work/(module['package']+'-unsigned.apk')
        with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(unsigned,'w') as new:
            for info in old.infolist():
                if re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))',info.filename,re.I):continue
                new.writestr(info,replacements.get(info.filename,startup_resources.get(info.filename,old.read(info.filename))))
        aligned=work/(module['package']+'-aligned.apk');signed=work/(module['package']+'-signed.apk')
        run(bt/'zipalign.exe','-p','-f','4',unsigned,aligned)
        run(bt/'apksigner.bat','sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--v1-signing-enabled','true','--v2-signing-enabled','true','--out',signed,aligned)
        run(bt/'apksigner.bat','verify','--min-sdk-version','17','--verbose','--print-certs',signed)
        with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(signed) as new:
            assert new.testzip() is None
            assert all(new.read(name)==value for name,value in replacements.items())
            retained=[n for n in old.namelist() if n not in replacements and n not in startup_resources and not n.startswith('META-INF/')]
            assert all(old.read(n)==new.read(n) for n in retained)
        audit.append({'package':module['package'],'before':sha(baseline),'after':sha(signed),'changed_dex_entries':sorted(replacements),'changed_classes':changed,'native_hook_classes':native_changed,'changed_startup_resources':sorted(startup_resources),'other_entries_identical':True})
        shutil.copy2(signed,original);module['sha256']=sha(original);module['bytes']=original.stat().st_size
    manifest.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n','utf-8')
    (work/'module-audit.json').write_text(json.dumps(audit,ensure_ascii=False,indent=2)+'\n','utf-8')
    print('Nested startup helper/artwork rebuilt; other APK entries unchanged.',flush=True)
if __name__=='__main__':main()
