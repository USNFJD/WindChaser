package cn.hydrogen.launcher;

/** Adapter boundary only. No vehicle protocol or readings are fabricated. */
final class TirePressureData {
    interface Source { Snapshot read(); }
    private static volatile Source source;
    static void attach(Source adapter){source=adapter;}
    static boolean attached(){return source!=null;}
    static Snapshot snapshot(){Source adapter=source;if(adapter==null)return Snapshot.missing();try{Snapshot value=adapter.read();return value==null?Snapshot.disconnected("胎压模块"):value;}catch(RuntimeException unavailable){return Snapshot.disconnected("胎压模块");}}
    static final class Wheel {
        final Float bar,celsius;final long elapsed;
        Wheel(Float pressure,Float temperature,long elapsed){bar=valid(pressure)&&pressure>=0?pressure:null;celsius=valid(temperature)?temperature:null;this.elapsed=elapsed;}
        private static boolean valid(Float value){return value!=null&&!Float.isNaN(value)&&!Float.isInfinite(value);}
        boolean fresh(long now,long age){return elapsed>0&&now>=elapsed&&now-elapsed<=age;}
    }
    static final class Snapshot {
        final boolean installed,connected;final String source;final long maxAge;final Wheel[] wheels;
        Snapshot(boolean installed,boolean connected,String source,long maxAge,Wheel[] wheels){this.installed=installed;this.connected=connected;this.source=source==null?"胎压模块":source;this.maxAge=Math.max(1,maxAge);this.wheels=new Wheel[4];if(wheels!=null)System.arraycopy(wheels,0,this.wheels,0,Math.min(4,wheels.length));}
        static Snapshot missing(){return new Snapshot(false,false,"未接入",1,null);}
        static Snapshot disconnected(String name){return new Snapshot(true,false,name,1,null);}
        boolean fresh(int index,long now){Wheel wheel=wheels[index];return installed&&connected&&wheel!=null&&wheel.fresh(now,maxAge);}
        String status(long now){if(!installed)return "";if(!connected)return "胎压模块已断开";for(int n=0;n<4;n++)if(!fresh(n,now)||wheels[n].bar==null)return "胎压数据未完整更新";return "胎压模块已连接";}
    }
}
