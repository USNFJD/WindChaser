package cn.hydrogen.launcher;

/** Bounds display-only interpolation; never changes native playback or declares buffering. */
final class PlaybackFreshness {
    static final long MAX_ESTIMATE=6000;
    private long sampleAt=-1,projectionAt=-1,advancedAt=-1,lastPosition=-1;
    private boolean stalled,lastPlaying;
    void sample(long position,boolean playing,long now){
        if(sampleAt<0||position!=lastPosition||playing!=lastPlaying){advancedAt=now;stalled=false;}
        else if(playing&&now-advancedAt>=1500)stalled=true;
        sampleAt=projectionAt=now;lastPosition=position;lastPlaying=playing;
    }
    void seek(long now){projectionAt=now;advancedAt=now;stalled=false;}
    void state(boolean playing,long now){
        if(playing!=lastPlaying){advancedAt=now;stalled=false;}lastPlaying=playing;
    }
    long project(long now){
        if(projectionAt<0)return 0; // No timing evidence: keep the display at its known position.
        return Math.min(now,stalled?sampleAt:projectionAt+MAX_ESTIMATE);
    }
    boolean waiting(long now){return projectionAt<0||stalled||now>projectionAt+MAX_ESTIMATE;}
    long sampledAt(){return sampleAt;}
    void reset(){sampleAt=projectionAt=advancedAt=lastPosition=-1;stalled=lastPlaying=false;}
}
