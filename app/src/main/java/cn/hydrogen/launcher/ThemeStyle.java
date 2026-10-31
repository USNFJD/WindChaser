package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Idempotent styling for launcher-owned views. Remote application content is excluded. */
final class ThemeStyle {
    private static Config cached;private static Context app;
    static Config config(Context c){Context next=c.getApplicationContext();if(cached==null||app!=next){app=next;cached=new Config(next);}return cached;}
    static int color(Config c,String key,int fallback){String value=c.s(key,"");return ThemeColors.valid(value)?Color.parseColor(value):fallback;}
    static float scale(Config c,String role){return c.n("textScale",100)/100f*c.n(role+"Size",100)/100f;}
    static <T extends View>T role(T v,String name){v.setTag(R.id.theme_role,name);return v;}
    static <T extends View>T owned(T v){v.setTag(R.id.theme_owned,Boolean.TRUE);return v;}
    static final class Base {final float px;final int color;Base(TextView t){px=t.getTextSize();color=t.getCurrentTextColor();}}
    static void apply(View v,String scope){apply(v,scope,config(v.getContext()));}
    private static void apply(View v,String scope,Config cfg){
        if(v instanceof android.appwidget.AppWidgetHostView)return;
        if(v.getBackground() instanceof Panel)((Panel)v.getBackground()).refreshRadius(cfg);
        Object tagged=v.getTag(R.id.theme_role);if(tagged instanceof String)scope=(String)tagged;
        if(v instanceof MusicCard)scope="musicText";else if(v instanceof WeatherPanel||v instanceof WeatherChip)scope="weather";
        if(v instanceof TextView&&!Boolean.TRUE.equals(v.getTag(R.id.theme_owned))){
            TextView t=(TextView)v;Base base=(Base)t.getTag(R.id.theme_base);if(base==null){base=new Base(t);t.setTag(R.id.theme_base,base);}
            float size=base.px*scale(cfg,scope);int fallback=color(cfg,"themeText",Ui.WHITE);
            // Preserve the hierarchy between primary and secondary labels.
            int r=Color.red(base.color),g=Color.green(base.color),b=Color.blue(base.color);
            if(Math.min(r,Math.min(g,b))<195)fallback=color(cfg,"themeMuted",Ui.MUTED);
            if(base.color==color(cfg,"themeText",Ui.WHITE)||base.color==Ui.WHITE)fallback=color(cfg,"themeText",Ui.WHITE);
            if(base.color==color(cfg,"themeAccent",0xffa7e2d2))fallback=color(cfg,"themeAccent",0xffa7e2d2);
            if(scope.equals("temperature"))fallback=color(cfg,"themeWeather",fallback);if(scope.equals("clock"))fallback=color(cfg,"themeClock",fallback);if(scope.equals("weather"))fallback=color(cfg,"themeWeather",color(cfg,"themeAccent",0xffa7e2d2));int ink=color(cfg,scope+"Color",fallback);
            if(Math.abs(t.getTextSize()-size)>.1f)t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);
            if(t.getCurrentTextColor()!=ink)t.setTextColor(ink);
            if(t instanceof EditText)((EditText)t).setHintTextColor(color(cfg,"themeMuted",Ui.MUTED));
        }
        if(v instanceof SeekBar){SeekBar bar=(SeekBar)v;int tint=color(cfg,"themeAccent",0xffa7e2d2);if(bar.getProgressDrawable()!=null)bar.getProgressDrawable().mutate().setColorFilter(tint,android.graphics.PorterDuff.Mode.SRC_IN);if(bar.getThumb()!=null)bar.getThumb().mutate().setColorFilter(tint,android.graphics.PorterDuff.Mode.SRC_IN);}
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i),scope,cfg);}
    }
    static int border(Config cfg,String area){return (cfg.n("borderOpacity",22)*255/100<<24)|(color(cfg,area+"Border",color(cfg,"themeBorder",0xff6e9bb4))&0xffffff);}
    static GradientDrawable panel(Context c,String area,int original,float radius,int stroke,int line){
        Config cfg=config(c);int surface=color(cfg,area+"Surface",color(cfg,"themeSurface",0xff101e2a));
        return new Panel(cfg,(original&0xff000000)|(surface&0xffffff),radius,stroke==0?0:Math.round(stroke*cfg.n("border",1)),border(cfg,area));
    }
    static GradientDrawable dialogPanel(Context c,float radius){
        Config cfg=config(c);int alpha=Math.max(30,Math.min(100,cfg.n("dialogAlpha",74)))*255/100;
        return panel(c,"dialog",(alpha<<24)|0x101e2a,radius,Ui.dp(c,1),Ui.GLASS_LINE);
    }
    static void dialogWindow(Dialog dialog,boolean custom){
        Window w=dialog.getWindow();if(w==null)return;Context c=dialog.getContext();
        WindowManager.LayoutParams p=w.getAttributes();p.dimAmount=Math.max(0,Math.min(40,config(c).n("dialogDim",16)))/100f;w.setAttributes(p);
        if(!custom){w.setBackgroundDrawable(dialogPanel(c,Ui.dp(c,18)));View root=w.getDecorView();
            for(String id:new String[]{"parentPanel","topPanel","contentPanel","customPanel","buttonPanel"})clearPanel(root,c,id);
        }
    }
    static void radiusScale(View v,float scale){radiusScale(v,scale,config(v.getContext()));}
    private static void radiusScale(View v,float scale,Config cfg){
        if(v.getBackground() instanceof Panel){Panel panel=(Panel)v.getBackground();panel.localScale=scale;panel.refreshRadius(cfg);}
        if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount();i++)radiusScale(group.getChildAt(i),scale,cfg);}
    }
    /** Baseline radii retain each surface's default proportions; the user setting scales them once. */
    private static final class Panel extends GradientDrawable {
        private final float baseline;private float applied=Float.NaN,localScale=1f;
        Panel(Config cfg,int color,float radius,int stroke,int line){baseline=radius;setColor(color);setStroke(stroke,line);refreshRadius(cfg);}
        void refreshRadius(Config cfg){float next=baseline*localScale*Math.max(0,Math.min(40,cfg.n("radius",20)))/20f;if(Float.compare(applied,next)!=0){applied=next;setCornerRadius(next);}}
    }
    static void activity(Activity a){View root=a.getWindow().getDecorView();String scope=a instanceof SettingsActivity?"settingsText":a instanceof FileCenterActivity?"filesText":"dialogText";apply(root,scope);}
    private static final java.util.Set<Dialog> watched=java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Dialog,Boolean>());
    static void watchDialog(Dialog dialog){
        if(dialog.getWindow()==null||!watched.add(dialog))return;MapWorkspace.get(dialog.getContext()).watch(dialog);View root=dialog.getWindow().getDecorView();
        boolean compact=dialog instanceof AlertDialog&&((AlertDialog)dialog).getListView()!=null;
        if(compact)compactMenu((AlertDialog)dialog);apply(root,"dialogText");
        final class Styler implements android.view.ViewTreeObserver.OnGlobalLayoutListener,View.OnAttachStateChangeListener {
            public void onGlobalLayout(){if(dialog.isShowing()){if(compact)compactRows(((AlertDialog)dialog).getListView());apply(root,"dialogText");}}
            public void onViewAttachedToWindow(View v){}
            public void onViewDetachedFromWindow(View v){if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnGlobalLayoutListener(this);root.removeOnAttachStateChangeListener(this);watched.remove(dialog);}
        }
        Styler styler=new Styler();root.getViewTreeObserver().addOnGlobalLayoutListener(styler);root.addOnAttachStateChangeListener(styler);
    }
    private static int menuRowHeight(Context c){android.util.DisplayMetrics dm=c.getResources().getDisplayMetrics();return Math.max(Math.round(44*dm.density),Math.round(16*dm.scaledDensity*scale(config(c),"dialogText")+14*dm.density));}
    /** Scale the previous effective width once, including the space beside the map. */
    static int dialogWidth(Context owner,int baseline,float fraction){
        // WRAP_CONTENT/MATCH_PARENT are layout sentinels, never a pixel width.
        if(baseline<=0){android.util.DisplayMetrics dm=owner.getResources().getDisplayMetrics();
            baseline=Math.max(1,Math.min(dm.widthPixels-Ui.dp(owner,24),Math.max(Ui.dp(owner,280),Math.round(dm.widthPixels*.5f))));}
        if(owner instanceof MainActivity){MainActivity activity=(MainActivity)owner;
            if(activity.mapDialogEligible()){android.graphics.Rect area=activity.mapDialogArea();if(area!=null&&area.width()>=48&&area.height()>=48)baseline=Math.min(baseline,area.width());}}
        return Math.max(1,Math.round(baseline*fraction));
    }
    /** Set stable dimensions without inflating decor before AlertDialog.onCreate(). */
    static void prepareMenu(AlertDialog dialog,int count){
        Window window=dialog.getWindow();if(window==null)return;Context c=dialog.getContext();android.util.DisplayMetrics dm=c.getResources().getDisplayMetrics();float dp=dm.density;
        int width=Math.max(1,Math.min(dm.widthPixels-Math.round(24*dp),Math.max(Math.round(280*dp),Math.round(dm.widthPixels*.5f))));
        int cap=Math.max(1,Math.min(dm.heightPixels-Math.round(24*dp),Math.round(dm.heightPixels*.62f)));
        int height=(int)Math.min(cap,Math.round(112*dp)+(long)Math.max(1,count)*menuRowHeight(c));
        WindowManager.LayoutParams params=window.getAttributes();params.width=width;params.height=height;params.dimAmount=Math.max(0,Math.min(40,config(c).n("dialogDim",16)))/100f;window.setAttributes(params);
    }
    /** A fixed window contains a scrolling list; only content scrolls or changes size. */
    private static void compactMenu(AlertDialog dialog){
        Window window=dialog.getWindow();Context c=dialog.getContext();android.util.DisplayMetrics dm=c.getResources().getDisplayMetrics();float dp=dm.density;
        ListView list=dialog.getListView();if(window.getAttributes().height<=0)prepareMenu(dialog,list.getAdapter()==null?1:list.getAdapter().getCount());
        window.setBackgroundDrawable(dialogPanel(c,18*dp));
        View root=window.getDecorView();clearPanel(root,c,"parentPanel");clearPanel(root,c,"topPanel");clearPanel(root,c,"contentPanel");clearPanel(root,c,"customPanel");clearPanel(root,c,"buttonPanel");
        int titleId=c.getResources().getIdentifier("alertTitle","id","android");View title=root.findViewById(titleId);if(title instanceof TextView){TextView t=(TextView)title;t.setTextSize(19);t.setMaxLines(2);t.setEllipsize(android.text.TextUtils.TruncateAt.END);t.setMinHeight(0);}
        for(int id:new int[]{AlertDialog.BUTTON_POSITIVE,AlertDialog.BUTTON_NEGATIVE,AlertDialog.BUTTON_NEUTRAL}){Button button=dialog.getButton(id);if(button!=null){button.setMinimumHeight(Math.round(40*dp));button.setMinHeight(Math.round(40*dp));button.setTextSize(15);}}
        list.setDivider(new android.graphics.drawable.ColorDrawable(0x246e9bb4));list.setDividerHeight(Math.max(1,Math.round(dp)));list.setPadding(0,0,0,0);
        int contentId=c.getResources().getIdentifier("contentPanel","id","android");View content=root.findViewById(contentId);
        if(content!=null&&content.getLayoutParams() instanceof LinearLayout.LayoutParams){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)content.getLayoutParams();lp.height=0;lp.weight=1;content.setLayoutParams(lp);}
        ViewGroup.LayoutParams lp=list.getLayoutParams();lp.height=ViewGroup.LayoutParams.MATCH_PARENT;list.setLayoutParams(lp);compactRows(list);
    }
    private static void clearPanel(View root,Context c,String name){int id=c.getResources().getIdentifier(name,"id","android");View panel=root.findViewById(id);if(panel!=null){panel.setBackgroundColor(android.graphics.Color.TRANSPARENT);panel.setMinimumWidth(0);}}
    private static void compactRows(ListView list){
        float dp=list.getResources().getDisplayMetrics().density;
        for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);int height=menuRowHeight(list.getContext());if(row.getMinimumHeight()!=height)row.setMinimumHeight(height);View text=row.findViewById(android.R.id.text1);
            if(text instanceof TextView){TextView t=(TextView)text;if(t.getTag(R.id.theme_base)==null){t.setTextSize(16);t.setTag(R.id.theme_base,new Base(t));}if(t.getMaxLines()!=1)t.setSingleLine(true);t.setEllipsize(android.text.TextUtils.TruncateAt.END);if(t.getGravity()!=Gravity.CENTER_VERTICAL)t.setGravity(Gravity.CENTER_VERTICAL);int padding=Math.round(18*dp);if(t.getPaddingLeft()!=padding||t.getPaddingRight()!=padding||t.getPaddingTop()!=0||t.getPaddingBottom()!=0)t.setPadding(padding,0,padding,0);if(t.getMinHeight()!=height)t.setMinHeight(height);}
        }
    }
}
