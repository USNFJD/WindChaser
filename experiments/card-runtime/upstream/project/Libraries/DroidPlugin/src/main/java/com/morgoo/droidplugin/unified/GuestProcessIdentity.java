/* WindChaser single-package experiment, LGPL-3.0-or-later. */
package com.morgoo.droidplugin.unified;

/** Declared identity of this guest process, available before Application.attach. */
public final class GuestProcessIdentity {
    private static volatile Identity current;
    private GuestProcessIdentity(){}
    private static final class Identity {
        final String pkg,process;
        Identity(String pkg,String process){this.pkg=pkg;this.process=process;}
    }
    public static synchronized void initialize(String pkg,String process){
        if(pkg==null||pkg.isEmpty())throw new IllegalArgumentException("guest package missing");
        if(process==null||process.isEmpty())process=pkg;
        Identity old=current;
        if(old!=null){
            if(!old.pkg.equals(pkg)||!old.process.equals(process))
                throw new IllegalStateException("a live guest process cannot change its Application identity");
            return;
        }
        current=new Identity(pkg,process);
    }
    /** Select only this guest's declared process; never borrow another package's identity. */
    public static String receiverProcess(String pkg){Identity value=current;return value!=null&&value.pkg.equals(pkg)?value.process:null;}
    public static String packageName(){Identity value=current;return value==null?null:value.pkg;}
    public static String processName(){Identity value=current;return value==null?null:value.process;}
}
