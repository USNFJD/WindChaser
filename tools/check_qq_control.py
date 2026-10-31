from pathlib import Path
import subprocess,os,argparse
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args();s=Path(__file__).resolve().parents[1];w=a.work.resolve();w.mkdir(parents=True,exist_ok=True)
classes=w/'classes';classes.mkdir(exist_ok=True);java=Path(r'D:/SOFT/Android Studio/jbr/bin');runtime=s/'experiments/dual-splash/src/cn/windchaser/startup'
files=list((s/'tests/qq-control/src').rglob('*.java'))+[runtime/'QqControlReadiness.java']
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=60,env={**os.environ,'JAVA_TOOL_OPTIONS':'-Dfile.encoding=UTF-8'});text=(r.stdout+r.stderr).decode('utf-8','replace')
 if r.returncode:raise RuntimeError(text)
 return text
run([java/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-d',classes,*files]);text=run([java/'java.exe','-cp',classes,'cn.windchaser.startup.QqControlChecks']);print(text);(w/'qq-control-results.txt').write_text(text,'utf-8')
