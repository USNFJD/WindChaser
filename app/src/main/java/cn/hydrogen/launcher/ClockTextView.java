package cn.hydrogen.launcher;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
/** Fits the requested clock font to its measured slot, including narrow screens. */
final class ClockTextView extends TextView {
    final MainActivity activity;final int base;final Config cfg;
    ClockTextView(MainActivity a,int size){super(a);activity=a;base=size;cfg=ThemeStyle.config(a);setSingleLine();setTextColor(new ThemeColors(cfg).clock);ThemeStyle.owned(this);}
    @Override protected void onMeasure(int ws,int hs){
        float font=activity.u(base)*ThemeStyle.scale(cfg,"clock");Paint p=new Paint(getPaint());p.setTextSize(font);
        int w=View.MeasureSpec.getSize(ws)-getPaddingLeft()-getPaddingRight(),h=View.MeasureSpec.getSize(hs)-getPaddingTop()-getPaddingBottom();
        float fit=1;float width=p.measureText(getText().length()==0?"00:00":getText().toString());if(w>0&&width>w)fit=Math.min(fit,w/width);if(h>0)fit=Math.min(fit,h/(font*1.16f));
        setTextSize(TypedValue.COMPLEX_UNIT_PX,font*Math.max(.2f,fit));super.onMeasure(ws,hs);
    }
}
