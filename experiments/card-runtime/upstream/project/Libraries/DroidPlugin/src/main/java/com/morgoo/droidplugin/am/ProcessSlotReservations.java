/* WindChaser single-package experiment, LGPL-3.0-or-later. */
package com.morgoo.droidplugin.am;
import java.util.HashMap;
import java.util.Map;

/** Reserves a selected stub before its child pid registers with the manager. */
final class ProcessSlotReservations {
    private static final long PENDING_MS=30000;
    private final Map<String,Slot> slots=new HashMap<>();
    private static final class Slot {
        final String pkg,process;final long selected;int pid;
        Slot(String pkg,String process,long now){this.pkg=pkg;this.process=process;selected=now;}
        boolean matches(String p,String n){return pkg.equals(p)&&process.equals(n);}
    }
    private Slot live(String stub,long now){
        Slot s=slots.get(stub);
        if(s!=null&&s.pid==0&&now-s.selected>=PENDING_MS){slots.remove(stub);return null;}
        return s;
    }
    private static String process(String pkg,String name){return name==null||name.isEmpty()?pkg:name;}
    synchronized boolean canUse(String stub,String pkg,String name,long now){
        Slot s=live(stub,now);return s==null||s.matches(pkg,process(pkg,name));
    }
    synchronized void reserve(String stub,String pkg,String name,long now){
        name=process(pkg,name);Slot s=live(stub,now);
        if(s==null){slots.put(stub,new Slot(pkg,name,now));return;}
        if(!s.matches(pkg,name))throw new IllegalStateException("guest stub already assigned to another native process");
    }
    synchronized void claim(String stub,String pkg,String name,int pid,long now){
        reserve(stub,pkg,name,now);Slot s=slots.get(stub);
        if(s.pid!=0&&s.pid!=pid)throw new IllegalStateException("guest stub still owns a different live pid");
        s.pid=pid;
    }
    synchronized void died(int pid){java.util.Iterator<Slot> values=slots.values().iterator();while(values.hasNext())if(values.next().pid==pid)values.remove();}
    synchronized boolean hasPackage(String pkg,long now){
        for(String stub:new java.util.ArrayList<String>(slots.keySet())){
            Slot slot=live(stub,now);if(slot!=null&&slot.pkg.equals(pkg))return true;
        }
        return false;
    }
    synchronized void clear(){slots.clear();}
}
