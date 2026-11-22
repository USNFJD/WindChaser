package cn.hydrogen.launcher;

import android.content.Context;
import android.media.AudioManager;
import android.view.Gravity;
import android.widget.*;

/** Uses the real shared media stream; never represents an independent QQ volume. */
final class MediaVolume extends LinearLayout {
    private final AudioManager audio;
    private final SeekBar slider;
    private final TextView value;
    private boolean dragging;
    MediaVolume(MainActivity a) {
        super(a);setOrientation(VERTICAL);setTag("live-widget");
        audio=(AudioManager)a.getSystemService(Context.AUDIO_SERVICE);
        LinearLayout caption=new LinearLayout(a);caption.setGravity(Gravity.CENTER_VERTICAL);
        TextView label=Ui.text(a,"媒体音量",a.sp(14),Ui.MUTED);caption.addView(label,new LayoutParams(0,a.u(24),1));
        value=Ui.text(a,"",a.sp(14),Ui.MUTED);caption.addView(value,new LayoutParams(-2,a.u(24)));addView(caption);
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView down=Ui.text(a,"−",a.sp(28),Ui.WHITE);down.setGravity(Gravity.CENTER);down.setContentDescription("简洁页音量减");down.setOnClickListener(v->adjust(-1));row.addView(down,new LayoutParams(a.u(52),a.u(52)));
        slider=new SeekBar(a);if(slider.getThumb()!=null)slider.setThumb(new VolumeThumb(slider.getThumb()));slider.setMax(audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));slider.setContentDescription("媒体音量滑块");
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onStartTrackingTouch(SeekBar bar){dragging=true;}
            public void onStopTrackingTouch(SeekBar bar){dragging=false;refresh();}
            public void onProgressChanged(SeekBar bar,int n,boolean user){if(user)set(n);}
        });row.addView(slider,new LayoutParams(0,a.u(52),1));
        TextView up=Ui.text(a,"+",a.sp(26),Ui.WHITE);up.setGravity(Gravity.CENTER);up.setContentDescription("简洁页音量加");up.setOnClickListener(v->adjust(1));row.addView(up,new LayoutParams(a.u(52),a.u(52)));addView(row);refresh();
    }
    private void set(int n){try{audio.setStreamVolume(AudioManager.STREAM_MUSIC,n,0);value.setText(audio.getStreamVolume(AudioManager.STREAM_MUSIC)+" / "+slider.getMax());}catch(Exception e){Ui.toast(getContext(),"请使用原车音量控制");} }
    private void adjust(int n){set(Math.max(0,Math.min(slider.getMax(),audio.getStreamVolume(AudioManager.STREAM_MUSIC)+n)));refresh();}
    void refresh(){if(!dragging){int n=audio.getStreamVolume(AudioManager.STREAM_MUSIC);if(slider.getProgress()!=n)slider.setProgress(n);String text=n+" / "+slider.getMax();if(!android.text.TextUtils.equals(value.getText(),text))value.setText(text);}}
    private final android.os.Handler sync=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable poll=new Runnable(){public void run(){if(isShown())refresh();sync.postDelayed(this,1000);}};
    private final android.content.BroadcastReceiver volumeChanged=new android.content.BroadcastReceiver(){public void onReceive(android.content.Context c,android.content.Intent i){refresh();}};
    private final android.database.ContentObserver volumeObserver=new android.database.ContentObserver(sync){@Override public void onChange(boolean self){refresh();}};
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();refresh();getContext().registerReceiver(volumeChanged,new android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"));getContext().getContentResolver().registerContentObserver(android.provider.Settings.System.CONTENT_URI,true,volumeObserver);sync.post(poll);}
    @Override protected void onDetachedFromWindow(){sync.removeCallbacksAndMessages(null);try{getContext().unregisterReceiver(volumeChanged);}catch(IllegalArgumentException ignored){}getContext().getContentResolver().unregisterContentObserver(volumeObserver);super.onDetachedFromWindow();}
    /** Scale the ROM's own thumb, including its pressed halo, while keeping the full touch area. */
    static final class VolumeThumb extends android.graphics.drawable.Drawable implements android.graphics.drawable.Drawable.Callback {
        private final android.graphics.drawable.Drawable source;
        VolumeThumb(android.graphics.drawable.Drawable source){this.source=source;source.setCallback(this);}
        @Override public int getIntrinsicWidth(){return Math.max(1,Math.round(source.getIntrinsicWidth()*.75f));}
        @Override public int getIntrinsicHeight(){return Math.max(1,Math.round(source.getIntrinsicHeight()*.75f));}
        @Override protected void onBoundsChange(android.graphics.Rect bounds){source.setBounds(bounds);}
        @Override public void draw(android.graphics.Canvas canvas){source.draw(canvas);}
        @Override public void setAlpha(int alpha){source.setAlpha(alpha);}
        @Override public void setColorFilter(android.graphics.ColorFilter filter){source.setColorFilter(filter);}
        @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        @Override public boolean isStateful(){return source.isStateful();}
        @Override protected boolean onStateChange(int[] state){return source.setState(state);}
        @Override protected boolean onLevelChange(int level){return source.setLevel(level);}
        @Override public void invalidateDrawable(android.graphics.drawable.Drawable who){invalidateSelf();}
        @Override public void scheduleDrawable(android.graphics.drawable.Drawable who,Runnable what,long when){scheduleSelf(what,when);}
        @Override public void unscheduleDrawable(android.graphics.drawable.Drawable who,Runnable what){unscheduleSelf(what);}
    }
}
