package cn.hydrogen.launcher;

import android.content.Context;
import android.graphics.*;
import android.view.View;

final class Icons extends View {
    private final String kind; private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG); private final Path path=new Path();
    Icons(Context c,String k) { super(c); kind=k; setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas c) {
        c.save(); float s=Math.min(getWidth(),getHeight())/48f; c.translate((getWidth()-s*48)/2,(getHeight()-s*48)/2); c.scale(s,s);
        p.setColor(ThemeStyle.color(ThemeStyle.config(getContext()),"themeIcon",ThemeStyle.color(ThemeStyle.config(getContext()),"themeAccent",0xfff4f9ff))); p.setStrokeWidth(3); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setStyle(Paint.Style.FILL); path.reset();
        switch(kind) {
            case "home": path.moveTo(4,23); path.lineTo(24,6); path.lineTo(44,23); path.lineTo(37,23); path.lineTo(37,42); path.lineTo(28,42); path.lineTo(28,29); path.lineTo(20,29); path.lineTo(20,42); path.lineTo(11,42); path.lineTo(11,23); path.close(); c.drawPath(path,p); break;
            case "down": case "up": case "mute":
                c.drawRect(4,18,13,31,p); path.moveTo(12,18); path.lineTo(24,9); path.lineTo(24,40); path.lineTo(12,31); path.close(); c.drawPath(path,p);
                p.setStyle(Paint.Style.STROKE); if(kind.equals("mute")) { c.drawLine(32,19,41,29,p); c.drawLine(41,19,32,29,p); }
                else { c.drawLine(31,24,42,24,p); if(kind.equals("up")) c.drawLine(36.5f,18,36.5f,30,p); } break;
            case "apps": for(int i=0;i<2;i++) for(int j=0;j<2;j++) c.drawRoundRect(new RectF(6+i*21,6+j*21,21+i*21,21+j*21),2,2,p); break;
            case "settings":
                c.save(); c.translate(24,24); for(int i=0;i<8;i++){c.drawRect(-3,-22,3,-13,p);c.rotate(45);} c.restore(); c.drawCircle(24,24,16,p); p.setColor(ThemeStyle.color(ThemeStyle.config(getContext()),"themeSurface",0xff3c4a60)); c.drawCircle(24,24,7,p); break;
            case "close":
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.2f);c.drawLine(12,12,36,36,p);c.drawLine(36,12,12,36,p);break;
            case "tpms":
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3.2f);
                path.moveTo(12,8);path.cubicTo(7,14,5,25,8,34);path.cubicTo(11,44,37,44,40,34);path.cubicTo(43,25,41,14,36,8);c.drawPath(path,p);
                for(int x=10;x<=38;x+=7)c.drawLine(x,38,x,43,p);
                c.drawLine(24,13,24,27,p);p.setStyle(Paint.Style.FILL);c.drawCircle(24,34,2.1f,p);break;
            case "theme": path.moveTo(7,7);path.lineTo(17,4);path.cubicTo(19,14,29,14,31,4);path.lineTo(41,7);path.lineTo(46,19);path.lineTo(36,23);path.lineTo(36,43);path.lineTo(12,43);path.lineTo(12,23);path.lineTo(2,19);path.close();c.drawPath(path,p);break;
            case "phone": path.moveTo(12,4);path.lineTo(21,15);path.lineTo(15,22);path.cubicTo(18,28,22,32,28,34);path.lineTo(34,27);path.lineTo(45,36);path.lineTo(40,45);path.cubicTo(22,46,2,24,4,11);path.close();c.drawPath(path,p);break;
            case "map": path.moveTo(24,3);path.lineTo(42,43);path.lineTo(24,34);path.lineTo(6,43);path.close();c.drawPath(path,p);break;
            case "music": p.setStyle(Paint.Style.STROKE);c.drawLine(19,36,19,10,p);c.drawLine(19,10,39,5,p);c.drawLine(39,5,39,31,p);p.setStyle(Paint.Style.FILL);c.drawOval(new RectF(6,30,20,40),p);c.drawOval(new RectF(26,25,40,35),p);break;
            default: p.setStyle(Paint.Style.STROKE);c.drawLine(10,24,38,24,p);c.drawLine(24,10,24,38,p);
        }
        c.restore();
    }
}
