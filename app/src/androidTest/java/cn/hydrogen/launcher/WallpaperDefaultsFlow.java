package cn.hydrogen.launcher;
import android.app.*;import android.content.*;import android.media.MediaPlayer;import android.os.*;import android.view.*;import android.widget.*;import java.io.*;import java.lang.reflect.*;
/** Real decoder playback, real lifecycle, and native map toggle; no synthetic readiness. */
final class WallpaperDefaultsFlow {
 final Instrumentation test;MainActivity activity;int passed;StringBuilder report=new StringBuilder("Real video looping and map default checks\n");
 interface Probe {boolean ready()throws Exception;}
 WallpaperDefaultsFlow(Instrumentation value){test=value;}
 Object field(Object target,String name)throws Exception{Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target);}
 void main(Runnable action){Throwable[] error={null};test.runOnMainSync(()->{try{action.run();}catch(Throwable value){error[0]=value;}});if(error[0]!=null)throw new AssertionError(error[0]);}
 void require(boolean value,String why){if(!value)throw new AssertionError(why);passed++;report.append("PASS ").append(why).append('\n');}
 void await(String why,int millis,Probe probe)throws Exception{long until=SystemClock.elapsedRealtime()+millis;while(SystemClock.elapsedRealtime()<until){boolean[] ok={false};Throwable[] error={null};main(()->{try{ok[0]=probe.ready();}catch(Throwable value){error[0]=value;}});if(error[0]!=null)throw new AssertionError(error[0]);if(ok[0])return;Thread.sleep(80);}throw new AssertionError("Timeout: "+why);}
 View desc(View view,String name){if(view==null)return null;if(name.contentEquals(view.getContentDescription()==null?"":view.getContentDescription()))return view;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View found=desc(((ViewGroup)view).getChildAt(n),name);if(found!=null)return found;}return null;}
 boolean text(View view,String name){if(view instanceof TextView&&name.contentEquals(((TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++)if(text(((ViewGroup)view).getChildAt(n),name))return true;return false;}
 WallpaperLayer layer()throws Exception{return (WallpaperLayer)field(activity,"wallpaper");}
 MediaPlayer player()throws Exception{return (MediaPlayer)field(layer(),"player");}
 boolean playing()throws Exception{MediaPlayer p=player();return p!=null&&(Boolean)field(layer(),"prepared")&&p.isPlaying();}
 int observeWraps(MediaPlayer target,int required,int millis)throws Exception{int wraps=0,last=-1;long until=SystemClock.elapsedRealtime()+millis;while(SystemClock.elapsedRealtime()<until&&wraps<required){int[] position={-1};main(()->{if(target.isPlaying())position[0]=target.getCurrentPosition();});if(last>position[0]+250&&position[0]>=0)wraps++;if(position[0]>=0)last=position[0];Thread.sleep(60);}report.append("observed wraps=").append(wraps).append("\n");return wraps;}
 void run(){Context ctx=test.getTargetContext();TestPreferences saved=new TestPreferences(ctx);Config cfg=new Config(ctx);File original=new File(ctx.getFilesDir(),"wallpaper"),backup=new File(ctx.getFilesDir(),"wallpaper-loop-check-backup");boolean moved=false,touched=false;Bundle result=new Bundle();boolean success=false;try{
  await("focused admitted desktop",15000,()->{activity=(MainActivity)((java.lang.ref.WeakReference<?>)field(MapWorkspace.get(ctx),"main")).get();return activity!=null&&!activity.isFinishing()&&activity.hasWindowFocus();});
  main(()->{cfg.p.edit().remove("amapFloating").commit();activity.render();activity.showPage(MainActivity.CARDS);});Thread.sleep(400);
  require(!cfg.p.contains("amapFloating")&&cfg.b("amapFloating",true),"absent desktop map preference defaults to enabled");
  await("default native map card",15000,()->activity.mapCardConnected());require((Boolean)field(field(activity,"floatingMap"),"windowVisible"),"default desktop map really displays");
  main(()->desc(activity.getWindow().getDecorView(),"切换高德悬浮地图").performClick());Thread.sleep(300);
  require(!cfg.b("amapFloating",true)&&!(Boolean)field(field(activity,"floatingMap"),"windowVisible"),"explicit disable removes default map window");
  main(()->desc(activity.getWindow().getDecorView(),"切换高德悬浮地图").performClick());
  await("user enabled native map",15000,()->activity.mapCardConnected());require(cfg.b("amapFloating",false),"explicit enable reconnects the real Amap card");
  main(()->desc(activity.getWindow().getDecorView(),"切换高德悬浮地图").performClick());Thread.sleep(300);
  require(!cfg.b("amapFloating",true)&&!(Boolean)field(field(activity,"floatingMap"),"windowVisible"),"explicit disable removes map window again");
  if(backup.exists())throw new IOException("Unexpected existing wallpaper backup");if(original.exists()){if(!original.renameTo(backup))throw new IOException("Cannot back up wallpaper");moved=true;}touched=true;
  try(InputStream in=test.getContext().getAssets().open("wallpaper-test.mp4");OutputStream out=new FileOutputStream(original)){byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);}
  main(()->{cfg.put("wallpaperKind","video");activity.render();});await("real video prepared and playing",10000,()->playing());
  WallpaperLayer initial=layer();MediaPlayer first=player();int duration=first.getDuration();report.append("durationMs=").append(duration).append("\n");require(first.isLooping()&&duration>0,"prepared decoder has native looping enabled");
  require(observeWraps(first,3,Math.max(15000,duration*5))>=3&&first.isPlaying(),"video genuinely plays across at least three end-to-start loops");
  main(()->{first.setLooping(false);first.seekTo(Math.max(0,duration-300));});
  await("completion fallback restarts same decoder",Math.max(8000,duration+2000),()->first.isLooping()&&first.isPlaying()&&first.getCurrentPosition()<duration-350);
  require(player()==first,"natural completion fallback reuses the same player");require(observeWraps(first,1,Math.max(6000,duration*2))>=1,"playback continues looping after completion recovery");
  for(int page:new int[]{MainActivity.MINIMAL,MainActivity.CARDS,MainActivity.APPS}){main(()->activity.showPage(page));Thread.sleep(300);require(layer()==initial&&player()==first&&first.isPlaying(),"page "+page+" reuses the playing video and view");}
  Activity settings=test.startActivitySync(new Intent(ctx,SettingsActivity.class).putExtra("category",7).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
  require(field(initial,"player")==null,"leaving desktop releases video decoder");require(!cfg.b("amapFloating",true),"theme association retains disabled map selection");
  main(settings::finish);await("desktop video resumes after settings",12000,()->activity.hasWindowFocus()&&playing());MediaPlayer resumed=player();
  require(resumed!=first&&resumed.isLooping(),"returning from settings prepares a looping decoder");require(observeWraps(resumed,2,Math.max(10000,duration*3))>=2,"resumed video actually loops repeatedly");success=true;
 }catch(Throwable error){report.append("FAIL ").append(error).append('\n');}finally{try{if(touched){if(original.exists()&&!original.delete())throw new IOException("Cannot remove test video");if(moved&&!backup.renameTo(original))throw new IOException("Cannot restore original wallpaper");}saved.close();if(activity!=null&&!activity.isFinishing())main(activity::render);}catch(Throwable error){success=false;report.append("RESTORE FAIL ").append(error).append('\n');}}
 report.append("RESULT wallpaper/default checks=").append(passed).append(success?" PASS":" FAIL").append('\n');result.putString("stream",report.toString());test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
 }
}
