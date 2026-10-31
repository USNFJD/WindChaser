package cn.windchaser.startup;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;

/** A measured, independent home target; the original transport actions are retained. */
public final class QqHome {
    private static final String TAG="windchaser-home",PENDING="windchaser-home-pending",PLAYER="windchaser-player-home";
    private static volatile boolean initialized;
    static boolean contentReady(){return initialized;}
    private static void field(Object p,String name,int value)throws Exception{
        java.lang.reflect.Field f;
        try{f=p.getClass().getField(name);}catch(NoSuchFieldException obfuscated){
            // Verified against the paired 2.1.1.1 ConstraintLayout DEX. Refuse
            // unknown layouts instead of applying a guessed constraint field.
            if(!p.getClass().getName().equals("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams"))throw obfuscated;
            String alias="startToStart".equals(name)?"t":"startToEnd".equals(name)?"s":"topToTop".equals(name)?"i":"bottomToBottom".equals(name)?"l":"endToStart".equals(name)?"u":"endToEnd".equals(name)?"v":null;
            if(alias==null)throw obfuscated;f=p.getClass().getField(alias);
        }
        if(f.getType()!=int.class)throw new IllegalStateException("unexpected layout constraint "+name);f.setInt(p,value);
    }
    /** The native overlap heuristic applies only to its original fixed layout. */
    public static boolean ownsHomeLayout(View host){
        View home=host==null?null:host.findViewWithTag(TAG);
        return home instanceof Home&&home.getParent()==host;
    }
    public static void install(View host){
        if(!(host instanceof ViewGroup)||host.findViewWithTag(TAG)!=null||host.findViewWithTag(PENDING)!=null)return;
        // The constructor runs before measurement. A layout callback also covers
        // views inflated off-window; no static Activity/View reference is retained.
        View marker=new View(host.getContext());marker.setTag(PENDING);marker.setVisibility(View.GONE);
        ((ViewGroup)host).addView(marker,new ViewGroup.LayoutParams(0,0));
        Installer waiter=new Installer((ViewGroup)host,marker);host.addOnLayoutChangeListener(waiter);host.addOnAttachStateChangeListener(waiter);
        waiter.tryInstall();
    }
    private static final class Installer implements View.OnLayoutChangeListener,View.OnAttachStateChangeListener {
        final ViewGroup group;final View marker;boolean finished;
        Installer(ViewGroup g,View m){group=g;marker=m;}
        public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){tryInstall();}
        public void onViewAttachedToWindow(View v){group.addOnLayoutChangeListener(this);}
        public void onViewDetachedFromWindow(View v){group.removeOnLayoutChangeListener(this);}
        private void end(){finished=true;group.removeOnLayoutChangeListener(this);group.removeOnAttachStateChangeListener(this);group.removeView(marker);}
        void tryInstall(){
            if(finished||group.getWidth()<=0||group.getHeight()<=0)return;
            try{
                Context c=group.getContext();
                String[] names={"play_bar_album_img","ll_song_info","play_bar_prev","fl_play","play_bar_next","play_bar_colloct","play_bar_lyric","play_bar_playlist","play_bar_play_mode","play_bar_sound","play_bar_time"};
                View[] row=new View[names.length+1];
                for(int n=0;n<names.length;n++){
                    row[n+1]=group.findViewById(c.getResources().getIdentifier(names[n],"id",c.getPackageName()));
                    if(row[n+1]==null||row[n+1].getParent()!=group){end();return;}
                }
                View album=row[1],info=row[2];if(info.getWidth()<=0||album.getWidth()<=0)return;
                initialized=true;
                float d=c.getResources().getDisplayMetrics().density;
                if(group.getHeight()<Math.round(40*d)||group.getWidth()<Math.round(360*d)){end();return;}
                Home button=new Home(c);button.lightFooter=true;button.setId(View.generateViewId());button.setTag(TAG);button.setContentDescription("返回追风桌面");button.setFocusable(true);button.setClickable(true);button.setOnClickListener(v->returnHome(c));row[0]=button;
                Class<?> type=info.getLayoutParams().getClass();ViewGroup.LayoutParams[] prepared=new ViewGroup.LayoutParams[row.length];
                for(int n=0;n<row.length;n++){
                    Object params=n==0?type.getConstructor(int.class,int.class).newInstance(0,Math.round(52*d))
                        :type.getConstructor(type).newInstance(row[n].getLayoutParams());
                    field(params,"startToStart",n==0?0:-1);field(params,"startToEnd",n==0?-1:row[n-1].getId());
                    field(params,"endToStart",n==row.length-1?-1:row[n+1].getId());field(params,"endToEnd",n==row.length-1?0:-1);
                    if(n==0){field(params,"topToTop",0);field(params,"bottomToBottom",0);}
                    ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)params;
                    margins.leftMargin=margins.rightMargin=0;margins.setMarginStart(0);margins.setMarginEnd(0);
                    if(n>=3&&n<=9)margins.width=0;
                    prepared[n]=margins;
                }
                for(int n=1;n<row.length;n++)row[n].setLayoutParams(prepared[n]);group.addView(button,prepared[0]);
                // The existing play/loading FrameLayout stays intact. Let its
                // original play image follow the resized parent cell as well.
                View play=group.findViewById(c.getResources().getIdentifier("play_bar_play","id",c.getPackageName()));
                for(int n=3;n<=10;n++)if(n!=4){
                    if(n<=9)row[n].setPadding(0,row[n].getPaddingTop(),0,row[n].getPaddingBottom());
                    if(row[n] instanceof ImageView)((ImageView)row[n]).setScaleType(ImageView.ScaleType.FIT_CENTER);
                }
                if(play!=null&&play.getParent()==row[4]){ViewGroup.LayoutParams pp=play.getLayoutParams();pp.width=-1;pp.height=-1;play.setLayoutParams(pp);play.setPadding(0,play.getPaddingTop(),0,play.getPaddingBottom());if(play instanceof ImageView)((ImageView)play).setScaleType(ImageView.ScaleType.FIT_CENTER);}
                HomeRowSizing sizing=new HomeRowSizing(group,row,d);group.addOnLayoutChangeListener(sizing);group.addOnAttachStateChangeListener(sizing);sizing.resize();
                android.util.Log.i("WindChaserHome","home joined native responsive bottom group");end();
            }catch(Exception error){android.util.Log.w("WindChaserHome","home control unavailable",error);end();}
        }
    }
    /** Owned only by this native view tree; no static Activity/View references. */
    private static final class HomeRowSizing implements View.OnLayoutChangeListener,View.OnAttachStateChangeListener {
        final ViewGroup group;final View[] row;final float density;int width=-1,height=-1;
        HomeRowSizing(ViewGroup g,View[] views,float d){group=g;row=views;density=d;}
        public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){resize();}
        public void onViewAttachedToWindow(View v){group.addOnLayoutChangeListener(this);width=-1;resize();}
        public void onViewDetachedFromWindow(View v){group.removeOnLayoutChangeListener(this);}
        void resize(){
            int w=group.getWidth(),h=group.getHeight();if(w<=0||h<=0||(width==w&&height==h))return;width=w;height=h;
            float ratio=Math.max(.60f,Math.min(1,w/(1140f*density)));
            int cell=Math.round(density*Math.max(32,56*ratio));
            int controlHeight=Math.min(Math.round(density*Math.max(44,64*ratio)),Math.max(1,h-Math.round(12*density)));
            int[] widths=new int[row.length];int occupied=0;
            for(int n=0;n<row.length;n++){
                widths[n]=n==2?Math.round(density*Math.max(88,160*ratio))
                    :n==10?Math.round(density*Math.max(48,70*ratio))
                    :n==11?Math.round(density*Math.max(70,92*ratio)):cell;
                occupied+=widths[n];
            }
            // Eleven equal inner gaps plus two symmetric edge gaps. Each
            // outer gap is 1.2 times an inner gap, before pixel rounding.
            int available=Math.max(0,w-group.getPaddingLeft()-group.getPaddingRight()-occupied);
            int inset=Math.round(available*1.2f/(row.length-1+2.4f));
            int free=Math.max(0,available-2*inset);
            int gap=free/(row.length-1),remainder=free%(row.length-1);
            for(int n=0;n<row.length;n++){
                ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)row[n].getLayoutParams();
                p.width=widths[n];
                if(n==0)p.height=Math.min(Math.round(density*Math.max(32,44*ratio)),controlHeight);
                else if(n==1)p.height=Math.min(p.width,controlHeight);
                else if(n>=3&&n<=10)p.height=controlHeight;
                p.leftMargin=n==0?inset:gap+(n<=remainder?1:0);
                p.rightMargin=n==row.length-1?inset:0;
                p.setMarginStart(p.leftMargin);p.setMarginEnd(p.rightMargin);row[n].setLayoutParams(p);
                if(n>=3&&n<=9&&n!=4){int pad=Math.round(18*density*ratio);row[n].setPadding(0,pad,0,pad);}
                if(n==10){int pad=Math.round(12*density*ratio),side=Math.round(4*density);row[n].setPadding(side,pad,side,pad);}
                if(n==7||n==8||n==11)row[n].setVisibility(View.VISIBLE);
            }
            View play=group.findViewById(group.getContext().getResources().getIdentifier("play_bar_play","id",group.getContext().getPackageName()));
            if(play!=null){int pad=Math.round(12*density*ratio);play.setPadding(0,pad,0,pad);}

        }
    }
    /** Native full player retains its existing back arrow and transport actions. */
    public static void installPlayer(View host){
        if(!(host instanceof ViewGroup)||host.findViewWithTag(PLAYER)!=null||host.findViewWithTag(PLAYER+"-pending")!=null)return;
        ViewGroup group=(ViewGroup)host;View marker=new View(host.getContext());marker.setTag(PLAYER+"-pending");marker.setVisibility(View.GONE);group.addView(marker,new ViewGroup.LayoutParams(0,0));
        PlayerInstaller waiter=new PlayerInstaller(group,marker);group.addOnLayoutChangeListener(waiter);group.addOnAttachStateChangeListener(waiter);waiter.tryInstall();
    }
    private static final class PlayerInstaller implements View.OnLayoutChangeListener,View.OnAttachStateChangeListener{
        final ViewGroup group;final View marker;boolean finished;
        PlayerInstaller(ViewGroup g,View m){group=g;marker=m;}
        public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){tryInstall();}
        public void onViewAttachedToWindow(View v){group.addOnLayoutChangeListener(this);}
        public void onViewDetachedFromWindow(View v){group.removeOnLayoutChangeListener(this);}
        private void end(){finished=true;group.removeOnLayoutChangeListener(this);group.removeOnAttachStateChangeListener(this);group.removeView(marker);}
        void tryInstall(){
            if(finished||group.getWidth()<=0||group.getHeight()<=0)return;
            try{
                Context c=group.getContext();
                // These are the paired native player's existing bottom controls,
                // in their original order. Keep the same Views and listeners.
                String[] names={"prev","play","next","collect","download","playMode","mv","ll_sound"};
                View[] nativeControls=new View[names.length];
                for(int n=0;n<names.length;n++){
                    nativeControls[n]=group.findViewById(c.getResources().getIdentifier(names[n],"id",c.getPackageName()));
                    if(nativeControls[n]==null||nativeControls[n].getParent()!=group){end();return;}
                }
                View prev=nativeControls[0];
                if(prev.getWidth()<=0||prev.getHeight()<=0)return;
                View boundary=group.findViewById(c.getResources().getIdentifier("guideline","id",c.getPackageName()));
                if(boundary==null){end();return;}
                float d=c.getResources().getDisplayMetrics().density;
                int h=Math.min(Math.round(52*d),prev.getHeight()),inset=Math.round(14*d);
                Home button=new Home(c);button.setId(View.generateViewId());button.setTag(PLAYER);button.setContentDescription("返回追风桌面");button.setFocusable(true);button.setClickable(true);button.setOnClickListener(v->returnHome(c));
                View[] row=new View[nativeControls.length+1];row[0]=button;System.arraycopy(nativeControls,0,row,1,nativeControls.length);
                ViewGroup.LayoutParams[] prepared=new ViewGroup.LayoutParams[row.length];
                Class<?> type=prev.getLayoutParams().getClass();
                // Prepare every constraint before changing the live group. An
                // unknown layout cannot leave half of the player rearranged.
                for(int n=0;n<row.length;n++){
                    Object params=n==0?type.getConstructor(int.class,int.class).newInstance(0,h)
                        :type.getConstructor(type).newInstance(row[n].getLayoutParams());
                    field(params,"startToStart",n==0?0:-1);field(params,"startToEnd",n==0?-1:row[n-1].getId());
                    field(params,"endToStart",n==row.length-1?-1:row[n+1].getId());field(params,"endToEnd",n==row.length-1?boundary.getId():-1);
                    ViewGroup.MarginLayoutParams margins=(ViewGroup.MarginLayoutParams)params;
                    margins.width=n==row.length-1?-2:0;
                    margins.leftMargin=n==0?inset:0;margins.rightMargin=n==row.length-1?inset:0;
                    margins.setMarginStart(margins.leftMargin);margins.setMarginEnd(margins.rightMargin);
                    if(n==0){field(params,"topToTop",prev.getId());field(params,"bottomToBottom",prev.getId());}
                    prepared[n]=margins;
                }
                for(int n=1;n<row.length;n++){
                    row[n].setLayoutParams(prepared[n]);
                    // The paired native ImageViews have large horizontal padding.
                    // Keep their vertical icon size, while reserving real icon
                    // space when the same chain is laid out on a narrow screen.
                    if(n<row.length-1)row[n].setPadding(0,row[n].getPaddingTop(),0,row[n].getPaddingBottom());
                }
                group.addView(button,prepared[0]);
                android.util.Log.i("WindChaserHome","home joined native bottom controls");end();
            }catch(Exception error){android.util.Log.w("WindChaserHome","full-player home unavailable",error);end();}
        }
    }
    private static void returnHome(Context c){
        try{
            Intent home=new Intent("cn.windchaser.unified.RETURN_DESKTOP")
                .setComponent(new ComponentName("cn.windchaser.unified","cn.hydrogen.launcher.DesktopReturnReceiver"))
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            c.sendBroadcast(home);
        }catch(RuntimeException e){Toast.makeText(c,"暂时无法返回追风桌面",Toast.LENGTH_SHORT).show();}
    }
    static final class Home extends View{
        boolean lightFooter;float glyphOffset;final Paint p=new Paint(3);final Path path=new Path();private int pathWidth=-1,pathHeight=-1;Home(Context c){super(c);}
        @Override protected void onDraw(Canvas canvas){float size=Math.min(getWidth(),getHeight()),cx=getWidth()/2f+glyphOffset,cy=getHeight()/2f;float scale=lightFooter?1.3225f:1f;float r=size*(glyphOffset<0?.40f:.42f)*scale;p.setStyle(Paint.Style.FILL);p.setColor(isPressed()?0x9050839e:0x5033485c);if(!lightFooter||isPressed())canvas.drawCircle(cx,cy,r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1.5f,size*.035f)*scale);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(lightFooter?0xff31c875:0xffecf6ff);float u=size*(lightFooter?.29f:.23f)*scale;if(pathWidth!=getWidth()||pathHeight!=getHeight()){pathWidth=getWidth();pathHeight=getHeight();path.reset();path.moveTo(cx-u,cy);path.lineTo(cx,cy-u*.85f);path.lineTo(cx+u,cy);path.moveTo(cx-u*.72f,cy-u*.15f);path.lineTo(cx-u*.72f,cy+u*.88f);path.lineTo(cx-u*.20f,cy+u*.88f);path.lineTo(cx-u*.20f,cy+u*.25f);path.lineTo(cx+u*.20f,cy+u*.25f);path.lineTo(cx+u*.20f,cy+u*.88f);path.lineTo(cx+u*.72f,cy+u*.88f);path.lineTo(cx+u*.72f,cy-u*.15f);}canvas.drawPath(path,p);}
        @Override public void setPressed(boolean pressed){super.setPressed(pressed);invalidate();}
    }
}
