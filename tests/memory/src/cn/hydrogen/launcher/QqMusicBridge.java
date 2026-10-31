package cn.hydrogen.launcher;
import android.content.Context;
final class QqMusicBridge {
 static int created,registrations,starts,pauses,stops;
 final Context context;final Runnable changed;final Object cover=new Object();boolean registered;
 QqMusicBridge(Context context,Runnable callback){this.context=context;changed=callback;created++;callback.run();}
 void start(){if(!registered){registered=true;registrations++;}}
 void resume(){starts++;}void pause(){pauses++;}void stop(){if(registered){registered=false;stops++;}}
 void emit(){changed.run();}
}
