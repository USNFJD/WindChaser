from pathlib import Path
import argparse,subprocess,os,hashlib,json,re
p=argparse.ArgumentParser(description="Verify API19 signatures, package identities, release flags and alignment.")
p.add_argument('folder',type=Path);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);a=p.parse_args();t=a.sdk/'build-tools/35.0.0'
def run(*cmd):
 q=subprocess.run(list(map(str,cmd)),capture_output=True);text=(q.stdout+q.stderr).decode('utf-8','replace');assert q.returncode==0,text;return text
records=[];certs=set()
for file in sorted(a.folder.glob('*.apk')):
 badging=run(t/'aapt.exe','dump','badging',file);assert 'application-debuggable' not in badging
 sign=run(a.java_home/'bin/java.exe','-jar',t/'lib/apksigner.jar','verify','--min-sdk-version','19','--max-sdk-version','19','--verbose','--print-certs',file)
 cert=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)',sign).group(1);certs.add(cert)
 run(t/'zipalign.exe','-c','4',file)
 pkg=re.search("package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging).groups()
 records.append(dict(file=file.name,package=pkg[0],versionCode=int(pkg[1]),versionName=pkg[2],bytes=file.stat().st_size,sha256=hashlib.sha256(file.read_bytes()).hexdigest(),certificate=cert))
assert len(records)==3 and len(certs)==1
assert {r['package'] for r in records}=={'cn.hydrogen.launcher','com.autonavi.amapauto','com.tencent.qqmusiccar'}
print(json.dumps(records,ensure_ascii=False,indent=2))
