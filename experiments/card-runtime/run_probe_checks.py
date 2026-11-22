"""Real API 19/1280x720 emulator checks, using only the lab APK and its guest APKs.
No root grants, system window calls, HOME changes or logcat clearing. If adbd is
root, UI commands explicitly DROP to shell UID 2000. No car is auto-selected.
"""
import argparse, hashlib, io, json, pathlib, re, subprocess, time, uuid

p=argparse.ArgumentParser()
p.add_argument('--sdk',required=True)
p.add_argument('--serial',required=True)
p.add_argument('--out',type=pathlib.Path)
a=p.parse_args()
assert a.serial.startswith('emulator-'), 'This coordinate-based test is for an explicitly selected emulator only'
root=pathlib.Path(__file__).resolve().parent
out=a.out.resolve() if a.out else root/'evidence';out.mkdir(parents=True,exist_ok=True)
adb=pathlib.Path(a.sdk)/'platform-tools/adb.exe'
transcript=[];checks={};run_id='probe-'+uuid.uuid4().hex
def run(*args,timeout=30,raw=False):
    r=subprocess.run([str(adb),'-s',a.serial,*map(str,args)],capture_output=True,timeout=timeout)
    if r.returncode:raise RuntimeError((r.stdout+r.stderr).decode('utf-8','replace'))
    if raw:return r.stdout
    text=(r.stdout+r.stderr).decode('utf-8','replace').replace('\r','')
    transcript.append('$ adb '+ ' '.join(map(str,args))+'\n'+text)
    return text
identity=run('shell','id')
shell=['shell','su','2000'] if 'uid=0' in identity else ['shell']
assert 'uid=2000' in run(*shell,'id'), 'Use an authorized shell identity for this emulator test'
assert run(*shell,'getprop','ro.build.version.sdk').strip()=='19'
def logs():
    text=run('logcat','-d','-v','time','-s','CardLab:I','CardProbe:I','AndroidRuntime:E','*:S')
    return text[text.rfind('RUN '+run_id):] if 'RUN '+run_id in text else ''
def wait_for(needle,timeout=12):
    start=time.monotonic()
    while time.monotonic()-start<timeout:
        text=logs()
        if needle in text:return text
        time.sleep(.25)
    raise AssertionError('Not observed: '+needle)
def tap(x,y):run(*shell,'input','tap',x,y);time.sleep(.4)
def screenshot(name):
    remote='/data/local/tmp/'+run_id+'-'+name+'.png'
    file=out/(name+'.png')
    run(*shell,'screencap','-p',remote)
    run('pull',remote,file)
    return file.read_bytes()
try:
    apk=root/'host/build/outputs/apk/debug/host-debug.apk'
    assert 'Success' in run('install','-r',apk,timeout=80)
    run(*shell,'am','force-stop','cn.hydrogen.cardlab')
    run(*shell,'am','start','-W','-n','cn.hydrogen.cardlab/.LabActivity','--es','run',run_id)
    wait_for('PARENT uid=')
    from PIL import Image,ImageChops
    assert Image.open(io.BytesIO(screenshot('probe-host'))).size==(1280,720)
    tap(55,56);wait_for('BOUND cn.hydrogen.probe.left')
    tap(130,56);wait_for('BOUND cn.hydrogen.probe.right')
    time.sleep(.6)
    before=screenshot('probe-dual-before')
    tap(325,174);wait_for('cn.hydrogen.probe.left counter=1')
    tap(950,174);wait_for('cn.hydrogen.probe.right counter=1')
    tap(325,174);wait_for('cn.hydrogen.probe.left counter=2')
    after=screenshot('probe-dual-interactive')
    checks['independent_clicks']=True
    from PIL import Image,ImageChops
    first=Image.open(io.BytesIO(before)).convert('RGB');last=Image.open(io.BytesIO(after)).convert('RGB')
    assert first.size==(1280,720)
    for name,box in [('left',(35,340,620,640)),('right',(660,340,1240,640))]:
        assert ImageChops.difference(first.crop(box),last.crop(box)).getbbox(),name+' Surface frozen'
        checks[name+'_surface_updates']=True
    events=logs()
    checks['touch_resumes_left']=events.count('cn.hydrogen.probe.left RESUME')>=2
    checks['touch_resumes_right']=events.count('cn.hydrogen.probe.right RESUME')>=2
    assert checks['touch_resumes_left'] and checks['touch_resumes_right']
    tap(325,220);wait_for('activity=cn.hydrogen.probe.DetailActivity')
    screenshot('probe-second-activity')
    tap(325,375);tap(950,174);wait_for('cn.hydrogen.probe.right counter=2')
    checks['second_activity_returns_and_other_card_works']=True
    tap(325,270);screenshot('probe-dialog')
    run(*shell,'input','keyevent',4);time.sleep(.3)
    tap(325,314);run(*shell,'input','text','left123');time.sleep(.5)
    screenshot('probe-input-left')
    tap(950,314);run(*shell,'input','text','right456');time.sleep(.5)
    screenshot('probe-input-right')
    tap(325,450);tap(950,450)
    screenshot('probe-final')
    text=logs();(out/'probe-logcat.txt').write_text(text,encoding='utf-8')
    assert 'FATAL EXCEPTION' not in text
    assert 'FOCUS FAILED' not in text
    checks['no_java_crash_in_run']=True
    for kind in ['window windows','activity activities','package cn.hydrogen.cardlab']:
        (out/('probe-'+kind.split()[0]+'.txt')).write_text(run(*shell,'dumpsys',*kind.split()),encoding='utf-8')
    packages=run(*shell,'pm','list','packages')
    assert 'package:cn.hydrogen.probe.left\n' not in packages and 'package:cn.hydrogen.probe.right\n' not in packages
    checks['probes_not_system_installed']=True
    checks['dialogs_and_text_input']='screenshots require visual inspection; not auto-passed'
    checks['software_keyboard_verified']=False
    tap(543,56);time.sleep(1)
    windows=run(*shell,'dumpsys','window','windows')
    assert 'CardLab/cn.hydrogen.probe.' not in windows
    checks['exit_removes_guest_windows']=True
finally:
    (out/'probe-commands.txt').write_text('\n'.join(transcript),encoding='utf-8')
    report={'run_id':run_id,'serial':a.serial,'sdk':19,'shell_identity':run(*shell,'id').strip(),
            'adbd_identity':identity.strip(),'host_apk_sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),
            'checks':checks,'original_amap_qq_verified':False,'whole_screen_scaling_verified':False,
            'physical_unrooted_car_verified':False}
    (out/'probe-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False,indent=2))
