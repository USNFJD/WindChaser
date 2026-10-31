"""Verify fail-closed ABI handling on the explicitly selected API 19 x86 emulator.

Copies the two original APKs unchanged for inspection. Does not try to execute ARM
code, patch a guest, grant privileges or select a physical device automatically.
"""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import time
import uuid

parser = argparse.ArgumentParser()
parser.add_argument('--sdk', required=True, type=Path)
parser.add_argument('--serial', required=True)
parser.add_argument('--amap-apk', required=True, type=Path)
parser.add_argument('--qq-apk', required=True, type=Path)
args = parser.parse_args()
assert args.serial.startswith('emulator-'), 'An explicit test emulator is required'
root = Path(__file__).resolve().parent
out = root / 'evidence'
adb = args.sdk / 'platform-tools/adb.exe'
transcript = []

def run(*command, timeout=90):
    result = subprocess.run([str(adb), '-s', args.serial, *map(str, command)],
                            capture_output=True, timeout=timeout)
    text = (result.stdout + result.stderr).decode('utf-8', 'replace').replace('\r', '')
    transcript.append('$ adb ' + ' '.join(map(str, command)) + '\n' + text)
    if result.returncode:
        raise RuntimeError(text)
    return text

identity = run('shell', 'id')
shell = ['shell', 'su', '2000'] if 'uid=0' in identity else ['shell']
assert 'uid=2000' in run(*shell, 'id')
assert run(*shell, 'getprop', 'ro.build.version.sdk').strip() == '19'
assert run(*shell, 'getprop', 'ro.product.cpu.abi').strip() == 'x86'
host_apk = root / 'host/build/outputs/apk/debug/host-debug.apk'
host_hash = hashlib.sha256(host_apk.read_bytes()).hexdigest()
assert 'Success' in run('install', '-r', host_apk)
run(*shell, 'mkdir', '-p', '/data/local/tmp/cardlab')
items = []
log_sections = []
for slot, (name, apk) in enumerate((('amap', args.amap_apk), ('qq', args.qq_apk))):
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    remote = '/data/local/tmp/cardlab/' + name + '.apk'
    run('push', apk, remote)
    run_id = 'abi-guard-' + name + '-' + uuid.uuid4().hex
    run(*shell, 'am', 'force-stop', 'cn.hydrogen.cardlab')
    run(*shell, 'am', 'start', '-W', '-n', 'cn.hydrogen.cardlab/.LabActivity',
        '--es', 'run', run_id, '--es', 'apk', remote, '--ei', 'slot', slot)
    section = ''
    deadline = time.monotonic() + 18
    while time.monotonic() < deadline:
        text = run('logcat', '-d', '-v', 'time', '-s', 'CardLab:I', 'AndroidRuntime:E', '*:S')
        marker = text.rfind('RUN ' + run_id)
        section = text[marker:] if marker >= 0 else ''
        if 'APK native ABI=' in section:
            break
        time.sleep(.25)
    assert digest in section, name + ': Original file hash not observed'
    assert 'APK native ABI=[armeabi-v7a], device=x86/unknown' in section, name + ': No ABI rejection'
    assert 'IMPORT ' not in section and 'FATAL EXCEPTION' not in section
    items.append({'name': name, 'run_id': run_id, 'original_sha256': digest,
                  'abi_mismatch_rejected_before_import': True, 'original_app_executed': False})
    log_sections.append(section)
run(*shell, 'am', 'force-stop', 'cn.hydrogen.cardlab')
(out / 'original-apk-abi-guard.txt').write_text('\n'.join(transcript), encoding='utf-8')
(out / 'original-apk-abi-guard-decoded.txt').write_text('\n'.join(log_sections), encoding='utf-8')
report = {'serial': args.serial, 'host_apk_sha256': host_hash, 'checks': items,
          'original_amap_qq_compatibility_verified': False}
(out / 'original-apk-abi-guard-results.json').write_text(
    json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
