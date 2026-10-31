package cn.hydrogen.launcher;

import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Native transport and synchronized lyrics, shared by both home pages. */
final class MusicCard extends LinearLayout {
    private final MainActivity a;private final QqMusicBridge bridge;private final ThemeColors colors;
    private final TextView title,artist,time;
    private final ImageView play,mode;
    private final TextView[] lyricRows=new TextView[12];
    private final PlaybackModeIcon modeIcon=new PlaybackModeIcon();
    private final TransportIcon playIcon=new TransportIcon(TransportIcon.PLAY);
    private android.app.AlertDialog playbackMenu;
    private SongInfoDialog songInfo;
    private final boolean minimal;
    private int lyricCount=4,shownLyricIndex=-1;
    private final SeekBar progress;
    private final LinearLayout playback;
    private boolean tracking,seekCancelled,lyricTouch;
    private int gestureTrack;private long previewPosition=-1;
    private final int lyricHeight;private final float lyricScale;
    private final MediaVolume volume;
    private final ImageView artwork;private final TextView artFallback;private android.graphics.Bitmap shown;
    private static final int UNDECIDED=0,AXIS_HORIZONTAL=1,AXIS_VERTICAL=2;
    private int swipeAxis;private float swipeX,swipeY;private boolean swipeArea,swipeCancelled;
    MusicCard(MainActivity activity,QqMusicBridge music,boolean minimal,Runnable menu){
        super(activity);a=activity;bridge=music;this.minimal=minimal;Config cfg=new Config(a);lyricScale=cfg.n("lyricSize",110)/100f*cfg.n("textScale",100)/100f;colors=new ThemeColors(cfg);modeIcon.setColor(colors.accent);playIcon.setColor(colors.accent);setOrientation(VERTICAL);setPadding(a.u(26),a.u(16),a.u(26),a.u(12));
        setTag("live-widget");setContentDescription(minimal?"简洁页无边框音乐区":"QQ音乐卡片");
        if(minimal)setPadding(0,0,0,0);else setBackground(ThemeStyle.panel(a,"music",colors.panel(cfg.n("cardAlpha",Config.CARD_ALPHA)*255/100),a.cardRadius(20),a.u(1),Ui.GLASS_LINE));
        LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text(minimal?"正在聆听  /  QQ音乐":"●  QQ音乐",minimal?14:17,colors.accent);header.addView(brand,new LayoutParams(0,a.u(36),1));
        View home=Ui.appButton(a,cfg.pkg(true),"music","打开QQ音乐",v->Ui.launch(a,cfg.pkg(true)),minimal?1f:MainActivity.SECOND_PAGE_RADIUS_SCALE);header.addView(home,new LayoutParams(a.u(48),a.u(48)));
        TextView more=text("•••",23,Ui.MUTED);more.setGravity(Gravity.CENTER);more.setContentDescription("音乐卡片菜单");more.setOnClickListener(v->menu.run());header.addView(more,new LayoutParams(a.u(48),a.u(48)));addView(header,new LayoutParams(-1,a.u(48)));
        playback=Ui.column(a);addView(playback,new LayoutParams(-1,0,1));
        LinearLayout track=new LinearLayout(a);track.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout art=new FrameLayout(a);art.setBackground(Ui.panel(0x303a617b,a.u(12),0,0));
        artwork=new ImageView(a);artwork.setScaleType(ImageView.ScaleType.CENTER_CROP);artwork.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->syncArtwork());artwork.setContentDescription("当前歌曲封面");art.addView(artwork,new FrameLayout.LayoutParams(-1,-1));
        artFallback=text("♪",36,0xff9cb8ce);artFallback.setGravity(Gravity.CENTER);art.addView(artFallback,new FrameLayout.LayoutParams(-1,-1));track.addView(art,new LayoutParams(a.u((minimal?90:78)*cfg.n("coverSize",100)/100f),a.u((minimal?90:78)*cfg.n("coverSize",100)/100f)));
        LinearLayout meta=Ui.column(a);meta.setGravity(Gravity.CENTER_VERTICAL);meta.setPadding(a.u(minimal?22:14),0,0,0);
        title=ThemeStyle.role(text("等待播放",minimal?32:24,Ui.WHITE),"title");title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(android.text.TextUtils.TruncateAt.END);meta.addView(title,new LayoutParams(-1,a.u((minimal?52:46)*Math.max(1,ThemeStyle.scale(cfg,"title")))));
        artist=ThemeStyle.role(text("QQ音乐车机版",16,Ui.MUTED),"artist");artist.setSingleLine();artist.setEllipsize(android.text.TextUtils.TruncateAt.END);meta.addView(artist,new LayoutParams(-1,a.u(28*Math.max(1,ThemeStyle.scale(cfg,"artist")))));track.addView(meta,new LayoutParams(0,-1,1));playback.addView(track,new LayoutParams(-1,a.u(Math.max(Math.max(minimal?96:84,(minimal?90:78)*cfg.n("coverSize",100)/100f+6),(minimal?52:46)*Math.max(1,ThemeStyle.scale(cfg,"title"))+28*Math.max(1,ThemeStyle.scale(cfg,"artist"))))));
        lyricHeight=a.u((minimal?44:40)*lyricScale);
        LyricArea words=new LyricArea();words.setOrientation(VERTICAL);words.setGravity(Gravity.CENTER_VERTICAL);words.setContentDescription("歌词，可上下拖动或点击调整播放进度");
        for(int i=0;i<lyricRows.length;i++){
            TextView row=ThemeStyle.owned(text("",minimal?19:18,0xff8aa6b9));
            row.setSingleLine();row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setIncludeFontPadding(false);row.setGravity(Gravity.CENTER_VERTICAL);row.setContentDescription("歌词第"+(i+1)+"行");
            row.setOnClickListener(v->{Object line=v.getTag();if(line instanceof QqMusicBridge.Line)requestSeek(((QqMusicBridge.Line)line).time,lyricTouch?gestureTrack:bridge.trackRevision);});
            lyricRows[i]=row;row.setVisibility(i<lyricCount?VISIBLE:GONE);words.addView(row,new LayoutParams(-1,0,1));
        }
        styleLyrics();LayoutParams wp=new LayoutParams(-1,0,1);wp.topMargin=a.u(minimal?10:14);wp.bottomMargin=a.u(minimal?8:10);playback.addView(words,wp);
        progress=new SeekBar(a){
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas){
                float left=getPaddingLeft(),right=getWidth()-getPaddingRight(),y=getHeight()/2f,x=left+(right-left)*getProgress()/getMax();
                paint.setStrokeWidth(Math.max(1,a.u(4)));paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);paint.setColor((ThemeStyle.color(cfg,"progressTrackColor",0xff698498)&0xffffff)|0x48000000);canvas.drawLine(left,y,right,y,paint);
                paint.setColor(isEnabled()?colors.accent:0xff66838c);canvas.drawLine(left,y,x,y,paint);canvas.drawCircle(x,y,Math.max(2,a.u(7)),paint);
            }
            @Override public boolean onTouchEvent(MotionEvent event){seekCancelled=event.getActionMasked()==MotionEvent.ACTION_CANCEL;return super.onTouchEvent(event);}
        };
        progress.setMax(1000);progress.setPadding(a.u(8),0,a.u(8),0);progress.setMinimumHeight(0);
        progress.setThumb(null);progress.setProgressDrawable(null);
        progress.setContentDescription("歌曲播放进度，可点击或拖动");
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){tracking=true;gestureTrack=bridge.trackRevision;}
            public void onProgressChanged(SeekBar bar,int value,boolean user){if(user&&tracking){previewPosition=bridge.duration*value/1000;update();}}
            public void onStopTrackingTouch(SeekBar bar){long target=previewPosition;tracking=false;previewPosition=-1;if(!seekCancelled&&target>=0)requestSeek(target,gestureTrack);else update();}
        });playback.addView(progress,new LayoutParams(-1,a.u(28)));
        time=text("--:--   /   --:--",13,Ui.MUTED);playback.addView(time,new LayoutParams(-1,a.u(minimal?24:28)));
        LinearLayout controls=new LinearLayout(a){
            @Override protected void onMeasure(int widthSpec,int heightSpec){super.onMeasure(MeasureSpec.makeMeasureSpec(Math.min(MeasureSpec.getSize(widthSpec),a.u(460)*cfg.n("transportWidth",100)/100),MeasureSpec.EXACTLY),heightSpec);}
        };controls.setGravity(Gravity.CENTER);controls.setContentDescription("居中播放控制栏");
        mode=control("切换播放模式",modeIcon,30,v->bridge.cycleMode());mode.setOnLongClickListener(v->{showPlaybackMenu();return true;});controls.addView(mode,new LayoutParams(0,-1,1));
        controls.addView(control("上一首",new TransportIcon(TransportIcon.PREVIOUS),52,v->bridge.command(2)),new LayoutParams(0,-1,1));
        play=control("播放 / 暂停",playIcon,72,v->bridge.command(bridge.state==2?1:0));controls.addView(play,new LayoutParams(0,-1,1));
        controls.addView(control("下一首",new TransportIcon(TransportIcon.NEXT),52,v->bridge.command(3)),new LayoutParams(0,-1,1));
        controls.addView(control("歌曲信息",new TransportIcon(TransportIcon.INFO),44,v->showSongInfo()),new LayoutParams(0,-1,1));
        LayoutParams cp=new LayoutParams(-1,a.u(80));cp.gravity=Gravity.CENTER_HORIZONTAL;playback.addView(controls,cp);
        volume=minimal?new MediaVolume(a):null;if(volume!=null){LayoutParams vp=new LayoutParams(-1,a.u(76));vp.topMargin=a.u(5);playback.addView(volume,vp);}
        update();
    }
    private TextView text(String s,int size,int color){TextView t=Ui.text(a,s,a.sp(size),color==Ui.WHITE?colors.text:color);t.setShadowLayer(a.u(4),0,a.u(1),0xff06111b);return t;}
    private ImageView control(String name,android.graphics.drawable.Drawable icon,int size,View.OnClickListener action){
        if(icon instanceof TransportIcon)((TransportIcon)icon).setColor(colors.accent);
        ImageView view=new ImageView(a);view.setImageDrawable(icon);view.setScaleType(ImageView.ScaleType.FIT_CENTER);view.setContentDescription(name);view.setOnClickListener(action);view.setFocusable(true);
        int vertical=a.u((80-size)/2),horizontal=a.u(size==72?3:size==52?6:10);view.setPadding(horizontal,vertical,horizontal,vertical);
        android.graphics.drawable.StateListDrawable feedback=new android.graphics.drawable.StateListDrawable();feedback.addState(new int[]{android.R.attr.state_pressed},Ui.panel(0x284f9b91,minimal?a.u(40):a.cardRadius(40),0,0));feedback.addState(new int[]{android.R.attr.state_focused},Ui.panel(0x284f9b91,minimal?a.u(40):a.cardRadius(40),0,0));feedback.addState(new int[]{},new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));view.setBackground(feedback);return view;
    }
    private void showPlaybackMenu(){
        int[] values={QqMusicBridge.SINGLE,QqMusicBridge.SHUFFLE,QqMusicBridge.ORDER};String[] names={"单曲循环","随机播放","顺序播放","打开 QQ音乐"};
        for(int i=0;i<values.length;i++)if(bridge.playMode==values[i])names[i]="✓  "+names[i];
        playbackMenu=new ThemeDialog.Builder(a).setTitle("播放菜单 · "+bridge.modeText()).setItems(names,(d,n)->{if(n<values.length)bridge.setMode(values[n]);else Ui.launch(a,new Config(a).pkg(true));}).setNegativeButton("取消",null).create();playbackMenu.setOnDismissListener(d->playbackMenu=null);playbackMenu.show();
    }
    private void showSongInfo(){if(songInfo!=null)songInfo.dismiss();songInfo=new SongInfoDialog(a,bridge);songInfo.setOnDismissListener(d->songInfo=null);songInfo.show();}
    private void clearArtwork(){if(artwork==null||bridge==null)return;bridge.cover.releaseTarget(artwork);shown=null;artwork.setImageDrawable(null);}
    private void syncArtwork(){
        if(artwork==null||bridge==null)return;
        if(!isShown()||getWindowVisibility()!=VISIBLE||getWindowToken()==null){clearArtwork();return;}
        bridge.cover.target(artwork,artwork.getWidth(),artwork.getHeight());
    }
    void release(){if(playbackMenu!=null)playbackMenu.dismiss();if(songInfo!=null)songInfo.dismiss();clearArtwork();}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();syncArtwork();update();}
    @Override protected void onVisibilityChanged(View changed,int visibility){super.onVisibilityChanged(changed,visibility);if(artwork!=null){syncArtwork();if(isShown())update();}}
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);if(artwork!=null){syncArtwork();if(visibility==VISIBLE)update();}}
    @Override protected void onDetachedFromWindow(){release();super.onDetachedFromWindow();}

    void update(){
        if(!isShown()||getWindowVisibility()!=VISIBLE){clearArtwork();return;}
        syncArtwork();
        if(songInfo!=null)songInfo.update();
        bridge.modeText();modeIcon.setMode(bridge.playMode);mode.setAlpha(bridge.pendingMode!=0?.45f:1f);
        if(volume!=null)volume.refresh();
        if(shown!=bridge.cover.bitmap){shown=bridge.cover.bitmap;artwork.setImageBitmap(shown);}artFallback.setVisibility(shown==null?VISIBLE:GONE);
        textIfChanged(title,bridge.title.isEmpty()?"等待播放":bridge.title);String singer=bridge.artist.isEmpty()?"QQ音乐车机版":bridge.artist;textIfChanged(artist,!bridge.trackConfirmed&&!bridge.title.isEmpty()?singer+" · 上次播放":singer);
        long actual=bridge.currentPosition();
        long p=previewPosition>=0&&gestureTrack==bridge.trackRevision?previewPosition:actual;
        if(!lyricTouch)showLyrics(bridge.lyricIndex(p));playIcon.setPlaying(bridge.state==2);
        progress.setEnabled(bridge.canSeek());if(!tracking)progress.setProgress(bridge.duration>0?(int)(p*1000/bridge.duration):0);
        textIfChanged(time,stamp(p)+"   /   "+(bridge.duration>0?stamp(bridge.duration):"--:--")+(bridge.seekPending()?"  …":bridge.waitingForProgress()?"  · 等待同步":""));
    }
    private static void textIfChanged(TextView view,String value){if(!android.text.TextUtils.equals(view.getText(),value))view.setText(value);}
    private void showLyrics(int index){
        shownLyricIndex=index;
        for(int row=0;row<lyricRows.length;row++){
            int n=index+row-activeLyricRow();QqMusicBridge.Line line=row<lyricCount&&n>=0&&n<bridge.lyrics.size()?bridge.lyrics.get(n):null;
            textIfChanged(lyricRows[row],bridge.lyrics.isEmpty()?(row==activeLyricRow()?bridge.lyricMessage:""):line!=null?line.text:"");
            lyricRows[row].setTag(line);lyricRows[row].setClickable(line!=null&&bridge.canSeek());
        }
    }
    private int activeLyricRow(){return (lyricCount-1)/2;}
    private void styleLyrics(){for(int i=0;i<lyricRows.length;i++){boolean active=i==activeLyricRow();lyricRows[i].setSelected(active);lyricRows[i].setTextSize(a.sp((active?(minimal?26:23):(minimal?19:18))*lyricScale));lyricRows[i].setTextColor(active?ThemeStyle.color(ThemeStyle.config(a),"lyricActiveColor",colors.highlight()):ThemeStyle.color(ThemeStyle.config(a),"lyricIdleColor",0xff8aa6b9));}}
    private void requestSeek(long target,int revision){
        bridge.seekTo(target,revision);update();
    }
    @Override public boolean dispatchTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            swipeX=event.getX();swipeY=event.getY();swipeAxis=UNDECIDED;swipeCancelled=false;
            // Keep the seek bar, playback buttons and volume slider in charge of their own gestures.
            swipeArea=swipeY<playback.getTop()+progress.getTop();
        }else if(swipeArea){
            if(action==MotionEvent.ACTION_POINTER_DOWN)swipeCancelled=true;
            if(action==MotionEvent.ACTION_MOVE&&!swipeCancelled&&swipeAxis==UNDECIDED){
                float dx=Math.abs(event.getX()-swipeX),dy=Math.abs(event.getY()-swipeY);
                int slop=ViewConfiguration.get(a).getScaledTouchSlop();
                if(dx>slop&&dx>dy*1.2f)swipeAxis=AXIS_HORIZONTAL;
                else if(dy>slop&&dy>dx*1.2f)swipeAxis=AXIS_VERTICAL;
            }
        }
        boolean handled=super.dispatchTouchEvent(event);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){
            boolean navigate=action==MotionEvent.ACTION_UP&&swipeArea&&!swipeCancelled&&swipeAxis==AXIS_HORIZONTAL
                &&Math.abs(event.getX()-swipeX)>=Math.max(a.u(80),ViewConfiguration.get(a).getScaledTouchSlop()*3);
            boolean next=event.getX()<swipeX;swipeArea=false;swipeCancelled=false;swipeAxis=UNDECIDED;
            if(navigate)a.swipePage(next);
        }
        return handled;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        // Intercepting sends ACTION_CANCEL to the lyric row, preventing a swipe from also seeking.
        return swipeArea&&(swipeAxis==AXIS_HORIZONTAL||swipeCancelled)||super.onInterceptTouchEvent(event);
    }
    @Override public boolean onTouchEvent(MotionEvent event){return swipeArea||super.onTouchEvent(event);}
    private final class LyricArea extends LinearLayout{
        float startX,startY;int startIndex,previewIndex;boolean dragging;
        LyricArea(){super(a);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int height=MeasureSpec.getSize(heightSpec);
            if(height>0&&MeasureSpec.getMode(heightSpec)!=MeasureSpec.UNSPECIFIED){
                int count=Math.max(1,Math.min(lyricRows.length,height/Math.max(1,lyricHeight)));
                if(count!=lyricCount){lyricCount=count;for(int i=0;i<lyricRows.length;i++)lyricRows[i].setVisibility(i<count?VISIBLE:GONE);styleLyrics();showLyrics(shownLyricIndex);}
            }super.onMeasure(widthSpec,heightSpec);
        }
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){lyricTouch=true;dragging=false;gestureTrack=bridge.trackRevision;startX=event.getX();startY=event.getY();startIndex=bridge.lyrics.indexOf(lyricRows[activeLyricRow()].getTag());previewIndex=Math.max(0,startIndex);}
            boolean handled=super.dispatchTouchEvent(event);
            if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){lyricTouch=false;dragging=false;previewPosition=-1;update();}
            return handled;
        }
        @Override public boolean onInterceptTouchEvent(MotionEvent event){
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE&&swipeAxis==AXIS_VERTICAL&&bridge.canSeek()&&!bridge.lyrics.isEmpty()&&gestureTrack==bridge.trackRevision){
                float dy=Math.abs(event.getY()-startY);if(dy>ViewConfiguration.get(a).getScaledTouchSlop()&&dy>Math.abs(event.getX()-startX)){dragging=true;return true;}
            }return false;
        }
        @Override public boolean onTouchEvent(MotionEvent event){
            if(!bridge.canSeek()||bridge.lyrics.isEmpty()||gestureTrack!=bridge.trackRevision)return true;
            if(dragging&&(event.getActionMasked()==MotionEvent.ACTION_MOVE||event.getActionMasked()==MotionEvent.ACTION_UP)){
                previewIndex=Math.max(0,Math.min(bridge.lyrics.size()-1,startIndex+Math.round((startY-event.getY())/Math.max(1,getHeight()/lyricCount))));
                previewPosition=bridge.lyrics.get(previewIndex).time;showLyrics(previewIndex);update();
                if(event.getActionMasked()==MotionEvent.ACTION_UP)requestSeek(previewPosition,gestureTrack);
            }return true;
        }
    }
    private String stamp(long ms){return String.format(Locale.US,"%02d:%02d",ms/60000,ms/1000%60);}
}
