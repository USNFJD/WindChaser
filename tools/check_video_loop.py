from pathlib import Path
import argparse, subprocess, json, re
p=argparse.ArgumentParser();p.add_argument('--java-home',type=Path,required=True);p.add_argument('--work',type=Path,required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[1];a.work.mkdir(parents=True,exist_ok=True);classes=a.work/'classes';classes.mkdir(exist_ok=True)
files=list((r/'tests/video-loop/src').rglob('*.java'))+[r/'app/src/main/java/cn/hydrogen/launcher/VideoLoopGuard.java']
def run(args):
 q=subprocess.run(list(map(str,args)),capture_output=True,timeout=60);text=(q.stdout+q.stderr).decode('utf-8','replace')
 if q.returncode:raise RuntimeError(text)
 return text
run([a.java_home/'bin/javac.exe','-source','8','-target','8','-encoding','UTF-8','-d',classes,*files])
out=run([a.java_home/'bin/java.exe','-cp',classes,'cn.hydrogen.launcher.VideoLoopChecks']);print(out.strip())
m=re.search(r'scenarios=(\d+) checks=(\d+)',out);assert m
summary={'platform':'JVM production playback-observation logic; no native decoder simulation claims','scenarios':int(m[1]),'assertions':int(m[2]),'all_passed':True}
(a.work/'results.json').write_text(json.dumps(summary,indent=2)+'\n','utf-8')
