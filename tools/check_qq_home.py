from pathlib import Path
import subprocess,os,argparse,json
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);a=p.parse_args();s=Path(__file__).resolve().parents[1];w=a.work.resolve();w.mkdir(parents=True,exist_ok=True)
classes=w/'classes';classes.mkdir(exist_ok=True);java=Path(r'D:/SOFT/Android Studio/jbr/bin');jar=Path(r'C:/Users/Wnnp/AppData/Local/Android/Sdk/platforms/android-35/android.jar')
files=list((s/'tests/qq-home/src').rglob('*.java'))+[s/'experiments/dual-splash/src/cn/windchaser/startup/QqHome.java']
env={**os.environ,'JAVA_TOOL_OPTIONS':'-Dfile.encoding=UTF-8 -Duser.language=en -Duser.country=US'}
def run(cmd):
 r=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=60,env=env);text=(r.stdout+r.stderr).decode('utf-8','replace')
 if r.returncode:raise RuntimeError(text)
 return text
run([java/'javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',jar,'-d',classes,*files]);text=run([java/'java.exe','-cp',os.pathsep.join(map(str,[classes,jar])),'QqHomeChecks']);print(text);(w/'qq-home-results.txt').write_text(text,'utf-8')
