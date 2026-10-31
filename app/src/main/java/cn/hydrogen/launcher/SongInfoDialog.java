package cn.hydrogen.launcher;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Only displays metadata actually supplied by QQ; the bridge remains authoritative. */
final class SongInfoDialog extends Dialog {
    private final MainActivity a;private final QqMusicBridge bridge;private final ThemeColors colors;
    private final TextView song,singer,state,position,mode,notice,fallback;private final ImageView artwork;
    SongInfoDialog(MainActivity a,QqMusicBridge bridge){
        super(a);this.a=a;this.bridge=bridge;colors=new ThemeColors(new Config(a));requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content=Ui.column(a);content.setPadding(a.u(24),a.u(18),a.u(24),a.u(18));content.setBackground(ThemeStyle.dialogPanel(a,a.u(24)));
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);header.addView(text("歌曲信息",22,colors.text),new LinearLayout.LayoutParams(0,a.u(48),1));TextView close=text("×",28,Ui.MUTED);close.setGravity(Gravity.CENTER);close.setContentDescription("关闭歌曲信息");close.setOnClickListener(v->dismiss());header.addView(close,new LinearLayout.LayoutParams(a.u(48),a.u(48)));content.addView(header);
        ScrollView scroll=new ScrollView(a);LinearLayout body=Ui.column(a);body.setPadding(0,a.u(16),0,a.u(10));scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout track=new LinearLayout(a);track.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout cover=new FrameLayout(a);cover.setBackground(ThemeStyle.panel(a,"music",colors.panel(255),a.u(14),a.u(1),Ui.GLASS_LINE));artwork=new ImageView(a);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.setContentDescription("歌曲信息封面");cover.addView(artwork,new FrameLayout.LayoutParams(-1,-1));fallback=text("♪",40,colors.accent);fallback.setGravity(Gravity.CENTER);cover.addView(fallback,new FrameLayout.LayoutParams(-1,-1));track.addView(cover,new LinearLayout.LayoutParams(a.u(110),a.u(110)));
        LinearLayout meta=Ui.column(a);meta.setPadding(a.u(20),0,0,0);meta.setGravity(Gravity.CENTER_VERTICAL);song=text("",25,colors.text);song.setContentDescription("歌曲信息歌名");song.setMaxLines(4);meta.addView(song);singer=text("",17,Ui.MUTED);singer.setPadding(0,a.u(10),0,0);singer.setContentDescription("歌曲信息歌手");singer.setMaxLines(3);meta.addView(singer);track.addView(meta,new LinearLayout.LayoutParams(0,-2,1));body.addView(track);
        View divider=new View(a);divider.setBackgroundColor(0x284f7585);LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,Math.max(1,a.u(1)));dp.topMargin=a.u(24);dp.bottomMargin=a.u(12);body.addView(divider,dp);
        state=value(body,"播放状态");position=value(body,"播放进度");mode=value(body,"播放模式");notice=text("",14,Ui.MUTED);notice.setPadding(0,a.u(12),0,a.u(4));notice.setContentDescription("歌曲信息来源");body.addView(notice);
        TextView open=text("打开 QQ音乐  ↗",16,colors.accent);open.setGravity(Gravity.CENTER);open.setContentDescription("歌曲信息打开QQ音乐");open.setBackground(ThemeStyle.panel(a,"music",colors.panel(150),a.u(12),a.u(1),Ui.GLASS_LINE));open.setOnClickListener(v->{dismiss();Ui.launch(a,new Config(a).pkg(true));});content.addView(open,new LinearLayout.LayoutParams(-1,a.u(48)));
        ThemeStyle.role(content,"dialogText");ThemeStyle.apply(content,"dialogText");setContentView(content);Window window=getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=Math.max(0,Math.min(40,new Config(a).n("dialogDim",16)))/100f;window.setAttributes(p);}update();
    }
    private TextView text(String s,int size,int color){return Ui.text(a,s,a.sp(size),color);}
    private TextView value(LinearLayout body,String title){LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.addView(text(title,15,Ui.MUTED),new LinearLayout.LayoutParams(a.u(110),a.u(42)));TextView value=text("",16,colors.text);value.setContentDescription("歌曲信息"+title);row.addView(value,new LinearLayout.LayoutParams(0,-2,1));body.addView(row);return value;}
    @Override public void show(){
        Window window=getWindow();int systemUi=a.getWindow().getDecorView().getSystemUiVisibility();
        if(window!=null){window.setWindowAnimations(0);window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setLayout(ThemeStyle.dialogWidth(a,Math.min(a.u(680),(int)(a.getResources().getDisplayMetrics().widthPixels*.88f)),.6f),Math.min(a.u(470),(int)(a.getResources().getDisplayMetrics().heightPixels*.68f)));}
        a.prepareMapDialog(this);a.mapDialogShowing(this);
        try{super.show();if(window!=null){window.getDecorView().setSystemUiVisibility(systemUi);a.mapDialogShown(this);window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);}}
        catch(RuntimeException error){a.mapDialogFailed(this);throw error;}
    }
    void update(){
        song.setText(bridge.title.isEmpty()?"暂无歌曲":bridge.title);singer.setText(bridge.artist.isEmpty()?"未提供歌手信息":bridge.artist);
        state.setText(!bridge.connected?"等待连接":bridge.state==2?(bridge.waitingForProgress()?"等待进度同步":"正在播放"):bridge.state==3?"已暂停":bridge.title.isEmpty()?"请选择歌曲":"已连接");
        position.setText(stamp(bridge.currentPosition())+"  /  "+(bridge.duration>0?stamp(bridge.duration):"--:--"));
        mode.setText(bridge.playMode==QqMusicBridge.SINGLE?"单曲循环":bridge.playMode==QqMusicBridge.SHUFFLE?"随机播放":bridge.playMode==QqMusicBridge.ORDER?"顺序播放":"暂未提供");
        notice.setText(!bridge.trackConfirmed&&!bridge.title.isEmpty()?"上次播放记录 · 等待 QQ音乐更新":"歌曲信息由 QQ音乐提供");artwork.setImageBitmap(bridge.cover.bitmap);fallback.setVisibility(bridge.cover.bitmap==null?View.VISIBLE:View.GONE);
    }
    private static String stamp(long value){return String.format(Locale.US,"%02d:%02d",Math.max(0,value)/60000,Math.max(0,value)/1000%60);}
}
