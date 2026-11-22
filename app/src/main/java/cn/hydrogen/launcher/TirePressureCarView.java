package cn.hydrogen.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/** A static top-down car, drawn at the actual view size without a bitmap or animator. */
final class TirePressureCarView extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path=new Path();
    private final RectF rect=new RectF();
    private final boolean[] current=new boolean[4];
    private final int accent;
    private final LinearGradient body=new LinearGradient(25,0,87,0,
            new int[]{0xff8199ae,0xffdfebf3,0xffadbfce,0xffe4edf3,0xff758b9f},
            new float[]{0,.23f,.53f,.8f,1},Shader.TileMode.CLAMP);
    private final LinearGradient glass=new LinearGradient(0,72,0,170,
            0xff426074,0xff101f30,Shader.TileMode.CLAMP);
    private final DashPathEffect dashes=new DashPathEffect(new float[]{3,3},0);

    public TirePressureCarView(Context context){this(context,ThemeStyle.color(ThemeStyle.config(context),"themeAccent",0xffa7e2d2));}
    TirePressureCarView(Context context,int color){
        super(context);accent=color;setContentDescription("汽车俯视图，车头朝上；左前、右前、左后、右后");
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    void readings(TirePressureData.Snapshot data,long now){
        boolean changed=false;
        for(int n=0;n<4;n++){boolean fresh=data.fresh(n,now)&&data.wheels[n].bar!=null;
            if(current[n]!=fresh){current[n]=fresh;changed=true;}}
        if(changed)invalidate();
    }
    private void fill(int color){paint.setShader(null);paint.setColor(color);paint.setStyle(Paint.Style.FILL);paint.setPathEffect(null);}
    private void stroke(int color,float width){fill(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);}
    private void rounded(Canvas c,float l,float t,float r,float b,float radius){rect.set(l,t,r,b);c.drawRoundRect(rect,radius,radius,paint);}

    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float scale=Math.min(getWidth()/112f,getHeight()/212f);if(scale<=0)return;
        c.save();c.translate((getWidth()-112*scale)/2,(getHeight()-212*scale)/2);c.scale(scale,scale);
        // Direction is readable independently of the four data labels.
        fill(0xffbfd3df);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(10);c.drawText("车头",56,12,paint);
        stroke(accent,1.6f);c.drawLine(56,18,56,24,paint);c.drawLine(52,21,56,17,paint);c.drawLine(60,21,56,17,paint);
        for(int n=0;n<4;n++){
            boolean left=n%2==0;float y=n<2?65:165;
            stroke(current[n]?accent:0x667d9aad,1);paint.setPathEffect(dashes);
            c.drawLine(left?2:97,y,left?15:110,y,paint);paint.setPathEffect(null);
            fill(0xff0b1723);rounded(c,left?15:85,y-16,left?27:97,y+16,4);
            stroke(current[n]?accent:0xff7892a5,1.4f);rounded(c,left?15:85,y-16,left?27:97,y+16,4);
            stroke(0xff344b5b,1);for(int line=0;line<4;line++)c.drawLine(left?18:88,y-10+line*7,left?24:94,y-10+line*7,paint);
        }
        fill(0x28101a24);rounded(c,25,32,89,203,21);
        path.reset();path.moveTo(39,30);path.cubicTo(31,33,26,42,26,56);
        path.lineTo(25,170);path.cubicTo(25,191,33,199,43,200);
        path.lineTo(69,200);path.cubicTo(79,199,87,191,87,170);
        path.lineTo(86,56);path.cubicTo(86,42,81,33,73,30);path.close();
        fill(0xffc5d5df);paint.setShader(body);c.drawPath(path,paint);
        stroke(0xffdbe8ee,1.2f);c.drawPath(path,paint);
        // Bonnet, windshield, roof and rear window distinguish front from rear.
        stroke(0xff6f879b,1);c.drawLine(38,42,34,68,paint);c.drawLine(74,42,78,68,paint);
        fill(0xff1a2f41);paint.setShader(glass);path.reset();path.moveTo(34,75);path.quadTo(56,65,78,75);
        path.lineTo(73,96);path.quadTo(56,91,39,96);path.close();c.drawPath(path,paint);
        fill(0xff9eb4c6);rounded(c,38,99,74,143,9);stroke(0xffe0e9ee,1);rounded(c,39,100,73,142,8);
        fill(0xff152a3a);paint.setShader(glass);path.reset();path.moveTo(39,148);path.quadTo(56,153,73,148);
        path.lineTo(78,171);path.quadTo(56,178,34,171);path.close();c.drawPath(path,paint);
        stroke(0xff6c869a,1);c.drawLine(30,97,29,155,paint);c.drawLine(82,97,83,155,paint);
        c.drawLine(34,182,78,182,paint);c.drawLine(30,111,34,111,paint);c.drawLine(78,111,82,111,paint);
        fill(0xffd8eff6);rounded(c,30,48,37,57,2);rounded(c,75,48,82,57,2);
        fill(0xffa86770);rounded(c,31,184,38,188,1);rounded(c,74,184,81,188,1);
        fill(0xff7791a5);rounded(c,19,86,28,92,2);rounded(c,84,86,93,92,2);
        paint.setShader(null);paint.setPathEffect(null);c.restore();
    }
}
