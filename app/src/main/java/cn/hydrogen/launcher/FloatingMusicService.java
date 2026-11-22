package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;

/** A movable native QQ controller. It does not host another application's Activity. */
public class FloatingMusicService extends Service {
    private static final String CLOSE="cn.windchaser.unified.CLOSE_FLOATING_MUSIC";
    private WindowManager wm;private WindowManager.LayoutParams bounds;private LinearLayout panel;
    private MusicSession.Lease musicLease;
    private QqMusicBridge music;private TextView title,artist,lyric,play;private ImageView art;private Bitmap shown;
    private SeekBar volume;private AudioManager audio;private float scale;private int screenW,screenH;private boolean attached;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener themeChanged=(prefs,key)->{if((key==null||"radius".equals(key))&&panel!=null)ThemeStyle.apply(panel,"musicText");};
    static boolean show(Activity a) {
        if(Build.VERSION.SDK_INT>=23&&!Settings.canDrawOverlays(a)) {
            Ui.toast(a,"请允许追风桌面显示悬浮窗，然后返回再次点击悬浮音乐");
            Ui.open(a,new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+a.getPackageName())));return false;
        }
        try{a.startService(new Intent(a,FloatingMusicService.class));return true;}
        catch(Exception e){LauncherApp.record("悬浮音乐启动失败",e);Ui.toast(a,"系统未允许悬浮音乐，请查看诊断");return false;}
    }
    @Override public IBinder onBind(Intent intent){return null;}
    @Override public int onStartCommand(Intent intent,int flags,int id){if(intent!=null&&CLOSE.equals(intent.getAction()))stopSelf();return START_NOT_STICKY;}
    @Override public void onCreate(){
        super.onCreate();ThemeStyle.config(this).p.registerOnSharedPreferenceChangeListener(themeChanged);
        try {
            foreground();wm=(WindowManager)getSystemService(WINDOW_SERVICE);audio=(AudioManager)getSystemService(AUDIO_SERVICE);measure();
            musicLease=MusicSession.acquire(this,this::refresh);music=musicLease.bridge();build();
            bounds=new WindowManager.LayoutParams(Math.min(u(440),screenW),u(278),Build.VERSION.SDK_INT>=26?WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY:WindowManager.LayoutParams.TYPE_PHONE,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);
            bounds.gravity=Gravity.TOP|Gravity.LEFT;Config cfg=new Config(this);bounds.x=cfg.n("floatingX",screenW-bounds.width-u(20));bounds.y=cfg.n("floatingY",u(24));clamp();
            ThemeStyle.apply(panel,"musicText");wm.addView(panel,bounds);attached=true;musicLease.resume();
        }catch(Exception e){LauncherApp.record("悬浮窗口不可用",e);Ui.toast(this,"系统拒绝悬浮窗口；完整应用小窗仍需原厂支持");stopSelf();}
    }
    private void measure(){DisplayMetrics d=new DisplayMetrics();wm.getDefaultDisplay().getMetrics(d);screenW=d.widthPixels;screenH=d.heightPixels;scale=Math.max(.5f,Math.min(screenW/1280f,screenH/720f));}
    private int u(int value){return Math.max(1,Math.round(value*scale));}
    private TextView text(String label,int size){TextView t=Ui.text(this,label,size*scale/getResources().getDisplayMetrics().scaledDensity,Ui.WHITE);t.setSingleLine();t.setEllipsize(android.text.TextUtils.TruncateAt.END);return t;}
    private TextView button(String label,String description,Runnable click){TextView t=text(label,17);t.setGravity(Gravity.CENTER);t.setContentDescription(description);t.setOnClickListener(v->click.run());return t;}
    private void build(){
        panel=Ui.column(this);panel.setContentDescription("QQ音乐悬浮控制");panel.setPadding(u(16),u(8),u(16),u(8));panel.setBackground(ThemeStyle.panel(this,"music",0xf00c2031,u(20),u(1),0xff59988f));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView drag=text("⠿  QQ音乐 · 拖动",15);drag.setContentDescription("拖动悬浮音乐");header.addView(drag,new LinearLayout.LayoutParams(0,-1,1));
        drag.setOnTouchListener(new View.OnTouchListener(){float x,y;int bx,by;public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){x=e.getRawX();y=e.getRawY();bx=bounds.x;by=bounds.y;return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){bounds.x=bx+Math.round(e.getRawX()-x);bounds.y=by+Math.round(e.getRawY()-y);clamp();updateBounds();return true;}if(e.getActionMasked()==MotionEvent.ACTION_UP){new Config(FloatingMusicService.this).p.edit().putInt("floatingX",bounds.x).putInt("floatingY",bounds.y).apply();v.performClick();return true;}return e.getActionMasked()==MotionEvent.ACTION_CANCEL;}});
        header.addView(button("QQ 首页 ↗","悬浮窗返回QQ音乐首页",()->QqNavigation.openHome(this)),new LinearLayout.LayoutParams(u(110),-1));
        header.addView(button("×","关闭悬浮音乐",this::stopSelf),new LinearLayout.LayoutParams(u(48),-1));panel.addView(header,new LinearLayout.LayoutParams(-1,u(48)));
        LinearLayout track=new LinearLayout(this);track.setGravity(Gravity.CENTER_VERTICAL);
        art=new ImageView(this);art.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(music!=null&&attached)music.cover.target(art,art.getWidth(),art.getHeight());});art.setContentDescription("悬浮歌曲封面");art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setBackground(ThemeStyle.panel(this,"music",0xff264657,u(8),0,0));track.addView(art,new LinearLayout.LayoutParams(u(64),u(64)));
        LinearLayout meta=Ui.column(this);meta.setPadding(u(14),0,0,0);title=text("等待 QQ音乐播放",20);artist=text("QQ音乐车机版",14);artist.setTextColor(Ui.MUTED);meta.addView(title,new LinearLayout.LayoutParams(-1,u(36)));meta.addView(artist,new LinearLayout.LayoutParams(-1,u(26)));track.addView(meta,new LinearLayout.LayoutParams(0,-1,1));panel.addView(track,new LinearLayout.LayoutParams(-1,u(68)));
        lyric=text("等待 QQ音乐发送歌词",17);lyric.setTextColor(0xffc8f7e9);lyric.setContentDescription("悬浮歌曲歌词");panel.addView(lyric,new LinearLayout.LayoutParams(-1,u(36)));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(button("|◀","悬浮上一首",()->music.command(2)),new LinearLayout.LayoutParams(0,u(50),1));play=button("▶","悬浮播放 / 暂停",()->music.command(music.state==2?1:0));play.setTextColor(0xffa7e2d2);controls.addView(play,new LinearLayout.LayoutParams(0,u(50),1));controls.addView(button("▶|","悬浮下一首",()->music.command(3)),new LinearLayout.LayoutParams(0,u(50),1));panel.addView(controls,new LinearLayout.LayoutParams(-1,u(50)));
        LinearLayout vol=new LinearLayout(this);vol.setGravity(Gravity.CENTER_VERTICAL);vol.addView(text("音量",13),new LinearLayout.LayoutParams(u(40),-1));volume=new SeekBar(this);volume.setContentDescription("悬浮媒体音量");volume.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int value,boolean user){if(user)audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});vol.addView(volume,new LinearLayout.LayoutParams(0,-1,1));panel.addView(vol,new LinearLayout.LayoutParams(-1,u(48)));
    }
    private void refresh(){if(panel==null||music==null)return;if(attached)music.cover.target(art,art.getWidth(),art.getHeight());title.setText(music.title.isEmpty()?"等待 QQ音乐播放":music.title);artist.setText(music.artist.isEmpty()?"QQ音乐车机版":music.artist);lyric.setText(music.lyricLines()[0]);play.setText(music.state==2?"Ⅱ":"▶");if(shown!=music.cover.bitmap){shown=music.cover.bitmap;art.setImageBitmap(shown);}volume.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));}
    private void clamp(){bounds.x=Math.max(0,Math.min(bounds.x,screenW-bounds.width));bounds.y=Math.max(0,Math.min(bounds.y,screenH-bounds.height));}
    private void updateBounds(){if(attached)try{wm.updateViewLayout(panel,bounds);}catch(Exception e){LauncherApp.record("悬浮窗口更新失败",e);stopSelf();}}
    @Override public void onConfigurationChanged(Configuration c){super.onConfigurationChanged(c);if(bounds!=null&&attached)try{clearArtwork();wm.removeView(panel);attached=false;measure();shown=null;build();bounds.width=Math.min(u(440),screenW);bounds.height=u(278);clamp();ThemeStyle.apply(panel,"musicText");wm.addView(panel,bounds);attached=true;refresh();}catch(Exception e){LauncherApp.record("悬浮窗口方向调整失败",e);stopSelf();}}
    private void foreground(){
        String channel="floating-music";if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(channel,"悬浮音乐控制",NotificationManager.IMPORTANCE_LOW));
        int flags=PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=23?PendingIntent.FLAG_IMMUTABLE:0);
        PendingIntent home=PendingIntent.getActivity(this,30,new Intent(this,MainActivity.class),flags),close=PendingIntent.getService(this,31,new Intent(this,FloatingMusicService.class).setAction(CLOSE),flags);
        Notification.Builder n=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,channel):new Notification.Builder(this);
        startForeground(41,n.setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("QQ音乐悬浮控制").setContentText("拖动标题移动，点击 × 关闭；点击通知返回追风桌面").setOngoing(true).setContentIntent(home).addAction(android.R.drawable.ic_menu_close_clear_cancel,"关闭",close).build());
    }
    private void clearArtwork(){if(music!=null&&art!=null)music.cover.releaseTarget(art);if(art!=null)art.setImageDrawable(null);shown=null;}
    @Override public void onDestroy(){ThemeStyle.config(this).p.unregisterOnSharedPreferenceChangeListener(themeChanged);clearArtwork();if(musicLease!=null){musicLease.close();musicLease=null;}if(attached){try{wm.removeView(panel);}catch(Exception e){LauncherApp.record("悬浮窗口移除失败",e);}attached=false;}if(panel!=null)panel.removeAllViews();panel=null;art=null;title=artist=lyric=play=null;volume=null;music=null;stopForeground(true);super.onDestroy();}
}
