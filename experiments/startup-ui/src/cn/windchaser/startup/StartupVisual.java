package cn.windchaser.startup;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.view.View;
import java.io.*;

/** Same bounded bitmap and animation in host and guest Activity windows. */
public final class StartupVisual extends Drawable {
    private static Bitmap mark;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final RectF target=new RectF();private Shader gradient;private final float density;
    public StartupVisual(Context c){density=c.getResources().getDisplayMetrics().density;load(c);}
    private static synchronized void load(Context c){
        if(mark!=null)return;
        try(InputStream in=c.getAssets().open("windchaser-startup-mark.png")){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;mark=BitmapFactory.decodeStream(in,null,o);
        }catch(IOException unavailable){android.util.Log.w("WindChaserStartup","startup bitmap unavailable",unavailable);}
    }
    @Override protected void onBoundsChange(Rect b){
        gradient=new LinearGradient(0,b.top,0,Math.max(b.top+1,b.bottom),0xff030b17,0xff071628,Shader.TileMode.CLAMP);
        float width=Math.min(252*density,b.width()*.65f),height=width*107f/252;
        if(height>b.height()*.5f){height=b.height()*.5f;width=height*252/107;}
        target.set(b.exactCenterX()-width/2,b.exactCenterY()-height/2,b.exactCenterX()+width/2,b.exactCenterY()+height/2);
    }
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();paint.setAlpha(255);paint.setShader(gradient);canvas.drawRect(b,paint);paint.setShader(null);
        if(mark!=null)canvas.drawBitmap(mark,null,target,paint);
        float y=Math.min(b.bottom-20*density,target.bottom+24*density);long t=SystemClock.uptimeMillis()/350;
        for(int i=0;i<3;i++){paint.setColor(0xffa9cce1);paint.setAlpha(i==t%3?205:55);canvas.drawCircle(b.exactCenterX()+(i-1)*12*density,y,2*density,paint);}
        paint.setAlpha(255);
    }
    @Override public void setAlpha(int a){}@Override public void setColorFilter(ColorFilter f){}@Override public int getOpacity(){return PixelFormat.OPAQUE;}
    public static View view(Context c){return new Screen(c);}
    private static final class Screen extends View {
        private final StartupVisual artwork;
        Screen(Context c){super(c);artwork=new StartupVisual(c);setClickable(true);setContentDescription("追风桌面正在准备地图和音乐");}
        @Override protected void onSizeChanged(int w,int h,int ow,int oh){artwork.setBounds(0,0,w,h);}
        @Override protected void onDraw(Canvas canvas){artwork.draw(canvas);if(isShown()&&getWindowVisibility()==VISIBLE)postInvalidateDelayed(120);}
        @Override protected void onWindowVisibilityChanged(int v){super.onWindowVisibilityChanged(v);if(v==VISIBLE)invalidate();}
    }
}
