package cn.hydrogen.launcher;

import android.content.Intent;
import android.view.*;
import android.widget.*;

/** All app pages share the controller and the same two-level location label. */
final class WeatherChip extends LinearLayout {
    private final WeatherController weather;private final TextView location,summary;private final WeatherPanel.Symbol symbol;private final boolean city;private final MainActivity a;private final LinearLayout line;private final boolean stacked;
    WeatherChip(MainActivity a,WeatherController weather,boolean city,String description){
        this(a,weather,city,description,false);
    }
    WeatherChip(MainActivity a,WeatherController weather,boolean city,String description,boolean stacked){
        super(a);this.a=a;this.weather=weather;this.city=city;this.stacked=stacked;setGravity(Gravity.CENTER_VERTICAL);setBaselineAligned(false);setContentDescription(description);Config cfg=new Config(a);ThemeColors colors=new ThemeColors(cfg);
        line=city?new LinearLayout(a):this;
        if(stacked){setOrientation(VERTICAL);setGravity(Gravity.CENTER);line.setGravity(Gravity.CENTER);line.setBaselineAligned(false);}
        if(city&&!stacked){line.setGravity(Gravity.CENTER_VERTICAL);line.setBaselineAligned(false);line.setPadding(a.u(10),0,a.u(12),0);line.setBackground(ThemeStyle.panel(a,"quick",colors.panel(cfg.n("weatherPillAlpha",25)*255/100),a.cardRadius(18),Math.max(1,a.u(1)),0x20c5e6e8));addView(line,new LayoutParams(-1,-1));}
        location=Ui.text(a,"",a.sp(city?16.25f:18),colors.weather);location.setGravity(Gravity.CENTER_VERTICAL);location.setSingleLine();location.setEllipsize(android.text.TextUtils.TruncateAt.END);location.setIncludeFontPadding(false);location.setPadding(0,0,a.u(4),0);if(stacked){location.setGravity(Gravity.CENTER);location.setPadding(0,0,0,0);addView(location,new LayoutParams(-1,0,1));addView(line,new LayoutParams(-2,0,1));}else line.addView(location,new LayoutParams(-2,-1));
        symbol=new WeatherPanel.Symbol(a);line.addView(symbol,new LayoutParams(a.u(stacked?20:city?24:30),a.u(stacked?20:city?24:30)));
        summary=Ui.text(a,"",a.sp(city?16.25f:18),colors.weather);summary.setGravity(Gravity.CENTER_VERTICAL);summary.setSingleLine();summary.setEllipsize(android.text.TextUtils.TruncateAt.END);summary.setIncludeFontPadding(false);summary.setPadding(a.u(3),0,0,0);line.addView(summary,new LayoutParams(-2,-1));
        if(city){ThemeStyle.owned(summary);ThemeStyle.owned(location);}
        setOnClickListener(v->a.startActivity(new Intent(a,WeatherSettingsActivity.class)));setOnLongClickListener(v->{weather.refresh(true);return true;});update();
    }
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        if(stacked){
            int width=Math.max(1,MeasureSpec.getSize(widthSpec)),height=Math.max(1,MeasureSpec.getSize(heightSpec));
            float scale=ThemeStyle.scale(ThemeStyle.config(a),"weather");
            float size=Math.min(a.u((width<a.u(180)?13.5f:15)*scale),height*.31f);
            location.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);summary.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);
            location.setMaxWidth(width);summary.setMaxWidth(Math.max(1,width-a.u(26)));
            int icon=Math.min(a.u(20),Math.max(1,height/2));symbol.getLayoutParams().width=icon;symbol.getLayoutParams().height=icon;
            super.onMeasure(widthSpec,heightSpec);return;
        }
        int room=Math.max(1,MeasureSpec.getSize(widthSpec)-line.getPaddingLeft()-line.getPaddingRight()-a.u(city?24:30));
        if(city){float size=a.sp((room<a.u(190)?11:13)*1.25f*ThemeStyle.scale(ThemeStyle.config(a),"weather"));summary.setTextSize(size);location.setTextSize(size);}
        summary.setMaxWidth(Math.max(1,Math.round(room*.54f)));
        summary.measure(MeasureSpec.makeMeasureSpec(room,MeasureSpec.AT_MOST),heightSpec);
        location.setMaxWidth(Math.max(1,room-summary.getMeasuredWidth()-a.u(8)));super.onMeasure(widthSpec,heightSpec);
    }
    void update(){
        WeatherController.Reading r=weather.reading;location.setText(weather.city.shortLabel());
        long now=System.currentTimeMillis();
        boolean cached=r!=null&&(weather.fetchedAt<=0||now-weather.fetchedAt>=WeatherController.FRESH||weather.fetchedAt>now+60000);
        summary.setText(r==null?(weather.loading?"天气更新中":"天气待更新"):Math.round(r.temperature)+"°  "+WeatherController.description(r.code)+(cached?" · 缓存":""));
        setContentDescription(weather.city.shortLabel()+"，"+summary.getText());symbol.code=r==null?-1:r.code;symbol.day=r==null||r.day;symbol.invalidate();
    }
}
