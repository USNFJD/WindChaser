package cn.hydrogen.launcher;

import android.graphics.*;
import android.view.*;
import android.widget.*;

/** A single centered clock/date/weather composition for resizable quick tiles. */
final class ClockWeatherTile extends LinearLayout {
    final TextView time,date;final WeatherChip weather;
    private final MainActivity a;private final Config cfg;
    ClockWeatherTile(MainActivity a,Config cfg,WeatherController controller){
        super(a);this.a=a;this.cfg=cfg;ThemeColors colors=new ThemeColors(cfg);setOrientation(VERTICAL);setGravity(Gravity.CENTER);
        setContentDescription("快捷区时间日期，横跨两列");
        setBackground(ThemeStyle.panel(a,"quick",colors.panel(95),a.cardRadius(20),a.u(1),Ui.GLASS_LINE));
        time=ThemeStyle.owned(Ui.text(a,"",a.sp(54),colors.clock));time.setGravity(Gravity.CENTER);time.setIncludeFontPadding(false);time.setSingleLine();time.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));time.setContentDescription("快捷区时间");addView(time,new LayoutParams(-1,a.u(66)));
        date=ThemeStyle.owned(Ui.text(a,"",a.sp(17.5f),ThemeStyle.color(cfg,"dateColor",colors.text)));date.setGravity(Gravity.CENTER);date.setSingleLine();date.setEllipsize(android.text.TextUtils.TruncateAt.END);date.setIncludeFontPadding(false);date.setContentDescription("快捷区日期");addView(date,new LayoutParams(-1,a.u(26)));
        weather=new WeatherChip(a,controller,true,"快捷区天气",true);LayoutParams wp=new LayoutParams(-1,a.u(48));wp.topMargin=a.u(8);addView(weather,wp);
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int width=MeasureSpec.getSize(widthSpec),height=MeasureSpec.getSize(heightSpec);
        int inset=Math.min(a.u(12),Math.max(0,width/16)),vInset=Math.min(a.u(12),Math.max(0,height/18));setPadding(inset,vInset,inset,vInset);
        int available=Math.max(1,width-2*inset),vertical=Math.max(1,height-2*vInset);
        float tf=a.u(54*ThemeStyle.scale(cfg,"clock")),df=a.u(17.5f*ThemeStyle.scale(cfg,"date"));
        Paint metrics=new Paint(time.getPaint());metrics.setTextSize(tf);float tw=metrics.measureText(time.length()==0?"00:00":time.getText().toString());if(tw>available)tf*=available/tw;
        metrics.setTextSize(df);float dw=metrics.measureText(date.length()==0?"10月27日 星期二":date.getText().toString());if(dw>available)df*=available/dw;
        int th=Math.round(tf*1.22f),dh=Math.round(df*1.45f),wh=a.u(48*Math.max(1,ThemeStyle.scale(cfg,"weather"))),gap=a.u(8);
        float fit=Math.min(1,vertical/(float)Math.max(1,th+dh+wh+gap));
        time.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,tf*fit);date.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,df*fit);
        time.getLayoutParams().height=Math.max(1,Math.round(th*fit));date.getLayoutParams().height=Math.max(1,Math.round(dh*fit));
        LayoutParams wp=(LayoutParams)weather.getLayoutParams();wp.height=Math.max(1,Math.round(wh*fit));wp.topMargin=Math.round(gap*fit);
        super.onMeasure(widthSpec,heightSpec);
    }
}
