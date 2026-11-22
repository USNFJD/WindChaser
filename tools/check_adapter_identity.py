"""Run production AdapterTrust using the APK's real metadata/assets/certificate on JVM stubs."""
from pathlib import Path
import argparse,os,re,shutil,subprocess,zipfile
p=argparse.ArgumentParser();p.add_argument('apk',type=Path);p.add_argument('--source',type=Path,required=True);p.add_argument('--work',type=Path,required=True);p.add_argument('--java-home',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--json-jar',type=Path,required=True);p.add_argument('--expect-reject',action='store_true');a=p.parse_args()
a.work=a.work.resolve();a.java_home=a.java_home.resolve();a.json_jar=a.json_jar.resolve();a.work.mkdir(parents=True,exist_ok=True)
def run(*cmd):
 q=subprocess.run(list(map(str,cmd)),capture_output=True,timeout=60);s=(q.stdout+q.stderr).decode('utf-8','replace')
 if q.returncode:raise RuntimeError(s)
 return s
shutil.copyfile(a.apk,a.work/'input.apk')
badging=run(a.sdk/'build-tools/35.0.0/aapt.exe','dump','badging',a.work/'input.apk')
package,code,version=re.search("package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'",badging).groups()
with zipfile.ZipFile(a.apk) as z:
 (a.work/'marker.json').write_bytes(z.read('assets/windchaser-card-bridge.json'))
 certs=[n for n in z.namelist() if re.fullmatch(r'META-INF/[^/]+\.RSA',n,re.I)];assert len(certs)==1,certs
 (a.work/'certificate.p7b').write_bytes(z.read(certs[0]))
(a.work/'package.properties').write_text(f'package={package}\ncode={code}\nversion={version}\n','ascii')
files={
'android/content/Context.java':'''package android.content; import android.content.pm.PackageManager; import android.content.res.AssetManager; public abstract class Context {public abstract String getPackageName();public abstract PackageManager getPackageManager();public abstract AssetManager getAssets();}''',
'android/content/pm/PackageManager.java':'''package android.content.pm; public abstract class PackageManager {public static final int GET_SIGNATURES=64;public abstract PackageInfo getPackageInfo(String name,int flags) throws Exception;}''',
'android/content/pm/PackageInfo.java':'''package android.content.pm; public class PackageInfo {public android.content.pm.ApplicationInfo applicationInfo;public String packageName,versionName;public int versionCode;public Signature[] signatures;}''',
'android/content/pm/Signature.java':'''package android.content.pm;public class Signature {private byte[] bytes;public Signature(byte[] b){bytes=b.clone();}public byte[] toByteArray(){return bytes.clone();}}''',
'android/content/res/AssetManager.java':'''package android.content.res; import java.io.*;public abstract class AssetManager {public abstract InputStream open(String name) throws IOException;}''',
'android/util/Log.java':'''package android.util; public class Log {public static int i(String t,String m){return 0;}public static int w(String t,String m,Throwable e){return 0;}}''',
'fixture/AdapterIdentityCheck.java':r'''package fixture;
import android.content.*;import android.content.pm.*;import android.content.res.*;
import cn.windchaser.mapbridge.AdapterTrust;import java.io.*;import java.nio.file.*;import java.security.cert.*;import java.security.MessageDigest;import java.util.*;import org.json.*;
public final class AdapterIdentityCheck {
 static byte[] marker,cert;static Properties properties;static int checks;
 static class Fixture extends Context {
  String name=properties.getProperty("package");PackageInfo info=new PackageInfo();byte[] asset=marker.clone();boolean metadataFailure;
  Fixture(){info.packageName=name;info.versionName=properties.getProperty("version");info.versionCode=Integer.parseInt(properties.getProperty("code"));info.signatures=new Signature[]{new Signature(cert)};}
  public String getPackageName(){return name;}
  public PackageManager getPackageManager(){return new PackageManager(){public PackageInfo getPackageInfo(String n,int flags) throws Exception{if(metadataFailure)throw new Exception("not found");if(flags!=GET_SIGNATURES||!n.equals(info.packageName))throw new Exception("unexpected lookup");return info;}};}
  public AssetManager getAssets(){return new AssetManager(){public InputStream open(String n)throws IOException{if(!n.equals("windchaser-card-bridge.json")||asset==null)throw new FileNotFoundException();return new ByteArrayInputStream(asset);}};}
 }
 static void check(String label,boolean expected,Context f){boolean actual=AdapterTrust.isTrustedAdapter(f);if(actual!=expected)throw new AssertionError(label+" expected="+expected+" actual="+actual);checks++;System.out.println("PASS "+label);}
 static Fixture changed(String k,Object v)throws Exception{Fixture f=new Fixture();JSONObject j=new JSONObject(new String(marker,"UTF-8"));if(v==null)j.remove(k);else j.put(k,v);f.asset=j.toString().getBytes("UTF-8");return f;}
 public static void main(String[] args)throws Exception{
  Path w=Paths.get(args[0]);marker=Files.readAllBytes(w.resolve("marker.json"));properties=new Properties();try(InputStream in=Files.newInputStream(w.resolve("package.properties"))){properties.load(in);}
  Collection<? extends Certificate> certificates;try(InputStream in=Files.newInputStream(w.resolve("certificate.p7b"))){certificates=CertificateFactory.getInstance("X.509").generateCertificates(in);}if(certificates.size()!=1)throw new AssertionError("certificate count");cert=certificates.iterator().next().getEncoded();
  StringBuilder hex=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(cert))hex.append(String.format("%02x",b&255));if(!hex.toString().equals("8b4f8e84026a5ac5ad75b4a0f34d21bd81957e4fb38dd80b7fe0c1537e66e7d8"))throw new AssertionError("wrong fixture signer");
  if(args.length>1){check("previous production code rejects actual revision-10 APK",false,new Fixture());return;}
  check("actual signed final APK identity accepted",true,new Fixture());
  check("null context rejected",false,null);
  Fixture f=new Fixture();f.name="other.package";check("wrong context package rejected",false,f);
  f=new Fixture();f.info.packageName="other.package";check("wrong package metadata rejected",false,f);
  f=new Fixture();f.info.versionCode--;check("different version code rejected",false,f);
  f=new Fixture();f.info.versionName="7.5.0";check("different version name rejected",false,f);
  f=new Fixture();f.info.signatures=null;check("missing signatures rejected",false,f);
  f=new Fixture();f.info.signatures=new Signature[0];check("empty signature list rejected",false,f);
  f=new Fixture();f.info.signatures=new Signature[]{new Signature(cert),new Signature(cert)};check("extra signer rejected",false,f);
  f=new Fixture();f.info.signatures=new Signature[]{null};check("null signer rejected",false,f);
  f=new Fixture();f.info.signatures=new Signature[]{new Signature(new byte[]{1,2,3})};check("wrong certificate rejected",false,f);
  for(int revision:new int[]{7,8,9,11,100})check("revision "+revision+" rejected",false,changed("bridge_revision",revision));
  check("missing revision rejected",false,changed("bridge_revision",null));
  check("wrong bridge protocol rejected",false,changed("bridge_version",2));
  check("missing bridge protocol rejected",false,changed("bridge_version",null));
  check("wrong base version rejected",false,changed("base_version","7.5.0"));
  check("wrong base digest rejected",false,changed("base_sha256","bad"));
  f=new Fixture();f.asset=null;check("missing asset rejected",false,f);
  f=new Fixture();f.asset="{".getBytes("UTF-8");check("invalid JSON rejected",false,f);
  f=new Fixture();f.asset="{}".getBytes("UTF-8");check("empty marker rejected",false,f);
  f=new Fixture();f.asset=new byte[2049];check("oversized asset rejected",false,f);
  f=new Fixture();f.metadataFailure=true;check("metadata exception rejected",false,f);
  check("valid identity still accepted after failures",true,new Fixture());
  System.out.println("RESULT: "+checks+" checks passed; production Java on JVM test stubs; not Android engine execution.");
 }
}'''
}
files['android/content/pm/ApplicationInfo.java']='package android.content.pm;public class ApplicationInfo {public int uid;}'
files['android/os/Process.java']='package android.os;public class Process {public static int myUid(){return 10000;}}'
src=a.work/'src';classes=a.work/'classes';classes.mkdir(exist_ok=True)
for name,text in files.items():
 f=src/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(text,'utf-8')
f=src/'cn/windchaser/mapbridge/AdapterTrust.java';f.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(a.source,f)
run(a.java_home/'bin/javac.exe','-source','8','-target','8','-encoding','UTF-8','-cp',a.json_jar,'-d',classes,*src.rglob('*.java'))
cmd=[a.java_home/'bin/java.exe','-cp',str(classes)+os.pathsep+str(a.json_jar),'fixture.AdapterIdentityCheck',a.work]
if a.expect_reject:cmd.append('baseline')
result=run(*cmd);(a.work/'result.txt').write_text(result,'utf-8');print(result)
