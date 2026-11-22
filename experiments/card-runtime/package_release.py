"""Package this independent lab only, with matching test evidence and LGPL source."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import shutil
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--out', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parent
out = args.out.resolve() if args.out else root.parents[2] / '交付/应用虚拟化窗口实验'
apk = root / 'host/build/outputs/apk/debug/host-debug.apk'
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
report = json.loads((root / 'evidence/probe-results.json').read_text(encoding='utf-8'))
visual = json.loads((root / 'evidence/visual-review.json').read_text(encoding='utf-8'))
assert report['host_apk_sha256'] == visual['host_apk_sha256'] == digest, 'Evidence belongs to another APK'
assert report['run_id'] == visual['run_id'], 'Visual review belongs to another test run'
for key in ('independent_clicks', 'left_surface_updates', 'right_surface_updates',
            'touch_resumes_left', 'touch_resumes_right',
            'second_activity_returns_and_other_card_works', 'no_java_crash_in_run',
            'probes_not_system_installed', 'exit_removes_guest_windows'):
    assert report['checks'][key] is True, 'Not verified: ' + key
assert visual['dialog_within_left_card'] and visual['second_activity_within_left_card']
assert visual['left_text'] == 'left123' and visual['right_text'] == 'right456'
with zipfile.ZipFile(apk) as app:
    bundled_apks = {name for name in app.namelist() if name.lower().endswith('.apk')}
    assert bundled_apks == {'assets/probe-left.apk', 'assets/probe-right.apk'}, bundled_apks

out.mkdir(parents=True, exist_ok=True)
name = 'HydrogenCardLab-0.1-api19.apk'
shutil.copy2(apk, out / name)
shutil.copy2(root / 'README.md', out / '安装与试用说明.md')
shutil.copy2(root.parents[1] / 'docs/虚拟化窗口原型实测.md', out / '原型实测报告.md')
shutil.copy2(root / 'evidence/probe-final.png', out / '两个独立测试APK-实测.png')
shutil.copy2(root / 'evidence/probe-results.json', out / '探针结果.json')

identity = {
    'package': 'cn.hydrogen.cardlab', 'version': '0.1-experiment', 'minSdk': 19,
    'runtime_entry_api': [19], 'apk': name, 'apk_sha256': digest,
    'apk_bytes': apk.stat().st_size, 'probe_run_id': report['run_id'],
    'packaged_at_utc': datetime.now(timezone.utc).isoformat(timespec='seconds'),
    'original_amap_qq_verified': False, 'whole_screen_scaling_verified': False,
    'physical_unrooted_car_verified': False,
    'production_launcher_modified': False,
}
(out / '交付清单.json').write_text(json.dumps(identity, ensure_ascii=False, indent=2), encoding='utf-8')

excluded = {'build', '.gradle', '.idea', '.git', '__pycache__'}
archive = out / 'HydrogenCardLab-0.1-完整源码与证据.zip'
with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=7) as target:
    for file in sorted(root.rglob('*')):
        relative = file.relative_to(root)
        if not file.is_file() or any(part in excluded for part in relative.parts):
            continue
        if file.name in {'local.properties', '.env', 'standalone-build.log'} or file.suffix in {'.apk', '.jks', '.keystore', '.pyc'}:
            continue
        target.write(file, 'card-runtime/' + relative.as_posix())
    target.write(root.parents[1] / 'docs/虚拟化窗口原型实测.md', 'card-runtime/原型实测报告.md')
    target.write(apk, 'card-runtime/apk/' + name)

with zipfile.ZipFile(archive) as source:
    assert source.testzip() is None, 'Source ZIP integrity failure'
    assert hashlib.sha256(source.read('card-runtime/apk/' + name)).hexdigest() == digest
    for required in ('COPYING', 'COPYING.LESSER', 'THIRD_PARTY_NOTICES.md',
                     'gradle/wrapper/gradle-wrapper.jar', 'upstream/LICENSE',
                     'upstream/project/Libraries/DroidPlugin/src/main/java/com/morgoo/droidplugin/card/CardWindowAdapter.java'):
        assert 'card-runtime/' + required in source.namelist(), required

lines = []
for file in sorted(out.iterdir()):
    if file.is_file() and file.name != 'SHA256.txt':
        lines.append(hashlib.sha256(file.read_bytes()).hexdigest() + '  ' + file.name)
(out / 'SHA256.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
print(json.dumps({'out': str(out), 'apk_sha256': digest, 'zip_bytes': archive.stat().st_size}, ensure_ascii=False, indent=2))
