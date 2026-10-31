package cn.hydrogen.launcher;

import android.content.Context;
import java.lang.ref.WeakReference;
import java.util.*;

/** One application-scoped QQ receiver, clock, seek state and cover for all visible clients. */
final class MusicSession {
    private static MusicSession shared;
    private final ArrayList<WeakReference<Lease>> clients=new ArrayList<>();
    private final QqMusicBridge bridge;
    private boolean ticking,closed;
    private MusicSession(Context context){bridge=new QqMusicBridge(context.getApplicationContext(),this::changed);}
    static Lease acquire(Context context,Runnable callback){
        if(shared==null||shared.closed)shared=new MusicSession(context);
        Lease lease=new Lease(shared,callback);shared.clients.add(new WeakReference<>(lease));shared.bridge.start();return lease;
    }
    static final class Lease implements java.io.Closeable {
        private MusicSession session;private Runnable callback;private boolean active,closed;
        private Lease(MusicSession s,Runnable r){session=s;callback=r;}
        QqMusicBridge bridge(){return session.bridge;}
        void resume(){if(closed)return;active=true;session.sync();if(!closed&&callback!=null)callback.run();}
        void pause(){if(closed)return;active=false;session.sync();}
        @Override public void close(){if(closed)return;closed=true;active=false;callback=null;MusicSession owner=session;session=null;owner.sync();}
    }
    private ArrayList<Lease> live(){
        ArrayList<Lease> result=new ArrayList<>();Iterator<WeakReference<Lease>> it=clients.iterator();
        while(it.hasNext()){Lease lease=it.next().get();if(lease==null||lease.closed)it.remove();else result.add(lease);}return result;
    }
    private void sync(){
        if(closed)return;ArrayList<Lease> live=live();boolean active=false;for(Lease lease:live)active|=lease.active;
        if(live.isEmpty()){closed=true;ticking=false;bridge.stop();if(shared==this)shared=null;return;}
        if(active&&!ticking){ticking=true;bridge.resume();}else if(!active&&ticking){ticking=false;bridge.pause();}
    }
    private void changed(){
        if(closed||bridge==null)return;ArrayList<Lease> live=live();
        for(Lease lease:live)if(!lease.closed&&lease.active&&lease.callback!=null)lease.callback.run();
        sync();
    }
}
