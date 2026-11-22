from pathlib import Path
import subprocess,os,json,re,argparse
p=argparse.ArgumentParser();p.add_argument('--java-home',type=Path,required=True);p.add_argument('--work',type=Path,required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];a.work.mkdir(parents=True,exist_ok=True);classes=a.work/'classes';classes.mkdir(exist_ok=True);j=a.java_home/'bin'
files=list((r/'tests/memory/src').rglob('*.java'))+[r/'app/src/main/java/cn/hydrogen/launcher/MusicSession.java',r/'app/src/main/java/cn/hydrogen/launcher/ImageSizing.java']
def run(cmd):
 q=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=60);text=(q.stdout+q.stderr).decode('utf-8','replace')
 if q.returncode:raise RuntimeError(text)
 return text
run([j/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-d',classes,*files]);results=[]
for scenario in ['sharing','clock','callbacks','reentrant-close','weak-ownership','image-plans']:
 text=run([j/'java.exe','-cp',classes,'cn.hydrogen.launcher.MemoryChecks',scenario]);print(text.strip());results.append({'scenario':scenario,'output':text})
summary={'platform':'JVM stubs; not Android/native graphics execution','scenarios':len(results),'assertions':sum(int(re.search(r'checks=(\d+)',r['output']).group(1)) for r in results),'all_passed':True,'results':results}
(a.work/'summary.json').write_text(json.dumps(summary,indent=2),'utf-8');print(json.dumps({k:v for k,v in summary.items() if k!='results'}))
