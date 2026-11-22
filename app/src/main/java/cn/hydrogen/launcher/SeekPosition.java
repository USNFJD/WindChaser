package cn.hydrogen.launcher;

import java.util.ArrayList;

/** One seek clock for all views. QQ 2.1 sends sparse, untimestamped snapshots. */
final class SeekPosition {
    private static final long TOLERANCE=1800, SETTLING=1200, OLD_SAMPLE_GUARD=8000;
    private final ArrayList<Path> previous=new ArrayList<>();
    private long anchor=-1,anchoredAt,requestedAt;
    private boolean playing,sought,pending;
    private static final class Path {
        long position,at;boolean playing;
        Path(long p,long t,boolean active){position=p;at=t;playing=active;}
        long at(long now){return position+(playing?Math.max(0,now-at):0);}
        void state(boolean active,long now){position=at(now);at=now;playing=active;}
    }
    void begin(long before,long target,long now,boolean active){
        if(!sought||now-requestedAt>=OLD_SAMPLE_GUARD)previous.clear();
        previous.add(new Path(before,now,active));
        if(previous.size()>8)previous.remove(0);
        anchor=target;anchoredAt=requestedAt=now;playing=active;sought=pending=true;
    }
    long position(long fallback,long now){return anchor<0?fallback:anchor+(playing?Math.max(0,now-anchoredAt):0);}
    boolean accept(long sample,long fallback,long now,boolean authoritative){
        if(!sought)return true;
        if(Math.abs(sample-position(fallback,now))<=TOLERANCE){anchor=-1;pending=false;return true;}
        // Album/lyric replies are metadata snapshots, not seek completion events.
        if(!authoritative)return false;
        long age=now-requestedAt;
        if(age<SETTLING)return false;
        if(age<OLD_SAMPLE_GUARD)for(Path path:previous)
            if(Math.abs(sample-path.at(now))<=TOLERANCE)return false;
        // A fresh state can correct a failed seek or a seek made inside QQ itself.
        anchor=-1;pending=false;return true;
    }
    void state(boolean active,long now){
        if(anchor>=0){anchor=position(0,now);anchoredAt=now;}
        for(Path path:previous)path.state(active,now);
        playing=active;
    }
    boolean pending(long now){return pending&&now-requestedAt<OLD_SAMPLE_GUARD;}
    void reset(){anchor=-1;sought=pending=false;previous.clear();}
}
