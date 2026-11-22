package com.morgoo.droidplugin.unified;
/** Production process identity; no Android or synthetic guest responses. */
public final class GuestIdentityChecks {
 static int checks;static void check(boolean v,String m){if(!v)throw new AssertionError(m);checks++;}
 public static void main(String[] args){String pkg="com.tencent.qqmusiccar",process="player".equals(args[0])?pkg+":QQPlayerProcess":"default".equals(args[0])?null:pkg;
  check(GuestProcessIdentity.receiverProcess(pkg)==null,"uninitialized identity falls back to manager");
  GuestProcessIdentity.initialize(pkg,process);String expected=process==null?pkg:process;
  check(expected.equals(GuestProcessIdentity.receiverProcess(pkg)),"receiver process available before PID table");
  check(GuestProcessIdentity.receiverProcess("com.autonavi.amapauto")==null,"map cannot borrow QQ identity");
  check(GuestProcessIdentity.receiverProcess(null)==null,"unknown package cannot borrow guest identity");
  check(GuestProcessIdentity.packageName().equals(pkg)&&GuestProcessIdentity.processName().equals(expected),"declared identity retained");
  GuestProcessIdentity.initialize(pkg,process);check(expected.equals(GuestProcessIdentity.receiverProcess(pkg)),"reuse keeps exact process");
  boolean denied=false;try{GuestProcessIdentity.initialize(pkg,expected+":other");}catch(IllegalStateException required){denied=true;}check(denied,"live process cannot switch identity");
  check(expected.equals(GuestProcessIdentity.receiverProcess(pkg)),"failed change retains original identity");
  System.out.println("PASS guest receiver identity "+args[0]+" checks="+checks);
 }
}
