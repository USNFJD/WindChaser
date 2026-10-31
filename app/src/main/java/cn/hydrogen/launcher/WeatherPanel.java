package cn.hydrogen.launcher;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Borderless typography and a small vector weather symbol; no placeholder readings. */
final class WeatherPanel extends LinearLayout {
    private final MainActivity a;private final WeatherController weather;
    private final TextView city,temperature,condition,range,details,source;private final Symbol symbol;
    WeatherPanel(MainActivity a,WeatherController weather){
        super(a);this.a=a;this.weather=weather;setOrientation(VERTICAL);setContentDescription("首页天气");
        city=text("",18,0xffb2c8da);addView(city,new LayoutParams(-1,a.u(34)));
        LinearLayout sky=new LinearLayout(a);sky.setGravity(Gravity.CENTER_VERTICAL);symbol=new Symbol(a);sky.addView(symbol,new LayoutParams(a.u(66),a.u(66)));
        temperature=ThemeStyle.role(text("--°",52,Ui.WHITE),"temperature");sky.addView(temperature,new LayoutParams(-2,a.u(76)));
        condition=text("",19,Ui.WHITE);condition.setPadding(a.u(18),0,0,0);condition.setGravity(Gravity.CENTER_VERTICAL);sky.addView(condition,new LayoutParams(0,a.u(66),1));addView(sky);
        range=text("",17,0xffcedce7);addView(range,new LayoutParams(-1,a.u(34)));
        details=text("",14,Ui.MUTED);addView(details,new LayoutParams(-1,a.u(30)));
        source=text("",12,0xff94adbf);source.setMaxLines(2);source.setGravity(Gravity.CENTER_VERTICAL);addView(source,new LayoutParams(-1,a.u(42)));
        setOnClickListener(v->new ThemeDialog.Builder(a).setTitle("天气").setItems(new String[]{"刷新天气","城市与自动定位设置"},(d,n)->{if(n==0)weather.refresh(true);else a.startActivity(new Intent(a,WeatherSettingsActivity.class));}).setNegativeButton("取消",null).show());update();
    }
    private TextView text(String s,int size,int color){TextView t=Ui.text(a,s,a.sp(size),color);t.setShadowLayer(a.u(4),0,a.u(1),0xff06111b);return t;}
    void update(){
        city.setText(weather.city.homeLabel(new Config(a).n("weatherPlaceLevel",1))+"  ›");city.setSingleLine();city.setEllipsize(android.text.TextUtils.TruncateAt.END);WeatherController.Reading r=weather.reading;
        if(r==null){temperature.setText("--°");condition.setText(weather.loading?"更新中":"待更新");range.setText("联网后显示天气");details.setText("点击设置城市或刷新");symbol.code=-1;}
        else{temperature.setText(Math.round(r.temperature)+"°");condition.setText(WeatherController.description(r.code));range.setText("最高 "+Math.round(r.high)+"°    最低 "+Math.round(r.low)+"°");details.setText(String.format(Locale.CHINA,"湿度 %d%%    风速 %.0f km/h",r.humidity,r.wind));symbol.code=r.code;symbol.day=r.day;}
        symbol.invalidate();String stamp=r==null?"":r.observed.replace('T',' ');
        boolean stale=r!=null&&(System.currentTimeMillis()-weather.fetchedAt>=WeatherController.FRESH);
        String detail=weather.notice.isEmpty()?(weather.loading?"正在更新":stale?"缓存 · 等待更新":""):weather.notice;
        source.setText((r==null?"Open-Meteo 天气":stamp+" · Open-Meteo")+(detail.isEmpty()?"":"\n"+detail));
    }
    static final class Symbol extends View{
        final Paint p=new Paint(3);int code=-1;boolean day=true;Symbol(android.content.Context c){super(c);}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();canvas.scale(getWidth()/66f,getHeight()/66f);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.2f);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(ThemeStyle.color(ThemeStyle.config(getContext()),"weatherIconColor",0xffaee4ef));
            if(code<0){canvas.drawCircle(33,33,17,p);canvas.drawLine(25,33,41,33,p);canvas.restore();return;}
            if(code<=2){canvas.drawCircle(27,26,11,p);if(day){for(int i=0;i<8;i++){double angle=i*Math.PI/4;canvas.drawLine(27+(float)Math.cos(angle)*16,26+(float)Math.sin(angle)*16,27+(float)Math.cos(angle)*20,26+(float)Math.sin(angle)*20,p);}}else{p.setStyle(Paint.Style.FILL);p.setColor(0xff7ca4bf);canvas.drawCircle(31,22,10,p);p.setStyle(Paint.Style.STROKE);p.setColor(ThemeStyle.color(ThemeStyle.config(getContext()),"weatherIconColor",0xffaee4ef));}}
            if(code>=2){p.setStyle(Paint.Style.FILL);p.setColor(ThemeStyle.color(ThemeStyle.config(getContext()),"weatherIconColor",0xffa8c5d7));canvas.drawCircle(27,33,12,p);canvas.drawCircle(42,34,10,p);canvas.drawRoundRect(new RectF(16,33,52,45),6,6,p);}
            if(code>=51){p.setStyle(Paint.Style.STROKE);p.setColor(ThemeStyle.color(ThemeStyle.config(getContext()),"weatherIconColor",0xff79b7ed));for(int x=22;x<50;x+=11)canvas.drawLine(x,50,x-3,57,p);}canvas.restore();
        }
    }
}
