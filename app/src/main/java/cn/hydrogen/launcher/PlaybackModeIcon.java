package cn.hydrogen.launcher;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Drawn paths keep repeat/shuffle/order symbols legible on KitKat's older fonts. */
final class PlaybackModeIcon extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();private int mode,color=0xffd4eee7;
    void setColor(int value){color=value;invalidateSelf();}
    void setMode(int value){if(mode!=value){mode=value;invalidateSelf();}}
    @Override public int getIntrinsicWidth(){return 24;}
    @Override public int getIntrinsicHeight(){return 24;}
    private void line(Canvas canvas,float x1,float y1,float x2,float y2){canvas.drawLine(x1,y1,x2,y2,paint);}
    @Override public void draw(Canvas canvas){
        Rect bounds=getBounds();canvas.save();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/24f,bounds.height()/24f);
        paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);path.reset();
        if(mode==QqMusicBridge.SINGLE||mode==0){
            path.moveTo(11,3);path.lineTo(8,3);path.cubicTo(4,3,3,6,3,9);path.lineTo(3,16);path.cubicTo(3,20,5,22,9,22);path.lineTo(16,22);path.cubicTo(20,22,22,20,22,16);path.lineTo(22,9);path.quadTo(22,4,17,3);canvas.drawPath(path,paint);
            path.reset();path.moveTo(10,0);path.lineTo(15,3);path.lineTo(10,6);path.close();paint.setStyle(Paint.Style.FILL);canvas.drawPath(path,paint);
            if(mode==QqMusicBridge.SINGLE){
            paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(11);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText("1",12,15.8f,paint);
            }
        }else if(mode==QqMusicBridge.SHUFFLE){
            path.moveTo(3,5);path.lineTo(6,5);path.cubicTo(10,5,14,19,18,19);path.lineTo(21,19);path.moveTo(18,16);path.lineTo(21,19);path.lineTo(18,22);
            path.moveTo(3,19);path.lineTo(6,19);path.cubicTo(8,19,9,16,10,15);path.moveTo(14,9);path.cubicTo(16,5,17,5,18,5);path.lineTo(21,5);path.moveTo(18,2);path.lineTo(21,5);path.lineTo(18,8);canvas.drawPath(path,paint);
        }else{
            for(int y:new int[]{5,11,17}){line(canvas,3,y,4,y);line(canvas,8,y,mode==QqMusicBridge.ORDER&&y==17?12:20,y);}
            if(mode==QqMusicBridge.ORDER){line(canvas,16,17,22,17);line(canvas,19,14,22,17);line(canvas,19,20,22,17);}
        }canvas.restore();
    }
    @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
