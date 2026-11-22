package cn.hydrogen.launcher;
/** The standalone APK installer was retired when both guests became private modules. */
final class ContainerInstallFlow {
 private final android.app.Instrumentation test;
 ContainerInstallFlow(android.app.Instrumentation t){test=t;}
 void run(){android.os.Bundle result=new android.os.Bundle();result.putString("stream","SKIP retired standalone container installation; use the private module hash and admission checks");test.finish(android.app.Activity.RESULT_CANCELED,result);}
}
