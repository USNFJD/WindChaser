package cn.windchaser.mapbridge;

/** Deterministic clock and ROM task observations, alongside real window tests. */
public final class GuardPolicyChecks {
    public interface Task { void run(); }
    public interface Check { void run(String title,Task task); }
    private static void require(boolean ok){if(!ok)throw new AssertionError("window focus guard policy");}
    private static void prove(HostWindowGuard g,long now){long nonce=g.probe(now);require(g.reply(nonce,now,now,true)==1);}
    public static void run(Check check){
        check.run("unavailable ROM task list accepts fresh verified window focus",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);
            for(long t=100;t<6000;t+=240){g.signal(t);prove(g,t);require(g.blocked(t,HostWindowGuard.UNKNOWN)==null&&g.focused(t));}
        });
        check.run("stale foreign ROM task entry accepts repeatedly rechecked real focus",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);
            for(long t=100;t<6000;t+=240){g.signal(t);prove(g,t);require(g.blocked(t,HostWindowGuard.OTHER)==null);}
        });
        check.run("short slow-device pause does not expire the foreground lease",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);prove(g,100);
            require(g.blocked(1600,HostWindowGuard.HOST)==null);g.signal(1700);prove(g,1700);require(g.focused(1700));
        });
        check.run("conflicting full-screen task needs new focus proof within 400ms",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);prove(g,100);g.signal(500);
            require(g.blocked(500,HostWindowGuard.OTHER)==null);g.signal(901);
            require("foreground_unconfirmed".equals(g.blocked(901,HostWindowGuard.OTHER)));
        });
        check.run("explicit window focus denial cannot be used as permission",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);long nonce=g.probe(100);
            require(g.reply(nonce,110,110,false)==0&&!g.focused(110));
        });
        check.run("replayed nonce and pre-probe focus sample cannot authorize a show",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);long old=g.probe(100);
            g.begin(200);long current=g.probe(200);
            require(g.reply(old,210,210,true)==-1);require(g.reply(current,199,210,true)==-1);
            require(g.reply(current,300,1100,true)==-1);require(!g.focused(1100));
        });
        check.run("independent close accepts a dead Binder even during a fresh lease",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);prove(g,100);require(g.allowFallbackClose(110,false));
        });
        check.run("independent close never hides a live freshly focused card",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);prove(g,100);require(!g.allowFallbackClose(110,true));
        });
        check.run("independent close accepts an expired foreground lease",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);prove(g,100);require(g.allowFallbackClose(2601,true));
        });
        check.run("a newly reconnected focused host rejects an older close sweep",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);prove(g,100);g.begin(300);prove(g,300);require(!g.allowFallbackClose(320,true));
        });
        check.run("an unverified host cannot block independent cleanup",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);require(g.allowFallbackClose(110,true));
        });
        check.run("lost focus reply permits a new nonce instead of stalling forever",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);long old=g.probe(100);
            require(!g.needsProbe(500,HostWindowGuard.UNKNOWN));require(g.needsProbe(851,HostWindowGuard.UNKNOWN));long current=g.probe(851);
            require(g.reply(old,860,860,true)==-1);require(g.reply(current,860,860,true)==1&&g.focused(860));
        });
        check.run("lost focus reply still withdraws for a foreign full-screen task",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);g.probe(100);g.signal(600);require(g.blocked(100,HostWindowGuard.OTHER)==null);
            require("foreground_unconfirmed".equals(g.blocked(501,HostWindowGuard.OTHER)));
        });
        check.run("lost heartbeat and unresponsive host still expire without task data",()->{
            HostWindowGuard g=new HostWindowGuard();g.begin(100);prove(g,100);
            require("heartbeat_timeout".equals(g.blocked(2601,HostWindowGuard.UNKNOWN)));
        });
    }
}
