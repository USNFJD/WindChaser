package cn.hydrogen.launcher;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.database.DataSetObserver;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Actual API19 Views/bitmaps/services; QQ status is a fixture, not commercial playback. */
final class MemoryFlow {
    private final Instrumentation test;private MainActivity activity;private int passed,failed,assertions;
    private final StringBuilder report=new StringBuilder("Memory optimizations: real Android UI/bitmap/service execution; synthetic QQ metadata.\n");
    private interface Check{void run()throws Exception;}
    private interface Condition{boolean ready()throws Exception;}
    MemoryFlow(Instrumentation t){test=t;}
    private void require(boolean value,String why){assertions++;if(!value)throw new AssertionError(why);}
    private Object field(Object obj,String name)throws Exception{Field f=obj.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(obj);}
    private Object shared()throws Exception{Field f=MusicSession.class.getDeclaredField("shared");f.setAccessible(true);return f.get(null);}
    private void main(Runnable r){final Throwable[] error={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);}
    private void await(String why,Condition c)throws Exception{for(int i=0;i<300;i++){final boolean[] ready={false};final Throwable[] error={null};main(()->{try{ready[0]=c.ready();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new AssertionError(error[0]);if(ready[0])return;Thread.sleep(100);}throw new AssertionError("Timeout: "+why);}
    private void check(String name,Check task){android.util.Log.i("MemoryFlow","START "+name);try{task.run();passed++;report.append("PASS ");android.util.Log.i("MemoryFlow","PASS "+name);}catch(Throwable t){failed++;report.append("FAIL ").append(t).append(" ");android.util.Log.e("MemoryFlow","FAIL "+name,t);}report.append(name).append('\n');}
    private View find(View root,String name){if(root==null)return null;if(name.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View result=find(((ViewGroup)root).getChildAt(i),name);if(result!=null)return result;}return null;}
    private View floatingRoot()throws Exception{
        Class<?> type=Class.forName("android.view.WindowManagerGlobal");Object global=type.getMethod("getInstance").invoke(null);Field f=type.getDeclaredField("mViews");f.setAccessible(true);Object roots=f.get(global);
        Iterable<?> values=roots instanceof List?(List<?>)roots:Arrays.asList((Object[])roots);
        for(Object item:values)if(item instanceof View&&find((View)item,"悬浮歌曲封面")!=null)return (View)item;return null;
    }
    private String song(String title)throws Exception{return new JSONObject().put("module","play").put("command",new JSONObject().put("method","update_song").put("data",new JSONObject().put("key_title",title).put("key_artist",new JSONArray().put(new JSONObject().put("singer","内存回归样例"))).put("curr_time",10000).put("total_time",180000))).toString();}
    private void restore(Config cfg,Map<String,?> values){SharedPreferences.Editor e=cfg.p.edit().clear();for(Map.Entry<String,?> x:values.entrySet()){Object v=x.getValue();if(v instanceof Integer)e.putInt(x.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(x.getKey(),(Long)v);else if(v instanceof Boolean)e.putBoolean(x.getKey(),(Boolean)v);else if(v instanceof String)e.putString(x.getKey(),(String)v);else if(v instanceof Float)e.putFloat(x.getKey(),(Float)v);}e.commit();}
    private void capture(String name)throws Exception{final Bitmap[] image={null};main(()->{View root=activity.getWindow().getDecorView();image[0]=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888);root.draw(new Canvas(image[0]));});try(OutputStream out=new FileOutputStream(new File(test.getTargetContext().getFilesDir(),name))){image[0].compress(Bitmap.CompressFormat.PNG,100,out);}finally{image[0].recycle();}}
    void run(){
        Context context=test.getTargetContext();Config cfg=new Config(context);Map<String,?> saved=new HashMap<>(cfg.p.getAll());
        File original=new File(context.getFilesDir(),"wallpaper"),backup=new File(context.getFilesDir(),"memory-wallpaper-backup"),fixture=new File(context.getFilesDir(),"memory-image.png");boolean moved=false;
        MusicSession.Lease[] extra={null},mainLease={null};QqMusicBridge[] bridge={null};WallpaperLayer[] layer={null};
        try{
            cfg.put("autoRecoverApps",false);cfg.put("pageAnimation",false);cfg.put("homePage",0);cfg.put("musicMode",0);cfg.put("wallpaperKind","");cfg.p.edit().remove("qqLastTitle").remove("qqCoverUrl").remove("qqCoverSong").commit();
            // The shared player intentionally posts a clock tick; use a lifecycle monitor rather than waiting for a globally idle render queue.
            Instrumentation.ActivityMonitor monitor=test.addMonitor(MainActivity.class.getName(),null,false);
            try{context.startActivity(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));activity=(MainActivity)monitor.waitForActivityWithTimeout(45000);}finally{test.removeMonitor(monitor);}
            if(activity==null)throw new AssertionError("Desktop did not start");await("desktop measured",()->activity.hasWindowFocus()&&activity.getWindow().getDecorView().getWidth()>0&&field(activity,"minimalPage")!=null);
            main(()->{try{bridge[0]=(QqMusicBridge)field(activity,"musicBridge");mainLease[0]=(MusicSession.Lease)field(activity,"musicLease");}catch(Exception e){throw new RuntimeException(e);}});
            Bitmap source=Bitmap.createBitmap(1600,900,Bitmap.Config.RGB_565);Canvas canvas=new Canvas(source);canvas.drawColor(0xff448844);Paint paint=new Paint();paint.setColor(0xffff0000);canvas.drawRect(0,0,400,900,paint);paint.setColor(0xff0000ff);canvas.drawRect(1200,0,1600,900,paint);try(OutputStream out=new FileOutputStream(fixture)){source.compress(Bitmap.CompressFormat.PNG,100,out);}source.recycle();
            check("pixel plans keep crops inside source and final allocation inside measured bounds",()->{Random random=new Random(20261025);for(int i=0;i<300;i++){int sw=1+random.nextInt(30000),sh=1+random.nextInt(30000),w=1+random.nextInt(2000),h=1+random.nextInt(1200),fit=i%3;ImageSizing.Plan p=ImageSizing.plan(sw,sh,w,h,fit);require(p.left>=0&&p.top>=0&&p.left+p.width<=sw&&p.top+p.height<=sh,"crop outside source");require(p.outWidth>0&&p.outHeight>0&&p.outWidth<=w&&p.outHeight<=h,"oversized bitmap");require(p.sample>0&&(p.sample&(p.sample-1))==0,"non-power-of-two sample");}});
            check("real region decode preserves center crop at 200x200",()->{Bitmap b=DisplayBitmap.decode(()->new FileInputStream(fixture),200,200,0);try{require(b.getWidth()==200&&b.getHeight()==200,"crop dimensions");int color=b.getPixel(100,100);require(Color.green(color)>Color.red(color)&&Color.green(color)>Color.blue(color),"wrong crop center");}finally{b.recycle();}});
            check("real fit-center decode keeps source aspect ratio",()->{Bitmap b=DisplayBitmap.decode(()->new FileInputStream(fixture),400,300,1);try{require(b.getWidth()==400&&b.getHeight()==225,"fit-center dimensions");}finally{b.recycle();}});
            check("center mode keeps original pixels instead of scaling entire source",()->{Bitmap b=DisplayBitmap.decode(()->new FileInputStream(fixture),320,180,2);try{require(b.getWidth()==320&&b.getHeight()==180,"center region dimensions");}finally{b.recycle();}});
            check("same-settings render reuses both home pages and their music controls",()->main(()->{try{Object home=field(activity,"home"),minimal=field(activity,"minimalPage"),grid=field(activity,"grid");int count=(Integer)field(activity,"renderCount");for(int i=0;i<8;i++)activity.render();require(field(activity,"home")==home&&field(activity,"minimalPage")==minimal&&field(activity,"grid")==grid,"page was rebuilt");require((Integer)field(activity,"renderCount")==count,"unchanged layout counted as rebuild");}catch(Exception e){throw new RuntimeException(e);}}));
            check("repeated home-page switches reuse controls and leave hidden grid data unchanged",()->{
                final int[] changes={0};final DataSetObserver observer=new DataSetObserver(){@Override public void onChanged(){changes[0]++;}};
                main(()->{try{GridView grid=(GridView)field(activity,"grid");grid.getAdapter().registerDataSetObserver(observer);Object home=field(activity,"home"),minimal=field(activity,"minimalPage");for(int i=0;i<40;i++){activity.showPage(1);activity.showPage(0);}require(field(activity,"home")==home&&field(activity,"minimalPage")==minimal,"switch replaced page controls");require(changes[0]==0,"hidden grid refreshed during music swipes");grid.getAdapter().unregisterDataSetObserver(observer);}catch(Exception e){throw new RuntimeException(e);}});
            });
            check("actual style change still rebuilds measured layout",()->main(()->{try{Object old=field(activity,"home");int previous=cfg.n("lyricSize",110);cfg.put("lyricSize",previous==110?111:110);activity.render();require(field(activity,"home")!=old,"style change ignored");}catch(Exception e){throw new RuntimeException(e);}}));
            capture("memory-minimal.png");main(()->activity.showPage(1));Thread.sleep(150);capture("memory-cards.png");main(()->activity.showPage(0));
            check("music session shares one bridge and cover and uses application context",()->main(()->{extra[0]=MusicSession.acquire(activity,()->{});require(extra[0].bridge()==bridge[0]&&extra[0].bridge().cover==bridge[0].cover,"duplicate state/cover object");require(bridge[0].context==activity.getApplicationContext(),"shared bridge retains Activity context");extra[0].resume();extra[0].close();extra[0]=null;require(bridge[0].registered,"closing one client unregisters remaining clients");}));
            check("all paused clients stop tick and one resume restarts it",()->main(()->{try{mainLease[0].pause();require(!(Boolean)field(bridge[0],"ticking"),"tick remained active");mainLease[0].resume();require((Boolean)field(bridge[0],"ticking"),"tick did not resume");}catch(Exception e){throw new RuntimeException(e);}}));
            if(original.isFile()){if(backup.exists())throw new IOException("backup exists");if(!original.renameTo(backup))throw new IOException("cannot preserve original wallpaper");moved=true;}
            try(InputStream in=new FileInputStream(fixture);OutputStream out=new FileOutputStream(original)){byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1)out.write(data,0,n);}cfg.put("wallpaperKind","image");cfg.put("fit",0);
            check("wallpaper keeps only a bitmap matching its actual 300x180 layout",()->{
                main(()->{layer[0]=new WallpaperLayer(activity);layer[0].measure(View.MeasureSpec.makeMeasureSpec(300,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(180,View.MeasureSpec.EXACTLY));layer[0].layout(0,0,300,180);});await("wallpaper decode",()->field(layer[0],"bitmap")!=null);
                main(()->{try{Bitmap b=(Bitmap)field(layer[0],"bitmap");require(b.getWidth()==300&&b.getHeight()==180,"wallpaper decoded above layout size");require(((ImageView)field(layer[0],"defaultImage")).getDrawable()==null,"unneeded default bitmap retained");}catch(Exception e){throw new RuntimeException(e);}});
            });
            check("settings/file/device/weather backgrounds reuse measured default view and ignore custom wallpaper",()->{
                final WallpaperLayer[] defaults={null};try{main(()->{defaults[0]=WallpaperLayer.defaultBackground(activity,null);new FrameLayout(activity).addView(defaults[0]);defaults[0].measure(View.MeasureSpec.makeMeasureSpec(280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(160,View.MeasureSpec.EXACTLY));defaults[0].layout(0,0,280,160);});await("default wallpaper",()->field(defaults[0],"bitmap")!=null);
                    main(()->{try{Bitmap b=(Bitmap)field(defaults[0],"bitmap");require(b.getWidth()<=280&&b.getHeight()<=160,"default background too large");require(field(defaults[0],"image")==null,"settings accidentally loads custom image");WallpaperLayer same=WallpaperLayer.defaultBackground(activity,defaults[0]);require(same==defaults[0]&&same.getParent()==null&&field(same,"bitmap")==b,"category switch recreates background or decode");}catch(Exception e){throw new RuntimeException(e);}});
                }finally{if(defaults[0]!=null)main(defaults[0]::dispose);}
            });
            check("same-size default backgrounds share live pixels without owning each other's lifetime",()->{
                final WallpaperLayer[] pair=new WallpaperLayer[2];try{
                    main(()->{for(int i=0;i<2;i++){pair[i]=WallpaperLayer.defaultBackground(activity,null);pair[i].measure(View.MeasureSpec.makeMeasureSpec(260,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(150,View.MeasureSpec.EXACTLY));pair[i].layout(0,0,260,150);}});
                    await("two shared backgrounds",()->field(pair[0],"bitmap")!=null&&field(pair[1],"bitmap")!=null);
                    main(()->{try{Bitmap shared=(Bitmap)field(pair[1],"bitmap");require(field(pair[0],"bitmap")==shared,"same geometry decoded twice");pair[0].dispose();require(!shared.isRecycled()&&((android.graphics.drawable.BitmapDrawable)((ImageView)field(pair[1],"defaultImage")).getDrawable()).getBitmap()==shared,"closing one background invalidates the other");pair[0]=null;}catch(Exception e){throw new RuntimeException(e);}});
                }finally{for(WallpaperLayer view:pair)if(view!=null)main(view::dispose);}
            });
            check("startup artwork and QQ home redraw reuse resources while bounds changes still update geometry",()->main(()->{
                Bitmap target=Bitmap.createBitmap(400,240,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(target);
                try{
                    Class<?> splash=cn.windchaser.startup.DualSplash.class;Field until=splash.getDeclaredField("automaticUntil");until.setAccessible(true);long old=until.getLong(null);
                    try{until.setLong(null,SystemClock.elapsedRealtime()+60000);android.graphics.drawable.Drawable art=cn.windchaser.startup.DualSplash.drawable(test.getContext(),0);art.setBounds(0,0,400,240);art.draw(c);require(art instanceof android.graphics.drawable.ColorDrawable,"paired modules must not show independent startup artwork");require(((android.graphics.drawable.ColorDrawable)art).getColor()==Color.TRANSPARENT,"companion startup background is not transparent");for(int i=0;i<20;i++)art.draw(c);art.setBounds(0,0,300,180);art.draw(c);
                    }finally{until.setLong(null,old);}
                    Class<?> home=Class.forName("cn.windchaser.startup.QqHome$Home");Constructor<?> constructor=home.getDeclaredConstructor(Context.class);constructor.setAccessible(true);View button=(View)constructor.newInstance(activity);button.layout(0,0,80,64);button.draw(c);Object path=field(button,"path");button.setPressed(true);button.draw(c);button.layout(0,0,100,72);button.draw(c);require(field(button,"path")==path&&(Integer)field(button,"pathWidth")==100,"QQ home path was recreated or resize ignored");
                }catch(Exception e){throw new RuntimeException(e);}finally{target.recycle();}
            }));
            check("disposed wallpaper rejects late decode and releases drawable references",()->{main(()->{try{layer[0].layout(0,0,170,90);layer[0].dispose();require(field(layer[0],"bitmap")==null&&((ImageView)field(layer[0],"image")).getDrawable()==null,"disposed wallpaper keeps bitmap");}catch(Exception e){throw new RuntimeException(e);}});Thread.sleep(400);main(()->{try{require(field(layer[0],"bitmap")==null,"late task repopulated disposed view");}catch(Exception e){throw new RuntimeException(e);}});layer[0]=null;});
            cfg.put("wallpaperKind","");
            check("floating controller joins existing session instead of creating a second receiver",()->{
                context.startService(new Intent(context,FloatingMusicService.class));await("floating window",()->floatingRoot()!=null);main(()->{try{Object session=shared();Method live=session.getClass().getDeclaredMethod("live");live.setAccessible(true);require(((List<?>)live.invoke(session)).size()==2,"floating controller did not share two-client session");}catch(Exception e){throw new RuntimeException(e);}});
            });
            check("one native-style state event updates desktop and floating controller",()->{main(()->{try{bridge[0].accept(song("共享状态测试"));}catch(Exception e){throw new RuntimeException(e);}});await("floating metadata",()->{View root=floatingRoot();return root!=null&&allText(root).contains("共享状态测试");});main(()->require(allText(activity.getWindow().getDecorView()).contains("共享状态测试"),"desktop metadata stale"));});
            String url="https://y.gtimg.cn/windchaser-memory-test.jpg";File cache=new File(context.getCacheDir(),"qq-cover.jpg");try(InputStream in=new FileInputStream(fixture);OutputStream out=new FileOutputStream(cache)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}context.getSharedPreferences("cover-cache",0).edit().putString("url",url).commit();
            check("one cached cover decode serves desktop and floating art at their measured dimensions",()->{main(()->bridge[0].cover.load(url));await("shared cover",()->bridge[0].cover.bitmap!=null);main(()->{try{View desktop=find(activity.getWindow().getDecorView(),"简洁页无边框音乐区");ImageView art=(ImageView)find(desktop,"当前歌曲封面");ImageView floating=(ImageView)find(floatingRoot(),"悬浮歌曲封面");Bitmap b=bridge[0].cover.bitmap;require(b.getWidth()<=Math.max(art.getWidth(),floating.getWidth())&&b.getHeight()<=Math.max(art.getHeight(),floating.getHeight()),"cover exceeds active display bounds");require(((android.graphics.drawable.BitmapDrawable)art.getDrawable()).getBitmap()==b&&((android.graphics.drawable.BitmapDrawable)floating.getDrawable()).getBitmap()==b,"duplicate decoded cover");}catch(Exception e){throw new RuntimeException(e);}});});
            check("hidden music page does not retain a separate cover reference",()->main(()->{View hidden=find(activity.getWindow().getDecorView(),"QQ音乐卡片");ImageView art=(ImageView)find(hidden,"当前歌曲封面");require(art.getDrawable()==null,"hidden music card retains bitmap drawable");}));
            check("closing floating controller leaves desktop session and controls alive",()->{context.stopService(new Intent(context,FloatingMusicService.class));await("floating closed",()->floatingRoot()==null);main(()->{require(bridge[0].registered,"desktop receiver was stopped with floating window");try{bridge[0].accept(song("桌面继续播放"));require(allText(activity.getWindow().getDecorView()).contains("桌面继续播放"),"desktop update stopped");}catch(Exception e){throw new RuntimeException(e);}});});
            context.startService(new Intent(context,FloatingMusicService.class));await("second floating window",()->floatingRoot()!=null);
            MainActivity old=activity;MusicSession.Lease oldLease=mainLease[0];
            check("destroying desktop clears page tree without stopping active floating music",()->{main(old::finish);await("desktop destroyed",()->(Boolean)field(old,"destroyed"));main(()->{try{require(field(old,"root")==null&&field(old,"musicLease")==null,"destroyed Activity retains page/lease");require(field(oldLease,"callback")==null&&field(oldLease,"session")==null,"closed lease retains callback or session");require(bridge[0].registered,"desktop teardown stopped floating receiver");bridge[0].accept(song("悬浮继续播放"));}catch(Exception e){throw new RuntimeException(e);}});await("floating still updates",()->allText(floatingRoot()).contains("悬浮继续播放"));});
            check("last client close unregisters state receiver and releases shared cover",()->{context.stopService(new Intent(context,FloatingMusicService.class));await("session released",()->shared()==null);main(()->{require(!bridge[0].registered,"last owner did not unregister receiver");require(bridge[0].cover.bitmap==null,"last owner retained bitmap");try{require(!(Boolean)field(bridge[0],"ticking"),"last owner retained timer");}catch(Exception e){throw new RuntimeException(e);}});});
        }catch(Throwable t){failed++;report.append("FAIL setup/runtime ").append(t).append('\n');}
        finally{
            if(extra[0]!=null)main(extra[0]::close);if(layer[0]!=null)main(layer[0]::dispose);context.stopService(new Intent(context,FloatingMusicService.class));if(activity!=null&&!activity.isFinishing())main(activity::finish);
            if(moved){if(original.exists())original.delete();backup.renameTo(original);}else if(backup.exists())report.append("Existing backup preserved.\n");restore(cfg,saved);
        }
        report.append("RESULT memory checks=").append(passed+failed).append(" passed=").append(passed).append(" failed=").append(failed).append(" assertions=").append(assertions).append('\n');
        try(Writer out=new OutputStreamWriter(new FileOutputStream(new File(context.getFilesDir(),"memory-checks.txt")),"UTF-8")){out.write(report.toString());}catch(IOException ignored){}
        Bundle result=new Bundle();result.putString("stream",report.toString());test.finish(failed==0?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
    private String allText(View root){if(root==null)return "";StringBuilder out=new StringBuilder();if(root instanceof TextView)out.append(((TextView)root).getText()).append('\n');if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)out.append(allText(((ViewGroup)root).getChildAt(i)));return out.toString();}
}
