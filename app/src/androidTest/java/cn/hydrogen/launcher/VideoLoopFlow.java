package cn.hydrogen.launcher;

import android.app.*;import android.content.*;import android.media.MediaPlayer;import android.os.*;import java.io.*;import java.lang.reflect.*;
/** Actual native decoder output plus deliberate callback-loss and memory-pressure faults. */
final class VideoLoopFlow {
    final Instrumentation test;final Bundle args;MainActivity activity;int passed;
    final StringBuilder report=new StringBuilder("Video loop regression\n");
    interface Probe {boolean ready()throws Exception;}
    VideoLoopFlow(Instrumentation test,Bundle args){this.test=test;this.args=args;}
    Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    void main(Runnable r){Throwable[] e={null};test.runOnMainSync(()->{try{r.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new AssertionError(e[0]);}
    void require(boolean b,String why){if(!b)throw new AssertionError(why);passed++;report.append("PASS ").append(why).append('\n');}
    void await(String why,int timeout,Probe probe)throws Exception{long until=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<until){boolean[] ok={false};Throwable[] error={null};main(()->{try{ok[0]=probe.ready();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new AssertionError(error[0]);if(ok[0])return;Thread.sleep(100);}throw new AssertionError("Timeout: "+why);}
    WallpaperLayer layer()throws Exception{return (WallpaperLayer)field(activity,"wallpaper");}
    MediaPlayer player()throws Exception{return (MediaPlayer)field(layer(),"player");}
    boolean playing()throws Exception{MediaPlayer p=player();return p!=null&&(Boolean)field(layer(),"prepared")&&p.isPlaying();}
    int wraps(MediaPlayer expected,int count,int timeout)throws Exception{int wraps=0,last=-1;long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end&&wraps<count){int[] pos={-1};main(()->{try{if(player()!=expected)throw new AssertionError("Normal playback recreated decoder");if(expected.isPlaying())pos[0]=expected.getCurrentPosition();}catch(Exception e){throw new AssertionError(e);}});if(pos[0]>=0){if(last>pos[0]+250)wraps++;last=pos[0];}Thread.sleep(100);}report.append("observed native wraps=").append(wraps).append('\n');return wraps;}
    void run(){Context ctx=test.getTargetContext();TestPreferences saved=new TestPreferences(ctx);Config cfg=new Config(ctx);File original=new File(ctx.getFilesDir(),"wallpaper"),backup=new File(ctx.getFilesDir(),"wallpaper-video-loop-backup");boolean moved=false,touched=false,success=false;Bundle result=new Bundle();try{
        await("admitted focused desktop",15000,()->{activity=(MainActivity)((java.lang.ref.WeakReference<?>)field(MapWorkspace.get(ctx),"main")).get();return activity!=null&&!activity.isFinishing()&&activity.hasWindowFocus();});
        if(backup.exists())throw new IOException("Unexpected wallpaper backup");
        // Release the old file's decoder before replacing it.
        main(()->{try{layer().pause();}catch(Exception e){throw new AssertionError(e);}});
        if(original.exists()){if(!original.renameTo(backup))throw new IOException("Cannot back up wallpaper");moved=true;}touched=true;
        String source=args.getString("videoSource");
        if(source!=null&&(!source.startsWith("video-loop-")||source.contains("/")||source.contains("\\")))throw new IOException("Invalid video fixture");
        try(InputStream in=source==null?test.getContext().getAssets().open("wallpaper-test.mp4"):new FileInputStream(new File(ctx.getFilesDir(),source));OutputStream out=new FileOutputStream(original)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        main(()->{cfg.put("wallpaperKind","video");activity.render();});await("video starts",15000,()->playing());
        WallpaperLayer initial=layer();MediaPlayer first=player();int duration=first.getDuration();report.append("source=").append(source==null?"asset":source).append(" duration=").append(duration).append('\n');
        require(duration>0&&first.isLooping(),"native looping enabled after preparation");
        long frames=(Long)field(initial,"videoFrames");int count=source==null?3:2;
        require(wraps(first,count,Math.max(15000,duration*(count+1)+5000))>=count,"actual decoder crosses "+count+" complete end-to-start loops");
        require((Long)field(initial,"videoFrames")>frames+30,"texture receives actual video frames across loops");
        if(source==null){
            main(()->{first.setOnSeekCompleteListener(null);first.setLooping(false);first.seekTo(Math.max(0,duration-250));});
            await("completion without seek callback",6000,()->player()==first&&first.isLooping()&&first.isPlaying()&&first.getCurrentPosition()<duration-350);
            require(player()==first,"lost seek callback recovers immediately on the same decoder");
            await("observed progress after callback recovery",6000,()->!((VideoLoopGuard)field(initial,"loopGuard")).recovering()&&first.isPlaying());
            main(()->{first.setOnCompletionListener(null);first.setLooping(false);first.seekTo(Math.max(0,duration-250));});
            await("missing completion and seek callbacks",10000,()->player()==first&&first.isLooping()&&first.isPlaying()&&first.getCurrentPosition()<duration-350);
            require(player()==first,"watchdog restores looping even when both callbacks are absent");
            for(int page:new int[]{MainActivity.MINIMAL,MainActivity.CARDS,MainActivity.APPS}){
                main(()->activity.showPage(page));Thread.sleep(350);require(layer()==initial&&player()==first&&first.isPlaying(),"page "+page+" keeps one decoder and video view");
            }
            main(()->activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW));
            require(player()==first&&first.isPlaying(),"ordinary low-memory hint does not permanently stop the visible video");
            main(()->activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
            require(player()==null&&(Boolean)field(initial,"memorySuspended"),"critical memory pressure releases decoder");
            await("memory recovery restores video without leaving desktop",16000,()->playing());
            MediaPlayer recovered=player();require(recovered!=first&&recovered.isLooping(),"memory recovery creates a looping decoder");
            require(wraps(recovered,1,Math.max(8000,duration*2+2000))>=1,"memory-recovered video actually loops");
            main(()->{try{((VideoLoopGuard)field(initial,"loopGuard")).restarting(SystemClock.uptimeMillis()-5000);recovered.pause();((Runnable)field(initial,"videoCheck")).run();}catch(Exception e){throw new AssertionError(e);}});
            await("stalled seek recreates decoder",12000,()->playing()&&player()!=recovered);
            require(player()!=recovered,"seek recovery timeout rebuilds the decoder instead of waiting forever");
            main(()->activity.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
            Activity settings=test.startActivitySync(new Intent(ctx,SettingsActivity.class).putExtra("category",7).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();
            main(()->{try{((Runnable)field(initial,"memoryRetry")).run();}catch(Exception e){throw new AssertionError(e);}});
            require(field(initial,"player")==null&&!(Boolean)field(initial,"active"),"leaving desktop cancels recovery and releases decoder");
            main(settings::finish);await("video resumes on returning from settings",16000,()->activity.hasWindowFocus()&&playing());
            require(player().isLooping(),"returned desktop restores looping");
        }
        success=true;
    }catch(Throwable e){report.append("FAIL ").append(e).append('\n');}
    finally{try{
        if(activity!=null)main(()->{try{layer().pause();}catch(Exception e){throw new AssertionError(e);}});
        if(touched){if(original.exists()&&!original.delete())throw new IOException("Cannot remove test video");if(moved&&!backup.renameTo(original))throw new IOException("Cannot restore wallpaper");}
        saved.close();if(activity!=null&&!activity.isFinishing())main(activity::render);
    }catch(Throwable e){success=false;report.append("RESTORE FAIL ").append(e).append('\n');}}
    report.append("RESULT video-loop checks=").append(passed).append(success?" PASS":" FAIL").append('\n');result.putString("stream",report.toString());test.finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
