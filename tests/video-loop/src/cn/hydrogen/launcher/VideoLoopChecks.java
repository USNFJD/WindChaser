package cn.hydrogen.launcher;
public final class VideoLoopChecks {
    static int checks, scenarios;
    static void check(boolean yes,String message){if(!yes)throw new AssertionError(message);checks++;}
    static VideoLoopGuard fresh(){scenarios++;VideoLoopGuard g=new VideoLoopGuard();g.reset(0);return g;}
    public static void main(String[] args){
        VideoLoopGuard g=fresh();
        for(int n=0;n<30;n++)check(g.inspect(n*1100L,(n*1100)%5000,5000,true,n*33)==0,"normal looping never repaired");
        g=fresh();
        for(int n=0;n<20;n++)check(g.inspect(n*1100L,900,1000,true,n*33)==0,"frame progress protects very short loops with equal sampled positions");
        g=fresh();g.inspect(0,4950,5000,false,0);
        check(g.inspect(2200,4950,5000,false,0)==0,"stopped EOF allowed brief settling");
        check(g.inspect(3300,4950,5000,false,0)==1,"missing completion callback triggers restart");
        g=fresh();g.inspect(0,4990,5000,true,0);
        check(g.inspect(3300,4990,5000,true,0)==1,"EOF stall detected despite isPlaying true");
        g=fresh();g.inspect(0,1000,5000,true,0);
        check(g.inspect(3300,1000,5000,true,0)==0,"mid-file frame delay gets longer allowance");
        check(g.inspect(9900,1000,5000,true,0)==1,"persistent local mid-file stall recovers");
        g=fresh();g.restarting(100);check(g.recovering(),"recovery is tracked");
        check(g.inspect(1200,0,5000,true,0)==0&&g.recovering(),"seek-to-zero alone is not playback progress");
        check(g.inspect(3400,0,5000,true,0)==2,"seek without progress rebuilds decoder");
        g=fresh();g.restarting(100);g.inspect(1200,0,5000,true,0);
        check(g.inspect(2300,900,5000,true,20)==0&&!g.recovering(),"actual movement acknowledges recovery without callbacks");
        check(g.madeProgress(),"progress can reset consecutive rebuild budget");
        g=fresh();g.restarting(100);g.inspect(1200,0,1000,true,0);
        check(g.inspect(2300,0,1000,true,30)==0&&!g.recovering(),"frames acknowledge short-loop recovery");
        g=fresh();g.restarting(100);g.inspect(1200,0,5000,false,0);
        check(g.inspect(3400,300,5000,false,0)==2,"position changes with stopped playback cannot acknowledge recovery");
        check(!g.madeProgress(),"stopped position updates cannot clear the failed-decoder budget");
        g=fresh();g.restarting(100);g.reset(500);
        check(!g.recovering()&&!g.madeProgress(),"pause/release clears recovery state");
        check(g.inspect(501,0,5000,true,0)==0,"new decoder has a fresh baseline");
        g=fresh();check(g.inspect(10000,0,0,false,0)==0,"unknown duration is not an EOF");
        check(g.inspect(11000,-1,-1,false,0)==0,"invalid position/duration do not loop-rebuild");
        g=fresh();g.inspect(0,4990,5000,true,0);g.inspect(2200,30,5000,true,0);
        check(g.inspect(4400,2000,5000,true,0)==0,"SurfaceView path recognizes normal position wraps without texture frames");
        g=fresh();g.inspect(0,4990,5000,true,0);g.inspect(2200,4990,5000,true,0);g.inspect(3300,20,5000,true,0);
        check(g.inspect(5500,2000,5000,true,0)==0,"late native loop cancels stall candidate");
        g=fresh();g.restarting(100);g.inspect(200,0,5000,true,0);g.reset(300);g.inspect(400,100,5000,true,0);
        check(!g.recovering()&&g.inspect(1400,1100,5000,true,0)==0,"surface recreation cannot inherit old seek timeout");
        System.out.println("PASS video-loop scenarios="+scenarios+" checks="+checks);
    }
}
