import android.view.*;import android.widget.ImageView;import android.content.*;import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams;import cn.windchaser.startup.QqHome;
public class QqHomeChecks {
 static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
 static boolean contentReady(){try{java.lang.reflect.Method m=QqHome.class.getDeclaredMethod("contentReady");m.setAccessible(true);return (Boolean)m.invoke(null);}catch(Exception e){throw new AssertionError(e);}}
 public static void main(String[] args){
  check(!contentReady(),"cold QQ home is not ready before native layout");
  for(float density:new float[]{1f,1.5f,2f}){
   Context c=new Context();c.resources.metrics.density=density;ViewGroup host=new ViewGroup(c);View[] nativeViews=new View[11];View.OnClickListener nativeAction=v->{};int[] ids={1,2,21,22,23,24,25,26,27,28,29};
   for(int n=0;n<ids.length;n++){View v=n==3?new ViewGroup(c):n==1||n==10?new View(c):new ImageView(c);v.setId(ids[n]);v.width=(int)((n==1?240:64)*density);v.height=(int)(64*density);v.setOnClickListener(nativeAction);v.setPadding(36,24,36,24);if(n==6||n==7||n==10)v.setVisibility(View.GONE);LayoutParams p=new LayoutParams(v.width,v.height);p.i=p.l=0;host.addView(v,p);nativeViews[n]=v;}
   ImageView play=new ImageView(c);play.setId(30);play.setOnClickListener(nativeAction);play.setPadding(36,24,36,24);((ViewGroup)nativeViews[3]).addView(play,new ViewGroup.LayoutParams(96,96));
   QqHome.install(host);check(host.findViewWithTag("windchaser-home")==null,"unmeasured constructor defers installation");host.layout((int)(1280*density),(int)(96*density));View home=host.findViewWithTag("windchaser-home");
   check(home!=null,"measured layout installs native home group");check(contentReady(),"native layout admits retained home readiness");LayoutParams hp=(LayoutParams)home.getLayoutParams();check(hp.t==0&&hp.u==1&&hp.leftMargin>0,"home starts inset native chain before album/song");
   View previous=home;for(int n=0;n<nativeViews.length;n++){View v=host.findViewById(ids[n]);LayoutParams p=(LayoutParams)v.getLayoutParams();check(v==nativeViews[n]&&v.click==nativeAction,"native control identity and click retained");check(p.s==previous.getId(),"native group keeps requested home -> song -> transport order");check(n==10?p.v==0:p.u==ids[n+1],"native group has reciprocal constraints through effects and time");previous=v;}
   check(play.click==nativeAction&&play.getLayoutParams().width==-1&&play.paddingLeft==0,"original play image follows existing play/loading group width");check(host.layouts.size()==1&&host.attachments.size()==1,"installer releases callbacks and keeps one tree-owned resize observer");
   QqHome.install(host);check(host.children.size()==12&&host.layouts.size()==1,"repeat install never duplicates group or resize observer");
   check(QqHome.ownsHomeLayout(host),"only a successfully installed native footer owns visibility");
   check(!QqHome.ownsHomeLayout(new ViewGroup(c))&&!QqHome.ownsHomeLayout(null),"unknown layouts retain original native auto-hide policy");
   for(int viewport:new int[]{640,711,800,1024,1280,1920}){
    host.layout((int)(viewport*density),(int)(96*density));int occupied=0,minGap=Integer.MAX_VALUE,maxGap=0,gapSum=0;int homeInset=((LayoutParams)home.getLayoutParams()).leftMargin;
    for(View v:host.children){LayoutParams p=(LayoutParams)v.getLayoutParams();check(p.width>0,"each item has a real measured slot");occupied+=p.width+p.leftMargin+p.rightMargin;if(v!=home){gapSum+=p.leftMargin;minGap=Math.min(minGap,p.leftMargin);maxGap=Math.max(maxGap,p.leftMargin);}}
    check(occupied==host.width,"row fills available width without overflow or trailing hole");
    check(minGap>=0&&maxGap-minGap<=1,"all eleven horizontal gaps differ by at most one rounding pixel");
    check(nativeViews[1].getLayoutParams().width>=88*density,"bounded song information remains readable");
    check(nativeViews[1].getLayoutParams().width<host.width/4,"song block cannot absorb a large empty area");
    for(int n:new int[]{2,3,4,5,6,7,8})check(nativeViews[n].getLayoutParams().width==home.getLayoutParams().width,"home and transport use equal center spacing");
    check(((LayoutParams)nativeViews[10].getLayoutParams()).rightMargin==homeInset,"both edges retain matching insets");
    check(Math.abs(homeInset-1.2f*gapSum/11f)<=.7f,"outer spacing is 20 percent wider than the mean inner gap within integer rounding");
   }
   for(int n:new int[]{2,4,5,6,7,8,9})check(((ImageView)nativeViews[n]).scale==ImageView.ScaleType.FIT_CENTER,"native icons keep aspect ratio in expanded cells");check(play.scale==ImageView.ScaleType.FIT_CENTER,"original play image keeps aspect ratio");for(int n:new int[]{6,7,10})check(nativeViews[n].visibility==View.VISIBLE,"native auto-hidden controls restored");
   for(View.OnAttachStateChangeListener l:new java.util.ArrayList<>(host.attachments))l.onViewDetachedFromWindow(host);check(host.layouts.isEmpty(),"resize callback removed while detached");for(View.OnAttachStateChangeListener l:new java.util.ArrayList<>(host.attachments))l.onViewAttachedToWindow(host);check(host.layouts.size()==1,"one resize callback restored on reattach");
   home.click.onClick(home);check(c.broadcast!=null&&c.launched==null&&"cn.windchaser.unified".equals(c.broadcast.component.pkg)&&"cn.hydrogen.launcher.DesktopReturnReceiver".equals(c.broadcast.component.cls),"home click requests existing desktop task");
  }
  for(float density:new float[]{1f,1.5f,2f}){
   for(int viewport:new int[]{800,1024,1280}){
    Context pc=new Context();pc.resources.metrics.density=density;ViewGroup page=new ViewGroup(pc);View back=new View(pc);back.setId(3);back.width=back.height=(int)(64*density);LayoutParams backParams=new LayoutParams(back.width,back.height);page.addView(back,backParams);
    View boundary=new View(pc);boundary.setId(12);page.addView(boundary,new LayoutParams(0,0));View[] original=new View[8];View.OnClickListener nativeAction=v->{};
    for(int n=0;n<original.length;n++){View v=new View(pc);v.setId(n+4);v.width=(int)((n==7?146:96)*density);v.height=(int)(96*density);v.setOnClickListener(nativeAction);v.setPadding(36,24,36,24);LayoutParams p=new LayoutParams(v.width,v.height);p.l=0;p.i=20;p.leftMargin=40;page.addView(v,p);original[n]=v;}
    QqHome.installPlayer(page);check(page.findViewWithTag("windchaser-player-home")==null,"unmeasured player defers");page.layout((int)(viewport*density),(int)(720*density));View target=page.findViewWithTag("windchaser-player-home");check(target!=null,"player adds bottom home");
    LayoutParams hp=(LayoutParams)target.getLayoutParams();check(hp.t==0&&hp.u==4&&hp.i==4&&hp.l==4,"home shares native bottom control chain and baseline");check(hp.leftMargin>0,"bottom row has edge padding");
    View previous=target;for(int n=0;n<original.length;n++){
     View current=page.findViewById(n+4);LayoutParams p=(LayoutParams)current.getLayoutParams();check(current==original[n]&&current.click==nativeAction,"native control identity and listener retained");check(n==7?current.paddingLeft==36:current.paddingLeft==0&&current.paddingRight==0&&current.paddingTop==24&&current.paddingBottom==24,"narrow screens retain icon space without changing quality group padding");check(p.s==previous.getId(),"native chain is reciprocal");check(p.l==0&&p.i==20,"native vertical constraints preserved");check(n==7?p.v==12&&p.width==-2:p.u==n+5&&p.width==0,"row stays within native player boundary");previous=current;
    }
    float slot=(viewport*.76f-28-146)/8;check(slot>=40,"test viewport gives every icon a usable nonoverlapping slot");
    check(page.findViewById(3)==back&&back.getLayoutParams()==backParams&&back.click==null,"native back arrow unmodified; no top home");
    QqHome.installPlayer(page);check(page.children.size()==11&&page.layouts.isEmpty()&&page.attachments.isEmpty(),"player is deduplicated and releases installation listeners");target.click.onClick(target);check(pc.broadcast!=null&&pc.launched==null,"player returns through host task owner");check((pc.broadcast.flags&Intent.FLAG_RECEIVER_FOREGROUND)!=0,"return broadcast is foreground priority");
   }
  }
  Context missing=new Context();ViewGroup page=new ViewGroup(missing);page.width=1280;page.height=720;View prev=new View(missing);prev.setId(4);prev.width=prev.height=96;LayoutParams originalParams=new LayoutParams(96,96);page.addView(prev,originalParams);QqHome.installPlayer(page);check(page.findViewWithTag("windchaser-player-home")==null&&prev.getLayoutParams()==originalParams,"unknown layout leaves original controls unchanged");check(page.layouts.isEmpty()&&page.attachments.isEmpty(),"unsupported layout releases listeners");
  Context c=new Context();ViewGroup host=new ViewGroup(c);host.width=640;host.height=48;View album=new View(c),info=new View(c);album.setId(1);info.setId(2);album.width=50;info.width=80;
  LayoutParams art=new LayoutParams(50,50),song=new LayoutParams(80,40);art.t=0;song.s=1;host.addView(album,art);host.addView(info,song);QqHome.install(host);
  check(host.findViewWithTag("windchaser-home")==null&&song.width==80,"too-small layout refuses overlap");check(host.layouts.isEmpty()&&host.attachments.isEmpty(),"refused layout releases callbacks");
  System.out.println("RESULT qq-home checks="+checks+" PASS (production helper; JVM layout stubs, not rendered QQ)");
 }
}
