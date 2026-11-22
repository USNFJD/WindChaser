from pathlib import Path
import argparse,subprocess
p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--java-home',type=Path,default=Path(r'D:/SOFT/Android Studio/jbr'));a=p.parse_args();s=Path(__file__).resolve().parents[1];a.work.mkdir(parents=True,exist_ok=True)
files=[s/'experiments/card-runtime/upstream/project/Libraries/DroidPlugin/src/main/java/com/morgoo/droidplugin/unified/GuestProcessIdentity.java',*sorted((s/'tests/guest-identity/src').rglob('*.java'))]
subprocess.run([str(a.java_home/'bin/javac.exe'),'-source','8','-target','8','-encoding','UTF-8','-d',str(a.work),*map(str,files)],check=True)
for mode in ['main','player','default']:subprocess.run([str(a.java_home/'bin/java.exe'),'-cp',str(a.work),'com.morgoo.droidplugin.unified.GuestIdentityChecks',mode],check=True)
