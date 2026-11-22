import android.content.*;import android.os.*;import cn.windchaser.mapbridge.DefaultMapView;import com.autonavi.amapauto.jni.protocol.AndroidProtocolExe;
public class DefaultMapViewChecks {
static int checks;static void check(String name,boolean result){if(!result)throw new AssertionError(name);checks++;System.out.println("PASS "+name);}static int revision(Context c){return c.getSharedPreferences("windchaser-map-defaults",0).getInt("initial-view-revision",0);}public static void main(String[] args){fixture.Env.reset();Context c=new Context();c.pkg="com.autonavi.amapauto";String scenario=args[0];
if(scenario.equals("saved"))c.getSharedPreferences("windchaser-map-defaults",0).edit().putInt("initial-view-revision",1).commit();
DefaultMapView.install(c);Handler.runFor(0);check("no request before original HMI readiness",a20.calls==0);
if(scenario.equals("failure"))a20.fail=true;
DefaultMapView.onReady(true);Handler.runFor(0);
if(scenario.equals("saved")){check("saved one-time migration preserves later user selection",a20.calls==0);}
else if(scenario.equals("failure")){check("original protocol failure does not escape helper",a20.calls==0);check("failed dispatch not persisted",revision(c)==0);}
else{check("original view action receives explicit 3D operation",a20.calls==1&&a20.type==2&&a20.mode==2);check("dispatch without acknowledgement not persisted",revision(c)==0);
if(scenario.equals("success")){DefaultMapView.onReady(true);Handler.runFor(0);check("duplicate readiness does not duplicate pending command",a20.calls==1);DefaultMapView.onViewResult(2,true);Handler.runFor(10000);check("native success persists once",revision(c)==1);check("successful migration cancels retries",a20.calls==1);DefaultMapView.onReady(true);Handler.runFor(0);check("later readiness never forces selected view",a20.calls==1);}
if(scenario.equals("timeout")){DefaultMapView.onViewResult(2,false);DefaultMapView.onViewResult(3,true);Handler.runFor(1499);check("failed and unrelated results do not persist",revision(c)==0);check("retry not early",a20.calls==1);Handler.runFor(3001);check("missing native acknowledgement retries at most three times",a20.calls==3);check("timeout does not mark success",revision(c)==0);Handler.runFor(10000);check("no unbounded startup work",a20.calls==3);}
if(scenario.equals("manual")){DefaultMapView.onViewResult(0,true);Handler.runFor(10000);check("manual 2D success cancels default retries",a20.calls==1&&revision(c)==1);}
if(scenario.equals("not-ready")){DefaultMapView.onReady(false);Handler.runFor(10000);check("HMI teardown cancels pending retry",a20.calls==1&&revision(c)==0);DefaultMapView.onReady(true);Handler.runFor(0);check("next HMI readiness can retry unconfirmed initialization",a20.calls==2);}}
System.out.println("RESULT "+scenario+" checks="+checks+" PASS (production helper, JVM stubs)");}}
