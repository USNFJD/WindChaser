package cn.hydrogen.launcher;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Font-independent transport artwork, with a larger outlined central button. */
final class TransportIcon extends Drawable {
    static final int PREVIOUS=0,PLAY=1,NEXT=2,MENU=3,INFO=4;
    private final int kind;private boolean playing;private int alpha=255,color=0xffd4eee7;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Path path=new Path();
    TransportIcon(int kind){this.kind=kind;}
    void setColor(int value){color=value;invalidateSelf();}
    void setPlaying(boolean value){if(playing!=value){playing=value;invalidateSelf();}}
    @Override public int getIntrinsicWidth(){return 72;}
    @Override public int getIntrinsicHeight(){return 72;}
    @Override public void draw(Canvas canvas){
        Rect b=getBounds();canvas.save();canvas.translate(b.left,b.top);canvas.scale(b.width()/72f,b.height()/72f);
        paint.setColor(color);paint.setAlpha(alpha);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.2f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);path.reset();
        if(kind==PLAY){
            canvas.drawCircle(36,36,31,paint);paint.setStyle(Paint.Style.FILL);
            if(playing){canvas.drawRoundRect(new RectF(26,24,32,48),1.5f,1.5f,paint);canvas.drawRoundRect(new RectF(40,24,46,48),1.5f,1.5f,paint);}
            else{path.moveTo(29,23);path.quadTo(27,22,27,25);path.lineTo(27,47);path.quadTo(27,50,30,48);path.lineTo(48,38);path.quadTo(51,36,48,34);path.close();canvas.drawPath(path,paint);}
        }else if(kind==INFO){
            canvas.drawCircle(36,36,25,paint);paint.setStrokeWidth(3.4f);canvas.drawLine(36,33,36,49,paint);paint.setStyle(Paint.Style.FILL);canvas.drawCircle(36,23,2.1f,paint);
        }else if(kind==MENU){
            canvas.drawLine(13,17,58,17,paint);canvas.drawLine(13,35,58,35,paint);canvas.drawLine(13,53,41,53,paint);
            paint.setStyle(Paint.Style.FILL);path.moveTo(51,45);path.lineTo(63,53);path.lineTo(51,61);path.close();canvas.drawPath(path,paint);
        }else{
            if(kind==PREVIOUS){canvas.drawLine(18,16,18,56,paint);path.moveTo(51,19);path.quadTo(54,17,54,21);path.lineTo(54,51);path.quadTo(54,55,51,53);path.lineTo(25,38);path.quadTo(22,36,25,34);}
            else{canvas.drawLine(54,16,54,56,paint);path.moveTo(21,19);path.quadTo(18,17,18,21);path.lineTo(18,51);path.quadTo(18,55,21,53);path.lineTo(47,38);path.quadTo(50,36,47,34);}
            path.close();paint.setStyle(Paint.Style.FILL);canvas.drawPath(path,paint);
        }canvas.restore();
    }
    @Override public void setAlpha(int value){alpha=value;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
