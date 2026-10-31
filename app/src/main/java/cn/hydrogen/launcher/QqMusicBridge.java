package cn.hydrogen.launcher;

import android.content.*;
import android.net.Uri;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Protocol verified in the supplied QQ Music Car 2.1.1.1 APK. No private files or hooks. */
final class QqMusicBridge {
    static final String EVENTS="com.tencent.qqmusiccar.action.PLAY_COMMAND_SEND_FOR_THIRD";
    static final String EXTRA="com.tencent.qqmusiccar.EXTRA_COMMAND_DATA";
    private static final String RECEIVER="com.tencent.qqmusiccar.app.reciver.BroadcastReceiverCenterForThird";
    static final String WEDRIVE_ACTION="com.wedrive.action.QQMUSIC_COMMAND_SEND",WEDRIVE_EXTRA="com.wedrive.extra.COMMAND_DATA";
    private static final String WEDRIVE_RECEIVER="com.tencent.qqmusiccar.app.reciver.BroadcastReceiverCenterForWeDrive";
    final Context context; final Runnable changed;
    private final Runnable interfaceChanged;
    final AlbumArt cover;
    String title="",artist="",lyricMessage="等待 QQ音乐发送歌词",hint="先在 QQ音乐选择歌曲，之后可在卡片播放、暂停和切歌";
    int state,trackRevision; long position,duration,receivedAt,positionRevision; boolean connected,registered,trackConfirmed;
    int playMode,pendingMode;private long modeRequestedAt;
    static final int SINGLE=101,SHUFFLE=105,ORDER=103;
    final ArrayList<Line> lyrics=new ArrayList<>();
    private final SeekPosition seekPosition=new SeekPosition();
    private final PlaybackFreshness playbackFreshness=new PlaybackFreshness();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable modeFallback=this::sendModeFallback;
    private final Runnable modeTimeout=()->expireMode();
    private final Runnable seekRefresh=()->subscribe();
    private static final long[] ALBUM_RETRIES={1500,5000,15000};
    private static final long[] LYRIC_RETRIES={2000,6000,15000},PROGRESS_RECHECKS={4000,10000,22000};
    private int lyricMisses,progressAttempt;private boolean lyricRetryPending;private long progressAnchor;
    private static final long[] SNAPSHOT_DELAYS={700,2200,5500};
    private int snapshotParts,snapshotAttempt;private long snapshotAt;
    private final Runnable snapshotRefresh=this::recoverSnapshot;
    private int albumMisses;private boolean albumRetryPending;private long lastAlbumNetwork;
    private boolean ticking,closed,lastRequestable,lastReady,lastLost;
    private final Runnable lyricRefresh=()->{lyricRetryPending=false;if(ticking&&!closed){subscribe();retryLyrics();}};
    private long commandAt;private int commandPending=-1,commandAttempt;private String commandTrack="";
    private final Runnable commandCheck=this::checkCommand;
    private final Runnable albumRefresh=()->{albumRetryPending=false;if(ticking&&!closed)subscribe();};
    private final Runnable tick=new Runnable(){public void run(){if(!ticking||closed)return;checkProgress();changed.run();if(ticking&&!closed)handler.postDelayed(this,500);}};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){
        if(ConnectivityManager.CONNECTIVITY_ACTION.equals(i.getAction())){
            try{ConnectivityManager manager=(ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);NetworkInfo network=manager==null?null:manager.getActiveNetworkInfo();if(network!=null&&network.isConnected())networkAvailable();}catch(Exception e){LauncherApp.record("QQ音乐封面联网恢复检测失败",e);}return;
        }
        if(!EVENTS.equals(i.getAction()))return;
        try{accept(i.getStringExtra(EXTRA));}catch(Exception e){LauncherApp.record("QQ音乐消息格式不支持",e);}
    }};
    QqMusicBridge(Context c,Runnable callback){this(c,callback,"");}
    QqMusicBridge(Context c,Runnable callback,String cacheSuffix){
        context=c;changed=callback;interfaceChanged=this::interfaceChanged;cover=new AlbumArt(c,callback,cacheSuffix);
        android.content.SharedPreferences saved=new Config(c).p;
        title=saved.getString("qqLastTitle","");artist=saved.getString("qqLastArtist","");
        duration=Math.max(0,saved.getLong("qqLastDuration",0));position=Math.max(0,saved.getLong("qqLastPosition",0));
        if(!title.isEmpty()){
            String key=title+"\n"+artist;
            if(key.equals(saved.getString("qqLyricSong","")))lyrics.addAll(parseQqLrc(saved.getString("qqLrc","")));
            if(key.equals(saved.getString("qqCoverSong","")))cover.load(saved.getString("qqCoverUrl",""));
            lyricMessage="等待 QQ音乐恢复";
        }
    }
    private void interfaceChanged(){
        if(!registered||closed)return;
        LinkedAppCoordinator coordinator=LinkedAppCoordinator.get(context);
        boolean lost=coordinator.interfaceLost(Config.MUSIC),ready=coordinator.canConnect(Config.MUSIC),requestable=coordinator.canRequest(Config.MUSIC);
        boolean refresh=(lost&&!lastLost)||(ready&&!lastReady)||(requestable&&!lastRequestable);
        lastLost=lost;lastReady=ready;lastRequestable=requestable;
        // Readiness probes can time out while QQ is still serving playback.
        // Revoke actual playback only after a known death/replacement/startup.
        if(connected&&LinkedAppCoordinator.get(context).interfaceLost(Config.MUSIC)){
            position=currentPosition();receivedAt=SystemClock.elapsedRealtime();state=0;connected=false;trackConfirmed=false;
            seekPosition.reset();playbackFreshness.reset();cancelLyricRetry();handler.removeCallbacks(seekRefresh);cancelCommand();
            pendingMode=0;handler.removeCallbacks(modeFallback);handler.removeCallbacks(modeTimeout);
        }
        if(refresh){subscribe();beginSnapshot();}changed.run();
    }
    void start(){
        if(registered||closed)return;
        IntentFilter filter=new IntentFilter(EVENTS);filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);else context.registerReceiver(receiver,filter);
        registered=true;LinkedAppCoordinator.get(context).listen(interfaceChanged);subscribe();
    }
    void resume(){if(closed)return;ticking=true;albumMisses=lyricMisses=0;progressAttempt=0;progressAnchor=SystemClock.elapsedRealtime();cover.resume();beginSnapshot();subscribe();handler.removeCallbacks(tick);handler.post(tick);}
    void pause(){ticking=false;handler.removeCallbacks(tick);handler.removeCallbacks(snapshotRefresh);cancelAlbumRetry();cancelLyricRetry();saveLastTrack();}
    void stop(){if(closed)return;closed=true;ticking=false;saveLastTrack();LinkedAppCoordinator.get(context).unlisten(interfaceChanged);if(registered){context.unregisterReceiver(receiver);registered=false;}handler.removeCallbacksAndMessages(null);pendingMode=0;cover.close();}
    void subscribe(){
        if(closed)return;
        if(!Ui.installed(context,Config.MUSIC))return;
        LinkedAppCoordinator.get(context).requestMusicSubscription();
    }
    private void beginSnapshot(){
        handler.removeCallbacks(snapshotRefresh);snapshotParts=0;snapshotAttempt=0;
        snapshotAt=SystemClock.elapsedRealtime();if(ticking&&!closed)handler.postDelayed(snapshotRefresh,SNAPSHOT_DELAYS[0]);
    }
    private void recoverSnapshot(){
        if(closed||!ticking||snapshotParts==15)return;
        // Read-only repair for a missed initial/partial subscription, never replay playback.
        subscribe();snapshotAttempt++;
        if(snapshotAttempt<SNAPSHOT_DELAYS.length)handler.postDelayed(snapshotRefresh,
                Math.max(100,SNAPSHOT_DELAYS[snapshotAttempt]-(SystemClock.elapsedRealtime()-snapshotAt)));
    }
    private void cancelLyricRetry(){handler.removeCallbacks(lyricRefresh);lyricRetryPending=false;}
    private void retryLyrics(){
        if(!ticking||closed||lyricRetryPending||lyricMisses>=LYRIC_RETRIES.length)return;
        lyricRetryPending=true;handler.postDelayed(lyricRefresh,LYRIC_RETRIES[lyricMisses++]);
    }
    private void checkProgress(){
        if(state!=2||!connected||!trackConfirmed||progressAttempt>=PROGRESS_RECHECKS.length)return;
        long now=SystemClock.elapsedRealtime();
        if(now-progressAnchor<PROGRESS_RECHECKS[progressAttempt])return;
        // action=100 reads the current native state; it never plays or seeks.
        progressAttempt++;subscribe();
    }
    boolean waitingForProgress(){return state==2&&trackConfirmed&&playbackFreshness.waiting(SystemClock.elapsedRealtime());}
    static Intent subscriptionIntent(){return new Intent("com.tencent.qqmusiccar.action").setComponent(new ComponentName(Config.MUSIC,RECEIVER)).putExtra("action",100).addFlags(Intent.FLAG_RECEIVER_FOREGROUND);}
    static Intent commandIntent(int command){
        // Paired QQ 2.1.1.1 URI action=20 is bypassed while a native Activity
        // remains alive (LifeCycleManager.sAcounts > 0). The no-URI WeDrive
        // route dispatches directly to the same player, in either lifecycle state.
        // Send exactly one absolute play/pause or single previous/next command.
        if(command>=0&&command<=3){int[] indices={5,4,12,13};return indexedIntent(indices[command]);}
        return new Intent("com.tencent.qqmusiccar.action").setComponent(new ComponentName(Config.MUSIC,RECEIVER))
            .setData(Uri.parse("qqmusiccar://launcher?action=20&m0="+command));
    }
    private static Intent indexedIntent(int index){
        return new Intent(WEDRIVE_ACTION).setComponent(new ComponentName(Config.MUSIC,WEDRIVE_RECEIVER))
            .putExtra(WEDRIVE_EXTRA,"{\"command\":{\"extData\":{\"index\":"+index+"}}}").addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
    }
    void command(int command){
        if(closed)return;
        if(!Ui.installed(context,Config.MUSIC)){Ui.toast(context,"请先安装 QQ音乐车机版");return;}
        try{
            cancelCommand();subscribe(); context.sendBroadcast(commandIntent(command));
            if(command>=0&&command<=3){commandPending=command;commandAt=SystemClock.elapsedRealtime();commandTrack=title+"\n"+artist;commandAttempt=0;handler.postDelayed(commandCheck,600);}
            hint=connected?"指令已发送，等待 QQ音乐返回状态":"指令已发送；如无响应，请先在 QQ音乐选择一首歌";
            changed.run();
        }catch(Exception e){LauncherApp.record("QQ音乐控制失败",e);Ui.toast(context,"QQ音乐接口不可用，可改用原生小组件");}
    }
    private void cancelCommand(){handler.removeCallbacks(commandCheck);commandPending=-1;commandAttempt=0;}
    private boolean commandConfirmed(){
        if(commandPending==0)return state==2;
        if(commandPending==1)return state==3;
        return commandPending>=2&&!commandTrack.equals(title+"\n"+artist);
    }
    private void checkCommand(){
        if(closed||commandPending<0)return;
        // Refresh read-only state; never replay non-idempotent controls.
        subscribe();LinkedAppCoordinator coordinator=LinkedAppCoordinator.get(context);
        if(commandAttempt==0)coordinator.refreshSoon();
        long elapsed=SystemClock.elapsedRealtime()-commandAt;
        if(elapsed>=10000){cancelCommand();hint="QQ音乐未确认播放操作，请检查歌曲或网络后重试";LauncherApp.record("QQ音乐播放操作未收到确认；已重新检查接口，未重复发送控制命令",null);if(ticking)Ui.toast(context,hint);changed.run();return;}
        long[] offsets={1800,4500,10000};long next=offsets[Math.min(commandAttempt++,offsets.length-1)];
        handler.postDelayed(commandCheck,Math.max(100,next-elapsed));
    }
    boolean canSeek(){return connected&&trackConfirmed&&duration>0&&!title.isEmpty();}
    static boolean validMode(int mode){return mode==SINGLE||mode==SHUFFLE||mode==ORDER;}
    static int nextMode(int mode){return mode==SINGLE?SHUFFLE:mode==SHUFFLE?ORDER:SINGLE;}
    /** The no-URI WeDrive receiver dispatches these absolute modes even with no QQ Activity. */
    static Intent modeIntent(int mode){
        if(!validMode(mode))throw new IllegalArgumentException("Unsupported QQ mode");
        int index=mode==SINGLE?18:mode==SHUFFLE?17:16;
        return indexedIntent(index);
    }
    void cycleMode(){
        expireMode();setMode(nextMode(pendingMode!=0?pendingMode:playMode));
    }
    private void sendModeFallback(){if(pendingMode!=0)try{context.sendBroadcast(commandIntent(pendingMode));}catch(Exception e){LauncherApp.record("QQ音乐模式备用接口不可用",e);}}
    void setMode(int mode){
        if(closed)return;
        if(!validMode(mode))return;
        if(!Ui.installed(context,Config.MUSIC)){Ui.toast(context,"请先安装 QQ音乐车机版");return;}
        handler.removeCallbacks(modeFallback);handler.removeCallbacks(modeTimeout);
        pendingMode=mode;modeRequestedAt=SystemClock.elapsedRealtime();
        try{
            subscribe();context.sendBroadcast(modeIntent(mode));
            hint="正在请求 QQ音乐切换播放模式";changed.run();
            // Both transports set an absolute mode; retrying cannot cycle it twice.
            handler.postDelayed(modeFallback,900);handler.postDelayed(modeTimeout,3500);
        }catch(Exception e){pendingMode=0;LauncherApp.record("QQ音乐播放模式控制失败",e);hint="QQ音乐模式接口不可用";Ui.toast(context,hint);changed.run();}
    }
    private void expireMode(){if(pendingMode!=0&&SystemClock.elapsedRealtime()-modeRequestedAt>=3500){pendingMode=0;handler.removeCallbacks(modeFallback);handler.removeCallbacks(modeTimeout);hint="QQ音乐未确认模式切换，请先打开 QQ音乐选择普通歌曲后重试";Ui.toast(context,hint);changed.run();}}
    String modeText(){expireMode();if(pendingMode!=0)return "切换中…";return playMode==SINGLE?"单曲循环":playMode==SHUFFLE?"随机播放":playMode==ORDER?"顺序播放":"播放模式";}
    /** Supplied 2.1.1.1 receiver supports relative seconds: m0=7 forward, 8 back. */
    static Intent seekIntent(long target,long current,long duration){
        if(duration<=0)return null;
        target=Math.max(0,Math.min(target,Math.max(0,duration-1000)));
        long delta=target-current,seconds=(Math.abs(delta)+500)/1000;
        if(delta>0)seconds=Math.min(seconds,Math.max(0,(duration-1-current)/1000));
        seconds=Math.min(seconds,Integer.MAX_VALUE/1000); // receiver multiplies in a 32-bit int
        if(seconds==0)return null;
        return commandIntent(delta>0?7:8).setData(Uri.parse("qqmusiccar://launcher?action=20&m0="+(delta>0?7:8)+"&m1="+seconds));
    }
    boolean seekTo(long target,int expectedTrack){
        if(closed||!canSeek()||expectedTrack!=trackRevision)return false;
        long before=currentPosition();Intent intent=seekIntent(target,before,duration);if(intent==null)return false;
        try{
            context.sendBroadcast(intent);
            long delta=Long.parseLong(intent.getData().getQueryParameter("m1"))*1000;
            if("8".equals(intent.getData().getQueryParameter("m0")))delta=-delta;
            long seekAt=SystemClock.elapsedRealtime();playbackFreshness.seek(seekAt);progressAnchor=seekAt;progressAttempt=0;
            seekPosition.begin(before,Math.max(0,Math.min(duration,before+delta)),seekAt,state==2);
            // Query fresh state only; repeating the relative seek would move the audio twice.
            handler.removeCallbacks(seekRefresh);
            for(long delay:new long[]{500,1600,4000,8500})handler.postDelayed(seekRefresh,delay);
            changed.run();return true;
        }
        catch(Exception e){LauncherApp.record("QQ音乐进度调整失败",e);Ui.toast(context,"QQ音乐暂未响应进度调整");return false;}
    }
    void accept(String raw)throws JSONException{
        if(closed)return;
        if(raw==null||raw.length()>512*1024)return;
        JSONObject root=new JSONObject(raw);if(!"play".equals(root.optString("module")))return;
        JSONObject command=root.optJSONObject("command");if(command==null)return;
        String method=command.optString("method");JSONObject data=command.optJSONObject("data");if(data==null)return;
        if(!Arrays.asList("update_state","update_song","update_lyric","update_fav","update_album","update_play_mode").contains(method))return;
        String next=clean(data.optString("key_title",""));String singer=artistText(data.opt("key_artist"),data.opt("singer"));
        boolean artistProvided=data.has("key_artist")||data.has("singer");
        boolean emptyQueue="update_song".equals(method)&&data.has("key_title")&&next.isEmpty();
        if(!next.isEmpty()){
            if(!trackConfirmed){duration=position=0;receivedAt=SystemClock.elapsedRealtime();seekPosition.reset();playbackFreshness.reset();}
            trackConfirmed=true;
        }else if(emptyQueue){
            trackConfirmed=false;duration=position=0;state=0;receivedAt=SystemClock.elapsedRealtime();seekPosition.reset();playbackFreshness.reset();
        }
        boolean songChanged=!next.isEmpty()&&(!title.equals(next)||(artistProvided&&!singer.isEmpty()&&!artist.isEmpty()&&!artist.equals(singer)));
        if(songChanged)beginSnapshot();
        if(songChanged){cancelAlbumRetry();cancelLyricRetry();albumMisses=lyricMisses=0;playbackFreshness.reset();seekPosition.reset();handler.removeCallbacks(seekRefresh);trackRevision++;title=next;artist=singer;lyrics.clear();lyricMessage="歌词加载中…";android.content.SharedPreferences cache=new Config(context).p;String key=title+"\n"+artist;cover.load(key.equals(cache.getString("qqCoverSong",""))?cache.getString("qqCoverUrl",""):"");if(key.equals(cache.getString("qqLyricSong",""))){lyrics.addAll(parseQqLrc(cache.getString("qqLrc","")));}position=duration=0;receivedAt=SystemClock.elapsedRealtime();}
        // Missing artist fields are partial metadata, not a new track. Enrichment
        // of an initially unknown artist keeps the already received LRC and cover.
        if(!songChanged&&!next.isEmpty()&&artistProvided&&!singer.isEmpty())artist=singer;
        if(emptyQueue){snapshotParts=15;handler.removeCallbacks(snapshotRefresh);cancelLyricRetry();cancelAlbumRetry();}
        else{
            if(!next.isEmpty())snapshotParts|=1;
            if("update_state".equals(method))snapshotParts|=2;
            if("update_lyric".equals(method))snapshotParts|=4;
            if("update_album".equals(method))snapshotParts|=8;
            if(snapshotParts==15)handler.removeCallbacks(snapshotRefresh);
        }
        if("update_album".equals(method)){
            String url=data.optInt("code",-1)==0?AlbumArt.safeUrl(clean(data.optString("album_url",""))):"";
            if(!url.isEmpty()){
                cancelAlbumRetry();albumMisses=0;cover.load(url);
                if(trackConfirmed&&!title.isEmpty())new Config(context).p.edit().putString("qqCoverSong",title+"\n"+artist).putString("qqCoverUrl",url).apply();
            }else if(ticking&&!albumRetryPending&&albumMisses<ALBUM_RETRIES.length){
                // A temporary miss must not erase a valid cover for this track.
                albumRetryPending=true;handler.postDelayed(albumRefresh,ALBUM_RETRIES[albumMisses++]);
            }
        }
        if(data.has("total_time"))duration=Math.max(0,data.optLong("total_time"));
        long now=SystemClock.elapsedRealtime();
        boolean authoritative="update_state".equals(method)||"update_song".equals(method);
        if(data.has("curr_time")&&(authoritative||playbackFreshness.sampledAt()<0)){
            long sample=Math.max(0,data.optLong("curr_time"));
            if(seekPosition.accept(sample,currentPosition(),now,authoritative)){
                position=sample;receivedAt=now;positionRevision++;
                playbackFreshness.sample(sample,"update_state".equals(method)?data.optInt("state",0)==2:state==2,now);
                progressAnchor=now;progressAttempt=0;if(!seekPending())handler.removeCallbacks(seekRefresh);
            }
        }
        if("update_state".equals(method)){
            // Extrapolate only after an actual QQ state event, never on button presses.
            position=currentPosition();receivedAt=now;state=data.optInt("state",0);playbackFreshness.state(state==2,now);seekPosition.state(state==2,playbackFreshness.project(now));
        }
        if("update_play_mode".equals(method)){
            int mode=data.optInt("play_mode",0);if(validMode(mode)){playMode=mode;if(pendingMode==mode){pendingMode=0;handler.removeCallbacks(modeFallback);handler.removeCallbacks(modeTimeout);}}
        }
        if("update_lyric".equals(method)){
            int code=data.optInt("code",-1);String text=clean(data.optString("lyric",""));
            ArrayList<Line> parsed=code==0?parseQqLrc(text):new ArrayList<>();
            if(!parsed.isEmpty()){
                lyrics.clear();lyrics.addAll(parsed);lyricMessage="";cancelLyricRetry();lyricMisses=0;
                if(trackConfirmed&&!title.isEmpty())new Config(context).p.edit().putString("qqLyricSong",title+"\n"+artist).putString("qqLrc",text).apply();
            }else if(code!=0){
                // A transport/loader failure must not erase valid lyrics for this song.
                if(lyrics.isEmpty())lyricMessage="歌词暂未取得，稍后重试";retryLyrics();
            }else{
                cancelLyricRetry();if(lyrics.isEmpty())lyricMessage="这首歌暂无可同步歌词";
            }
        }
        if(commandPending>=0&&((commandPending<=1&&"update_state".equals(method))||(commandPending>=2&&!next.isEmpty()))&&commandConfirmed())cancelCommand();
        LinkedAppCoordinator.get(context).controlResponded(Config.MUSIC);connected=true;hint=!trackConfirmed?"QQ音乐已连接 · 请选择歌曲":"QQ音乐车机版 · 已连接";
        if(songChanged||"update_song".equals(method)||"update_state".equals(method))saveLastTrack();changed.run();
    }
    void networkAvailable(){
        if(closed||!ticking)return;long now=SystemClock.elapsedRealtime();if(lastAlbumNetwork>0&&now-lastAlbumNetwork<3000)return;lastAlbumNetwork=now;
        cover.networkAvailable();cancelAlbumRetry();cancelLyricRetry();albumMisses=lyricMisses=0;progressAttempt=0;progressAnchor=now;beginSnapshot();subscribe();
    }
    private void cancelAlbumRetry(){handler.removeCallbacks(albumRefresh);albumRetryPending=false;}
    private void saveLastTrack(){if(!connected||!trackConfirmed||title.isEmpty())return;new Config(context).p.edit().putString("qqLastTitle",title).putString("qqLastArtist",artist).putLong("qqLastDuration",duration).putLong("qqLastPosition",currentPosition()).apply();}
    boolean seekPending(){return seekPosition.pending(SystemClock.elapsedRealtime());}
    long currentPosition(){long now=playbackFreshness.project(SystemClock.elapsedRealtime()),p=position+(state==2&&trackConfirmed&&receivedAt>0?Math.max(0,now-receivedAt):0);p=seekPosition.position(p,now);return duration>0?Math.min(p,duration):p;}
    int lyricIndex(long time){int index=-1;for(int i=0;i<lyrics.size();i++){if(lyrics.get(i).time<=time)index=i;else break;}return index;}
    String[] lyricLines(){
        String[] lines={"","","",""};
        if(lyrics.isEmpty()){lines[0]=lyricMessage;return lines;}
        int index=lyricIndex(currentPosition());
        for(int row=0;row<lines.length;row++){
            int item=index+row;
            lines[row]=item<0?"♪":item<lyrics.size()?lyrics.get(item).text:"";
        }
        return lines;
    }
    static String artistText(Object value,Object fallback){
        Object actual=value==null?fallback:value;
        if(actual instanceof JSONArray){JSONArray list=(JSONArray)actual;StringBuilder names=new StringBuilder();for(int i=0;i<list.length();i++){JSONObject item=list.optJSONObject(i);String name=item==null?list.optString(i):item.optString("singer",item.optString("name",""));if(!name.isEmpty()){if(names.length()>0)names.append(" / ");names.append(name);}}return names.toString();}
        return clean(actual==null?"":actual.toString());
    }
    static String clean(String text){return text==null||"null".equals(text)?"":text;}
    static final class Line {final long time;final String text;Line(long t,String s){time=t;text=s;}}
    static ArrayList<Line> parseLrc(String text){return parseLrc(text,false);}
    static ArrayList<Line> parseQqLrc(String text){return parseLrc(text,true);}
    private static ArrayList<Line> parseLrc(String text,boolean qqMilliseconds){
        ArrayList<Line> result=new ArrayList<>();Pattern tag=Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]");long offset=0;
        Matcher offsetMatch=Pattern.compile("\\[offset:([+-]?\\d{1,8})\\]",Pattern.CASE_INSENSITIVE).matcher(text);if(offsetMatch.find())offset=Long.parseLong(offsetMatch.group(1));
        for(String line:text.split("[\\r\\n]+")){
            Matcher m=tag.matcher(line);ArrayList<Long> times=new ArrayList<>();int end=0;
            while(m.find()){String f=m.group(3);long ms=f==null?0:Long.parseLong(f)*(qqMilliseconds?1:f.length()==1?100:f.length()==2?10:1);times.add(Long.parseLong(m.group(1))*60000+Long.parseLong(m.group(2))*1000+ms+offset);end=m.end();}
            String words=line.substring(end).trim();if(!words.isEmpty())for(long t:times)result.add(new Line(Math.max(0,t),words));
            if(result.size()>10000)break;
        }
        Collections.sort(result,(a,b)->a.time<b.time?-1:a.time==b.time?0:1);return result;
    }
}
