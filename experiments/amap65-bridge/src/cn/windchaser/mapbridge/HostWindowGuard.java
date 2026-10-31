package cn.windchaser.mapbridge;

/** A fresh window-focus reply is authoritative; task lists on car ROMs can be stale. */
final class HostWindowGuard {
    static final int UNKNOWN=0, HOST=1, OTHER=2;
    static final long LEASE_MS=2500;
    private long signalAt,verifiedAt=-1,sentAt=-1,pending,serial,foreignAt=-1;

    synchronized void begin(long issued){signalAt=issued;verifiedAt=sentAt=foreignAt=-1;pending=0;}
    synchronized void signal(long issued){signalAt=Math.max(signalAt,issued);}
    synchronized boolean focused(long now){return verifiedAt>=0&&now-verifiedAt<=LEASE_MS;}
    synchronized boolean allowFallbackClose(long now,boolean hostAlive){return !hostAlive||!focused(now);}
    synchronized boolean needsProbe(long now,int task){if(pending!=0&&now-sentAt>750)pending=0;return pending==0&&(sentAt<0||now-sentAt>=(task==OTHER?250:500));}
    synchronized long probe(long now){sentAt=now;return pending=++serial;}
    // 0 is an explicit denial, 1 is fresh focus, -1 is an obsolete/malformed reply.
    synchronized int reply(long nonce,long issued,long now,boolean focused){
        if(pending==0||nonce!=pending||issued<sentAt||issued>now||now-issued>750)return -1;
        pending=0;
        if(!focused)return 0;
        verifiedAt=issued;signal(issued);return 1;
    }
    synchronized String blocked(long now,int task){
        if(task==OTHER){if(foreignAt<0)foreignAt=now;}else foreignAt=-1;
        if(now-signalAt>LEASE_MS)return "heartbeat_timeout";
        if(verifiedAt<0&&sentAt>=0&&now-sentAt>1000)return "focus_timeout";
        if(verifiedAt>=0&&now-verifiedAt>LEASE_MS)return "focus_timeout";
        // A conflicting task list requires a NEW focus reply. An unknown task
        // list alone never claims the user has left the launcher.
        if(foreignAt>=0&&now-Math.max(foreignAt,verifiedAt)>400)return "foreground_unconfirmed";
        return null;
    }
}
